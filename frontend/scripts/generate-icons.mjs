#!/usr/bin/env node
// Gera src/app/shared/icons.ts com SÓ os ícones usados no painel (Lucide, licença ISC), como SVG embutido.
//
//   node scripts/generate-icons.mjs
//
// Por que embutir: nenhuma requisição extra, nenhuma fonte de ícones para baixar (funciona offline depois de aberto)
// e o pacote não entra no navegador, só os desenhos usados. Para usar um ícone novo, acrescente o nome em ICONS abaixo
// (nomes em https://lucide.dev/icons), rode o script e use <app-icon name="nome" />.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const nodes = JSON.parse(readFileSync(join(root, 'node_modules', 'lucide-static', 'icon-nodes.json'), 'utf8'));

const ICONS = [
  // navegação
  'layout-dashboard', 'monitor', 'map-pin', 'layers', 'megaphone', 'layout-template', 'bell', 'chart-column', 'image',
  'list-video', 'users', 'credit-card', 'download', 'building', 'receipt-text', 'tag', 'settings', 'menu', 'life-buoy',
  // ações
  'plus', 'pencil', 'trash', 'x', 'check', 'search', 'funnel', 'refresh-cw', 'upload', 'copy', 'external-link', 'send',
  'ellipsis', 'ellipsis-vertical', 'log-out', 'eye', 'link', 'qr-code', 'rotate-ccw-clock', 'play', 'pause', 'rocket',
  'arrow-right', 'arrow-left', 'arrow-up', 'arrow-down', 'chevron-down', 'chevron-up', 'chevron-right', 'chevron-left',
  'image-plus', 'grip-vertical', 'file-text', 'book-open', 'mail', 'key-round', 'lock',
  // tema, idioma e conta
  'sun', 'moon', 'globe', 'languages', 'user', 'palette',
  // estado
  'circle-check', 'circle-x', 'circle-alert', 'triangle-alert', 'info', 'wifi', 'wifi-off', 'clock', 'calendar',
  'calendar-clock', 'hourglass', 'circle-pause', 'badge-check', 'shield-check',
  // conteúdo e rede
  'monitor-play', 'monitor-off', 'monitor-check', 'monitor-x', 'monitor-dot', 'film', 'clapperboard', 'app-window',
  'activity', 'trending-up', 'hard-drive', 'package', 'server', 'zap', 'sparkles', 'star', 'house', 'smartphone',
  'folder-open', 'gauge', 'timer', 'eye-off', 'plug-zap', 'layout-grid', 'list-checks', 'trending-down', 'cloud-upload', 'image-off', 'list', 'cloud-sun', 'newspaper', 'badge-dollar-sign', 'puzzle',
];

const missing = ICONS.filter((name) => !nodes[name]);
if (missing.length > 0) {
  console.error('Ícones que não existem no Lucide:', missing.join(', '));
  process.exit(1);
}

const attrs = (props) => Object.entries(props).map(([key, value]) => `${key}="${String(value).replace(/"/g, '&quot;')}"`).join(' ');
const inner = (name) => nodes[name].map(([tag, props]) => `<${tag} ${attrs(props)}/>`).join('');

const lines = ICONS.slice().sort().map((name) => `  '${name}': '${inner(name)}',`);
const out = `// ARQUIVO GERADO por scripts/generate-icons.mjs a partir do Lucide (licença ISC, veja THIRD_PARTY_NOTICES.md). Não edite à mão.
// Cada valor é o miolo de um <svg viewBox="0 0 24 24"> de traço (stroke) de 2 px; quem desenha o <svg> é o componente app-icon.
export const ICONS = {
${lines.join('\n')}
} as const;

export type IconName = keyof typeof ICONS;
`;
writeFileSync(join(root, 'src', 'app', 'shared', 'icons.ts'), out);
console.log(`${ICONS.length} ícones em src/app/shared/icons.ts`);
