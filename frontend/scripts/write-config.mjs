// Roda antes do build. Se a variável de ambiente API_BASE_URL existir (ex.: no Cloudflare Pages),
// grava public/config.json com ela — assim o endereço do backend vem da configuração do host e não
// precisa de um commit. Sem a variável, mantém o config.json do repositório (desenvolvimento local).
import { writeFileSync } from 'node:fs';

const url = process.env.API_BASE_URL?.trim();

if (!url) {
  console.log('[config] API_BASE_URL não definida: mantendo public/config.json');
} else if (!/^https?:\/\//.test(url)) {
  console.error(`[config] API_BASE_URL inválida (precisa começar com http:// ou https://): ${url}`);
  process.exit(1);
} else {
  const apiBaseUrl = url.replace(/\/+$/, '');
  writeFileSync(new URL('../public/config.json', import.meta.url), JSON.stringify({ apiBaseUrl }, null, 2) + '\n');
  console.log(`[config] public/config.json -> ${apiBaseUrl}`);
}
