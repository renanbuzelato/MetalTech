import json
import random
import time
import paho.mqtt.client as mqtt

# Configuração do Broker MQTT (Docker Container metaltech-mqtt)
BROKER = "localhost"
PORT = 1883
TOPIC = "metaltech/esteira/sensores"

client = mqtt.Client()
client.connect(BROKER, PORT, 60)

print("--- Iniciando Simulação da Esteira MetalTech ---")

# Simulação de 20 leituras de peças
for i in range(1, 21):
    # 0 = Peça Não-Metálica (Sensor Capacitivo KI5309), 1 = Peça Metálica (Indutivo + Capacitivo)
    is_metalico = random.choice([True, False])
    
    # Simulação do Laser de Distância ToF IFM O5D150 (Reservatório em cm)
    nivel_reservatorio = round(random.uniform(10.0, 45.0), 2)

    payload = {
        "sensorIndutivo": is_metalico,
        "sensorCapacitivo": True,
        "distanciaLaserMm": nivel_reservatorio * 10.0
    }

    client.publish(TOPIC, json.dumps(payload))
    tipo = "METÁLICA" if is_metalico else "NÃO-METÁLICA"
    print(f"[{i}/20] Leitura Enviada: Peça {tipo} | Nível: {nivel_reservatorio} cm")
    time.sleep(0.5)

client.disconnect()
print("--- Simulação Concluída com Sucesso ---")