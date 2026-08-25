#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
PAYMENT_DIR="$ROOT_DIR/ms-payment"

if [ -f "$PAYMENT_DIR/.env" ]; then
    echo "Carregando variaveis de ambiente do .env..."
    set -a
    # shellcheck source=/dev/null
    source "$PAYMENT_DIR/.env"
    set +a
else
    echo "Erro: Arquivo .env nao encontrado em: $PAYMENT_DIR"
    exit 1
fi

echo "Iniciando o banco de dados PostgreSQL..."
cd "$PAYMENT_DIR" || exit
docker compose -f docker-compose.postgres.yml up -d

echo "Aguardando o PostgreSQL Inicializar na porta $DB_PORT..."
until docker exec payment_postgres_db pg_isready -p 5432 -U "$DB_PAYMENT" >/dev/null 2>&1; do
    sleep 1
done
echo "PostgreSQL do PAYMENT esta pronto para conexoes!"

echo "Compilando e iniciando a aplicacao Spring Boot..."
./mvnw spring-boot:run