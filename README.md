# MetalTech

## Sistema de Monitoramento e Gerenciamento de Esteira Industrial

O **MetalTech** é um projeto de monitoramento de uma esteira industrial, desenvolvido com o objetivo de acompanhar o fluxo de peças metálicas e não metálicas, monitorar o nível de um reservatório e disponibilizar informações operacionais por meio de uma interface web.

A solução integra tecnologias de desenvolvimento web, comunicação industrial, processamento de dados e visualização de informações em tempo real.

## 1. Objetivos do projeto

O projeto tem como principais objetivos:

- Monitorar o funcionamento de uma esteira industrial.
- Realizar a leitura de sensores por meio de um mestre IO-Link.
- Identificar e classificar peças a partir das informações recebidas dos sensores.
- Acompanhar o nível de um reservatório.
- Armazenar o histórico das leituras realizadas.
- Disponibilizar informações operacionais em um painel de monitoramento.
- Explorar a utilização de Realidade Aumentada (WebAR) para visualização de informações do sistema.

## 2. Tecnologias utilizadas

### Backend

- Java 17
- Spring Boot 3.2.3
- Spring Data JPA
- Maven
- PostgreSQL
- Eclipse Mosquitto (MQTT)

### Frontend

- React 19
- JavaScript
- Vite
- CSS3

### Comunicação e integração

- API REST
- MQTT
- IO-Link
- Server-Sent Events (SSE)

### Análise e simulação de dados

- Python
- Paho MQTT
- Matplotlib
- Requests

### Realidade Aumentada

- A-Frame
- AR.js
- JavaScript

### Infraestrutura

- Docker
- Docker Compose

## 3. Arquitetura do sistema

A aplicação está organizada em módulos responsáveis por diferentes partes da solução.

### 3.1 Backend

O backend é responsável pelo processamento das informações, comunicação com os dispositivos, disponibilização dos endpoints da API e persistência das leituras no banco de dados.

Entre seus principais componentes estão:

- **ApiGlobalController:** disponibiliza os endpoints da API e gerencia operações relacionadas aos dados do sistema.
- **IoLinkService:** realiza consultas periódicas ao mestre IO-Link para obter informações dos sensores.
- **ProcessamentoService:** recebe e processa informações encaminhadas pela comunicação MQTT.
- **SensorPayloadDTO:** representa a estrutura de dados utilizada no recebimento de informações.
- **LeituraSensor:** entidade responsável pelo mapeamento dos registros de leitura.
- **LeituraSensorRepository:** realiza as operações de persistência utilizando Spring Data JPA.

### 3.2 Frontend

O frontend foi desenvolvido com React e é responsável pela interface de monitoramento do sistema.

Sua função é apresentar os dados disponibilizados pelo backend, permitindo acompanhar informações operacionais por meio de um painel visual.

A comunicação com o backend é centralizada em serviços responsáveis pelas requisições HTTP, gerenciamento de sessão e atualização dos dados.

A aplicação utiliza REST e Server-Sent Events (SSE), com alternativa de atualização periódica por consultas à API.

### 3.3 Comunicação industrial

O módulo de comunicação industrial utiliza o protocolo IO-Link para consultar as informações disponibilizadas pelo mestre conectado aos sensores.

O backend realiza consultas periódicas, em intervalos configurados de dois segundos, e processa os valores recebidos.

Também existe uma estrutura de comunicação MQTT, utilizando o broker Eclipse Mosquitto, para receber mensagens publicadas no tópico:

`metaltech/esteira/sensores`

### 3.4 Simulação e análise de dados

O módulo desenvolvido em Python possui scripts destinados à simulação e análise de informações.

O simulador publica mensagens MQTT com valores gerados para representar o comportamento dos sensores da esteira.

O script de análise utiliza requisições HTTP e Matplotlib para consultar informações e gerar visualizações gráficas.

**Observação:** os contratos de dados entre o simulador MQTT e o DTO do backend, assim como o endpoint utilizado pelo script de análise, precisam de alinhamento para garantir a integração completa.

### 3.5 Realidade Aumentada (WebAR)

O módulo WebAR utiliza A-Frame e AR.js para apresentar uma experiência de visualização baseada em marcador.

A aplicação utiliza um marcador Hiro para iniciar e interromper o ciclo de atualização das informações.

Atualmente, a configuração do módulo utiliza dados simulados como padrão. A integração com dados reais depende da implementação ou adequação do endpoint esperado pelo módulo.

## 4. Banco de dados

O sistema utiliza PostgreSQL para armazenar os registros de leituras dos sensores.

Atualmente, a entidade persistida identificada no backend é `LeituraSensor`, mapeada para a tabela:

`tb_leituras_esteira`

A tabela possui os seguintes atributos:

| Campo | Tipo lógico | Descrição |
|---|---|---|
| id | BIGINT | Identificador da leitura e chave primária |
| tipo_peca | VARCHAR | Classificação da peça |
| nivel_reservatorio_cm | DOUBLE | Nível do reservatório em centímetros |
| data_hora | TIMESTAMP | Data e horário do registro |

O identificador é gerado automaticamente. No modelo persistente atual, não foram identificadas chaves estrangeiras ou relacionamentos com outras tabelas.

Informações administrativas, como usuários, estações, alertas e configurações, são mantidas em estruturas em memória no backend, não constituindo tabelas persistidas no PostgreSQL.

## 5. Infraestrutura e execução

A infraestrutura utiliza Docker Compose para organizar os serviços necessários à execução da aplicação.

Os serviços configurados incluem:

- **Backend:** aplicação Java com Spring Boot.
- **Banco de dados:** PostgreSQL 15.
- **Broker MQTT:** Eclipse Mosquitto 2.0.

Os serviços são conectados por uma rede Docker denominada `metaltech-net`.

Também são configurados volumes para persistência dos dados do PostgreSQL e do Mosquitto.

### Pré-requisitos

Para executar o ambiente, são necessários:

- Docker instalado.
- Docker Compose disponível.
- Git para obter o código-fonte.
- Acesso à rede do dispositivo IO-Link, caso seja utilizada a comunicação com o equipamento físico.

### Executando a infraestrutura

A partir do diretório que contém o arquivo `docker-compose.yml`, execute:

```bash
docker compose up -d --build
```

Para verificar os serviços em execução:

```bash
docker compose ps
```

Para acompanhar os logs:

```bash
docker compose logs -f
```

Para interromper os serviços:

```bash
docker compose down
```

**Atenção:** a comunicação com o mestre IO-Link depende do endereço de rede configurado no backend e da disponibilidade do equipamento. A execução dos containers, por si só, não garante a comunicação com os sensores físicos.

## 6. API REST

A API utiliza o prefixo:

`/api/v1`

Entre os grupos de endpoints implementados estão:

| Recurso | Finalidade |
|---|---|
| `/health` | Verificação de saúde da aplicação |
| `/events` | Atualização de informações por SSE |
| `/auth/*` | Operações de autenticação |
| `/users` | Operações relacionadas aos usuários |
| `/permissions/roles` | Consulta de perfis e permissões |
| `/stations` | Informações das estações |
| `/sensors` | Informações dos sensores |
| `/readings` | Consulta de leituras |
| `/reservoir/status` | Informações do reservatório |
| `/analytics/*` | Recursos de análise |
| `/alerts` | Informações de alertas |
| `/settings` | Configurações |
| `/audit-logs` | Registros de auditoria |

Os endpoints acima representam os recursos identificados no controlador. A persistência e a disponibilidade dos dados variam conforme a implementação de cada recurso.

## 7. Estrutura geral do projeto

O repositório está organizado em módulos que separam as responsabilidades da aplicação:

```text
MetalTech/
│
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── FrontEnd/
│   ├── src/
│   ├── public/
│   │   └── ra/
│   └── package.json
│
├── data-science/
│   ├── simulador_esteira.py
│   └── analise_dados.py
│
└── infra/
    └── docker-compose.yml
```

A estrutura acima representa os principais módulos e arquivos identificados no projeto, sem detalhar todos os diretórios internos.

## 8. Considerações técnicas e pontos de atenção

Alguns pontos devem ser considerados durante a evolução do projeto:

- Revisar a correspondência entre as portas do mestre IO-Link e os parâmetros utilizados no processamento dos sensores.
- Padronizar o formato das mensagens MQTT e os atributos esperados pelo backend.
- Alinhar os endpoints utilizados pelo script de análise de dados com os endpoints efetivamente disponibilizados pela API.
- Implementar ou adequar o endpoint de integração utilizado pelo módulo WebAR.
- Avaliar a persistência de informações administrativas atualmente mantidas em memória.
- Externalizar credenciais e endereços de infraestrutura por meio de variáveis de ambiente.
- Restringir as configurações de CORS e revisar os mecanismos de autenticação antes de uma implantação em ambiente de produção.

## 9. Status do projeto

O MetalTech possui uma estrutura de aplicação composta por backend, frontend, banco de dados, comunicação MQTT, integração IO-Link, scripts Python e módulo WebAR.

A solução apresenta uma base funcional para o desenvolvimento de um sistema de monitoramento industrial. Entretanto, algumas integrações entre os módulos ainda precisam de ajustes e validação para garantir o funcionamento completo do fluxo de dados, desde a aquisição das informações dos sensores até sua apresentação na interface.

## 10. Licença

A licença de utilização e distribuição do projeto deve ser definida pelos responsáveis pelo desenvolvimento.
