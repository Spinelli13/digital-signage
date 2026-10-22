#!/usr/bin/env bash
# Restaura uma cópia feita por backup-db.sh num banco VAZIO (de teste ou o novo de produção).
#
# Variáveis: BACKUP_PASSPHRASE (a mesma da cópia).
# Uso: scripts/backup/restore-db.sh <arquivo.dump.enc> <url-do-banco-de-destino>
# O destino precisa existir e ser descartável: o pg_restore recria as tabelas do schema public (--clean --if-exists).
set -euo pipefail

file="${1:?uso: restore-db.sh <arquivo.dump.enc> <url-do-banco-de-destino>}"
target="${2:?uso: restore-db.sh <arquivo.dump.enc> <url-do-banco-de-destino>}"
: "${BACKUP_PASSPHRASE:?defina BACKUP_PASSPHRASE}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -pass env:BACKUP_PASSPHRASE -in "$file" -out "$work/db.dump"

# A função rls_auto_enable pertence à configuração do próprio Supabase (um gatilho de evento dele depende dela): fica de fora,
# senão o --clean tentaria apagá-la num projeto Supabase de destino e falharia.
pg_restore --list "$work/db.dump" | grep -v 'rls_auto_enable' > "$work/toc.list"

# --no-owner/--no-privileges: o banco de destino tem outros papéis (o Supabase usa o "postgres").
# Avisos de "does not exist, skipping" do --clean --if-exists são esperados num banco vazio.
pg_restore --no-owner --no-privileges --clean --if-exists --exit-on-error --use-list="$work/toc.list" --dbname="$target" "$work/db.dump"

echo "restaurado em $(echo "$target" | sed -E 's#(://)[^@]*@#\1***@#')"
