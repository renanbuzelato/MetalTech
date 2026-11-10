package br.com.metaltech.projeto.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

@RestController
@RequestMapping("/api/esteiras/{esteiraId}")
public class MonitoramentoController {

    private final MonitoramentoService monitoramentoService;

    public MonitoramentoController(MonitoramentoService monitoramentoService) {
        this.monitoramentoService = monitoramentoService;
    }

    // Usado pela Automação (CLP) para enviar os dados dos sensores.
    @PostMapping("/leituras")
    @ResponseStatus(HttpStatus.CREATED)
    public LeituraReservatorioResponse registrarLeitura(@PathVariable Long esteiraId,
                                                        @Valid @RequestBody NovaLeituraRequest request) {
        return LeituraReservatorioResponse.de(monitoramentoService.registrarLeitura(esteiraId, request));
    }

    // Histórico do nível do reservatório (gráficos e Ciência de Dados).
    @GetMapping("/leituras")
    public List<LeituraReservatorioResponse> listarLeituras(@PathVariable Long esteiraId) {
        return monitoramentoService.listarLeituras(esteiraId).stream()
                .map(LeituraReservatorioResponse::de)
                .toList();
    }

    // Histórico das peças classificadas (Ciência de Dados).
    @GetMapping("/pecas")
    public List<PecaResponse> listarPecas(@PathVariable Long esteiraId) {
        return monitoramentoService.listarPecas(esteiraId).stream()
                .map(PecaResponse::de)
                .toList();
    }

    // Situação atual da esteira (dashboard e Realidade Aumentada).
    @GetMapping("/resumo")
    public ResumoEsteiraResponse obterResumo(@PathVariable Long esteiraId) {
        return monitoramentoService.gerarResumo(esteiraId);
    }
}
