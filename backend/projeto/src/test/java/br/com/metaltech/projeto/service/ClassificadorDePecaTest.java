package br.com.metaltech.projeto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.metaltech.projeto.model.Esteira;
import br.com.metaltech.projeto.model.Peca;
import br.com.metaltech.projeto.model.TipoPeca;

class ClassificadorDePecaTest {

    private final ClassificadorDePeca classificador = new ClassificadorDePeca();
    private final Esteira esteira = new Esteira("Esteira 1", 50, 80);

    @Test
    void indutivoAcionadoClassificaComoMetalica() {
        Optional<Peca> peca = classificador.classificar(esteira, true, false);

        assertEquals(TipoPeca.METALICA, peca.orElseThrow().getTipo());
    }

    @Test
    void metalQueAcionaOsDoisSensoresContinuaMetalico() {
        Optional<Peca> peca = classificador.classificar(esteira, true, true);

        assertEquals(TipoPeca.METALICA, peca.orElseThrow().getTipo());
    }

    @Test
    void somenteCapacitivoAcionadoClassificaComoNaoMetalica() {
        Optional<Peca> peca = classificador.classificar(esteira, false, true);

        assertEquals(TipoPeca.NAO_METALICA, peca.orElseThrow().getTipo());
    }

    @Test
    void semSensorAcionadoNaoHaPeca() {
        assertTrue(classificador.classificar(esteira, false, false).isEmpty());
    }
}
