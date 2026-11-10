package br.com.metaltech.projeto.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("METALICA")
public class PecaMetalica extends Peca {

    protected PecaMetalica() {
        // Exigido pelo JPA
    }

    public PecaMetalica(Esteira esteira) {
        super(esteira);
    }

    @Override
    public TipoPeca getTipo() {
        return TipoPeca.METALICA;
    }
}
