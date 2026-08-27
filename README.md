# Sistema de Pagamentos Idempotente

Um serviço baseado em Spring Boot projetado para processar transações financeiras seguras através de cont# Sistema de Pagamentos Idempotente Multilinguagem

Este repositorio abriga um ecossistema distribuido voltado para o processamento de transacoes financeiras seguras com controle estrito de idempotencia e resiliencia sob alta carga. O projeto foi desenhado sob uma abordagem poliglota para comparar e demonstrar a eficiencia de diferentes paradigmas de desenvolvimento.

## Estrutura do Repositorio

*   `/spring-boot`: Microsservico desenvolvido em Java 21 e Spring Boot 3.x utilizando arquitetura assincrona de alta vazao baseada em filas em memoria.
*   `/go-app`: Microsservico desenvolvido em Go (Golang) com foco em processamento nativo concorrente de baixa latencia utilizando goroutines.
*   `/performance-tests`: Suite de testes de carga e estresse utilizando Grafana k6 e relatorios historicos de evolucao de infraestrutura.

## Stack Tecnologica Global

*   Java 21 / Spring Boot 3.x
*   Go (Golang)
*   PostgreSQL 18 (Armazenamento Relacional ACID)
*   Redis 8.10 (Escudo de Idempotencia e Fila Assincrona)
*   Grafana k6 (Engenharia de Performance)

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
role estrito de idempotência e concorrência. Ele garante que qualquer reles ou requisição idêntica seja executada exatamente uma vez, evitando cobranças duplicadas e inconsistências de dados.

## Stack Tecnológica

* Java
* Spring Boot
* PostgreSQL (Transações)
* Idempotency Keys (Chaves de Idempotência)
* Locks & Constraints de Banco de Dados
* Log de Auditoria

## Princípios Centrais da Arquitetura

### 1. Idempotency Key como Chave Única
O cliente deve enviar uma chave única por operação. O sistema aplica uma restrição de unicidade nessa chave no banco de dados para evitar o reprocessamento de novas chamadas.

### 2. Transações Atômicas
Validar, registrar e coordenar a execução com parceiros externos devem viver na mesma transação atômica. Uma falha no meio do caminho causa um rollback completo, evitando o estado indefinido.

### 3. Estratégia de Auditoria
Cada tentativa é registrada como um evento imutável. O estado atual serve como uma proteção do log, garantindo um histórico limpo das alterações.

### 4. Proteção contra Condições de Corrida
O fluxo evita o padrão de checar a existência e depois inserir em passos separados. São usadas operações seguras de banco de dados para eliminar a brecha entre a verificação e a escrita.
