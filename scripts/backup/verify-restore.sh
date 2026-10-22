#!/usr/bin/env bash
# Confere um banco restaurado contra o manifesto da cópia: as mesmas tabelas, e a contagem de linhas de cada uma entre a
# contagem de antes e a de depois da cópia (o banco de produção recebe dados enquanto o pg_dump roda).
# Uso: scripts/backup/verify-restore.sh <manifesto.counts.txt> <url-do-banco-restaurado>
# Sai com erro (e diz qual tabela) se alguma faltar, sobrar ou tiver contagem fora do intervalo.
set -euo pipefail

manifest="${1:?uso: verify-restore.sh <manifesto.counts.txt> <url-do-banco-restaurado>}"
target="${2:?uso: verify-restore.sh <manifesto.counts.txt> <url-do-banco-restaurado>}"
tab="$(printf '\t')"

actual="$(mktemp)"
trap 'rm -f "$actual"' EXIT
"$(dirname "$0")/table-counts.sh" "$target" > "$actual"

# tabela, antes, depois, restaurado (MISSING quando a tabela só existe de um lado)
LC_ALL=C join -t "$tab" -a1 -a2 -e MISSING -o 0,1.2,1.3,2.2 "$manifest" "$actual" | awk -F'\t' '
  {
    tables++
    if ($4 == "MISSING" || $2 == "MISSING") { printf "tabela %s: %s\n", $1, ($4 == "MISSING" ? "faltou na restauração" : "não existia na cópia") > "/dev/stderr"; bad++; next }
    low = ($2 < $3) ? $2 : $3; high = ($2 < $3) ? $3 : $2
    rows += $4
    if ($4 + 0 < low + 0 || $4 + 0 > high + 0) { printf "tabela %s: restaurou %s linhas, a cópia tinha entre %s e %s\n", $1, $4, low, high > "/dev/stderr"; bad++ }
  }
  END {
    if (tables == 0) { print "o manifesto ou o banco restaurado está vazio: nada foi conferido" > "/dev/stderr"; exit 1 }
    if (bad > 0) { print "A RESTAURAÇÃO NÃO CONFERE com o manifesto da cópia" > "/dev/stderr"; exit 1 }
    printf "restauração conferida: %d tabelas, %d linhas, dentro das contagens da cópia\n", tables, rows
  }'
