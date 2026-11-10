package br.com.metaltech.projeto.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.metaltech.projeto.dto.NovaLeituraRequest;
import br.com.metaltech.projeto.dto.ResumoEsteiraResponse;
import br.com.metaltech.projeto.model.Esteira;
import br.com.metaltech.projeto.model.LeituraReservatorio;
import br.com.metaltech.projeto.model.Peca;
import br.com.metaltech.projeto.model.PecaMetalica;
import br.com.metaltech.projeto.model.PecaNaoMetalica;
import br.com.metaltech.projeto.model.StatusReservatorio;
import br.com.metaltech.projeto.repository.LeituraReservatorioRepository;
import br.com.metaltech.projeto.repository.PecaRepository;

// Recebe os dados da esteira e entrega as informações para dashboard, RA e Ciência de Dados.
@Service
public class MonitoramentoService {

    private final EsteiraService esteiraService;
    private final PecaRepository pecaRepository;
    private final LeituraReservatorioRepository leituraRepository;
    private final ClassificadorDePeca classificadorDePeca;

    public MonitoramentoService(EsteiraService esteiraService,
                                PecaRepository pecaRepository,
                                LeituraReservatorioRepository leituraRepository,
                                ClassificadorDePeca classificadorDePeca) {
        this.esteiraService = esteiraService;
        this.pecaRepository = pecaRepository;
        this.leituraRepository = leituraRepository;
        this.classificadorDePeca = classificadorDePeca;
    }

    @Transactional
    public LeituraReservatorio registrarLeitura(Long esteiraId, NovaLeituraRequest request) {
        Esteira esteira = esteiraService.buscarPorId(esteiraId);

        classificadorDePeca
                .classificar(esteira, request.sensorIndutivoAcionado(), request.sensorCapacitivoAcionado())
                .ifPresent(pecaRepository::save);

        LeituraReservatorio leitura = new LeituraReservatorio(esteira, request.distanciaReservatorioCm());
        return leituraRepository.save(leitura);
    }


     @Transactional(readOnly = true)
    public List<LeituraReservatorio> listarLeituras(Long esteiraId) {
        esteiraService.buscarPorId(esteiraId);
        return leituraRepository.findByEsteiraIdOrderByDataHoraLeituraAsc(esteiraId);
    }

    public List<Peca> listarPecas(Long esteiraId) {
        esteiraService.buscarPorId(esteiraId);
        return pecaRepository.findByEsteiraIdOrderByDataHoraClassificacaoAsc(esteiraId);
    }

    public ResumoEsteiraResponse gerarResumo(Long esteiraId) {
        Esteira esteira = esteiraService.buscarPorId(esteiraId);

        long totalMetalicas = pecaRepository.contarPorClasse(esteiraId, PecaMetalica.class);
        long totalNaoMetalicas = pecaRepository.contarPorClasse(esteiraId, PecaNaoMetalica.class);
        Optional<LeituraReservatorio> ultimaLeitura = leituraRepository.findFirstByEsteiraIdOrderByIdDesc(esteiraId);

        StatusReservatorio status = ultimaLeitura
                .map(leitura -> esteira.estaEmAlerta(leitura.getOcupacaoPercentual())
                        ? StatusReservatorio.ALERTA
                        : StatusReservatorio.NORMAL)
                .orElse(StatusReservatorio.SEM_LEITURA);

        return new ResumoEsteiraResponse(
                esteira.getId(),
                esteira.getNome(),
                totalMetalicas,
                totalNaoMetalicas,
                ultimaLeitura.map(LeituraReservatorio::getOcupacaoPercentual).orElse(null),
                ultimaLeitura.map(LeituraReservatorio::getDistanciaSensorCm).orElse(null),
                status,
                ultimaLeitura.map(LeituraReservatorio::getDataHoraLeitura).orElse(null));
    }
}
