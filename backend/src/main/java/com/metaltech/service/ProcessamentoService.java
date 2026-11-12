package com.metaltech.service;

import com.metaltech.dto.SensorPayloadDTO;
import com.metaltech.model.LeituraSensor;
import com.metaltech.repository.LeituraSensorRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ProcessamentoService {

    private final LeituraSensorRepository repository;

    public ProcessamentoService(LeituraSensorRepository repository) {
        this.repository = repository;
    }

    public void processarMensagemMqtt(SensorPayloadDTO payload) {
        if (payload == null) return;

        LeituraSensor leitura = new LeituraSensor();
        leitura.setTipoPeca(payload.getTipoPeca());
        leitura.setNivelReservatorioCm(payload.getNivelReservatorioCm());
        leitura.setDataHora(LocalDateTime.now());

        repository.save(leitura);
    }
}