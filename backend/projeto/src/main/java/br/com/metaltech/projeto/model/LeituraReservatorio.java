package br.com.metaltech.projeto.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

// Leitura do sensor de distância: guarda o nível do reservatório em um instante.
@Entity
public class LeituraReservatorio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Esteira esteira;

    @Column(nullable = false)
    private double distanciaSensorCm;

  

    @Column(nullable = false)
    private LocalDateTime dataHoraLeitura;

    protected LeituraReservatorio() {
        // Exigido pelo JPA
    }

    public LeituraReservatorio(Esteira esteira, double distanciaSensorCm) {
        this.esteira = esteira;
        this.distanciaSensorCm = distanciaSensorCm;
        
        this.dataHoraLeitura = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public double getDistanciaSensorCm() {
        return distanciaSensorCm;
    }

    public double getOcupacaoPercentual() {
        return esteira.calcularOcupacaoPercentual(distanciaSensorCm);
    }

    public LocalDateTime getDataHoraLeitura() {
        return dataHoraLeitura;
    }
}
