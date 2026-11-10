package br.com.metaltech.projeto.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.metaltech.projeto.model.Peca;

public interface PecaRepository extends JpaRepository<Peca, Long> {

    List<Peca> findByEsteiraIdOrderByDataHoraClassificacaoAsc(Long esteiraId);

    // Conta as peças de uma subclasse específica (PecaMetalica ou PecaNaoMetalica).
    @Query("select count(p) from Peca p where p.esteira.id = :esteiraId and type(p) = :classePeca")
    long contarPorClasse(@Param("esteiraId") Long esteiraId,
                         @Param("classePeca") Class<? extends Peca> classePeca);
}
