package br.com.metaltech.projeto.dto;

import br.com.metaltech.projeto.model.Esteira;

public record EsteiraResponse(
        Long id,
        String nome,
        double alturaReservatorioCm,
        double limiteAlertaPercentual) {

    public static EsteiraResponse de(Esteira esteira) {
        return new EsteiraResponse(
                esteira.getId(),
                esteira.getNome(),
                esteira.getAlturaReservatorioCm(),
                esteira.getLimiteAlertaPercentual());
    }
}
