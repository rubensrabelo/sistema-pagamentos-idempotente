# Backlog de Melhorias Corporativas (Production-Ready)

Para elevar o microsservico ms-payment para um nivel de alta escala e resiliencia distribuida, restam as seguintes implementacoes na arquitetura:

## 1. Observabilidade e Monitoramento (Metrics e Tracing)

*   **Cenario Atual:** Se uma transacao travar ou se o gateway externo falhar silenciosamente, nao existem metricas visuais ou rastreamento do incidente em tempo real.
*   **Melhoria:** Adicionar o Spring Boot Actuator integrado ao Micrometer para expor metricas nativas do sistema (taxa de requisicoes duplicadas paradas, tempo de resposta do gateway e consumo de CPU). Essas metricas serao coletadas pelo Prometheus e exibidas em paineis graficos no Grafana.

## 2. Tratamento de Erros de Conexao com Circuit Breaker

*   **Cenario Atual:** Se o gateway de pagamentos externo apresentar instabilidade ou ficar indisponivel, as requisicoes na API falharao em massa e o consumidor assincrono continuara forçando chamadas agressivas contra o parceiro.
*   **Melhoria:** Implementar o padrao Circuit Breaker utilizando a biblioteca Resilience4j. Se o parceiro externo cair, o circuito abrira e o sistema passara a responder imediatamente com um erro controlado de indisponibilidade temporaria, poupando recursos e evitando sobrecarregar o gateway parceiro.

## 3. Configuracao de Logs Estruturados (JSON Logging)

*   **Cenario Atual:** Os logs do sistema saem no terminal em formato de texto comum (String). Sob alta carga e volumetria de producao, este formato inviabiliza a indexacao, analise automatizada e correlacao de eventos.
*   **Melhoria:** Configurar o Logback do Spring para exportar os logs em formato JSON estruturado. Isso facilita o envio para ferramentas de busca centralizada (como ELK Stack ou Grafana Loki), permitindo rastrear todo o ciclo de vida de uma chave de idempotencia especifica com apenas uma query.
