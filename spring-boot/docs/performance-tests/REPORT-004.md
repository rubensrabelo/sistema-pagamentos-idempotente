# Relatorio Avancado de Engenharia de Performance #004

## 1. Resumo Executivo
*   **Data do Teste:** 27 de Agosto de 2026
*   **Ambiente:** Local (Ubuntu 24.04 LTS / OpenJDK 21)
*   **Ferramenta Utilizada:** Grafana k6 v1.x
*   **Alvo do Teste:** Endpoint `POST /api/v1/payments`
*   **Carga Aplicada:** Rampa progressiva com pico de 10.000 Usuarios Virtuais Simultaneos (VUs)
*   **Resultado Geral:** **FALHA POR SATURACAO DE ESCRITA SINCRONA EM DISCO**

---

## 2. Diagnostico do Comportamento Estatico

O acoplamento do pool Lettuce commons-pool2 blindou a camada de conexao com a memoria RAM do Redis, mas as metricas de latencia continuaram estagnadas na casa dos 43 segundos, mantendo a taxa de falhas em aproximadamente 61.76%.

O script do k6 gera 10.000 chaves exclusivas e ineditas atraves do sufixo dinâmico por VU. Como as chaves sao ineditas na primeira tentativa, todas as 10.000 threads adquirem o lock no Redis com sucesso e avancam simultaneamente para a camada de persistencia relacional. O colapso ocorre porque o PostgreSQL tenta executar 10.000 comandos de insercao fisica (INSERT) no disco rigido local no mesmo milissegundo, gerando gargalo extremo de I/O de hardware (I/O Wait). O banco relacional tornou-se o limitador síncrono do ecossistema.

---

## 3. Correcoes Recomendadas

*   **Abordagem de Validacao**: Alterar o script de estresse do k6 removendo a pulverizacao por VU para forcar a colisão real de chaves identicas e mensurar o throughput de rejeicao em memoria RAM do Redis.
*   **Abordagem Arquitetural (Escolhida)**: Desacoplar a gravacao fisica relacional do ciclo de vida da requisicao HTTP síncrona, adotando uma fila de mensageria assíncrona baseada no padrao Write-Behind para cadenciar a escrita em disco.
