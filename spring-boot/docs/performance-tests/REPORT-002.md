# Relatorio Avancado de Engenharia de Performance #002

## 1. Resumo Executivo
*   **Data do Teste:** 27 de Agosto de 2026
*   **Ambiente:** Local (Ubuntu 24.04 LTS / OpenJDK 21)
*   **Ferramenta Utilizada:** Grafana k6 v1.x
*   **Alvo do Teste:** Endpoint `POST /api/v1/payments`
*   **Carga Aplicada:** Pico de 10.000 Usuarios Virtuais Simultaneos (VUs) pós-tunagem de infraestrutura
*   **Resultado Geral:** **EVOLUCAO DE VAZAO COM GARGALO DE CONTENCAO NO POSTGRESQL**

---

## 2. Analise Comparativa de Evolucao (REPORT-001 vs REPORT-002)

| Metrica | Resultado Antigo (001) | Resultado Atual (002) | Evolucao / Impacto Tecnico |
| :--- | :--- | :--- | :--- |
| **`http_reqs` (Vazao)** | 27.29 reqs/s | **133.56 reqs/s** | Crescimento de 389% na capacidade de vazao do Spring Boot. |
| **`checks_total`** | 2.457 | **12.023** | O sistema processou quase 10.000 requisicoes a mais sob a mesma carga. |
| **`http_req_failed`** | 84.12% | **64.10%** | Reducao de 20.02% na taxa de falhas por esgotamento de IO. |
| **`http_req_duration` (Max)**| 1m 0s (60.000 ms) | **43.98s** (43.980 ms) | Reducao de 16 segundos no teto maximo de retencao de rede. |
| **`Interrupted Iterations`**| 7.931 | **4.910** | Menos conexoes dropadas à forca devido a resposta mais ágil do Java. |

---

## 3. Diagnostico do Novo Gargalo Encontrado

A tunagem do application.yml e o ajuste de timeout do HikariCP removeram com sucesso o estrangulamento de memoria e threads do Spring Boot e do Tomcat. 

O sistema esbarrou no limite fisico de I/O de disco do PostgreSQL 18. O uso do Lock Pessimista (`SELECT FOR UPDATE`) obriga o banco de dados relacional a serializar o acesso a linha física no disco para garantir a atomicidade. Sob a pressao extrema de 10.000 conexoes disparadas de forma concorrente em blocos repetidos, o enfileiramento das travas no Postgres causou lentidao operacional severa, esticando o tempo de resposta das transacoes e induzindo os erros de timeout controlados.

---

## 4. Proximo Passo Arquitetural (A Solucao Definitiva)

Os testes 001 e 002 provaram empiricamente que travar a chave de idempotencia direto no banco de dados relacional nao escala sob volume massivo de acessos simultaneos. 

A solucao definitiva e a implementacao do **Escudo de Cache Distribuido com Redis**. O Redis opera puramente em memoria RAM e resolve operacoes atomicas de chave-valor em menos de 1 milissegundo. Ao interceptar e travar a chave de idempotencia no Redis antes de tocar no PostgreSQL, as requisicoes concorrentes repetidas colididas serao barradas instantaneamente na camada de cache, impedindo-as de disputar conexoes ou forcar travas de escrita no disco do banco de dados relacional.
