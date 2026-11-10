package br.com.metaltech.projeto.dto;

import java.time.LocalDateTime;

import br.com.metaltech.projeto.model.LeituraReservatorio;

public record LeituraReservatorioResponse(
        Long id,
        double distanciaSensorCm,
        double ocupacaoPercentual,
        LocalDateTime dataHoraLeitura) {

    public static LeituraReservatorioResponse de(LeituraReservatorio leitura) {
        return new LeituraReservatorioResponse(
                leitura.getId(),
                leitura.getDistanciaSensorCm(),
                leitura.getOcupacaoPercentual(),
                leitura.getDataHoraLeitura());
    }
}
