package br.com.metaltech.projeto.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("NAO_METALICA")
public class PecaNaoMetalica extends Peca {

    protected PecaNaoMetalica() {
        // Exigido pelo JPA
    }

    public PecaNaoMetalica(Esteira esteira) {
        super(esteira);
    }

    @Override
    public TipoPeca getTipo() {
        return TipoPeca.NAO_METALICA;
    }
}
