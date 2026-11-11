package br.com.metaltech.projeto.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.metaltech.projeto.dto.LeituraReservatorioResponse;
import br.com.metaltech.projeto.dto.NovaLeituraRequest;
import br.com.metaltech.projeto.dto.PecaResponse;
import br.com.metaltech.projeto.dto.ResumoEsteiraResponse;
import br.com.metaltech.projeto.service.MonitoramentoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/monitoramento")
public class MonitoramentoController {

    private final MonitoramentoService monitoramentoService;

    public MonitoramentoController(MonitoramentoService monitoramentoService) {
        this.monitoramentoService = monitoramentoService;
    }

    // 1. No método de registrar leitura:
    @PostMapping("/leituras")
    @ResponseStatus(HttpStatus.CREATED)
    public LeituraReservatorioResponse registrarLeitura(
            @RequestParam(required = false, defaultValue = "1") Long esteiraId, // <--- COLOCA AQUI
            @Valid @RequestBody NovaLeituraRequest request) {
        return LeituraReservatorioResponse.de(monitoramentoService.registrarLeitura(esteiraId, request));
    }

    // 2. No método de listar leituras:
    @GetMapping("/leituras")
    public List<LeituraReservatorioResponse> listarLeituras(
            @RequestParam(required = false, defaultValue = "1") Long esteiraId) { // <--- COLOCA AQUI
        return monitoramentoService.listarLeituras(esteiraId).stream()
                .map(LeituraReservatorioResponse::de)
                .toList();
    }

    // 3. No método de listar peças:
    @GetMapping("/pecas")
    public List<PecaResponse> listarPecas(
            @RequestParam(required = false, defaultValue = "1") Long esteiraId) { // <--- COLOCA AQUI
        return monitoramentoService.listarPecas(esteiraId).stream()
                .map(PecaResponse::de)
                .toList();
    }

    // 4. No método de overview/resumo:
    @GetMapping("/overview")
    public ResumoEsteiraResponse obterResumo(
            @RequestParam(required = false, defaultValue = "1") Long esteiraId) { // <--- COLOCA AQUI
        return monitoramentoService.gerarResumo(esteiraId);
    }
}