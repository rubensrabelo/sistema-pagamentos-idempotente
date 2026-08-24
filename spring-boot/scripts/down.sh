#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
PAYMENT_DIR="$ROOT_DIR/idempotent-payment-system"

echo "Parando e removendo containers do MasterSys..."
if [ -d "$PAYMENT_DIR" ]; then
    cd "$PAYMENT_DIR" || exit
    docker compose -f docker-compose.postgres.yml down -v
else
    echo "Erro: Diretorio $PAYMENT_DIR nao encontrado."
    exit 1
fi

echo "Todos os containers foram parados e removidos com sucesso!"