package br.com.metaltech.projeto.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Esteira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    // Distância (cm) do sensor de distância, instalado no topo, até o fundo do reservatório.
    @Column(nullable = false)
    private double alturaReservatorioCm;

    // A partir deste percentual de ocupação o reservatório entra em alerta.
    @Column(nullable = false)
    private double limiteAlertaPercentual;

    protected Esteira() {
        // Exigido pelo JPA
    }

    public Esteira(String nome, double alturaReservatorioCm, double limiteAlertaPercentual) {
        this.nome = nome;
        this.alturaReservatorioCm = alturaReservatorioCm;
        this.limiteAlertaPercentual = limiteAlertaPercentual;
    }

    public void atualizarDados(String nome, double alturaReservatorioCm, double limiteAlertaPercentual) {
        this.nome = nome;
        this.alturaReservatorioCm = alturaReservatorioCm;
        this.limiteAlertaPercentual = limiteAlertaPercentual;
    }

    // Quanto menor a distância medida pelo sensor, mais cheio está o reservatório.
    public double calcularOcupacaoPercentual(double distanciaSensorCm) {
        double alturaOcupadaCm = alturaReservatorioCm - distanciaSensorCm;
        double ocupacaoPercentual = alturaOcupadaCm / alturaReservatorioCm * 100;
        return Math.max(0, Math.min(100, ocupacaoPercentual));
    }

    public boolean estaEmAlerta(double ocupacaoPercentual) {
        return ocupacaoPercentual >= limiteAlertaPercentual;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public double getAlturaReservatorioCm() {
        return alturaReservatorioCm;
    }

    public double getLimiteAlertaPercentual() {
        return limiteAlertaPercentual;
    }
}
