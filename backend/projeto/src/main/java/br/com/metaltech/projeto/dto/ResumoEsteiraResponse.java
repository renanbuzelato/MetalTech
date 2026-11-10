package br.com.metaltech.projeto.dto;

import java.time.LocalDateTime;

import br.com.metaltech.projeto.model.StatusReservatorio;

// Resposta pensada para o dashboard React e para o app de Realidade Aumentada.
// Os campos da última leitura ficam nulos enquanto a esteira não enviar nenhuma leitura.
public record ResumoEsteiraResponse(
        Long esteiraId,
        String nomeEsteira,
        long totalPecasMetalicas,
        long totalPecasNaoMetalicas,
        Double ocupacaoReservatorioPercentual,
        Double distanciaReservatorioCm,
        StatusReservatorio statusReservatorio,
        LocalDateTime dataHoraUltimaLeitura) {
}
