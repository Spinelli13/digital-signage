#!/usr/bin/env bash
# Imprime "tabela<TAB>linhas" para cada tabela do schema public, em ordem alfabética byte a byte (a que o join precisa).
# Uso: scripts/backup/table-counts.sh <url-do-banco>
# O sed tira o fim de linha do Windows (o psql do Windows termina as linhas com CR+LF).
set -euo pipefail

url="${1:?uso: table-counts.sh <url-do-banco>}"

tables="$(psql "$url" --no-psqlrc --tuples-only --no-align --command \
  "select table_name from information_schema.tables where table_schema = 'public' and table_type = 'BASE TABLE'" \
  | sed 's/[[:space:]]*$//' | LC_ALL=C sort)"

while IFS= read -r table; do
  [ -z "$table" ] && continue
  rows="$(psql "$url" --no-psqlrc --tuples-only --no-align --command "select count(*) from public.\"$table\"" | sed 's/[[:space:]]*$//')"
  printf '%s\t%s\n' "$table" "$rows"
done <<< "$tables"
