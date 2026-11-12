package com.metaltech.controller;

import com.metaltech.model.LeituraSensor;
import com.metaltech.repository.LeituraSensorRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(
    origins = "*",
    allowedHeaders = "*",
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
@SuppressWarnings({"unchecked", "rawtypes"})
public class ApiGlobalController {

    private final LeituraSensorRepository repository;

    private final List usersStore = new CopyOnWriteArrayList();
    private final List stationsStore = new CopyOnWriteArrayList();
    private final List alertsStore = new CopyOnWriteArrayList();
    private final List auditLogsStore = new CopyOnWriteArrayList();
    private final Map settingsStore = new ConcurrentHashMap();
    private final Map activeTokens = new ConcurrentHashMap();
    private final Map refreshTokens = new ConcurrentHashMap();

    private static final List ALL_PERMISSIONS = List.of(
        "overview", "station", "analytics", "history", "alerts",
        "users", "settings", "exportData", "manageAlerts", "manageUsers", "manageSettings"
    );

    public ApiGlobalController(LeituraSensorRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void initData() {
        usersStore.add(createUserMap("USR-000", "admim", "admim@metaltech.com.br", hashPassword("123"), "Administrador", "Ativo", ALL_PERMISSIONS));
        usersStore.add(createUserMap("USR-001", "Rhuan Oliveira", "rhuan.oliveira@metaltech.com.br", hashPassword("MetalTech@2026"), "Administrador", "Ativo", ALL_PERMISSIONS));
        usersStore.add(createUserMap("USR-002", "Carla Oliveira", "carla.oliveira@metaltech.com.br", hashPassword("MetalTech@2026"), "Administrador", "Ativo", ALL_PERMISSIONS));
        usersStore.add(createUserMap("USR-003", "João Santos", "joao.santos@metaltech.com.br", hashPassword("MetalTech@2026"), "Operador", "Ativo", List.of("overview", "station", "history", "alerts", "manageAlerts")));
        usersStore.add(createUserMap("USR-004", "Ana Souza", "ana.souza@metaltech.com.br", hashPassword("MetalTech@2026"), "Analista", "Ativo", List.of("overview", "station", "analytics", "history", "alerts", "exportData")));
        usersStore.add(createUserMap("USR-005", "Bruno Lima", "bruno.lima@metaltech.com.br", hashPassword("MetalTech@2026"), "Operador", "Pendente", List.of("overview", "station", "history", "alerts")));

        Map st = new ConcurrentHashMap();
        st.put("id", "ET-001");
        st.put("name", "Esteira de triagem 01");
        st.put("location", "Linha A · Fábrica São Paulo");
        st.put("status", "Em operação");
        st.put("shift", "Turno 06:00–14:32");
        st.put("controllerId", "CLP-001");
        stationsStore.add(st);

        alertsStore.add(createAlertMap("ALT-001", "Reservatório próximo da capacidade", nowUtc(), "Em aberto", "Atenção", 78, "ET-001"));
        alertsStore.add(createAlertMap("ALT-002", "Enchimento acima do padrão", nowUtc(), "Reconhecido", "Atenção", 80, "ET-001"));
        alertsStore.add(createAlertMap("ALT-003", "Comunicação restabelecida", nowUtc(), "Resolvido", "Atenção", 45, "ET-001"));

        settingsStore.put("alertThresholds", new ConcurrentHashMap(Map.of("attention", 75, "critical", 90, "variationInFiveMinutes", 10)));
        settingsStore.put("collection", new ConcurrentHashMap(Map.of("intervalSeconds", 2, "refreshSeconds", 2)));
        settingsStore.put("integration", new ConcurrentHashMap(Map.of("apiUrl", "http://10.110.12.104:8080/api/v1", "controllerId", "CLP-001", "protocol", "HTTP", "version", "1.0.0")));
        settingsStore.put("station", new ConcurrentHashMap(Map.of("name", "Esteira de triagem 01", "location", "Linha A · Fábrica São Paulo", "reservoirCapacity", 1000, "reservoirHeight", 100)));
        settingsStore.put("security", new ConcurrentHashMap(Map.of("twoFactorEnabled", false, "auditEnabled", true, "sessionTimeoutMinutes", 30)));

        registrarAuditoria("USR-000", "admim", "SYSTEM_BOOT", "SYSTEM", "Inicialização da API MetalTech v1.");
    }

    // =========================================================================
    // 1. HEALTH & REALTIME (SSE)
    // =========================================================================
    @GetMapping("/health")
    public Map health() {
        return Map.of(
            "status", "ok",
            "message", "API MetalTech operacional.",
            "timestamp", nowUtc()
        );
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@RequestParam(required = false) String token) {
        SseEmitter emitter = new SseEmitter(0L);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        Runnable sendPayload = () -> {
            try {
                Map data = new HashMap();
                data.put("overview", buildOverviewData());
                data.put("reservoir", buildReservoirData());
                data.put("sensors", buildSensorsList());
                data.put("readings", buildReadingsList(null, 20));
                data.put("production", buildProductionList());
                data.put("alerts", alertsStore);
                data.put("station", stationsStore.isEmpty() ? Map.of() : stationsStore.get(0));
                data.put("settings", settingsStore);
                data.put("users", sanitizeUsersList());
                data.put("rolePermissions", getRolePermissionsList());

                /*Map eventEnvelope = Map.of("type", "snapshot", "data", data);
                emitter.send(SseEmitter.event().data(eventEnvelope));*/
                Map<String, Object> eventEnvelope = Map.of("type", "snapshot", "data", data);
                emitter.send(SseEmitter.event().data(Objects.requireNonNull(eventEnvelope)));
            } catch (Exception e) {
                executor.shutdownNow();
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        };

        executor.scheduleAtFixedRate(sendPayload, 0, 2, TimeUnit.SECONDS);
        emitter.onCompletion(executor::shutdownNow);
        emitter.onTimeout(() -> {
            executor.shutdownNow();
            emitter.complete();
        });
        emitter.onError((ex) -> executor.shutdownNow());

        return emitter;
    }

    // =========================================================================
    // 2. AUTENTICAÇÃO (/auth/*)
    // =========================================================================
    @PostMapping("/auth/login")
    public ResponseEntity login(@RequestBody(required = false) Map body) {
        if (body == null || body.get("email") == null || body.get("password") == null) {
            return errorResponse(HttpStatus.BAD_REQUEST, "E-mail e senha são obrigatórios.", "VALIDATION_ERROR", Map.of());
        }

        String email = String.valueOf(body.get("email")).trim();
        String password = String.valueOf(body.get("password"));

        Map foundUser = null;
        for (Object obj : usersStore) {
            Map u = (Map) obj;
            if (email.equalsIgnoreCase(String.valueOf(u.get("email"))) || email.equalsIgnoreCase(String.valueOf(u.get("name")))) {
                foundUser = u;
                break;
            }
        }

        if (foundUser == null || !hashPassword(password).equals(foundUser.get("passwordHash"))) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.", "INVALID_CREDENTIALS", Map.of());
        }

        String status = String.valueOf(foundUser.get("status"));
        if ("Pendente".equalsIgnoreCase(status)) {
            return errorResponse(HttpStatus.FORBIDDEN, "Cadastro pendente de aprovação por um administrador.", "ACCOUNT_PENDING", Map.of());
        }
        if ("Inativo".equalsIgnoreCase(status)) {
            return errorResponse(HttpStatus.FORBIDDEN, "Usuário inativo. Contate o administrador.", "ACCOUNT_INACTIVE", Map.of());
        }

        foundUser.put("lastLoginAt", nowUtc());
        String userId = String.valueOf(foundUser.get("id"));
        String token = "mt_tok_" + UUID.randomUUID().toString().replace("-", "");
        String refToken = "mt_ref_" + UUID.randomUUID().toString().replace("-", "");

        activeTokens.put(token, userId);
        refreshTokens.put(refToken, userId);

        registrarAuditoria(userId, String.valueOf(foundUser.get("name")), "LOGIN", userId, "Autenticação realizada com sucesso.");

        Map payload = new HashMap();
        payload.put("token", token);
        payload.put("refreshToken", refToken);
        payload.put("user", sanitizeUser(foundUser));

        return ResponseEntity.ok(Map.of("data", payload));
    }

    @PostMapping("/auth/register")
    public ResponseEntity register(@RequestBody(required = false) Map body) {
        if (body == null || body.get("name") == null || body.get("email") == null || body.get("password") == null) {
            return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Nome, e-mail e senha são obrigatórios.", "VALIDATION_ERROR", Map.of());
        }

        String name = String.valueOf(body.get("name")).trim();
        String email = String.valueOf(body.get("email")).trim().toLowerCase();
        String password = String.valueOf(body.get("password"));

        if (name.isEmpty() || !email.contains("@") || password.length() < 3) {
            return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Dados cadastrais inválidos.", "VALIDATION_ERROR", Map.of());
        }

        for (Object obj : usersStore) {
            Map u = (Map) obj;
            if (email.equalsIgnoreCase(String.valueOf(u.get("email")))) {
                return errorResponse(HttpStatus.CONFLICT, "Este e-mail já está cadastrado.", "EMAIL_CONFLICT", Map.of("email", "E-mail em uso."));
            }
        }

        String id = "USR-" + String.format("%03d", usersStore.size() + 1);
        Map newUser = createUserMap(id, name, email, hashPassword(password), "Operador", "Pendente", List.of("overview", "station", "history", "alerts"));
        usersStore.add(newUser);

        registrarAuditoria(id, name, "PUBLIC_REGISTER", id, "Solicitação de acesso público criada como Operador Pendente.");

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "Cadastro realizado. Aguarde aprovação de um administrador.",
            "data", sanitizeUser(newUser)
        ));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map actor = resolveAuthenticatedUser(authHeader);
        String token = extractBearerToken(authHeader);
        if (token != null) {
            activeTokens.remove(token);
        }
        if (actor != null) {
            registrarAuditoria(String.valueOf(actor.get("id")), String.valueOf(actor.get("name")), "LOGOUT", String.valueOf(actor.get("id")), "Sessão encerrada.");
        }
        return ResponseEntity.ok(Map.of("message", "Sessão encerrada com sucesso."));
    }

    @GetMapping("/auth/me")
    public ResponseEntity me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map user = resolveAuthenticatedUser(authHeader);
        if (user == null) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "Não autenticado.", "UNAUTHORIZED", Map.of());
        }
        return ResponseEntity.ok(Map.of("data", sanitizeUser(user)));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity refresh(@RequestBody(required = false) Map body) {
        if (body == null || body.get("refreshToken") == null) {
            return errorResponse(HttpStatus.BAD_REQUEST, "Refresh token obrigatório.", "VALIDATION_ERROR", Map.of());
        }
        String oldRefresh = String.valueOf(body.get("refreshToken"));
        String userId = (String) refreshTokens.remove(oldRefresh);
        if (userId == null) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "Refresh token inválido ou expirado.", "INVALID_REFRESH_TOKEN", Map.of());
        }

        String newToken = "mt_tok_" + UUID.randomUUID().toString().replace("-", "");
        String newRefresh = "mt_ref_" + UUID.randomUUID().toString().replace("-", "");
        activeTokens.put(newToken, userId);
        refreshTokens.put(newRefresh, userId);

        return ResponseEntity.ok(Map.of("data", Map.of("token", newToken, "refreshToken", newRefresh)));
    }

    // =========================================================================
    // 3. USUÁRIOS (/users)
    // =========================================================================
    @GetMapping("/users")
    public ResponseEntity listUsers(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map actor = resolveAuthenticatedUser(authHeader);
        if (actor == null) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "Não autenticado.", "UNAUTHORIZED", Map.of());
        }
        if (!hasPermission(actor, "users")) {
            return errorResponse(HttpStatus.FORBIDDEN, "Sem permissão para listar usuários.", "FORBIDDEN", Map.of());
        }
        return ResponseEntity.ok(Map.of("data", sanitizeUsersList()));
    }

    @PostMapping("/users")
    public ResponseEntity createUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map body
    ) {
        Map actor = resolveAuthenticatedUser(authHeader);
        if (actor == null) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "Não autenticado.", "UNAUTHORIZED", Map.of());
        }
        if (!hasPermission(actor, "manageUsers")) {
            return errorResponse(HttpStatus.FORBIDDEN, "Sem permissão para cadastrar usuários.", "FORBIDDEN", Map.of());
        }
        if (body == null || body.get("name") == null || body.get("email") == null) {
            return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Nome e e-mail são obrigatórios.", "VALIDATION_ERROR", Map.of());
        }

        String email = String.valueOf(body.get("email")).trim().toLowerCase();
        for (Object obj : usersStore) {
            Map u = (Map) obj;
            if (email.equalsIgnoreCase(String.valueOf(u.get("email")))) {
                return errorResponse(HttpStatus.CONFLICT, "E-mail já cadastrado.", "EMAIL_CONFLICT", Map.of());
            }
        }

        String role = String.valueOf(body.getOrDefault("role", "Operador"));
        String status = String.valueOf(body.getOrDefault("status", "Pendente"));
        String rawPwd = body.get("password") != null ? String.valueOf(body.get("password")) : "MetalTech@2026";
        List perms = body.get("permissions") instanceof List ? (List) body.get("permissions") : defaultPermissionsForRole(role);

        String id = "USR-" + String.format("%03d", usersStore.size() + 1);
        Map user = createUserMap(id, String.valueOf(body.get("name")).trim(), email, hashPassword(rawPwd), role, status, perms);
        usersStore.add(user);

        registrarAuditoria(String.valueOf(actor.get("id")), String.valueOf(actor.get("name")), "USER_CREATED", id, "Usuário criado via painel administrativo.");

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "Convite criado com sucesso.",
            "data", sanitizeUser(user)
        ));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity updateUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String id,
            @RequestBody Map body
    ) {
        Map actor = resolveAuthenticatedUser(authHeader);
        if (actor == null) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "Não autenticado.", "UNAUTHORIZED", Map.of());
        }
        if (!hasPermission(actor, "manageUsers")) {
            return errorResponse(HttpStatus.FORBIDDEN, "Sem permissão para editar usuários.", "FORBIDDEN", Map.of());
        }

        Map target = findUserById(id);
        if (target == null) {
            return errorResponse(HttpStatus.NOT_FOUND, "Usuário não encontrado.", "NOT_FOUND", Map.of());
        }

        // Impede auto-escalonamento de privilégios
        if (id.equals(actor.get("id")) && (body.containsKey("role") || body.containsKey("permissions"))) {
            return errorResponse(HttpStatus.FORBIDDEN, "Não é permitido alterar seu próprio perfil ou permissões.", "SELF_PRIVILEGE_ESCALATION", Map.of());
        }

        if (body.get("name") != null) target.put("name", String.valueOf(body.get("name")).trim());
        if (body.get("email") != null) target.put("email", String.valueOf(body.get("email")).trim().toLowerCase());
        if (body.get("role") != null) {
            String newRole = String.valueOf(body.get("role"));
            target.put("role", newRole);
            if (!body.containsKey("permissions")) {
                target.put("permissions", defaultPermissionsForRole(newRole));
            }
        }
        if (body.get("status") != null) target.put("status", String.valueOf(body.get("status")));
        if (body.get("permissions") instanceof List) target.put("permissions", new ArrayList((List) body.get("permissions")));

        registrarAuditoria(String.valueOf(actor.get("id")), String.valueOf(actor.get("name")), "USER_UPDATED", id, "Dados/permissões do usuário atualizados.");

        return ResponseEntity.ok(Map.of(
            "message", "Usuário atualizado com sucesso.",
            "data", sanitizeUser(target)
        ));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity deleteUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String id
    ) {
        Map actor = resolveAuthenticatedUser(authHeader);
        if (actor == null) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "Não autenticado.", "UNAUTHORIZED", Map.of());
        }
        if (!hasPermission(actor, "manageUsers")) {
            return errorResponse(HttpStatus.FORBIDDEN, "Sem permissão para remover usuários.", "FORBIDDEN", Map.of());
        }

        Map target = findUserById(id);
        if (target == null) {
            return errorResponse(HttpStatus.NOT_FOUND, "Usuário não encontrado.", "NOT_FOUND", Map.of());
        }

        // Impede exclusão do último administrador ativo
        if ("Administrador".equalsIgnoreCase(String.valueOf(target.get("role"))) && "Ativo".equalsIgnoreCase(String.valueOf(target.get("status")))) {
            long activeAdmins = 0;
            for (Object obj : usersStore) {
                Map u = (Map) obj;
                if ("Administrador".equalsIgnoreCase(String.valueOf(u.get("role"))) && "Ativo".equalsIgnoreCase(String.valueOf(u.get("status")))) {
                    activeAdmins++;
                }
            }
            if (activeAdmins <= 1) {
                return errorResponse(HttpStatus.CONFLICT, "Não é possível remover o último administrador ativo.", "LAST_ADMIN_PROTECTION", Map.of());
            }
        }

        usersStore.remove(target);
        registrarAuditoria(String.valueOf(actor.get("id")), String.valueOf(actor.get("name")), "USER_DELETED", id, "Usuário excluído do sistema.");

        return ResponseEntity.ok(Map.of("message", "Usuário removido com sucesso."));
    }

    // =========================================================================
    // 4. PERMISSÕES (/permissions/roles)
    // =========================================================================
    @GetMapping("/permissions/roles")
    public Map getRolePermissions() {
        return Map.of("data", getRolePermissionsList());
    }

    private List getRolePermissionsList() {
        List list = new ArrayList();
        list.add(Map.of("resource", "overview", "operator", true, "analyst", true, "administrator", true));
        list.add(Map.of("resource", "station", "operator", true, "analyst", true, "administrator", true));
        list.add(Map.of("resource", "analytics", "operator", false, "analyst", true, "administrator", true));
        list.add(Map.of("resource", "history", "operator", true, "analyst", true, "administrator", true));
        list.add(Map.of("resource", "alerts", "operator", true, "analyst", true, "administrator", true));
        list.add(Map.of("resource", "users", "operator", false, "analyst", false, "administrator", true));
        list.add(Map.of("resource", "settings", "operator", false, "analyst", false, "administrator", true));
        list.add(Map.of("resource", "exportData", "operator", false, "analyst", true, "administrator", true));
        list.add(Map.of("resource", "manageAlerts", "operator", true, "analyst", false, "administrator", true));
        list.add(Map.of("resource", "manageUsers", "operator", false, "analyst", false, "administrator", true));
        list.add(Map.of("resource", "manageSettings", "operator", false, "analyst", false, "administrator", true));
        return list;
    }

    // =========================================================================
    // 5. ESTAÇÕES (/stations)
    // =========================================================================
    @GetMapping("/stations/current")
    public Map getCurrentStation() {
        return Map.of("data", stationsStore.isEmpty() ? Map.of() : stationsStore.get(0));
    }

    @GetMapping("/stations")
    public Map listStations() {
        return Map.of("data", stationsStore);
    }

    @GetMapping("/stations/{id}")
    public ResponseEntity getStationById(@PathVariable String id) {
        for (Object obj : stationsStore) {
            Map s = (Map) obj;
            if (id.equals(s.get("id"))) {
                return ResponseEntity.ok(Map.of("data", s));
            }
        }
        return errorResponse(HttpStatus.NOT_FOUND, "Estação não encontrada.", "NOT_FOUND", Map.of());
    }

    @PostMapping("/stations")
    public ResponseEntity createStation(@RequestHeader(value = "Authorization", required = false) String authHeader, @RequestBody Map body) {
        Map actor = resolveAuthenticatedUser(authHeader);
        if (actor == null || !hasPermission(actor, "manageSettings")) {
            return errorResponse(HttpStatus.FORBIDDEN, "Sem permissão para criar estação.", "FORBIDDEN", Map.of());
        }
        String id = "ET-" + String.format("%03d", stationsStore.size() + 1);
        Map st = new ConcurrentHashMap(body);
        st.putIfAbsent("id", id);
        st.putIfAbsent("status", "Em operação");
        stationsStore.add(st);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "Estação cadastrada.", "data", st));
    }

    @PatchMapping("/stations/{id}")
    public ResponseEntity updateStation(@RequestHeader(value = "Authorization", required = false) String authHeader, @PathVariable String id, @RequestBody Map body) {
        Map actor = resolveAuthenticatedUser(authHeader);
        if (actor == null || !hasPermission(actor, "manageSettings")) {
            return errorResponse(HttpStatus.FORBIDDEN, "Sem permissão para editar estação.", "FORBIDDEN", Map.of());
        }
        for (Object obj : stationsStore) {
            Map st = (Map) obj;
            if (id.equals(st.get("id"))) {
                body.forEach((k, v) -> {
                    if (v != null) st.put(k, v);
                });
                return ResponseEntity.ok(Map.of("message", "Estação atualizada.", "data", st));
            }
        }
        return errorResponse(HttpStatus.NOT_FOUND, "Estação não encontrada.", "NOT_FOUND", Map.of());
    }

    @DeleteMapping("/stations/{id}")
    public ResponseEntity deleteStation(@RequestHeader(value = "Authorization", required = false) String authHeader, @PathVariable String id) {
        Map actor = resolveAuthenticatedUser(authHeader);
        if (actor == null || !hasPermission(actor, "manageSettings")) {
            return errorResponse(HttpStatus.FORBIDDEN, "Sem permissão para excluir estação.", "FORBIDDEN", Map.of());
        }
        stationsStore.removeIf(obj -> id.equals(((Map) obj).get("id")));
        return ResponseEntity.ok(Map.of("message", "Estação removida."));
    }

    // =========================================================================
    // 6. SENSORES (/sensors)
    // =========================================================================
    @GetMapping("/sensors")
    public Map listSensors() {
        return Map.of("data", buildSensorsList());
    }

    @GetMapping("/sensors/status")
    public Map sensorStatus() {
        return Map.of("data", buildSensorsList());
    }

    @GetMapping("/sensors/readings")
    public Map sensorReadings(@RequestParam(defaultValue = "50") int limit) {
        return Map.of("data", buildReadingsList(null, limit));
    }

    @GetMapping("/sensors/readings/latest")
    public Map sensorReadingsLatest() {
        return Map.of("data", buildSensorsList());
    }

    private List buildSensorsList() {
        List todas = repository.findAll();
        LeituraSensor ultima = todas.isEmpty() ? null : (LeituraSensor) todas.get(todas.size() - 1);

        double distancia = ultima != null ? ultima.getNivelReservatorioCm() : 22.0;
        boolean metal = ultima != null && "METALICO".equalsIgnoreCase(ultima.getTipoPeca());
        boolean presenca = ultima != null && ("METALICO".equalsIgnoreCase(ultima.getTipoPeca()) || "NAO_METALICO".equalsIgnoreCase(ultima.getTipoPeca()));
        String lastSeen = (ultima != null && ultima.getDataHora() != null)
                ? ultima.getDataHora().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
                : nowUtc();

        List list = new ArrayList();

        Map s1 = new HashMap();
        s1.put("id", "SD-001");
        s1.put("type", "distance");
        s1.put("technology", "Ultrassônico");
        s1.put("value", distancia);
        s1.put("unit", "cm");
        s1.put("status", "Online");
        s1.put("lastSeenAt", lastSeen);
        list.add(s1);

        Map s2 = new HashMap();
        s2.put("id", "SI-001");
        s2.put("type", "inductive");
        s2.put("technology", "Detecção de metal");
        s2.put("value", metal);
        s2.put("unit", null);
        s2.put("status", "Online");
        s2.put("lastSeenAt", lastSeen);
        list.add(s2);

        Map s3 = new HashMap();
        s3.put("id", "SC-001");
        s3.put("type", "capacitive");
        s3.put("technology", "Presença de material");
        s3.put("value", presenca);
        s3.put("unit", null);
        s3.put("status", "Online");
        s3.put("lastSeenAt", lastSeen);
        list.add(s3);

        return list;
    }

    // =========================================================================
    // 7. LEITURAS E HISTÓRICO (/readings)
    // =========================================================================
    @GetMapping("/readings")
    public Map readingsList(
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String stationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List allFiltered = buildReadingsList(classification, 1000);
        int total = allFiltered.size();
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, limit);
        int fromIdx = Math.min((safePage - 1) * safeLimit, total);
        int toIdx = Math.min(fromIdx + safeLimit, total);
        int totalPages = (int) Math.ceil((double) total / safeLimit);

        Map resp = new HashMap();
        resp.put("data", allFiltered.subList(fromIdx, toIdx));
        resp.put("pagination", Map.of(
            "page", safePage,
            "limit", safeLimit,
            "total", total,
            "totalPages", Math.max(1, totalPages)
        ));
        return resp;
    }

    @GetMapping("/readings/{id}")
    public ResponseEntity getReadingById(@PathVariable String id) {
        for (Object obj : buildReadingsList(null, 500)) {
            Map r = (Map) obj;
            if (id.equals(r.get("id"))) {
                return ResponseEntity.ok(Map.of("data", r));
            }
        }
        return errorResponse(HttpStatus.NOT_FOUND, "Leitura não encontrada.", "NOT_FOUND", Map.of());
    }

    @GetMapping("/readings/export")
    public ResponseEntity exportReadings(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String stationId
    ) {
        Map actor = resolveAuthenticatedUser(authHeader);
        if (actor != null && !hasPermission(actor, "exportData")) {
            return errorResponse(HttpStatus.FORBIDDEN, "Sem permissão para exportar históricos.", "FORBIDDEN", Map.of());
        }
        return ResponseEntity.ok(Map.of("data", buildReadingsList(classification, 1000)));
    }

    private List buildReadingsList(String classification, int limit) {
        List todas = repository.findAll();
        Collections.reverse(todas);

        List lista = new ArrayList();
        for (Object obj : todas) {
            if (lista.size() >= limit) break;
            LeituraSensor l = (LeituraSensor) obj;

            boolean isMetal = "METALICO".equalsIgnoreCase(l.getTipoPeca());
            boolean isNaoMetal = "NAO_METALICO".equalsIgnoreCase(l.getTipoPeca());
            String classifLabel = isMetal ? "Metálica" : "Não metálica";

            if (classification != null && !classification.isEmpty() && !classifLabel.equalsIgnoreCase(classification)) {
                continue;
            }

            String ts = (l.getDataHora() != null)
                    ? l.getDataHora().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
                    : nowUtc();

            Map dto = new HashMap();
            dto.put("id", "LT-" + l.getId());
            dto.put("timestamp", ts);
            dto.put("classification", classifLabel);
            dto.put("inductive", isMetal);
            dto.put("capacitive", isMetal || isNaoMetal);
            dto.put("distance", l.getNivelReservatorioCm());
            dto.put("reservoirLevel", calcularPercentualNivel(l.getNivelReservatorioCm()));
            dto.put("status", "Recebida");
            lista.add(dto);
        }
        return lista;
    }

    // =========================================================================
    // 8. RESERVATÓRIO & ANALYTICS (/reservoir & /analytics)
    // =========================================================================
    @GetMapping("/reservoir/status")
    public Map getReservoirStatus() {
        return Map.of("data", buildReservoirData());
    }

    private Map buildReservoirData() {
        List todas = repository.findAll();
        double ultimaDistancia = todas.isEmpty() ? 22.0 : ((LeituraSensor) todas.get(todas.size() - 1)).getNivelReservatorioCm();
        int currentLevel = calcularPercentualNivel(ultimaDistancia);

        long pecasArmazenadas = 0;
        for (Object obj : todas) {
            LeituraSensor l = (LeituraSensor) obj;
            if ("METALICO".equalsIgnoreCase(l.getTipoPeca()) || "NAO_METALICO".equalsIgnoreCase(l.getTipoPeca())) {
                pecasArmazenadas++;
            }
        }

        Map thresholds = (Map) settingsStore.getOrDefault("alertThresholds", Map.of("attention", 75, "critical", 90));
        Map stationCfg = (Map) settingsStore.getOrDefault("station", Map.of("reservoirCapacity", 1000));

        Map res = new HashMap();
        res.put("currentLevel", currentLevel);
        res.put("distance", ultimaDistancia);
        res.put("capacity", ((Number) stationCfg.getOrDefault("reservoirCapacity", 1000)).intValue());
        res.put("attentionThreshold", ((Number) thresholds.getOrDefault("attention", 75)).intValue());
        res.put("criticalThreshold", ((Number) thresholds.getOrDefault("critical", 90)).intValue());
        res.put("storedPieces", (int) pecasArmazenadas);
        res.put("updatedAt", nowUtc());
        return res;
    }

    @GetMapping("/analytics/overview")
    public Map analyticsOverview() {
        return Map.of("data", buildOverviewData());
    }

    private Map buildOverviewData() {
        List todas = repository.findAll();
        long metallicPieces = 0;
        long nonMetallicPieces = 0;

        for (Object obj : todas) {
            LeituraSensor l = (LeituraSensor) obj;
            if ("METALICO".equalsIgnoreCase(l.getTipoPeca())) {
                metallicPieces++;
            } else if ("NAO_METALICO".equalsIgnoreCase(l.getTipoPeca())) {
                nonMetallicPieces++;
            }
        }

        long totalPieces = metallicPieces + nonMetallicPieces;
        double metallicPercent = totalPieces > 0 ? Math.round((metallicPieces * 1000.0 / totalPieces)) / 10.0 : 0.0;
        double nonMetallicPercent = totalPieces > 0 ? Math.round((nonMetallicPieces * 1000.0 / totalPieces)) / 10.0 : 0.0;

        Instant now = Instant.now();
        Map response = new HashMap();
        response.put("totalPieces", (int) totalPieces);
        response.put("metallicPieces", (int) metallicPieces);
        response.put("nonMetallicPieces", (int) nonMetallicPieces);
        response.put("metallicPercent", metallicPercent);
        response.put("nonMetallicPercent", nonMetallicPercent);
        response.put("period", Map.of(
            "from", now.minus(8, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString(),
            "to", now.truncatedTo(ChronoUnit.SECONDS).toString()
        ));
        response.put("updatedAt", nowUtc());
        return response;
    }

    @GetMapping("/analytics/production")
    public Map analyticsProduction() {
        return Map.of("data", buildProductionList());
    }

    private List buildProductionList() {
        List list = new ArrayList();
        String[] horas = {"06:00", "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00"};
        int[] metallic = {90, 96, 102, 108, 104, 112, 116, 104};
        int[] nonMetallic = {48, 52, 54, 58, 56, 60, 62, 58};
        int[] levels = {20, 28, 36, 44, 52, 60, 68, 76};

        for (int i = 0; i < horas.length; i++) {
            Map item = new HashMap();
            item.put("hour", horas[i]);
            item.put("metallic", metallic[i]);
            item.put("nonMetallic", nonMetallic[i]);
            item.put("reservoirLevel", levels[i]);
            list.add(item);
        }
        return list;
    }

    @GetMapping("/analytics/reservoir")
    public Map analyticsReservoir() {
        List todas = repository.findAll();
        List history = new ArrayList();
        int start = Math.max(0, todas.size() - 20);

        for (int i = start; i < todas.size(); i++) {
            LeituraSensor l = (LeituraSensor) todas.get(i);
            String ts = (l.getDataHora() != null)
                    ? l.getDataHora().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
                    : nowUtc();
            Map pt = new HashMap();
            pt.put("timestamp", ts);
            pt.put("level", calcularPercentualNivel(l.getNivelReservatorioCm()));
            pt.put("distance", l.getNivelReservatorioCm());
            history.add(pt);
        }
        return Map.of("data", history);
    }

    // =========================================================================
    // 9. ALERTAS, CONFIGURAÇÕES & AUDITORIA
    // =========================================================================
    @GetMapping("/alerts")
    public Map listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity
    ) {
        List filtered = new ArrayList();
        for (Object obj : alertsStore) {
            Map a = (Map) obj;
            if (status != null && !status.isEmpty() && !status.equalsIgnoreCase(String.valueOf(a.get("status")))) continue;
            if (severity != null && !severity.isEmpty() && !severity.equalsIgnoreCase(String.valueOf(a.get("severity")))) continue;
            filtered.add(a);
        }
        return Map.of("data", filtered);
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity getAlertById(@PathVariable String id) {
        for (Object obj : alertsStore) {
            Map a = (Map) obj;
            if (id.equals(a.get("id"))) {
                return ResponseEntity.ok(Map.of("data", a));
            }
        }
        return errorResponse(HttpStatus.NOT_FOUND, "Alerta não encontrado.", "NOT_FOUND", Map.of());
    }

    @PatchMapping("/alerts/{id}")
    public ResponseEntity updateAlert(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String id,
            @RequestBody Map body
    ) {
        Map actor = resolveAuthenticatedUser(authHeader);
        if (actor == null) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "Não autenticado.", "UNAUTHORIZED", Map.of());
        }
        if (!hasPermission(actor, "manageAlerts")) {
            return errorResponse(HttpStatus.FORBIDDEN, "Sem permissão para gerenciar alertas.", "FORBIDDEN", Map.of());
        }

        for (Object obj : alertsStore) {
            Map al = (Map) obj;
            if (id.equals(al.get("id"))) {
                body.forEach((k, v) -> {
                    if (v != null) al.put(k, v);
                });
                al.put("updatedBy", actor.get("id"));
                String newStatus = String.valueOf(al.get("status"));
                String action = "Resolvido".equalsIgnoreCase(newStatus) ? "ALERT_RESOLVED" : "ALERT_ACKNOWLEDGED";
                registrarAuditoria(String.valueOf(actor.get("id")), String.valueOf(actor.get("name")), action, id, "Status do alerta alterado para " + newStatus + ".");
                return ResponseEntity.ok(Map.of("message", "Alerta atualizado.", "data", al));
            }
        }
        return errorResponse(HttpStatus.NOT_FOUND, "Alerta não encontrado.", "NOT_FOUND", Map.of());
    }

    @GetMapping("/settings")
    public Map getSettings() {
        return Map.of("data", settingsStore);
    }

    @PutMapping("/settings")
    public ResponseEntity updateSettings(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map body
    ) {
        Map actor = resolveAuthenticatedUser(authHeader);
        if (actor == null) {
            return errorResponse(HttpStatus.UNAUTHORIZED, "Não autenticado.", "UNAUTHORIZED", Map.of());
        }
        if (!hasPermission(actor, "manageSettings")) {
            return errorResponse(HttpStatus.FORBIDDEN, "Sem permissão para alterar configurações.", "FORBIDDEN", Map.of());
        }

        if (body != null) {
            for (String section : List.of("alertThresholds", "collection", "integration", "station")) {
                if (body.get(section) instanceof Map) {
                    Map currentSection = (Map) settingsStore.computeIfAbsent(section, k -> new ConcurrentHashMap());
                    currentSection.putAll((Map) body.get(section));
                }
            }
        }

        registrarAuditoria(String.valueOf(actor.get("id")), String.valueOf(actor.get("name")), "SETTINGS_UPDATED", "SETTINGS", "Configurações operacionais atualizadas.");

        return ResponseEntity.ok(Map.of("message", "Configurações atualizadas com sucesso.", "data", settingsStore));
    }

    @GetMapping("/audit-logs")
    public Map auditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        int total = auditLogsStore.size();
        int safePage = Math.max(1, page);
        int safeLimit = Math.max(1, limit);
        int fromIdx = Math.min((safePage - 1) * safeLimit, total);
        int toIdx = Math.min(fromIdx + safeLimit, total);
        int totalPages = (int) Math.ceil((double) total / safeLimit);

        return Map.of(
            "data", auditLogsStore.subList(fromIdx, toIdx),
            "pagination", Map.of("page", safePage, "limit", safeLimit, "total", total, "totalPages", Math.max(1, totalPages))
        );
    }

    // =========================================================================
    // HELPERS DE SEGURANÇA, AUDITORIA E FORMATAÇÃO
    // =========================================================================
    private ResponseEntity errorResponse(HttpStatus status, String message, String code, Map details) {
        Map err = new HashMap();
        err.put("message", message);
        err.put("code", code);
        err.put("details", details);
        return ResponseEntity.status(Objects.requireNonNull(status)).body(err);
    }

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return null;
    }

    private Map resolveAuthenticatedUser(String authHeader) {
        String token = extractBearerToken(authHeader);
        if (token == null) return null;
        String userId = (String) activeTokens.get(token);
        if (userId == null) return null;
        return findUserById(userId);
    }

    private boolean hasPermission(Map user, String permission) {
        if (user == null) return false;
        if ("Administrador".equalsIgnoreCase(String.valueOf(user.get("role")))) return true;
        Object perms = user.get("permissions");
        if (perms instanceof List) {
            return ((List) perms).contains(permission);
        }
        return false;
    }

    private List defaultPermissionsForRole(String role) {
        if ("Administrador".equalsIgnoreCase(role)) return new ArrayList(ALL_PERMISSIONS);
        if ("Analista".equalsIgnoreCase(role)) return new ArrayList(List.of("overview", "station", "analytics", "history", "alerts", "exportData"));
        return new ArrayList(List.of("overview", "station", "history", "alerts", "manageAlerts"));
    }

    private Map findUserById(String id) {
        for (Object obj : usersStore) {
            Map u = (Map) obj;
            if (id.equals(u.get("id"))) return u;
        }
        return null;
    }

    private List sanitizeUsersList() {
        List list = new ArrayList();
        for (Object obj : usersStore) {
            list.add(sanitizeUser((Map) obj));
        }
        return list;
    }

    private Map sanitizeUser(Map rawUser) {
        Map copy = new HashMap(rawUser);
        copy.remove("passwordHash");
        return copy;
    }

    private String hashPassword(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(("metaltech_salt_" + raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : encoded) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return raw;
        }
    }

    private void registrarAuditoria(String actorId, String actorName, String action, String resource, String description) {
        Map log = new HashMap();
        log.put("id", "AUD-" + String.format("%03d", auditLogsStore.size() + 1));
        log.put("actorId", actorId);
        log.put("actorName", actorName);
        log.put("action", action);
        log.put("resource", resource);
        log.put("description", description);
        log.put("createdAt", nowUtc());
        auditLogsStore.add(0, log);
    }

    private int calcularPercentualNivel(double distanciaCm) {
        Map stationCfg = (Map) settingsStore.getOrDefault("station", Map.of("reservoirHeight", 100));
        double height = ((Number) stationCfg.getOrDefault("reservoirHeight", 100)).doubleValue();
        if (height <= 0) height = 100.0;
        int pct = (int) Math.round(((height - distanciaCm) / height) * 100.0);
        return Math.min(100, Math.max(0, pct));
    }

    private String nowUtc() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
    }

    private Map createUserMap(String id, String name, String email, String passwordHash, String role, String status, List permissions) {
        Map u = new ConcurrentHashMap();
        u.put("id", id);
        u.put("name", name);
        u.put("email", email);
        u.put("passwordHash", passwordHash);
        u.put("role", role);
        u.put("status", status);
        u.put("permissions", new ArrayList(permissions));
        u.put("createdAt", nowUtc());
        return u;
    }

    private Map createAlertMap(String id, String title, String occurredAt, String status, String severity, int reservoirLevel, String stationId) {
        Map al = new ConcurrentHashMap();
        al.put("id", id);
        al.put("title", title);
        al.put("occurredAt", occurredAt);
        al.put("status", status);
        al.put("severity", severity);
        al.put("reservoirLevel", reservoirLevel);
        al.put("stationId", stationId);
        return al;
    }
}