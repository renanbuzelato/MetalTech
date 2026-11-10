# MetalTech – Back-end (API REST)

API Spring Boot que recebe os dados da esteira de triagem (Automação), guarda no MySQL e
entrega as informações para o dashboard React, o app de Realidade Aumentada e a Ciência de Dados.

## Como rodar
1. Ter o MySQL ligado (padrão: `localhost:3306`, usuário `root`). Crie o banco com `banco_de_dados/01_criar_banco.sql`
   (modelo em 3FN, veja `banco_de_dados/MODELO_BANCO.md`). Sem o script, o Hibernate cria as tabelas sozinho.
2. Se necessário, definir as variáveis `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.
3. `./mvnw spring-boot:run` (API em `http://localhost:8080`)
4. Testes: `./mvnw test` (o teste `ProjetoApplicationTests` precisa do MySQL ligado).

## Estrutura (POO)
| Pacote | Responsabilidade |
|---|---|
| `model` | Entidades e regras do domínio. `Peca` é abstrata; `PecaMetalica` e `PecaNaoMetalica` herdam dela e respondem `getTipo()` (polimorfismo). `Esteira` calcula a ocupação do reservatório. Atributos privados, sem setters (encapsulamento). |
| `repository` | Acesso ao banco (Spring Data JPA). |
| `service` | Regras de negócio. `ClassificadorDePeca` combina os sensores indutivo e capacitivo. |
| `controller` | Endpoints REST. |
| `dto` | Formato dos JSONs de entrada e saída. |
| `exception` | Respostas de erro (404 e 400). |

## Regras de negócio
- **Classificação:** indutivo acionado → `METALICA`; só capacitivo acionado → `NAO_METALICA`; nenhum → não registra peça (só o nível).
- **Ocupação do reservatório:** `(alturaReservatorioCm - distanciaMedida) / alturaReservatorioCm * 100`.
  O sensor de distância fica no topo, apontado para baixo: quanto menor a distância, mais cheio.
- **Alerta:** ocupação ≥ `limiteAlertaPercentual` da esteira → `statusReservatorio = ALERTA`.

## Endpoints
| Método | Rota | Quem usa | Descrição |
|---|---|---|---|
| POST | `/api/esteiras` | Admin | Cadastra esteira |
| GET | `/api/esteiras` | Front | Lista esteiras |
| GET | `/api/esteiras/{id}` | Front | Busca esteira |
| PUT | `/api/esteiras/{id}` | Admin | Atualiza esteira |
| DELETE | `/api/esteiras/{id}` | Admin | Exclui esteira (e suas leituras/peças) |
| **POST** | **`/api/esteiras/{id}/leituras`** | **Automação (CLP)** | Envia leitura dos sensores |
| GET | `/api/esteiras/{id}/leituras` | Ciência de Dados, Front | Histórico do nível do reservatório |
| GET | `/api/esteiras/{id}/pecas` | Ciência de Dados | Histórico das peças classificadas |
| **GET** | **`/api/esteiras/{id}/resumo`** | **Front e RA** | Totais por tipo + nível + status atual |
| POST/GET/PUT/DELETE | `/api/usuarios` e `/api/usuarios/{id}` | Admin | CRUD de usuários |

### Exemplos
Cadastrar esteira — `POST /api/esteiras`
```json
{ "nome": "Esteira 1", "alturaReservatorioCm": 50.0, "limiteAlertaPercentual": 80.0 }
```

Enviar leitura (CLP) — `POST /api/esteiras/1/leituras`
```json
{ "sensorIndutivoAcionado": true, "sensorCapacitivoAcionado": false, "distanciaReservatorioCm": 20.0 }
```

Resumo — `GET /api/esteiras/1/resumo`
```json
{
  "esteiraId": 1,
  "nomeEsteira": "Esteira 1",
  "totalPecasMetalicas": 12,
  "totalPecasNaoMetalicas": 7,
  "ocupacaoReservatorioPercentual": 60.0,
  "distanciaReservatorioCm": 20.0,
  "statusReservatorio": "NORMAL",
  "dataHoraUltimaLeitura": "2026-11-10T21:30:00"
}
```
`statusReservatorio` pode ser `NORMAL`, `ALERTA` ou `SEM_LEITURA`.

### Erros
- `404` `{ "erro": "Esteira não encontrada: 9" }`
- `400` `{ "alturaReservatorioCm": "deve ser maior que 0" }` (campo → mensagem)
