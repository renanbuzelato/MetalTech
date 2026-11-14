package com.metaltech.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metaltech.dto.SensorPayloadDTO;
import com.metaltech.service.ProcessamentoService;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MqttConfig {

    private final ProcessamentoService processamentoService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MqttClient client;

    @Value("${mqtt.broker.url:tcp://metaltech-mqtt:1883}")
    private String brokerUrl;

    public MqttConfig(ProcessamentoService processamentoService) {
        this.processamentoService = processamentoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciarSubscriberMQTT() {
        try {
            client = new MqttClient(brokerUrl, MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);

            client.connect(options);

            String[] topicos = {"metaltech/esteira/sensores", "metaltech/sensores"};
            for (String topico : topicos) {
                client.subscribe(topico, (topic, message) -> {
                    try {
                        String payloadStr = new String(message.getPayload());
                        System.out.println(">>> Mensagem recebida em [" + topic + "]: " + payloadStr);
                        SensorPayloadDTO dto = objectMapper.readValue(payloadStr, SensorPayloadDTO.class);
                        processamentoService.processarLeituraMQTT(dto);
                    } catch (Exception ex) {
                        System.err.println("Erro ao processar payload MQTT: " + ex.getMessage());
                    }
                });
            }

            System.out.println(">>> Subscriber MQTT conectado e escutando metaltech/esteira/sensores e metaltech/sensores <<<");
        } catch (Exception e) {
            System.err.println("Erro ao conectar no MQTT Broker: " + e.getMessage());
        }
    }

    @PreDestroy
    public void encerrarSubscriberMQTT() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
            }
        } catch (Exception ignored) {
        }
    }
}