# Relatorio Avancado de Engenharia de Performance #003

## 1. Resumo Executivo
*   **Data do Teste:** 27 de Agosto de 2026
*   **Ambiente:** Local (Ubuntu 24.04 LTS / OpenJDK 21)
*   **Ferramenta Utilizada:** Grafana k6 v1.x
*   **Resultado Geral:** **ESTAGNACAO DE VAZAO POR FALHA DE CONEXAO LAZY COM O CACHE**

---

## 2. Metricas Coletadas e Diagnostico Estatico

| Metrica | Resultado Anterior (002) | Resultado Atual (003) | Status |
| :--- | :--- | :--- | :--- |
| **`http_reqs` (Vazao)** | 133.56 reqs/s | **137.18 reqs/s** | Estagnado |
| **`http_req_failed`** | 64.10% | **65.27%** | Estagnado |
| **`http_req_duration` (Max)**| 43.98s | **43.90s** | Estagnado |

O comportamento idêntico das curvas de latência e vazão comprova que a aplicação Java ignorou o escudo do Redis sob estresse, desviando o fluxo de concorrência direto para o disco rígido do PostgreSQL. Sem um pool ativo de conexões em memória RAM (Lettuce Pool), o Spring Boot sofreu saturação na inicialização dos sockets, abortando o uso do cache e reativando as filas de espera do banco relacional.

---

## 3. Plano de Correção e Mitigacao

*   **Acao 1**: Acoplar a biblioteca `commons-pool2` no pom.xml para dar suporte a conexões simultâneas estruturadas no driver Lettuce.
*   **Acao 2**: Definir limites agressivos de concorrência via `spring.data.redis.lettuce.pool.max-active` no application.yml para suportar a carga de até 10.000 VUs sem sofrer drops na camada de memória.
