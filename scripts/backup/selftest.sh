#!/usr/bin/env bash
# Autoteste dos scripts de backup: cria um banco de mentira, faz a cópia criptografada, restaura num banco novo e confere.
# Também prova que a conferência pega uma linha faltando e que a senha errada não abre a cópia.
# Uso: scripts/backup/selftest.sh <url-do-servidor-sem-banco>   ex.: postgresql://postgres:postgres@localhost:5432
# (roda na CI a cada mudança, e à mão antes de mexer nos scripts)
set -euo pipefail

admin="${1:?uso: selftest.sh <url-do-servidor-sem-banco>}"
here="$(cd "$(dirname "$0")" && pwd)"
src="$admin/vyzzu_selftest_src"
dst="$admin/vyzzu_selftest_dst"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT

run_sql() { psql "$1" --no-psqlrc --quiet --tuples-only --no-align --set ON_ERROR_STOP=1 --command "$2" > /dev/null; }

for name in vyzzu_selftest_src vyzzu_selftest_dst; do
  run_sql "$admin/postgres" "drop database if exists $name"
  run_sql "$admin/postgres" "create database $name"
done

run_sql "$src" "create table public.clientes (id serial primary key, nome text not null)"
run_sql "$src" "create table public.\"Tabela Com Espaco\" (id serial primary key, valor numeric)"
run_sql "$src" "insert into public.clientes (nome) select 'cliente ' || g from generate_series(1, 50) g"
run_sql "$src" "insert into public.\"Tabela Com Espaco\" (valor) select g * 1.5 from generate_series(1, 7) g"

export BACKUP_DB_URL="$src"
export BACKUP_PASSPHRASE="frase-so-do-autoteste-$$-$RANDOM"

file="$("$here/backup-db.sh" "$out")"
counts="${file%.dump.enc}.counts.txt"
[ -s "$file" ] && [ -s "$counts" ] || { echo "FALHOU: a cópia ou o manifesto não foram gerados" >&2; exit 1; }
if head -c 4096 "$file" | grep -aq "PGDMP"; then echo "FALHOU: a cópia não está criptografada" >&2; exit 1; fi

"$here/restore-db.sh" "$file" "$dst" > /dev/null
"$here/verify-restore.sh" "$counts" "$dst"

# a conferência precisa pegar uma restauração incompleta
run_sql "$dst" "delete from public.clientes where id = 1"
if "$here/verify-restore.sh" "$counts" "$dst" > /dev/null 2>&1; then
  echo "FALHOU: a conferência não percebeu uma linha faltando" >&2
  exit 1
fi

# e a senha errada não pode abrir a cópia
if BACKUP_PASSPHRASE="errada" "$here/restore-db.sh" "$file" "$dst" > /dev/null 2>&1; then
  echo "FALHOU: a cópia abriu com a senha errada" >&2
  exit 1
fi

for name in vyzzu_selftest_src vyzzu_selftest_dst; do
  run_sql "$admin/postgres" "drop database if exists $name"
done
echo "autoteste do backup: ok (cópia criptografada, restauração conferida, falhas detectadas)"
