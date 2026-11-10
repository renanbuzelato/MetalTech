package br.com.metaltech.projeto.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import br.com.metaltech.projeto.model.Esteira;
import br.com.metaltech.projeto.model.Peca;
import br.com.metaltech.projeto.model.PecaMetalica;
import br.com.metaltech.projeto.model.PecaNaoMetalica;

// Regra de classificação: combina os sensores indutivo e capacitivo.
@Component
public class ClassificadorDePeca {

    public Optional<Peca> classificar(Esteira esteira, boolean indutivoAcionado, boolean capacitivoAcionado) {
        // Peça de metal aciona os dois sensores, então o indutivo tem prioridade.
        if (indutivoAcionado) {
            return Optional.of(new PecaMetalica(esteira));
        }
        if (capacitivoAcionado) {
            return Optional.of(new PecaNaoMetalica(esteira));
        }
        return Optional.empty();
    }
}
