package com.metaltech.controller;

import com.metaltech.model.LeituraSensor;
import com.metaltech.repository.LeituraSensorRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ApiGlobalController {

    private static final ZoneOffset ZONE_SP = ZoneOffset.of("-03:00");
    private final LeituraSensorRepository repository;

    private final List usersStore = new CopyOnWriteArrayList();
    private final List stationsStore = new CopyOnWriteArrayList();
    private final List alertsStore = new CopyOnWriteArrayList();
    private final List auditLogsStore = new CopyOnWriteArrayList();
    private final Map settingsStore = new ConcurrentHashMap();
    private final Map activeTokens = new ConcurrentHashMap();

    public ApiGlobalController(LeituraSensorRepository repository) {
        this.repository = repository;
        inicializarDadosBase();
    }

    private void inicializarDadosBase() {
        // 1. Usuário Admin (acesso total e protegido contra exclusão)
        Map<String, Object> admin = new ConcurrentHashMap<>();
        admin.put("id", "USR-001");
        admin.put("name", "Admin");
        admin.put("email", "adm@metaltech.com.br");
        admin.put("password", "123");
        admin.put("role", "Administrador");
        admin.put("status", "Ativo");
        admin.put("permissions", List.of(
                "Monitorar e consultar leituras",
                "Reconhecer alertas",
                "Exportar análises e históricos",
                "Editar regras, estações e usuários"
        ));
        usersStore.add(admin);

        // 2. Usuário Comum - Rhuan (somente visualização)
        Map<String, Object> rhuan = new ConcurrentHashMap<>();
        rhuan.put("id", "USR-002");
        rhuan.put("name", "Rhuan");
        rhuan.put("email", "rhuan@metaltech.com.br");
        rhuan.put("password", "123");
        rhuan.put("role", "Analista");
        rhuan.put("status", "Ativo");
        rhuan.put("permissions", List.of(
                "Monitorar e consultar leituras"
        ));
        usersStore.add(rhuan);

        stationsStore.add(new ConcurrentHashMap<>(Map.of(
                "id", "ET-001", "name", "Esteira de triagem 01", "location", "Linha A · Fábrica São Paulo",
                "status", "Em operação", "shift", "Turno 06:00–14:32", "controllerId", "CLP-001"
        )));

        alertsStore.add(new ConcurrentHashMap<>(Map.of(
                "id", "AL-078", "title", "Reservatório próximo da capacidade", "occurredAt", nowIso(),
                "status", "Em aberto", "severity", "Atenção", "reservoirLevel", 78, "stationId", "ET-001"
        )));

        settingsStore.put("alertThresholds", new ConcurrentHashMap<>(Map.of(
                "attention", 75, "critical", 90, "variationInFiveMinutes", 10
        )));
        settingsStore.put("collection", new ConcurrentHashMap<>(Map.of(
                "intervalSeconds", 2, "refreshSeconds", 2
        )));
        settingsStore.put("integration", new ConcurrentHashMap<>(Map.of(
                "apiUrl", "http://10.110.12.104:8080/api/v1", "controllerId", "CLP-001", "protocol", "HTTP", "version", "v1"
        )));
        settingsStore.put("station", new ConcurrentHashMap<>(Map.of(
                "name", "Esteira de triagem 01", "location", "Linha A · Fábrica São Paulo", "reservoirCapacity", 1000, "reservoirHeight", 100
        )));
        settingsStore.put("security", new ConcurrentHashMap<>(Map.of(
                "twoFactorEnabled", true, "auditEnabled", true, "sessionTimeoutMinutes", 30
        )));

        registrarAuditoria("USR-001", "SYSTEM_INIT", "Backend MetalTech API v1 inicializado com Admin e Rhuan");
    }

    // ==================== HEALTH & REALTIME SSE ====================

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "metaltech-api",
                "timestamp", nowIso()
        );
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents() {
        SseEmitter emitter = new SseEmitter(0L);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        Runnable task = () -> {
            try {
                List<LeituraSensor> todas = repository.findAll();
                Map<String, Object> payload = Map.of(
                        "type", "sync",
                        "data", Map.of(
                                "users", usersStore,
                                "alerts", alertsStore,
                                "readings", mapearReadings(todas),
                                "sensors", montarSensores(todas),
                                "production", montarProducaoPorHora(todas),
                                "settings", settingsStore,
                                "station", stationsStore.isEmpty() ? Map.of() : stationsStore.get(0),
                                "reservoir", montarStatusReservatorio(todas),
                                "overview", montarOverview(todas)
                        )
                );
                emitter.send(SseEmitter.event().name("message").data(payload));
            } catch (IOException | IllegalStateException e) {
                executor.shutdown();
                emitter.complete();
            } catch (Exception e) {
                executor.shutdown();
                emitter.completeWithError(e);
            }
        };

        executor.scheduleAtFixedRate(task, 0, 2, TimeUnit.SECONDS);
        emitter.onCompletion(executor::shutdown);
        emitter.onTimeout(() -> {
            executor.shutdown();
            emitter.complete();
        });
        emitter.onError(ex -> executor.shutdown());

        return emitter;
    }

    // ==================== AUTH ====================

    @PostMapping("/auth/login")
    public ResponseEntity login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");

        if (email.isEmpty() || password.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "E-mail e senha são obrigatórios.", "INVALID_CREDENTIALS");
        }

        Map<String, Object> user = null;
        for (Object obj : usersStore) {
            Map<String, Object> u = (Map<String, Object>) obj;
            if (email.equalsIgnoreCase(String.valueOf(u.get("email")))) {
                user = u;
                break;
            }
        }

        if (user == null) {
            return error(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.", "INVALID_CREDENTIALS");
        }

        String savedPassword = String.valueOf(user.getOrDefault("password", "123"));
        if (!savedPassword.equals(password)) {
            return error(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.", "INVALID_CREDENTIALS");
        }

        String token = "mt-jwt-" + UUID.randomUUID();
        String refreshToken = "mt-ref-" + UUID.randomUUID();
        activeTokens.put(token, user);
        registrarAuditoria(String.valueOf(user.get("id")), "AUTH_LOGIN", "Login realizado: " + email);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "refreshToken", refreshToken,
                "user", user
        ));
    }

    @PostMapping("/auth/register")
    public ResponseEntity register(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "123");

        if (name.isEmpty() || email.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "Nome e e-mail são obrigatórios.", "VALIDATION_ERROR");
        }

        boolean exists = usersStore.stream().anyMatch(u -> email.equalsIgnoreCase(String.valueOf(((Map) u).get("email"))));
        if (exists) {
            return error(HttpStatus.CONFLICT, "E-mail já cadastrado.", "EMAIL_EXISTS");
        }

        String newId = gerarProximoUserId();
        Map<String, Object> newUser = new ConcurrentHashMap<>();
        newUser.put("id", newId);
        newUser.put("name", name);
        newUser.put("email", email);
        newUser.put("password", password);
        newUser.put("role", "Operador");
        newUser.put("status", "Pendente");
        newUser.put("permissions", List.of("Monitorar e consultar leituras"));

        usersStore.add(newUser);
        registrarAuditoria(newId, "AUTH_REGISTER", "Cadastro público aguardando aprovação: " + email);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Cadastro realizado com sucesso. Aguardando aprovação do administrador.",
                "user", newUser
        ));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity logout(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token != null) {
            activeTokens.remove(token);
        }
        return ResponseEntity.ok(Map.of("message", "Sessão encerrada com sucesso."));
    }

    @GetMapping("/auth/me")
    public ResponseEntity me(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token != null && activeTokens.containsKey(token)) {
            return ResponseEntity.ok((Map<String, Object>) activeTokens.get(token));
        }
        return ResponseEntity.ok((Map<String, Object>) usersStore.get(0));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity refresh(@RequestBody(required = false) Map<String, String> body) {
        String newToken = "mt-jwt-" + UUID.randomUUID();
        String newRefresh = "mt-ref-" + UUID.randomUUID();
        activeTokens.put(newToken, usersStore.get(0));
        return ResponseEntity.ok(Map.of(
                "token", newToken,
                "refreshToken", newRefresh
        ));
    }

    // ==================== USERS ====================

    private synchronized String gerarProximoUserId() {
        int maxId = 99;
        for (Object obj : usersStore) {
            Map<String, Object> u = (Map<String, Object>) obj;
            String idStr = String.valueOf(u.getOrDefault("id", ""));
            String numericPart = idStr.replaceAll("[^0-9]", "");
            if (!numericPart.isEmpty()) {
                try {
                    int val = Integer.parseInt(numericPart);
                    if (val > maxId) {
                        maxId = val;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return String.format("USR-%03d", maxId + 1);
    }

    @GetMapping("/users")
    public List listUsers() {
        return usersStore;
    }

    @PostMapping("/users")
    public ResponseEntity createUser(@RequestBody Map<String, Object> body) {
        String email = String.valueOf(body.getOrDefault("email", "")).trim().toLowerCase();
        if (!email.isEmpty()) {
            boolean exists = usersStore.stream().anyMatch(u -> email.equalsIgnoreCase(String.valueOf(((Map) u).get("email"))));
            if (exists) {
                return error(HttpStatus.CONFLICT, "E-mail já cadastrado.", "EMAIL_EXISTS");
            }
        }

        String id = gerarProximoUserId();
        String role = String.valueOf(body.getOrDefault("role", "Operador"));
        Map<String, Object> user = new ConcurrentHashMap<>();
        user.put("id", id);
        user.put("name", body.getOrDefault("name", "Novo Usuário"));
        user.put("email", email.isEmpty() ? id.toLowerCase() + "@metaltech.com.br" : email);
        user.put("password", body.getOrDefault("password", "123"));
        user.put("role", role);
        user.put("status", body.getOrDefault("status", "Ativo"));
        user.put("permissions", body.getOrDefault("permissions", List.of("Monitorar e consultar leituras")));

        usersStore.add(user);
        registrarAuditoria("USR-001", "USER_CREATE", "Usuário criado: " + id);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity updateUser(@PathVariable String id, @RequestBody Map<String, Object> body) {
        for (Object obj : usersStore) {
            Map<String, Object> u = (Map<String, Object>) obj;
            if (id.equals(u.get("id"))) {
                boolean isMainAdmin = "USR-001".equalsIgnoreCase(id)
                        || "adm@metaltech.com.br".equalsIgnoreCase(String.valueOf(u.getOrDefault("email", "")));

                body.forEach((k, v) -> {
                    if (v != null && !"id".equals(k)) {
                        if (isMainAdmin && ("role".equals(k) || "status".equals(k))) {
                            return;
                        }
                        u.put(k, v);
                    }
                });
                registrarAuditoria("USR-001", "USER_UPDATE", "Usuário atualizado: " + id);
                return ResponseEntity.ok(u);
            }
        }
        return error(HttpStatus.NOT_FOUND, "Usuário não encontrado.", "USER_NOT_FOUND");
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity deleteUser(@PathVariable String id) {
        for (Object obj : usersStore) {
            Map<String, Object> u = (Map<String, Object>) obj;
            if (id.equals(u.get("id"))) {
                String email = String.valueOf(u.getOrDefault("email", ""));
                if ("USR-001".equalsIgnoreCase(id) || "adm@metaltech.com.br".equalsIgnoreCase(email)) {
                    return error(HttpStatus.FORBIDDEN, "O perfil Administrador principal não pode ser excluído.", "ADMIN_PROTECTED");
                }
                break;
            }
        }

        boolean removed = usersStore.removeIf(u -> id.equals(((Map) u).get("id")));
        if (!removed) {
            return error(HttpStatus.NOT_FOUND, "Usuário não encontrado.", "USER_NOT_FOUND");
        }
        registrarAuditoria("USR-001", "USER_DELETE", "Usuário removido: " + id);
        return ResponseEntity.ok(Map.of("message", "Usuário removido com sucesso.", "id", id));
    }

    // ==================== STATIONS ====================

    @GetMapping("/stations/current")
    public ResponseEntity currentStation() {
        return ResponseEntity.ok(stationsStore.isEmpty() ? Map.of() : (Map<String, Object>) stationsStore.get(0));
    }

    @GetMapping("/stations")
    public List listStations() {
        return stationsStore;
    }

    @GetMapping("/stations/{id}")
    public ResponseEntity detailStation(@PathVariable String id) {
        for (Object obj : stationsStore) {
            Map<String, Object> s = (Map<String, Object>) obj;
            if (id.equals(s.get("id"))) {
                return ResponseEntity.ok(s);
            }
        }
        return error(HttpStatus.NOT_FOUND, "Estação não encontrada.", "STATION_NOT_FOUND");
    }

    @PostMapping("/stations")
    public ResponseEntity createStation(@RequestBody Map<String, Object> body) {
        String id = String.format("ET-%03d", stationsStore.size() + 1);
        Map<String, Object> st = new ConcurrentHashMap<>();
        st.put("id", id);
        st.put("name", body.getOrDefault("name", "Estação " + id));
        st.put("location", body.getOrDefault("location", "Linha A · Fábrica São Paulo"));
        st.put("status", body.getOrDefault("status", "Em operação"));
        st.put("shift", body.getOrDefault("shift", "Turno 06:00–14:32"));
        st.put("controllerId", body.getOrDefault("controllerId", "CLP-001"));
        stationsStore.add(st);
        registrarAuditoria("USR-001", "STATION_CREATE", "Estação criada: " + id);
        return ResponseEntity.status(HttpStatus.CREATED).body(st);
    }

    @PatchMapping("/stations/{id}")
    public ResponseEntity updateStation(@PathVariable String id, @RequestBody Map<String, Object> body) {
        for (Object obj : stationsStore) {
            Map<String, Object> st = (Map<String, Object>) obj;
            if (id.equals(st.get("id"))) {
                body.forEach((k, v) -> {
                    if (v != null && !"id".equals(k)) st.put(k, v);
                });
                registrarAuditoria("USR-001", "STATION_UPDATE", "Estação atualizada: " + id);
                return ResponseEntity.ok(st);
            }
        }
        return error(HttpStatus.NOT_FOUND, "Estação não encontrada.", "STATION_NOT_FOUND");
    }

    @DeleteMapping("/stations/{id}")
    public ResponseEntity deleteStation(@PathVariable String id) {
        boolean removed = stationsStore.removeIf(st -> id.equals(((Map) st).get("id")));
        if (!removed) {
            return error(HttpStatus.NOT_FOUND, "Estação não encontrada.", "STATION_NOT_FOUND");
        }
        registrarAuditoria("USR-001", "STATION_DELETE", "Estação removida: " + id);
        return ResponseEntity.ok(Map.of("message", "Estação removida.", "id", id));
    }

    // ==================== SENSORS ====================

    @GetMapping("/sensors")
    public List listSensors() {
        return montarSensores(repository.findAll());
    }

    @GetMapping("/sensors/readings")
    public List sensorReadings(
            @RequestParam(required = false) String stationId,
            @RequestParam(required = false) String sensorId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit) {
        return filtrarEPaginarReadings(null, from, to, page, limit);
    }

    // ==================== READINGS ====================

    @GetMapping("/readings")
    public List listReadings(
            @RequestParam(required = false) String stationId,
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "200") int limit) {
        return filtrarEPaginarReadings(classification, from, to, page, limit);
    }

    @GetMapping("/readings/{id}")
    public ResponseEntity detailReading(@PathVariable String id) {
        String numericStr = id.replaceAll("[^0-9]", "");
        if (!numericStr.isEmpty()) {
            Long dbId = Long.parseLong(numericStr);
            Optional<LeituraSensor> found = repository.findById(dbId);
            if (found.isPresent()) {
                return ResponseEntity.ok(mapearLeituraUnica(found.get()));
            }
        }
        return error(HttpStatus.NOT_FOUND, "Leitura não encontrada.", "READING_NOT_FOUND");
    }

    @GetMapping("/readings/export")
    public List exportReadings(
            @RequestParam(required = false) String stationId,
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return filtrarEPaginarReadings(classification, from, to, 1, 5000);
    }

    // ==================== RESERVOIR & PERMISSIONS ====================

    @GetMapping("/reservoir/status")
    public Map<String, Object> reservoirStatus() {
        return montarStatusReservatorio(repository.findAll());
    }

    @GetMapping("/permissions/roles")
    public Map<String, Object> rolePermissions() {
        List matrix = List.of(
                Map.of("resource", "Monitorar e consultar leituras", "operator", "Permitido", "analyst", "Permitido", "administrator", "Permitido"),
                Map.of("resource", "Reconhecer alertas", "operator", "Permitido", "analyst", "Somente leitura", "administrator", "Permitido"),
                Map.of("resource", "Exportar análises e históricos", "operator", "Não permitido", "analyst", "Permitido", "administrator", "Permitido"),
                Map.of("resource", "Editar regras, estações e usuários", "operator", "Não permitido", "analyst", "Não permitido", "administrator", "Permitido")
        );
        return Map.of("data", matrix);
    }

    // ==================== ANALYTICS ====================

    @GetMapping("/analytics/overview")
    public Map<String, Object> analyticsOverview() {
        return montarOverview(repository.findAll());
    }

    @GetMapping("/analytics/production")
    public List analyticsProduction(
            @RequestParam(required = false) String stationId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String interval) {
        return montarProducaoPorHora(repository.findAll());
    }

    @GetMapping("/analytics/reservoir")
    public List analyticsReservoir(
            @RequestParam(required = false) String stationId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String interval) {
        List<LeituraSensor> todas = repository.findAll();
        return todas.stream()
                .sorted(Comparator.comparing(LeituraSensor::getDataHora, Comparator.nullsLast(Comparator.naturalOrder())))
                .skip(Math.max(0, todas.size() - 50))
                .map(l -> {
                    double dist = l.getNivelReservatorioCm() != null ? l.getNivelReservatorioCm() : 0.0;
                    int level = calcularPercentualNivel(dist);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("timestamp", formatarDataHora(l.getDataHora()));
                    item.put("level", level);
                    item.put("distance", Math.round(dist * 10.0) / 10.0);
                    return item;
                })
                .collect(Collectors.toList());
    }

    // ==================== ALERTS ====================

    @GetMapping("/alerts")
    public List listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String stationId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return (List) alertsStore.stream()
                .filter(a -> status == null || status.isBlank() || status.equalsIgnoreCase(String.valueOf(((Map) a).get("status"))))
                .filter(a -> severity == null || severity.isBlank() || severity.equalsIgnoreCase(String.valueOf(((Map) a).get("severity"))))
                .filter(a -> stationId == null || stationId.isBlank() || stationId.equalsIgnoreCase(String.valueOf(((Map) a).get("stationId"))))
                .collect(Collectors.toList());
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity detailAlert(@PathVariable String id) {
        for (Object obj : alertsStore) {
            Map<String, Object> a = (Map<String, Object>) obj;
            if (id.equals(a.get("id"))) {
                return ResponseEntity.ok(a);
            }
        }
        return error(HttpStatus.NOT_FOUND, "Alerta não encontrado.", "ALERT_NOT_FOUND");
    }

    @PatchMapping("/alerts/{id}")
    public ResponseEntity updateAlert(@PathVariable String id, @RequestBody Map<String, Object> body) {
        for (Object obj : alertsStore) {
            Map<String, Object> alert = (Map<String, Object>) obj;
            if (id.equals(alert.get("id"))) {
                body.forEach((k, v) -> {
                    if (v != null && !"id".equals(k)) alert.put(k, v);
                });
                registrarAuditoria("USR-001", "ALERT_UPDATE", "Alerta atualizado: " + id + " -> " + alert.get("status"));
                return ResponseEntity.ok(alert);
            }
        }
        return error(HttpStatus.NOT_FOUND, "Alerta não encontrado.", "ALERT_NOT_FOUND");
    }

    // ==================== SETTINGS & AUDIT ====================

    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        return settingsStore;
    }

    @PutMapping("/settings")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> body) {
        body.forEach((k, v) -> {
            if (v != null) settingsStore.put(k, v);
        });
        registrarAuditoria("USR-001", "SETTINGS_UPDATE", "Configurações operacionais atualizadas");
        return settingsStore;
    }

    @GetMapping("/audit-logs")
    public List listAuditLogs(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit) {
        return (List) auditLogsStore.stream()
                .filter(log -> actorId == null || actorId.isBlank() || actorId.equalsIgnoreCase(String.valueOf(((Map) log).get("actorId"))))
                .filter(log -> action == null || action.isBlank() || action.equalsIgnoreCase(String.valueOf(((Map) log).get("action"))))
                .skip((long) Math.max(0, page - 1) * Math.max(1, limit))
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    // ==================== EXCEPTION HANDLER ====================

    @ExceptionHandler(Exception.class)
    public ResponseEntity handleAllExceptions(Exception ex) {
        if (ex instanceof IOException || (ex.getMessage() != null && ex.getMessage().contains("Broken pipe"))) {
            return null;
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno: " + ex.getMessage(), "INTERNAL_ERROR");
    }

    // ==================== HELPERS DE MAPEAMENTO ====================

    private List filtrarEPaginarReadings(String classification, String from, String to, int page, int limit) {
        List mapped = mapearReadings(repository.findAll());
        LocalDate fromDate = parseDateSafe(from);
        LocalDate toDate = parseDateSafe(to);

        return (List) mapped.stream()
                .filter(r -> {
                    if (classification == null || classification.isBlank() || "Todas".equalsIgnoreCase(classification)) return true;
                    String cls = String.valueOf(((Map) r).get("classification"));
                    return cls.equalsIgnoreCase(classification)
                            || (classification.toLowerCase().contains("nao") && cls.toLowerCase().contains("não"))
                            || (classification.toLowerCase().contains("não") && cls.toLowerCase().contains("nao"));
                })
                .filter(r -> {
                    if (fromDate == null && toDate == null) return true;
                    LocalDate itemDate = parseDateSafe(String.valueOf(((Map) r).get("timestamp")));
                    if (itemDate == null) return true;
                    if (fromDate != null && itemDate.isBefore(fromDate)) return false;
                    if (toDate != null && itemDate.isAfter(toDate)) return false;
                    return true;
                })
                .skip((long) Math.max(0, page - 1) * Math.max(1, limit))
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    private List mapearReadings(List<LeituraSensor> leituras) {
        return leituras.stream()
                .sorted(Comparator.comparing(LeituraSensor::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapearLeituraUnica)
                .collect(Collectors.toList());
    }

    private Map<String, Object> mapearLeituraUnica(LeituraSensor l) {
        String rawTipo = l.getTipoPeca() != null ? l.getTipoPeca().toUpperCase() : "";
        boolean isMetal = rawTipo.contains("METAL") && !rawTipo.contains("NAO");
        boolean isNaoMetal = rawTipo.contains("NAO_METAL");

        String classification = isMetal ? "Metálica" : (isNaoMetal ? "Não metálica" : "Leitura de nível");
        boolean inductive = Boolean.TRUE.equals(l.getIndutivoAtivo()) || isMetal;
        boolean capacitive = Boolean.TRUE.equals(l.getCapacitivoAtivo()) || isMetal || isNaoMetal;

        double dist = l.getNivelReservatorioCm() != null ? l.getNivelReservatorioCm() : 0.0;
        int reservoirLevel = calcularPercentualNivel(dist);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", "LT-" + (l.getId() != null ? l.getId() : 0));
        map.put("timestamp", formatarDataHora(l.getDataHora()));
        map.put("classification", classification);
        map.put("inductive", inductive);
        map.put("capacitive", capacitive);
        map.put("distance", Math.round(dist * 10.0) / 10.0);
        map.put("reservoirLevel", reservoirLevel);
        map.put("status", "Recebida");
        return map;
    }

    private List montarSensores(List<LeituraSensor> leituras) {
        LeituraSensor ultima = leituras.stream()
                .max(Comparator.comparing(LeituraSensor::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        double dist = (ultima != null && ultima.getNivelReservatorioCm() != null) ? ultima.getNivelReservatorioCm() : 0.0;
        boolean indutivo = ultima != null && Boolean.TRUE.equals(ultima.getIndutivoAtivo());
        boolean capacitivo = ultima != null && Boolean.TRUE.equals(ultima.getCapacitivoAtivo());
        String lastSeen = ultima != null ? formatarDataHora(ultima.getDataHora()) : nowIso();

        Map<String, Object> sd = new LinkedHashMap<>();
        sd.put("id", "SD-001");
        sd.put("type", "distance");
        sd.put("technology", "Ultrassônico");
        sd.put("value", Math.round(dist * 10.0) / 10.0);
        sd.put("unit", "cm");
        sd.put("status", "Online");
        sd.put("lastSeenAt", lastSeen);

        Map<String, Object> si = new LinkedHashMap<>();
        si.put("id", "SI-001");
        si.put("type", "inductive");
        si.put("technology", "Detecção de metal");
        si.put("value", indutivo);
        si.put("unit", null);
        si.put("status", "Online");
        si.put("lastSeenAt", lastSeen);

        Map<String, Object> sc = new LinkedHashMap<>();
        sc.put("id", "SC-001");
        sc.put("type", "capacitive");
        sc.put("technology", "Presença de material");
        sc.put("value", capacitivo);
        sc.put("unit", null);
        sc.put("status", "Online");
        sc.put("lastSeenAt", lastSeen);

        return List.of(sd, si, sc);
    }

    private Map<String, Object> montarStatusReservatorio(List<LeituraSensor> leituras) {
        LeituraSensor ultima = leituras.stream()
                .max(Comparator.comparing(LeituraSensor::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        double dist = (ultima != null && ultima.getNivelReservatorioCm() != null) ? ultima.getNivelReservatorioCm() : 0.0;
        int currentLevel = calcularPercentualNivel(dist);

        long pecasArmazenadas = leituras.stream()
                .filter(l -> l.getTipoPeca() != null && l.getTipoPeca().toUpperCase().contains("METAL"))
                .count();

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("currentLevel", currentLevel);
        map.put("distance", Math.round(dist * 10.0) / 10.0);
        map.put("capacity", 1000);
        map.put("attentionThreshold", 75);
        map.put("criticalThreshold", 90);
        map.put("storedPieces", pecasArmazenadas);
        map.put("updatedAt", ultima != null ? formatarDataHora(ultima.getDataHora()) : nowIso());
        return map;
    }

    private Map<String, Object> montarOverview(List<LeituraSensor> leituras) {
        long metallic = leituras.stream()
                .filter(l -> l.getTipoPeca() != null && l.getTipoPeca().equalsIgnoreCase("METALICO"))
                .count();
        long nonMetallic = leituras.stream()
                .filter(l -> l.getTipoPeca() != null && l.getTipoPeca().equalsIgnoreCase("NAO_METALICO"))
                .count();
        long total = metallic + nonMetallic;

        int metallicPercent = total > 0 ? (int) Math.round((metallic * 100.0) / total) : 0;
        int nonMetallicPercent = total > 0 ? (100 - metallicPercent) : 0;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalPieces", total);
        map.put("metallicPieces", metallic);
        map.put("nonMetallicPieces", nonMetallic);
        map.put("metallicPercent", metallicPercent);
        map.put("nonMetallicPercent", nonMetallicPercent);
        map.put("period", "Turno atual");
        map.put("updatedAt", nowIso());
        return map;
    }

    private List montarProducaoPorHora(List<LeituraSensor> leituras) {
        Map<String, List<LeituraSensor>> porHora = new TreeMap<>();
        for (LeituraSensor l : leituras) {
            if (l.getDataHora() == null) continue;
            String hora = String.format("%02d:00", l.getDataHora().getHour());
            porHora.computeIfAbsent(hora, k -> new ArrayList<>()).add(l);
        }

        List resultado = new ArrayList<>();
        for (Map.Entry<String, List<LeituraSensor>> entry : porHora.entrySet()) {
            List<LeituraSensor> grupo = entry.getValue();
            long metallic = grupo.stream().filter(l -> "METALICO".equalsIgnoreCase(l.getTipoPeca())).count();
            long nonMetallic = grupo.stream().filter(l -> "NAO_METALICO".equalsIgnoreCase(l.getTipoPeca())).count();
            LeituraSensor ultimaHora = grupo.get(grupo.size() - 1);
            double dist = ultimaHora.getNivelReservatorioCm() != null ? ultimaHora.getNivelReservatorioCm() : 0.0;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", entry.getKey());
            item.put("metallic", metallic);
            item.put("nonMetallic", nonMetallic);
            item.put("reservoirLevel", calcularPercentualNivel(dist));
            resultado.add(item);
        }
        return resultado;
    }

    private int calcularPercentualNivel(double distanciaCm) {
        double alturaReservatorio = 100.0;
        Object stationCfg = settingsStore.get("station");
        if (stationCfg instanceof Map) {
            Map<?, ?> stMap = (Map<?, ?>) stationCfg;
            Object heightObj = stMap.get("reservoirHeight");
            if (heightObj instanceof Number) {
                alturaReservatorio = ((Number) heightObj).doubleValue();
            }
        }
        if (alturaReservatorio <= 0) alturaReservatorio = 100.0;
        double preenchido = Math.max(0.0, Math.min(alturaReservatorio, alturaReservatorio - distanciaCm));
        return (int) Math.round((preenchido / alturaReservatorio) * 100.0);
    }

    private String formatarDataHora(LocalDateTime dt) {
        if (dt == null) return nowIso();
        return dt.atOffset(ZONE_SP).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private String nowIso() {
        return OffsetDateTime.now(ZONE_SP).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private LocalDate parseDateSafe(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.substring(0, Math.min(10, raw.length())));
        } catch (Exception e) {
            return null;
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private void registrarAuditoria(String actorId, String action, String details) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", "AUD-" + (auditLogsStore.size() + 1));
        entry.put("actorId", actorId);
        entry.put("action", action);
        entry.put("details", details);
        entry.put("timestamp", nowIso());
        auditLogsStore.add(0, entry);
    }

    private ResponseEntity error(HttpStatus status, String message, String code) {
        return ResponseEntity.status(status).body(Map.of(
                "message", message,
                "code", code,
                "details", Map.of()
        ));
    }
}