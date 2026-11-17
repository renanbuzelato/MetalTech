package com.metaltech.config;

import com.metaltech.dto.SensorPayloadDTO;
import com.metaltech.service.ProcessamentoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

/*@Configuration
public class MqttConfig {

    private final ProcessamentoService processamentoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MqttConfig(ProcessamentoService processamentoService) {
        this.processamentoService = processamentoService;
    }

    @PostConstruct
    public void conectarBroker() {
        try {
            MqttClient client = new MqttClient("tcp://metaltech-mqtt:1883", MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            client.connect(options);
            client.subscribe("metaltech/esteira/sensores", (topic, message) -> {
                String payload = new String(message.getPayload());
                SensorPayloadDTO dto = objectMapper.readValue(payload, SensorPayloadDTO.class);
                processamentoService.processarMensagemMqtt(dto);
            });
        } catch (MqttException e) {
            System.err.println("Erro ao conectar no broker MQTT: " + e.getMessage());
        }
    }
}*/
@Configuration
public class MqttConfig {

    private final ProcessamentoService processamentoService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MqttClient client;

    public MqttConfig(ProcessamentoService processamentoService) {
        this.processamentoService = processamentoService;
    }

    @PostConstruct
    public void conectarBroker() {
        try {
            this.client = new MqttClient("tcp://metaltech-mqtt:1883", MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            this.client.connect(options);
            this.client.subscribe("metaltech/esteira/sensores", (topic, message) -> {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                SensorPayloadDTO dto = objectMapper.readValue(payload, SensorPayloadDTO.class);
                processamentoService.processarMensagemMqtt(dto);
            });
        } catch (MqttException e) {
            System.err.println("Erro ao conectar no broker MQTT: " + e.getMessage());
        }
    }

    @PreDestroy
    public void desconectarBroker() {
        if (this.client != null) {
            try {
                if (this.client.isConnected()) {
                    this.client.disconnect();
                }
                this.client.close();
            } catch (MqttException e) {
                System.err.println("Erro ao fechar cliente MQTT: " + e.getMessage());
            }
        }
    }
}