package br.com.metaltech.projeto.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;

// Classe base das peças classificadas. Cada tipo de peça é uma subclasse.
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo_peca")
public abstract class Peca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Esteira esteira;

    @Column(nullable = false)
    private LocalDateTime dataHoraClassificacao;

    protected Peca() {
        // Exigido pelo JPA
    }

    protected Peca(Esteira esteira) {
        this.esteira = esteira;
        this.dataHoraClassificacao = LocalDateTime.now();
    }

    public abstract TipoPeca getTipo();

    public Long getId() {
        return id;
    }

    public LocalDateTime getDataHoraClassificacao() {
        return dataHoraClassificacao;
    }
}
