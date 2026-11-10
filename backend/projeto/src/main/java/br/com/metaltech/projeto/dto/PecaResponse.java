package br.com.metaltech.projeto.dto;

import java.time.LocalDateTime;

import br.com.metaltech.projeto.model.Peca;
import br.com.metaltech.projeto.model.TipoPeca;

public record PecaResponse(
        Long id,
        TipoPeca tipo,
        LocalDateTime dataHoraClassificacao) {

    public static PecaResponse de(Peca peca) {
        return new PecaResponse(peca.getId(), peca.getTipo(), peca.getDataHoraClassificacao());
    }
}
