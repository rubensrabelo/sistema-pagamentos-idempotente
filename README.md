# Sistema de Pagamentos Idempotente

Um serviço baseado em Spring Boot projetado para processar transações financeiras seguras através de controle estrito de idempotência e concorrência. Ele garante que qualquer reles ou requisição idêntica seja executada exatamente uma vez, evitando cobranças duplicadas e inconsistências de dados.

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
