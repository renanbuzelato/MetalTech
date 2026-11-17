import matplotlib.pyplot as plt
import requests

# Endpoint REST da API Spring Boot
URL_RESUMO = "http://localhost:8080/api/v1/leituras/resumo"

response = requests.get(URL_RESUMO)
data = response.json()

print("Dados obtidos da API:", data)

# Plotagem dos Gráficos Estatísticos
labels = ['Peças Metálicas', 'Peças Não-Metálicas']
valores = [data['totalPecasMetalicas'], data['totalPecasNaoMetalicas']]
cores = ['#2b5c8f', '#e07a5f']

plt.figure(figsize=(8, 5))
plt.bar(labels, valores, color=cores)
plt.title('Contagem de Peças Processadas na Esteira (MetalTech)')
plt.ylabel('Quantidade de Peças')
plt.grid(axis='y', linestyle='--', alpha=0.7)

plt.tight_layout()
plt.show()