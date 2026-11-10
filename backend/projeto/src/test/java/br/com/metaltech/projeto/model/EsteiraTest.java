package br.com.metaltech.projeto.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EsteiraTest {

    // Reservatório de 50 cm, alerta a partir de 80% de ocupação
    private final Esteira esteira = new Esteira("Esteira 1", 50, 80);

    @Test
    void reservatorioVazioTemOcupacaoZero() {
        assertEquals(0, esteira.calcularOcupacaoPercentual(50), 0.001);
    }

    @Test
    void reservatorioCheioTemOcupacaoCem() {
        assertEquals(100, esteira.calcularOcupacaoPercentual(0), 0.001);
    }

    @Test
    void calculaOcupacaoIntermediaria() {
        assertEquals(60, esteira.calcularOcupacaoPercentual(20), 0.001);
    }

    @Test
    void medidaForaDoIntervaloFicaEntreZeroECem() {
        assertEquals(0, esteira.calcularOcupacaoPercentual(70), 0.001);
        assertEquals(100, esteira.calcularOcupacaoPercentual(-5), 0.001);
    }

    @Test
    void alertaComecaNoLimiteConfigurado() {
        assertTrue(esteira.estaEmAlerta(80));
        assertFalse(esteira.estaEmAlerta(79.9));
    }
}
