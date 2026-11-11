package com.metaltech.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaltech.dto.SensorPayloadDTO;
import com.metaltech.service.ProcessamentoService;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MqttConfig {

    private final ProcessamentoService processamentoService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${mqtt.broker.url:tcp://metaltech-mqtt:1883}")
    private String brokerUrl;

    public MqttConfig(ProcessamentoService processamentoService) {
        this.processamentoService = processamentoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciarSubscriberMQTT() {
        try {
            MqttClient client = new MqttClient(brokerUrl, MqttClient.generateClientId());
            client.connect();
            
            client.subscribe("metaltech/esteira/sensores", (topic, message) -> {
                String payloadStr = new String(message.getPayload());
                SensorPayloadDTO dto = objectMapper.readValue(payloadStr, SensorPayloadDTO.class);
                processamentoService.processarLeituraMQTT(dto);
            });
            System.out.println(">>> Subscriber MQTT conectado ao broker e escutando tópico metaltech/esteira/sensores <<<");
        } catch (Exception e) {
            System.err.println("Erro ao conectar no MQTT Broker: " + e.getMessage());
        }
    }
}
