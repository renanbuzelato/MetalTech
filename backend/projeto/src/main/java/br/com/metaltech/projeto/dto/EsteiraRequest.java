package br.com.metaltech.projeto.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EsteiraRequest(
        @NotBlank String nome,
        @NotNull @Positive Double alturaReservatorioCm,
        @NotNull @DecimalMin("1") @DecimalMax("100") Double limiteAlertaPercentual) {
}
