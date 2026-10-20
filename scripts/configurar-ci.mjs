#!/usr/bin/env node
// Configura, UMA vez, os segredos que o GitHub Actions usa para publicar o Player automaticamente.
//
//   node scripts/configurar-ci.mjs
//
// Lê a chave de assinatura que já existe no seu computador (~/.signage e player/keystore.properties) e o token da
// Cloudflare (variável CLOUDFLARE_API_TOKEN do seu terminal) e os grava como segredos do repositório com o GitHub CLI.
// Nenhum valor é impresso. Pré-requisitos: `gh auth login` feito e um token da Cloudflare com permissão
// "Cloudflare Pages: Editar" (ver docs/DEPLOY.md).
//
// ATENÇÃO: isto envia a chave de assinatura do APK ao GitHub (como segredo, que ninguém consegue ler depois,
// só os fluxos do repositório usam). Quem pode editar os fluxos do repositório pode usar a chave. Se preferir
// não confiar a chave ao GitHub, não rode este script e publique pelo seu computador (node scripts/release-player.mjs).
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const propsFile = join(root, 'player', 'keystore.properties');
if (!existsSync(propsFile)) {
  console.error('Não achei player/keystore.properties. Rode antes: node scripts/create-release-key.mjs');
  process.exit(1);
}
const props = Object.fromEntries(
  readFileSync(propsFile, 'utf8')
    .split(/\r?\n/)
    .filter((line) => line.includes('=') && !line.trim().startsWith('#'))
    .map((line) => [line.slice(0, line.indexOf('=')).trim(), line.slice(line.indexOf('=') + 1).trim()]),
);
const storeFile = props.storeFile?.replace(/\\:/g, ':').replace(/\\\\/g, '\\');
if (!storeFile || !existsSync(storeFile)) {
  console.error('O arquivo da chave (storeFile) não foi encontrado.');
  process.exit(1);
}

const secrets = {
  SIGNAGE_KEYSTORE_BASE64: readFileSync(storeFile).toString('base64'),
  SIGNAGE_KEYSTORE_PASSWORD: props.storePassword,
  SIGNAGE_KEY_ALIAS: props.keyAlias,
  SIGNAGE_KEY_PASSWORD: props.keyPassword,
};
if (process.env.CLOUDFLARE_API_TOKEN) secrets.CLOUDFLARE_API_TOKEN = process.env.CLOUDFLARE_API_TOKEN;
else console.warn('! CLOUDFLARE_API_TOKEN não está definido neste terminal: esse segredo ficará de fora (o painel e o Player precisam dele no GitHub).');

for (const [name, value] of Object.entries(secrets)) {
  if (!value) {
    console.error(`Valor ausente para ${name}.`);
    process.exit(1);
  }
  // o valor vai pela entrada padrão: não aparece na linha de comando nem no histórico
  const result = spawnSync('gh', ['secret', 'set', name], { input: value, encoding: 'utf8', shell: process.platform === 'win32' });
  if (result.status !== 0) {
    console.error(`Falhou ao gravar ${name}: ${result.stderr || result.stdout}`);
    process.exit(1);
  }
  console.log(`✔ ${name} gravado`);
}
console.log('\nPronto. O próximo push na master que mude player/** publica uma versão nova sozinho.');
