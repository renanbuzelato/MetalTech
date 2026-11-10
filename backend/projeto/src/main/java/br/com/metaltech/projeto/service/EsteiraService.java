package br.com.metaltech.projeto.service;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.metaltech.projeto.dto.EsteiraRequest;
import br.com.metaltech.projeto.exception.RecursoNaoEncontradoException;
import br.com.metaltech.projeto.model.Esteira;
import br.com.metaltech.projeto.repository.EsteiraRepository;

@Service
public class EsteiraService {

    private final EsteiraRepository esteiraRepository;

    public EsteiraService(EsteiraRepository esteiraRepository) {
        this.esteiraRepository = esteiraRepository;
    }

    public Esteira cadastrar(EsteiraRequest request) {
        Esteira esteira = new Esteira(
                request.nome(), request.alturaReservatorioCm(), request.limiteAlertaPercentual());
        return esteiraRepository.save(esteira);
    }

    public List<Esteira> listar() {
        return esteiraRepository.findAll();
    }

    public Esteira buscarPorId(Long id) {
        return esteiraRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Esteira não encontrada: " + id));
    }

    public Esteira atualizar(Long id, EsteiraRequest request) {
        Esteira esteira = buscarPorId(id);
        esteira.atualizarDados(request.nome(), request.alturaReservatorioCm(), request.limiteAlertaPercentual());
        return esteiraRepository.save(esteira);
    }

    public void excluir(Long id) {
        esteiraRepository.delete(buscarPorId(id));
    }
}
