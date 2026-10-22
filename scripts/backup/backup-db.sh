#!/usr/bin/env bash
# Copia do banco (schema public) em formato custom do pg_dump, criptografada com AES-256, mais um manifesto com a contagem de
# linhas de cada tabela antes e depois da cópia (verify-restore.sh usa isso para provar que a restauração voltou completa; o
# banco continua recebendo dados durante a cópia, por isso são duas contagens e a restauração tem de cair entre elas).
#
# Variáveis (nunca em arquivo versionado):
#   BACKUP_DB_URL      endereço do banco, ex.: postgresql://usuario:senha@host:5432/postgres?sslmode=require
#   BACKUP_PASSPHRASE  frase para criptografar (gere com: openssl rand -base64 32; guarde no gerenciador de senhas)
# Uso: scripts/backup/backup-db.sh <pasta-de-saída>
# Saída (uma linha no stdout): o caminho do .dump.enc; ao lado dele fica o .counts.txt com "tabela<TAB>antes<TAB>depois".
set -euo pipefail

out_dir="${1:?uso: backup-db.sh <pasta-de-saída>}"
: "${BACKUP_DB_URL:?defina BACKUP_DB_URL}"
: "${BACKUP_PASSPHRASE:?defina BACKUP_PASSPHRASE}"

here="$(cd "$(dirname "$0")" && pwd)"
stamp="$(date -u +%Y%m%d-%H%M%S)"
mkdir -p "$out_dir"
dump="$out_dir/vyzzu-db-$stamp.dump"
counts="$out_dir/vyzzu-db-$stamp.counts.txt"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

"$here/table-counts.sh" "$BACKUP_DB_URL" > "$work/before.txt"
pg_dump --format=custom --no-owner --no-privileges --schema=public --file="$dump" "$BACKUP_DB_URL"
"$here/table-counts.sh" "$BACKUP_DB_URL" > "$work/after.txt"
LC_ALL=C join -t "$(printf '\t')" "$work/before.txt" "$work/after.txt" > "$counts"

# Criptografa e apaga o arquivo aberto. O PBKDF2 com muitas iterações torna lento adivinhar a frase.
openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -pass env:BACKUP_PASSPHRASE -in "$dump" -out "$dump.enc"
rm -f "$dump"

echo "$dump.enc"
