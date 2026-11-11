package com.metaltech.controller;

import com.metaltech.model.LeituraSensor;
import com.metaltech.repository.LeituraSensorRepository;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class ApiGlobalController {

    private final LeituraSensorRepository repository;

    public ApiGlobalController(LeituraSensorRepository repository) {
        this.repository = repository;
    }

    // --- HEALTH ---
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> map = new HashMap<>();
        map.put("status", "UP");
        map.put("service", "MetalTech API");
        return map;
    }

    // --- AUTH ---
    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", "USR-001");
        user.put("name", "Rhuan Oliveira");
        user.put("email", body != null && body.containsKey("email") ? body.get("email") : "rhuan.oliveira@metaltech.com.br");
        user.put("role", "Administrador");
        user.put("status", "Ativo");

        Map<String, Object> response = new HashMap<>();
        response.put("token", "bearer-token-metaltech-2026");
        response.put("user", user);
        return response;
    }

    @PostMapping("/auth/logout")
    public Map<String, String> logout() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "Sessão encerrada com sucesso.");
        return response;
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me() {
        Map<String, Object> user = new HashMap<>();
        user.put("id", "USR-001");
        user.put("name", "Rhuan Oliveira");
        user.put("email", "rhuan.oliveira@metaltech.com.br");
        user.put("role", "Administrador");
        user.put("status", "Ativo");
        return user;
    }

    // --- ANALYTICS & PRODUCTION ---
    @GetMapping("/analytics/overview")
    public Map<String, Object> analyticsOverview() {
        List<LeituraSensor> todas = repository.findAll();
        long metalicas = 0;
        long naoMetalicas = 0;

        for (LeituraSensor l : todas) {
            if ("METALICO".equalsIgnoreCase(l.getTipoPeca())) {
                metalicas++;
            } else if ("NAO_METALICO".equalsIgnoreCase(l.getTipoPeca())) {
                naoMetalicas++;
            }
        }

        double ultimaDistancia = todas.isEmpty() ? 0.0 : todas.get(todas.size() - 1).getNivelReservatorioCm();
        int percentualNivel = (int) Math.min(100, Math.max(0, 100 - ultimaDistancia));

        Map<String, Object> response = new HashMap<>();
        response.put("totalPecasMetalicas", metalicas);
        response.put("totalPecasNaoMetalicas", naoMetalicas);
        response.put("totalLeituras", todas.size());
        response.put("currentLevel", percentualNivel);
        response.put("distance", ultimaDistancia);
        return response;
    }

    @GetMapping("/analytics/production")
    public List analyticsProduction() {
        List list = new ArrayList<>();
        
        String[] horas = {"06:00", "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00"};
        int[] metallic = {90, 96, 102, 108, 104, 112, 116, 104};
        int[] nonMetallic = {48, 52, 54, 58, 56, 60, 62, 58};
        int[] levels = {20, 28, 36, 44, 52, 60, 68, 76};

        for (int i = 0; i < horas.length; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("hour", horas[i]);
            item.put("metallic", metallic[i]);
            item.put("nonMetallic", nonMetallic[i]);
            item.put("reservoirLevel", levels[i]);
            list.add(item);
        }

        return list;
    }

    @GetMapping("/readings")
    public List readingsList() {
        List<LeituraSensor> todas = repository.findAll();
        List lista = new ArrayList<>();

        for (LeituraSensor l : todas) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", "LT-" + l.getId());
            dto.put("timestamp", l.getDataHora() != null ? l.getDataHora().toString() : "");
            dto.put("classification", "METALICO".equalsIgnoreCase(l.getTipoPeca()) ? "Metálica" : "Não metálica");
            dto.put("inductive", "METALICO".equalsIgnoreCase(l.getTipoPeca()));
            dto.put("capacitive", true);
            dto.put("distance", l.getNivelReservatorioCm());
            dto.put("reservoirLevel", (int) Math.min(100, Math.max(0, 100 - l.getNivelReservatorioCm())));
            dto.put("status", "Recebida");
            lista.add(dto);
        }

        return lista;
    }

    @GetMapping("/leituras/resumo")
    public Map<String, Object> leiturasResumo() {
        return analyticsOverview();
    }

    // --- USERS ---
    @GetMapping("/users")
    public List listUsers() {
        List list = new ArrayList<>();
        Map<String, Object> u1 = new HashMap<>();
        u1.put("id", "USR-001");
        u1.put("name", "Rhuan Oliveira");
        u1.put("email", "rhuan.oliveira@metaltech.com.br");
        u1.put("role", "Administrador");
        u1.put("status", "Ativo");
        list.add(u1);
        return list;
    }

    // --- STATIONS ---
    @GetMapping("/stations")
    public List listStations() {
        return List.of(getStationData("ET-001"));
    }

    @GetMapping("/stations/{id}")
    public Map<String, Object> getStationById(@PathVariable String id) {
        return getStationData(id);
    }

    private Map<String, Object> getStationData(String id) {
        Map<String, Object> st = new HashMap<>();
        st.put("id", id != null ? id : "ET-001");
        st.put("name", "Esteira de triagem 01");
        st.put("location", "Linha A · Fábrica São Paulo");
        st.put("status", "Em operação");
        st.put("shift", "Turno 06:00–14:32");
        st.put("controllerId", "CLP-001");
        return st;
    }

    // --- SENSORS ---
    @GetMapping("/sensors")
    public List listSensors() {
        List list = new ArrayList<>();
        
        Map<String, Object> s1 = new HashMap<>();
        s1.put("id", "SD-001"); s1.put("type", "distance"); s1.put("technology", "Ultrassônico"); s1.put("status", "Online");
        list.add(s1);

        Map<String, Object> s2 = new HashMap<>();
        s2.put("id", "SI-001"); s2.put("type", "inductive"); s2.put("technology", "Detecção de metal"); s2.put("status", "Online");
        list.add(s2);

        Map<String, Object> s3 = new HashMap<>();
        s3.put("id", "SC-001"); s3.put("type", "capacitive"); s3.put("technology", "Presença de material"); s3.put("status", "Online");
        list.add(s3);

        return list;
    }

    // --- ALERTS ---
    @GetMapping("/alerts")
    public List listAlerts() {
        List list = new ArrayList<>();
        Map<String, Object> al = new HashMap<>();
        al.put("id", "AL-078");
        al.put("title", "Reservatório próximo da capacidade");
        al.put("occurredAt", "2026-11-10T14:32:00-03:00");
        al.put("status", "Em aberto");
        al.put("severity", "Atenção");
        al.put("reservoirLevel", 78);
        list.add(al);
        return list;
    }

    // --- SETTINGS ---
    @GetMapping("/settings")
    public Map<String, Object> getSettings() {
        Map<String, Object> map = new HashMap<>();
        map.put("alertThresholds", Map.of("attention", 75, "critical", 90));
        map.put("collection", Map.of("intervalSeconds", 2, "refreshSeconds", 2));
        map.put("integration", Map.of("apiUrl", "http://10.110.12.85:8080/api/v1", "controllerId", "CLP-001"));
        return map;
    }

    // --- AUDIT LOGS ---
    @GetMapping("/audit-logs")
    public List auditLogs() {
        List list = new ArrayList<>();
        Map<String, Object> log = new HashMap<>();
        log.put("id", "LOG-001");
        log.put("actorId", "USR-001");
        log.put("action", "LOGIN");
        log.put("timestamp", "2026-11-10T14:00:00-03:00");
        list.add(log);
        return list;
    }
}