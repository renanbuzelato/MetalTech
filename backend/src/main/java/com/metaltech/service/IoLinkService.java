package com.metaltech.service;

import com.metaltech.model.LeituraSensor;
import com.metaltech.repository.LeituraSensorRepository;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

@Service
public class IoLinkService {

    private final LeituraSensorRepository repository;
    private final RestTemplate restTemplate;
    private final String MASTER_IP = "http://10.110.12.41";

    public IoLinkService(LeituraSensorRepository repository) {
        this.repository = repository;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Scheduled(fixedRate = 2000)
    public void consultarPortasIoLink() {
        try {
            String hexP1 = consultarHexPorta(1);
            String hexP2 = consultarHexPorta(2);
            String hexP3 = consultarHexPorta(3);

            if (hexP1 != null || hexP2 != null || hexP3 != null) {
                processarLeituras(hexP1, hexP2, hexP3);
            }
        } catch (Exception e) {
            System.err.println("[IO-LINK ERRO GERAL] " + e.getMessage());
        }
    }

private String consultarHexPorta(int porta) {
    String rawUrl = String.format("%s/iolinkmaster/port%%5B%d%%5D/iolinkdevice/pdin/getdata", MASTER_IP, porta);
    try {
        URI uri = Objects.requireNonNull(URI.create(rawUrl));
        Map<?, ?> response = restTemplate.getForObject(uri, Map.class);
        if (response != null && response.get("data") instanceof Map<?, ?> dataMap) {
            Object value = dataMap.get("value");
            if (value != null) {
                return value.toString().trim();
            }
        }
    } catch (Exception e) {
        System.err.println("[IO-LINK ERRO] Porta " + porta + " (" + rawUrl + "): " + e.getMessage());
    }
    return null;
}

    private boolean isSensorAtivo(String hexValue) {
        if (hexValue == null || hexValue.isEmpty()) return false;
        try {
            int valorInt = Integer.parseInt(hexValue, 16);
            return (valorInt & 1) == 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void processarLeituras(String hexDistancia, String hexCapacitivo, String hexIndutivo) {
        double distanciaCm = 0.0;
        if (hexDistancia != null && !hexDistancia.isEmpty()) {
            try {
                int valorBruto = Integer.parseInt(hexDistancia, 16);
                distanciaCm = valorBruto / 10.0;
            } catch (NumberFormatException ignored) {}
        }

        boolean capacitivoAtivo = isSensorAtivo(hexCapacitivo);
        boolean indutivoAtivo = isSensorAtivo(hexIndutivo);

        String tipo = "LEITURA_NIVEL";
        if (indutivoAtivo) {
            tipo = "METALICO";
        } else if (capacitivoAtivo) {
            tipo = "NAO_METALICO";
        }

        LeituraSensor leitura = new LeituraSensor(tipo, indutivoAtivo, capacitivoAtivo, distanciaCm);
        repository.save(leitura);

        System.out.printf("[IO-LINK] Dist: %s (%.1f cm) | Capac: %s (%b) | Indut: %s (%b) => Classificado: %s%n",
                hexDistancia, distanciaCm, hexCapacitivo, capacitivoAtivo, hexIndutivo, indutivoAtivo, tipo);
    }
}