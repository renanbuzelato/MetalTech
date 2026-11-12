package com.metaltech.dto;

public class SensorPayloadDTO {
    private String tipoPeca;
    private double nivelReservatorioCm;

    public SensorPayloadDTO() {}

    public SensorPayloadDTO(String tipoPeca, double nivelReservatorioCm) {
        this.tipoPeca = tipoPeca;
        this.nivelReservatorioCm = nivelReservatorioCm;
    }

    public String getTipoPeca() {
        return tipoPeca;
    }

    public void setTipoPeca(String tipoPeca) {
        this.tipoPeca = tipoPeca;
    }

    public double getNivelReservatorioCm() {
        return nivelReservatorioCm;
    }

    public void setNivelReservatorioCm(double nivelReservatorioCm) {
        this.nivelReservatorioCm = nivelReservatorioCm;
    }
}