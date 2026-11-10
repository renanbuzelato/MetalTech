package br.com.metaltech.projeto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.metaltech.projeto.dto.NovaLeituraRequest;
import br.com.metaltech.projeto.dto.ResumoEsteiraResponse;
import br.com.metaltech.projeto.model.Esteira;
import br.com.metaltech.projeto.model.LeituraReservatorio;
import br.com.metaltech.projeto.model.PecaMetalica;
import br.com.metaltech.projeto.model.PecaNaoMetalica;
import br.com.metaltech.projeto.model.StatusReservatorio;
import br.com.metaltech.projeto.repository.LeituraReservatorioRepository;
import br.com.metaltech.projeto.repository.PecaRepository;

@ExtendWith(MockitoExtension.class)
class MonitoramentoServiceTest {

    private static final Long ESTEIRA_ID = 1L;

    @Mock
    private EsteiraService esteiraService;
    @Mock
    private PecaRepository pecaRepository;
    @Mock
    private LeituraReservatorioRepository leituraRepository;

    private final Esteira esteira = new Esteira("Esteira 1", 50, 80);
    private MonitoramentoService monitoramentoService;

    @BeforeEach
    void preparar() {
        monitoramentoService = new MonitoramentoService(
                esteiraService, pecaRepository, leituraRepository, new ClassificadorDePeca());
        when(esteiraService.buscarPorId(ESTEIRA_ID)).thenReturn(esteira);
    }

    @Test
    void registraPecaMetalicaELeituraDoReservatorio() {
        when(leituraRepository.save(any(LeituraReservatorio.class)))
                .thenAnswer(chamada -> chamada.getArgument(0));

        LeituraReservatorio leitura = monitoramentoService
                .registrarLeitura(ESTEIRA_ID, new NovaLeituraRequest(true, false, 20.0));

        verify(pecaRepository).save(any(PecaMetalica.class));
        assertEquals(60, leitura.getOcupacaoPercentual(), 0.001);
    }

    @Test
    void semSensorAcionadoGuardaApenasALeituraDoReservatorio() {
        when(leituraRepository.save(any(LeituraReservatorio.class)))
                .thenAnswer(chamada -> chamada.getArgument(0));

        monitoramentoService.registrarLeitura(ESTEIRA_ID, new NovaLeituraRequest(false, false, 30.0));

        verify(pecaRepository, never()).save(any());
        verify(leituraRepository).save(any(LeituraReservatorio.class));
    }

    @Test
    void resumoMostraTotaisEAlertaQuandoReservatorioEstaCheio() {
        when(pecaRepository.contarPorClasse(ESTEIRA_ID, PecaMetalica.class)).thenReturn(3L);
        when(pecaRepository.contarPorClasse(ESTEIRA_ID, PecaNaoMetalica.class)).thenReturn(2L);
        when(leituraRepository.findFirstByEsteiraIdOrderByIdDesc(ESTEIRA_ID))
                .thenReturn(Optional.of(new LeituraReservatorio(esteira, 5)));

        ResumoEsteiraResponse resumo = monitoramentoService.gerarResumo(ESTEIRA_ID);

        assertEquals(3, resumo.totalPecasMetalicas());
        assertEquals(2, resumo.totalPecasNaoMetalicas());
        assertEquals(90, resumo.ocupacaoReservatorioPercentual(), 0.001);
        assertEquals(StatusReservatorio.ALERTA, resumo.statusReservatorio());
    }

    @Test
    void resumoSemLeituraTemStatusSemLeitura() {
        when(leituraRepository.findFirstByEsteiraIdOrderByIdDesc(ESTEIRA_ID)).thenReturn(Optional.empty());

        ResumoEsteiraResponse resumo = monitoramentoService.gerarResumo(ESTEIRA_ID);

        assertEquals(StatusReservatorio.SEM_LEITURA, resumo.statusReservatorio());
        assertNull(resumo.ocupacaoReservatorioPercentual());
    }
}
