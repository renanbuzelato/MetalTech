package com.metaltech.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_leituras_esteira")
public class LeituraSensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipoPeca;
    private double nivelReservatorioCm;
    private LocalDateTime dataHora;

    public LeituraSensor() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }
}