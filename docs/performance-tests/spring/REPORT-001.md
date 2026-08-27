# Relatorio Avancado de Engenharia de Performance #001

## 1. Resumo Executivo
*   **Data do Teste:** 27 de Agosto de 2026
*   **Ambiente:** Local (Ubuntu 24.04 LTS / OpenJDK 21)
*   **Ferramenta Utilizada:** Grafana k6 v1.x
*   **Alvo do Teste:** Endpoint `POST /api/v1/payments`
*   **Carga Aplicada:** Rampa progressiva com pico de 10.000 Usuarios Virtuais Simultaneos (VUs)
*   **Resultado Geral:** **FALHA DE INFRAESTRUTURA (SISTEMA EM COLAPSO)**

---

## 2. Metricas Coletadas e Traducao Tecnica

| Metrica | Valor Coletado | Status | Impacto Tecnico Real |
| :--- | :--- | :--- | :--- |
| **`http_req_failed`** | **84.12%** (2.067 de 2.457) | FALHA | O servidor rejeitou a maioria absoluta do trafego. Clientes receberam timeouts severos. |
| **`http_req_duration` (Max)** | **1m 0s** (60.000 ms) | CRITICO | As requisicoes estouraram o tempo limite padrao do Tomcat, congelando conexoes por 60s. |
| **`http_req_duration` (p95)** | **1m 0s** (60.000 ms) | CRITICO | Significa que 95% de todas as pessoas que tentaram pagar ficaram presas por um minuto sem resposta. |
| **`http_reqs` (Vazão)** | **27.29 reqs/s** | BAIXO | Throughput baixissimo. O motor Java ficou asfixiado por bloqueios de IO e esgotamento de threads. |
| **`Interrupted Iterations`**| **7.931** | ALTO | Conexoes ativas cortadas a forca pelo k6 porque o teste mudou de estagio e o Java nao respondeu. |

---

## 3. Analise Detalhada da Causa Raiz e Gargalos de Codigo

O colapso nao foi causado por logica de programação ou sintaxe do codigo Java, mas por um efeito cascata destrutivo gerado pelas configuracoes de fabrica limitadas combinadas com o uso agressivo de concorrencia em disco.

### Gargalo 1: Esgotamento de Threads do Servidor HTTP (Tomcat)
Por padrao, o servidor web embutido do Spring Boot (Tomcat) esta limitado a processar no maximo 200 requisicoes simultaneas. Quando a rampa do k6 saltou para 10.000 conexoes, o Tomcat esgotou instantaneamente o seu pool de threads operacionais. As novas requisicoes entraram em uma fila interna de espera ate estourarem o tempo limite na rede, gerando os timeouts de 1 minuto.

### Gargalo 2: Starvation (Fome) no Pool de Conexoes do Banco (HikariCP)
O Spring Boot inicializa o gerenciador de conexoes com o PostgreSQL (HikariCP) configurado para abrir, no maximo, 10 conexoes simultaneas com o banco de dados. Como 10.000 operacoes tentavam disputar apenas 10 canais fisicos abertos com o Postgres, as threads do Java ficaram bloqueadas esperando uma vaga no pool do Hikari ate sofrerem timeout.

### Gargalo 3: Fila Infinita gerada pelo Lock Pessimista (SELECT FOR UPDATE)
Para garantir a consistencia financeira e evitar condicoes de corrida, implementamos um Lock Pessimista de escrita agressivo na camada de dados:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Payment p WHERE p.idempotencyKey = :idempotencyKey")
Optional<Payment> findByIdempotencyKeyForUpdate(String idempotencyKey);
```
No PostgreSQL, o `PESSIMISTIC_WRITE` executa uma instrução `SELECT ... FOR UPDATE`, que bloqueia fisicamente a linha ou o indice no banco ate que a transacao de o commit. Como a simulacao do gateway externo leva 2 segundos, a primeira thread bloqueou a chave por 2 segundos. As outras milhares de requisicoes simultaneas com a mesma chave ficaram empilhadas em uma fila infinita de espera dentro do banco, travando os recursos do sistema.

---

## 4. Próximas Ações Recomendadas e Justificativas Técnicas

### Fine-Tuning do `application.yml`

```yaml
server:
  tomcat:
    threads:
      max: 1000
      min-spare: 50

spring:
  datasource:
    hikari:
      maximum-pool-size: 100
      minimum-idle: 20
      connection-timeout: 5000
```
*   **Por que aplicar:** O aumento de conexoes do Hikari para 100 e de threads do Tomcat para 1000 expande a capacidade de processamento concorrente simultaneo em memoria da aplicacao. O `connection-timeout` reduzido para 5 segundos faz com que o sistema desista e falhe rapido caso o banco esteja lotado, impedindo que threads acumulem filas gigantescas de 1 minuto que travam o servidor inteiro.

### Adicionar Lock Timeout no Repositório

```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")})
    @Query("SELECT p FROM Payment p WHERE p.idempotencyKey = :idempotencyKey")
    Optional<Payment> findByIdempotencyKeyForUpdate(String idempotencyKey);
```

*   **Por que aplicar:** Por padrao, o PostgreSQL deixa a transacao esperando na fila do `SELECT FOR UPDATE` por tempo indeterminado. Injetar a propriedade de timeout limita essa espera a no maximo 2 segundos. Se outra thread demorar mais do que isso para processar, o banco rejeita a segunda chamada imediatamente, liberando os recursos do banco e gerando uma resposta controlada de erro em vez de congelar a aplicacao.

### Acoplar Escudo de Redis

```java
// Contrato conceitual da verificacao distribuida atômica
Boolean isSaved = redisTemplate.opsForValue()
    .setIfAbsent("idempotency:" + key, "PROCESSING", Duration.ofHours(24));

if (Boolean.FALSE.equals(isSaved)) {
    throw new PaymentProcessingException("Concurrent request blocked by cache shield.");
}
```

*   **Por que aplicar:** O banco relacional PostgreSQL realiza operacoes persistentes em disco, o que o torna um gargalo natural para travar milhares de acessos concorrentes por segundo. O Redis funciona puramente em memoria RAM distribuida e resolve operacoes atomicas de chave-valor em menos de 1 milissegundo. Ao checar a chave no Redis primeiro, as 9.999 requisicoes repetidas batem no cache e são barradas instantaneamente, sem sequer gastar conexoes de rede ou tocar no disco do PostgreSQL.
