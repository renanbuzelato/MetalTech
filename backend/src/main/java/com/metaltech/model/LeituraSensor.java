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
    private Boolean indutivoAtivo;
    private Boolean capacitivoAtivo;
    private Double nivelReservatorioCm;
    private LocalDateTime dataHora;

    public LeituraSensor() {}

    public LeituraSensor(String tipoPeca, Boolean indutivoAtivo, Boolean capacitivoAtivo, Double nivelReservatorioCm) {
        this.tipoPeca = tipoPeca;
        this.indutivoAtivo = indutivoAtivo;
        this.capacitivoAtivo = capacitivoAtivo;
        this.nivelReservatorioCm = nivelReservatorioCm;
        this.dataHora = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTipoPeca() { return tipoPeca; }
    public Boolean getIndutivoAtivo() { return indutivoAtivo; }
    public Boolean getCapacitivoAtivo() { return capacitivoAtivo; }
    public Double getNivelReservatorioCm() { return nivelReservatorioCm; }
    public LocalDateTime getDataHora() { return dataHora; }
}