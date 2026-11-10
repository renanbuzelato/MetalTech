package br.com.metaltech.projeto.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.metaltech.projeto.model.LeituraReservatorio;

public interface LeituraReservatorioRepository extends JpaRepository<LeituraReservatorio, Long> {

    List<LeituraReservatorio> findByEsteiraIdOrderByDataHoraLeituraAsc(Long esteiraId);

    Optional<LeituraReservatorio> findFirstByEsteiraIdOrderByIdDesc(Long esteiraId);
}
