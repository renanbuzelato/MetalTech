-- =====================================================================
-- MetalTech - Banco de dados da plataforma de monitoramento industrial
-- SGBD: MySQL 8.0.16 ou superior (necessário para os CHECK)
-- Modelo em 3ª Forma Normal (veja MODELO_BANCO.md)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS metaltech
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE metaltech;

-- ---------------------------------------------------------------------
-- ESTEIRA: equipamento monitorado e as regras do seu reservatório
-- ---------------------------------------------------------------------
CREATE TABLE esteira (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    nome                      VARCHAR(255) NOT NULL,
    altura_reservatorio_cm    DOUBLE       NOT NULL,  -- distância do sensor (topo) até o fundo
    limite_alerta_percentual  DOUBLE       NOT NULL,  -- ocupação a partir da qual há alerta
    PRIMARY KEY (id),
    CONSTRAINT ck_esteira_altura CHECK (altura_reservatorio_cm > 0),
    CONSTRAINT ck_esteira_limite CHECK (limite_alerta_percentual BETWEEN 1 AND 100)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- PECA: cada peça classificada pelos sensores indutivo/capacitivo
-- (herança SINGLE_TABLE do JPA: a coluna tipo_peca identifica a subclasse)
-- ---------------------------------------------------------------------
CREATE TABLE peca (
    id                       BIGINT      NOT NULL AUTO_INCREMENT,
    esteira_id               BIGINT      NOT NULL,
    tipo_peca                VARCHAR(31) NOT NULL,
    data_hora_classificacao  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_peca_esteira FOREIGN KEY (esteira_id)
        REFERENCES esteira (id) ON DELETE CASCADE,
    CONSTRAINT ck_peca_tipo CHECK (tipo_peca IN ('METALICA', 'NAO_METALICA')),
    INDEX idx_peca_esteira_tipo (esteira_id, tipo_peca),
    INDEX idx_peca_esteira_data (esteira_id, data_hora_classificacao)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- LEITURA_RESERVATORIO: medição do sensor de distância em cada ciclo
-- A ocupação (%) NÃO é gravada: é calculada na view vw_leitura_ocupacao.
-- ---------------------------------------------------------------------
CREATE TABLE leitura_reservatorio (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    esteira_id           BIGINT      NOT NULL,
    distancia_sensor_cm  DOUBLE      NOT NULL,
    data_hora_leitura    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_leitura_esteira FOREIGN KEY (esteira_id)
        REFERENCES esteira (id) ON DELETE CASCADE,
    CONSTRAINT ck_leitura_distancia CHECK (distancia_sensor_cm >= 0),
    INDEX idx_leitura_esteira_data (esteira_id, data_hora_leitura)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- USUARIO: pessoas com acesso à plataforma
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    nome    VARCHAR(255) NOT NULL,
    email   VARCHAR(255) NOT NULL,
    perfil  VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('ADMINISTRADOR', 'TECNICO', 'OPERADOR'))
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- VIEWS (dados derivados, para Ciência de Dados, dashboard e RA)
-- ---------------------------------------------------------------------

-- Cada leitura com a ocupação calculada (série temporal para gráficos e outliers)
CREATE VIEW vw_leitura_ocupacao AS
SELECT
    l.id,
    l.esteira_id,
    l.distancia_sensor_cm,
    LEAST(100, GREATEST(0,
        (e.altura_reservatorio_cm - l.distancia_sensor_cm) / e.altura_reservatorio_cm * 100
    )) AS ocupacao_percentual,
    l.data_hora_leitura
FROM leitura_reservatorio l
JOIN esteira e ON e.id = l.esteira_id;

-- Situação atual de cada esteira (mesma informação do endpoint /resumo)
CREATE VIEW vw_resumo_esteira AS
SELECT
    e.id AS esteira_id,
    e.nome AS nome_esteira,
    (SELECT COUNT(*) FROM peca p
      WHERE p.esteira_id = e.id AND p.tipo_peca = 'METALICA')      AS total_pecas_metalicas,
    (SELECT COUNT(*) FROM peca p
      WHERE p.esteira_id = e.id AND p.tipo_peca = 'NAO_METALICA')  AS total_pecas_nao_metalicas,
    u.ocupacao_percentual   AS ocupacao_reservatorio_percentual,
    u.distancia_sensor_cm   AS distancia_reservatorio_cm,
    CASE
        WHEN u.id IS NULL THEN 'SEM_LEITURA'
        WHEN u.ocupacao_percentual >= e.limite_alerta_percentual THEN 'ALERTA'
        ELSE 'NORMAL'
    END AS status_reservatorio,
    u.data_hora_leitura     AS data_hora_ultima_leitura
FROM esteira e
LEFT JOIN vw_leitura_ocupacao u
       ON u.id = (SELECT MAX(l.id) FROM leitura_reservatorio l WHERE l.esteira_id = e.id);
