# Sistema de Pagamentos Idempotente Multilinguagem

Este repositorio abriga um ecossistema distribuido voltado para o processamento de transacoes financeiras seguras com controle estrito de idempotencia e resiliencia sob alta carga. O projeto foi desenhado sob uma abordagem poliglota para comparar e demonstrar a eficiencia de diferentes paradigmas de desenvolvimento.

## Estrutura do Repositorio

*   `/spring-boot`: Microsservico desenvolvido em Java 21 e Spring Boot 3.x utilizando arquitetura assincrona de alta vazao baseada em filas em memoria.
*   `/go-app`: Microsservico planejado em Go (Golang) com foco em processamento nativo concorrente de baixa latencia utilizando goroutines (Aguardando inicializacao pos-leitura tecnica).
*   `/performance-tests`: Suite de testes de carga e estresse utilizando Grafana k6 e relatorios historicos de evolucao de infraestrutura.
*   `/docs`: Documentacao complementar, analises arquiteturais e referencias teoricas de engenharia financeira.

## Stack Tecnologica Global

*   Java 21 / Spring Boot 3.x
*   Go (Golang)
*   PostgreSQL 18 (Armazenamento Relacional ACID)
*   Redis 8.10 (Escudo de Idempotencia e Fila Assincrona)
*   Grafana k6 (Engenharia de Performance)

## Principios Centrais da Arquitetura

### 1. Idempotency Key como Chave Unica
O cliente deve enviar uma chave unica por operacao. O sistema aplica uma restricao de unicidade nessa chave no banco de dados para evitar o reprocessamento de novas chamadas.

### 2. Transacoes Atomicas
Validar, registrar e coordenar a execucao com parceiros externos devem viver na mesma transacao atomica. Uma falha no meio do caminho causa um rollback completo, evitando o estado indefinido.

### 3. Estrategia de Auditoria
Cada tentativa e registrada como um evento imutavel. O estado atual serve como uma protecao do log, garantindo um historico limpo das alteracoes.

### 4. Protecao contra Condicoes de Corrida
O fluxo evita o padrao de checar a existencia e depois inserir em passos separados. Sao usadas operacoes seguras de banco de dados para eliminar a brecha entre a verificacao e a escrita.

## Fluxo Macroscopico de Idempotencia

```mermaid
sequenceDiagram
    autonumber
    Actor Cliente
    Participant API as API Gateway / App
    Participant Cache as Redis 8.10 (RAM)
    Participant DB as PostgreSQL 18 (Disco)

    Cliente->>API: POST /payments (X-Idempotency-Key)
    API->>Cache: SETNX key "PROCESSING" (TTL 24h)
    
    alt Chave ja existente (Concorrencia ou Duplicidade)
        Cache-->>API: Retorna FALSO
        API->>DB: Busca registro financeiro existente
        DB-->>API: Dados da transacao
        API-->>Cliente: Retorna resposta clonada idonca
    else Chave Inedita (Sucesso)
        Cache-->>API: Retorna VERDADEIRO
        API->>Cache: Enfileira mensagem de persistencia
        API-->>Cliente: Responde 201 Created (Status: PROCESSING)
    end
```

## Status do Desenvolvimento Poliglota

### Modulo Java (Spring Boot)
*   **Status:** Concluido e 100% Validado.
*   **Caracteristicas:** Implementacao completa do padrao Write-Behind utilizando Redis como mensageria interna para blindar o PostgreSQL 18. Suporta cargas de estresse de 10.000 usuarios simultaneos atingindo vazao superior a 1.000 requisições por segundo nos testes de carga locais.

### Modulo Go (Golang)
*   **Status:** Em Fase de Planejamento Estreito.
*   **Estrategia:** A implementacao prática deste modulo sera iniciada imediatamente apos a conclusao do ciclo completo de leituras, anotacoes e estudos dirigidos mapeados no arquivo de diretrizes localizado em `docs/REFERENCES-PAYMENT.md`. O objetivo e absorver os padroes de mercado da Stripe, Adyen e Wise antes de desenhar os componentes concorrentes nativos em Go.
