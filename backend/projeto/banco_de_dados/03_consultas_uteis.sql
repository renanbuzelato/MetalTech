USE metaltech;

-- Situação atual (o que o dashboard e a RA mostram)
SELECT * FROM vw_resumo_esteira;

-- Proporção metálico x não metálico (gráfico de barras)
SELECT tipo_peca, COUNT(*) AS total
FROM peca
WHERE esteira_id = 1
GROUP BY tipo_peca;

-- Média, mínimo e máximo da ocupação (estatística descritiva)
SELECT AVG(ocupacao_percentual) AS media,
       MIN(ocupacao_percentual) AS minimo,
       MAX(ocupacao_percentual) AS maximo,
       STDDEV_SAMP(ocupacao_percentual) AS desvio_padrao
FROM vw_leitura_ocupacao
WHERE esteira_id = 1;

-- Série temporal do nível (gráfico de linha)
SELECT data_hora_leitura, ocupacao_percentual
FROM vw_leitura_ocupacao
WHERE esteira_id = 1
ORDER BY data_hora_leitura;

-- Peças por minuto (evolução da contagem ao longo do tempo)
SELECT DATE_FORMAT(data_hora_classificacao, '%Y-%m-%d %H:%i') AS minuto,
       tipo_peca,
       COUNT(*) AS total
FROM peca
WHERE esteira_id = 1
GROUP BY minuto, tipo_peca
ORDER BY minuto;

-- Possíveis anomalias: variação do nível entre leituras seguidas muito acima da média
SELECT data_hora_leitura, ocupacao_percentual, variacao
FROM (
    SELECT data_hora_leitura,
           ocupacao_percentual,
           ocupacao_percentual - LAG(ocupacao_percentual) OVER (ORDER BY data_hora_leitura) AS variacao
    FROM vw_leitura_ocupacao
    WHERE esteira_id = 1
) AS v
WHERE variacao > 1.5   -- critério simples de alerta: subiu mais de 1,5 ponto percentual entre leituras
ORDER BY data_hora_leitura;
