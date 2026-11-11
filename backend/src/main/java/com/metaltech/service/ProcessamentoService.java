package com.metaltech.service;

import com.metaltech.dto.SensorPayloadDTO;
import com.metaltech.model.LeituraSensor;
import com.metaltech.repository.LeituraSensorRepository;
import org.springframework.stereotype.Service;

@Service
public class ProcessamentoService {

    private final LeituraSensorRepository repository;

    public ProcessamentoService(LeituraSensorRepository repository) {
        this.repository = repository;
    }

    public void processarLeituraMQTT(SensorPayloadDTO payload) {
        boolean metal = payload.getSensorIndutivo();
        boolean capacitivo = payload.getSensorCapacitivo();
        double nivelCm = payload.getDistanciaLaserMm() / 10.0;

        String tipoPeca = "NENHUMA";
        if (metal) {
            tipoPeca = "METALICO";
        } else if (capacitivo) {
            tipoPeca = "NAO_METALICO";
        }

        LeituraSensor leitura = new LeituraSensor(tipoPeca, metal, capacitivo, nivelCm);
        repository.save(leitura);
        System.out.println(" Processado e salvo no PostgreSQL: " + tipoPeca + " | Nível: " + nivelCm + " cm");
    }
}