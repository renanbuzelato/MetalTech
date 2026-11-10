package br.com.metaltech.projeto.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// Dados enviados pela camada de Automação (CLP) a cada ciclo de leitura.
public record NovaLeituraRequest(
        boolean sensorIndutivoAcionado,
        boolean sensorCapacitivoAcionado,
        @NotNull @PositiveOrZero Double distanciaReservatorioCm) {
}
