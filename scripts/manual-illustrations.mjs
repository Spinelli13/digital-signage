#!/usr/bin/env node
// Gera os esquemas (SVG) usados nos manuais em docs/manual/img/.
//
//   node scripts/manual-illustrations.mjs
//
// São DESENHOS (esquemas), não capturas de tela: mostram o caminho dos menus e o fluxo das coisas. As capturas
// reais (painel e Player) ficam em docs/manual/img/*.png. Nos esquemas de menu, só a linha destacada traz o nome
// real da opção; as barras cinzas representam "outras opções" para não inventar o conteúdo de telas que não vimos.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const outDir = join(root, 'docs', 'manual', 'img');
mkdirSync(outDir, { recursive: true });

const C = { brand: '#6d28d9', navy: '#0c1410', text: '#0c1410', muted: '#6a6a73', line: '#e4e4e7', bg: '#f4f4f5', green: '#19a66a', magenta: '#d946ef', orange: '#e89a32', red: '#d6455d', white: '#ffffff', panel: '#16201c' };
const FONT = "'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif";

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function svg(width, height, body) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}" font-family="${FONT}">
<defs>
  <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 z" fill="${C.brand}"/></marker>
  <filter id="shadow" x="-10%" y="-10%" width="130%" height="140%"><feDropShadow dx="0" dy="3" stdDeviation="4" flood-color="#09090b" flood-opacity=".18"/></filter>
</defs>
<rect width="${width}" height="${height}" rx="18" fill="${C.bg}"/>
${body}
</svg>
`;
}

const text = (x, y, content, { size = 16, weight = 400, fill = C.text, anchor = 'start' } = {}) =>
  `<text x="${x}" y="${y}" font-size="${size}" font-weight="${weight}" fill="${fill}" text-anchor="${anchor}">${esc(content)}</text>`;

/** Várias linhas de texto centradas. */
const lines = (x, y, list, opts = {}) => list.map((l, i) => text(x, y + i * ((opts.size ?? 16) * 1.3), l, opts)).join('');

const arrow = (x1, y1, x2, y2) => `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${C.brand}" stroke-width="3" marker-end="url(#arrow)"/>`;

const badge = (x, y, n) => `<circle cx="${x}" cy="${y}" r="15" fill="${C.brand}"/>` + text(x, y + 6, n, { size: 16, weight: 700, fill: C.white, anchor: 'middle' });

/** Tela de TV em esquema: título, algumas barras cinzas ("outras opções") e a linha destacada com o nome real. */
function tvScreen(x, y, { w = 236, h = 170, title, target, note, before = 2, after = 1, toggle }) {
  let body = `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="12" fill="${C.panel}" filter="url(#shadow)"/>`;
  body += text(x + 16, y + 28, title, { size: 14, weight: 700, fill: '#c9d1ce' });
  let ry = y + 46;
  for (let i = 0; i < before; i++) {
    body += `<rect x="${x + 16}" y="${ry}" width="${w - 32 - (i % 2) * 40}" height="14" rx="7" fill="#3a3a3d"/>`;
    ry += 24;
  }
  body += `<rect x="${x + 8}" y="${ry - 6}" width="${w - 16}" height="32" rx="8" fill="${C.brand}"/>`;
  body += text(x + 18, ry + 15, target, { size: toggle ? 13 : 14, weight: 700, fill: C.white });
  if (toggle) {
    body += `<rect x="${x + w - 54}" y="${ry + 1}" width="34" height="18" rx="9" fill="#9fe3c4"/><circle cx="${x + w - 29}" cy="${ry + 10}" r="7" fill="${C.green}"/>`;
  }
  ry += 38;
  for (let i = 0; i < after; i++) {
    if (ry + 14 > y + h - (note ? 30 : 12)) break; // não deixa barra sair da tela
    body += `<rect x="${x + 16}" y="${ry}" width="${w - 32 - (i % 2) * 60}" height="14" rx="7" fill="#3a3a3d"/>`;
    ry += 24;
  }
  if (note) body += text(x + 16, y + h - 12, note, { size: 12, fill: '#aab4b0' });
  return body;
}

/** Sequência de telas ligadas por setas (caminho de menus). */
function menuPath(file, heading, steps, footer) {
  const w = 236, gap = 48, pad = 36;
  const width = pad * 2 + steps.length * w + (steps.length - 1) * gap;
  const height = 336;
  let body = text(pad, 44, heading, { size: 22, weight: 700 });
  steps.forEach((step, i) => {
    const x = pad + i * (w + gap);
    body += badge(x + 15, 92, i + 1);
    body += tvScreen(x, 116, step);
    if (i < steps.length - 1) body += arrow(x + w + 8, 200, x + w + gap - 8, 200);
  });
  body += text(pad, height - 22, footer ?? 'Esquema ilustrativo: os nomes dos menus podem variar um pouco conforme o modelo e a versão do sistema.', { size: 13, fill: C.muted });
  writeFileSync(join(outDir, file), svg(width, height, body));
  console.log('ok', file);
}

// ---------- ícones simples ----------
const iconTv = (x, y, s = 1, fill = C.navy) =>
  `<g transform="translate(${x} ${y}) scale(${s})"><rect width="120" height="76" rx="8" fill="${fill}"/><rect x="8" y="8" width="104" height="60" rx="4" fill="${C.brand}" opacity=".9"/><rect x="48" y="80" width="24" height="8" fill="${fill}"/><rect x="34" y="88" width="52" height="6" rx="3" fill="${fill}"/></g>`;
const iconBrowser = (x, y, s = 1) =>
  `<g transform="translate(${x} ${y}) scale(${s})"><rect width="130" height="92" rx="9" fill="${C.white}" stroke="${C.line}" stroke-width="2"/><path d="M0 18h130" stroke="${C.line}" stroke-width="2"/><circle cx="12" cy="9" r="3.5" fill="${C.red}"/><circle cx="24" cy="9" r="3.5" fill="${C.orange}"/><circle cx="36" cy="9" r="3.5" fill="${C.green}"/><rect x="14" y="32" width="102" height="12" rx="6" fill="#dfe5e2"/><rect x="14" y="52" width="70" height="12" rx="6" fill="#dfe5e2"/><rect x="14" y="72" width="44" height="12" rx="6" fill="${C.brand}"/></g>`;
const iconCloud = (x, y, s = 1, fill = C.brand) =>
  `<g transform="translate(${x} ${y}) scale(${s})"><path d="M30 70h72a24 24 0 0 0 2-48 34 34 0 0 0-66-6A28 28 0 0 0 30 70z" fill="${fill}"/></g>`;
const iconPc = (x, y, s = 1) =>
  `<g transform="translate(${x} ${y}) scale(${s})"><rect width="120" height="78" rx="7" fill="${C.navy}"/><rect x="7" y="7" width="106" height="64" rx="3" fill="#070a09"/><text x="14" y="30" font-size="12" fill="#7ee0a6" font-family="Consolas, monospace">PS&gt; adb connect</text><text x="14" y="48" font-size="12" fill="#7ee0a6" font-family="Consolas, monospace">connected to 192…</text><rect x="-8" y="82" width="136" height="8" rx="4" fill="#93a19b"/></g>`;

// =====================================================================================================
// 1. Fluxo geral (os 4 passos)
// =====================================================================================================
{
  const w = 1040, h = 300;
  const stepX = [40, 300, 560, 800];
  const titles = [['Instalar', 'o Player'], ['A TV mostra', 'um código'], ['Ativar', 'no painel'], ['Montar e enviar', 'a playlist']];
  const subs = [['Arquivo APK, sem', 'Google Play'], ['6 letras e números', 'na tela da TV'], ['Dispositivos →', 'Adicionar dispositivo'], ['A TV baixa e começa', 'a exibir sozinha']];
  let body = text(40, 44, 'Como funciona, em 4 passos', { size: 24, weight: 700 });
  stepX.forEach((x, i) => {
    body += badge(x + 15, 90, i + 1);
    body += `<rect x="${x}" y="112" width="200" height="158" rx="14" fill="${C.white}" stroke="${C.line}" stroke-width="2"/>`;
    if (i === 0) body += iconTv(x + 40, 124, 1.0) + `<path d="M${x + 100} 144v26m-12 -10l12 12 12 -12" stroke="${C.white}" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>`;
    if (i === 1) body += iconTv(x + 40, 124, 1.0) + text(x + 100, 172, 'K7M2QX', { size: 20, weight: 800, fill: C.white, anchor: 'middle' });
    if (i === 2) body += iconBrowser(x + 35, 122, 1.0);
    if (i === 3) body += iconTv(x + 40, 124, 1.0) + `<rect x="${x + 52}" y="136" width="96" height="52" rx="3" fill="${C.orange}"/>`;
    body += lines(x + 100, 236, titles[i], { size: 15, weight: 700, anchor: 'middle' });
    if (i < 3) body += arrow(x + 208, 182, stepX[i + 1] - 8, 182);
  });
  // legendas curtas abaixo
  stepX.forEach((x, i) => {
    body += lines(x + 100, 292, subs[i], { size: 12, fill: C.muted, anchor: 'middle' });
  });
  writeFileSync(join(outDir, 'fluxo-geral.svg'), svg(w, 330, body));
  console.log('ok fluxo-geral.svg');
}

// =====================================================================================================
// 2. Arquitetura (quem conversa com quem)
// =====================================================================================================
{
  const w = 1040, h = 380;
  let body = text(40, 44, 'Quem conversa com quem', { size: 24, weight: 700 });
  // painel
  body += iconBrowser(60, 110, 1.15) + lines(135, 238, ['Painel', 'no navegador'], { size: 15, weight: 700, anchor: 'middle' });
  // servidor
  body += iconCloud(425, 100, 1.5) + lines(520, 238, ['Servidor', '(playlists, mídias, TVs)'], { size: 15, weight: 700, anchor: 'middle' });
  // TVs
  body += iconTv(800, 100, 1.0) + iconTv(880, 150, 0.8) + lines(900, 262, ['TVs com o', 'aplicativo Player'], { size: 15, weight: 700, anchor: 'middle' });
  // setas painel <-> servidor <-> TV
  body += `<line x1="215" y1="160" x2="425" y2="160" stroke="${C.brand}" stroke-width="3" marker-end="url(#arrow)" marker-start="url(#arrow)"/>`;
  body += text(320, 148, 'você configura', { size: 12, fill: C.muted, anchor: 'middle' });
  body += `<line x1="640" y1="160" x2="790" y2="160" stroke="${C.brand}" stroke-width="3" marker-end="url(#arrow)" marker-start="url(#arrow)"/>`;
  body += text(715, 148, 'a cada 30 s', { size: 12, fill: C.muted, anchor: 'middle' });
  // site de downloads
  body += `<rect x="420" y="296" width="200" height="56" rx="12" fill="${C.white}" stroke="${C.line}" stroke-width="2"/>` + text(520, 320, 'Site de downloads', { size: 14, weight: 700, anchor: 'middle' }) + text(520, 338, 'APK e manuais (PDF)', { size: 12, fill: C.muted, anchor: 'middle' });
  body += `<path d="M620 324 H990 V215" stroke="${C.green}" stroke-width="3" fill="none" marker-end="url(#arrow)" stroke-dasharray="7 6"/>`;
  body += text(805, 316, 'a TV se atualiza sozinha', { size: 12, fill: C.green, anchor: 'middle', weight: 700 });
  body += `<path d="M520 296 V250" stroke="${C.muted}" stroke-width="2" fill="none" stroke-dasharray="4 4"/>`;
  body += text(60, 330, 'A TV continua exibindo o que já baixou mesmo sem internet.', { size: 13, fill: C.muted });
  writeFileSync(join(outDir, 'arquitetura.svg'), svg(w, h, body));
  console.log('ok arquitetura.svg');
}

// =====================================================================================================
// 3. Controles remotos (o que apertar)
// =====================================================================================================
{
  const w = 880, h = 520;
  function remote(x, title, variant) {
    let b = `<rect x="${x}" y="70" width="150" height="400" rx="40" fill="${C.navy}" filter="url(#shadow)"/>`;
    b += text(x + 75, 50, title, { size: 17, weight: 700, anchor: 'middle' });
    // anel direcional
    b += `<circle cx="${x + 75}" cy="170" r="58" fill="#2a2a2c"/><circle cx="${x + 75}" cy="170" r="24" fill="${C.brand}"/>` + text(x + 75, 176, 'OK', { size: 15, weight: 800, fill: C.white, anchor: 'middle' });
    b += `<path d="M${x + 75} 120l-10 14h20z M${x + 75} 220l-10 -14h20z M${x + 25} 170l14 -10v20z M${x + 125} 170l-14 -10v20z" fill="#c9d1ce"/>`;
    // botões
    b += `<circle cx="${x + 40}" cy="270" r="19" fill="#2a2a2c"/>` + text(x + 40, 276, '←', { size: 20, weight: 700, fill: C.white, anchor: 'middle' });
    b += `<circle cx="${x + 110}" cy="270" r="19" fill="#2a2a2c"/>` + `<path d="M${x + 100} 273v-8l10-8 10 8v8z" fill="none" stroke="#fff" stroke-width="2.4"/>`;
    b += `<circle cx="${x + 40}" cy="320" r="19" fill="#2a2a2c"/>` + text(x + 40, 326, '≡', { size: 22, weight: 700, fill: C.white, anchor: 'middle' });
    b += `<circle cx="${x + 110}" cy="320" r="19" fill="#2a2a2c"/>` + text(x + 110, 325, variant === 'fire' ? '▶❙❙' : '🎤', { size: 12, fill: C.white, anchor: 'middle' });
    b += `<rect x="${x + 26}" y="368" width="98" height="26" rx="13" fill="#2a2a2c"/>` + text(x + 75, 386, variant === 'fire' ? 'volume' : 'volume', { size: 12, fill: '#aab4b0', anchor: 'middle' });
    b += `<rect x="${x + 26}" y="406" width="98" height="26" rx="13" fill="#2a2a2c"/>` + text(x + 75, 424, 'canais / apps', { size: 12, fill: '#aab4b0', anchor: 'middle' });
    return b;
  }
  let body = remote(60, 'Android TV (TCL)', 'android') + remote(660, 'Fire TV Stick', 'fire');
  const labels = [
    ['OK / Selecionar', 230, 170, 'centro do anel: confirma'],
    ['Voltar  ←', 230, 270, 'volta uma tela'],
    ['Início  ⌂', 230, 300, 'tela inicial'],
    ['Menu  ≡', 230, 330, 'opções (só Fire TV)'],
    ['Setas  ▲▼◀▶', 230, 215, 'movem a seleção'],
  ];
  // legenda central
  body += `<rect x="290" y="110" width="300" height="330" rx="16" fill="${C.white}" stroke="${C.line}" stroke-width="2"/>`;
  body += text(440, 144, 'Teclas usadas nos manuais', { size: 16, weight: 700, anchor: 'middle' });
  const legend = [['OK', 'Confirma / "aperte OK"'], ['←', 'Voltar uma tela'], ['⌂', 'Voltar à tela inicial'], ['▲ ▼ ◀ ▶', 'Mover a seleção'], ['≡', 'Menu (Fire TV)']];
  legend.forEach(([k, d], i) => {
    const y = 186 + i * 50;
    body += `<rect x="310" y="${y - 22}" width="68" height="34" rx="9" fill="${C.navy}"/>` + text(344, y, k, { size: 15, weight: 700, fill: C.white, anchor: 'middle' });
    body += text(392, y, d, { size: 13 });
  });
  body += text(440, 420, 'Para digitar com mais facilidade, use o app', { size: 12, fill: C.muted, anchor: 'middle' }) + text(440, 436, 'Google Home / Fire TV (celular).', { size: 12, fill: C.muted, anchor: 'middle' });
  writeFileSync(join(outDir, 'controles-remotos.svg'), svg(w, h, body));
  console.log('ok controles-remotos.svg');
}

// =====================================================================================================
// 4. Caminhos de menu
// =====================================================================================================
menuPath('tcl-modo-desenvolvedor.svg', 'TCL · Ligar as opções de desenvolvedor', [
  { title: 'Tela inicial', target: 'Configurações  ⚙', before: 1, after: 2 },
  { title: 'Configurações', target: 'Preferências do dispositivo', before: 2, after: 2 },
  { title: 'Preferências do dispositivo', target: 'Sobre', before: 1, after: 3 },
  { title: 'Sobre', target: 'Compilação', note: 'Aperte OK 7 vezes seguidas', before: 2, after: 1 },
]);

menuPath('tcl-fontes-desconhecidas.svg', 'TCL · Liberar "instalar apps desconhecidos"', [
  { title: 'Configurações', target: 'Apps', before: 2, after: 2 },
  { title: 'Apps', target: 'Acesso especial', before: 2, after: 1 },
  { title: 'Acesso especial', target: 'Instalar apps desconhecidos', before: 1, after: 2 },
  { title: 'Instalar apps desconhecidos', target: 'Vyzzu', toggle: true, note: 'Ligue a chave (Permitido)', before: 2, after: 1 },
]);

menuPath('tcl-fontes-downloader.svg', 'TCL · Liberar o Downloader a instalar apps', [
  { title: 'Configurações', target: 'Apps', before: 2, after: 2 },
  { title: 'Apps', target: 'Acesso especial', before: 2, after: 1 },
  { title: 'Acesso especial', target: 'Instalar apps desconhecidos', before: 1, after: 2 },
  { title: 'Instalar apps desconhecidos', target: 'Downloader', toggle: true, note: 'Ligue a chave (Permitido)', before: 2, after: 1 },
]);

menuPath('tcl-sobre-outros-apps.svg', 'TCL · Liberar "exibir sobre outros apps"', [
  { title: 'Configurações', target: 'Apps', before: 2, after: 2 },
  { title: 'Apps', target: 'Acesso especial', before: 2, after: 1 },
  { title: 'Acesso especial', target: 'Exibir sobre outros apps', before: 2, after: 1 },
  { title: 'Exibir sobre outros apps', target: 'Vyzzu', toggle: true, note: 'Ligue a chave (Permitido)', before: 1, after: 2 },
]);

menuPath('firetv-modo-desenvolvedor.svg', 'Fire TV Stick · Opções do desenvolvedor', [
  { title: 'Tela inicial', target: 'Configurações  ⚙', before: 1, after: 2 },
  { title: 'Configurações', target: 'Meu Fire TV', before: 2, after: 2 },
  { title: 'Meu Fire TV', target: 'Sobre', before: 1, after: 2 },
  { title: 'Sobre', target: 'Fire TV Stick (nome)', note: 'Aperte OK 7 vezes seguidas', before: 2, after: 1 },
]);

menuPath('firetv-fontes-desconhecidas.svg', 'Fire TV Stick · Liberar "instalar apps desconhecidos"', [
  { title: 'Configurações', target: 'Meu Fire TV', before: 2, after: 2 },
  { title: 'Meu Fire TV', target: 'Opções do desenvolvedor', before: 2, after: 1 },
  { title: 'Opções do desenvolvedor', target: 'Instalar apps desconhecidos', before: 1, after: 2 },
  { title: 'Instalar apps desconhecidos', target: 'Downloader', toggle: true, note: 'Depois, ligue também o Player', before: 2, after: 1 },
]);

menuPath('firetv-abrir-app.svg', 'Fire TV Stick · Abrir o Player depois de instalar', [
  { title: 'Configurações', target: 'Aplicativos', before: 2, after: 2 },
  { title: 'Aplicativos', target: 'Gerenciar aplicativos instalados', before: 2, after: 1 },
  { title: 'Apps instalados', target: 'Vyzzu', before: 2, after: 2 },
  { title: 'Vyzzu', target: 'Iniciar aplicativo', note: 'Aparece na tela de apps', before: 1, after: 2 },
]);

// =====================================================================================================
// 5. Downloader (esquema da tela)
// =====================================================================================================
{
  const w = 880, h = 360;
  let body = text(40, 44, 'App Downloader · baixando o Player', { size: 22, weight: 700 });
  body += `<rect x="40" y="70" width="520" height="250" rx="14" fill="${C.panel}" filter="url(#shadow)"/>`;
  body += text(64, 104, 'Downloader', { size: 15, weight: 700, fill: '#c9d1ce' });
  body += text(64, 136, 'Digite o endereço (URL):', { size: 14, fill: '#aab4b0' });
  body += `<rect x="64" y="148" width="472" height="46" rx="10" fill="#070a09" stroke="${C.brand}" stroke-width="3"/>`;
  body += text(80, 178, 'signage-downloads.pages.dev/d', { size: 19, weight: 700, fill: C.white });
  body += `<rect x="64" y="216" width="96" height="42" rx="21" fill="${C.brand}"/>` + text(112, 243, 'Ir', { size: 17, weight: 800, fill: C.white, anchor: 'middle' });
  body += text(180, 243, '← aperte OK aqui', { size: 13, fill: '#aab4b0' });
  body += `<rect x="64" y="274" width="472" height="10" rx="5" fill="#3a3a3d"/><rect x="64" y="274" width="330" height="10" rx="5" fill="${C.green}"/>`;
  body += text(64, 304, 'Baixando player-….apk  ·  cerca de 6 MB', { size: 13, fill: '#aab4b0' });
  // legenda
  const notes = [['1', 'Abra o Downloader', 'na lista de apps da TV'], ['2', 'Digite o endereço curto', 'e escolha Ir'], ['3', 'Quando terminar,', 'escolha Instalar'], ['4', 'No final: Concluído', 'e Excluir o arquivo']];
  notes.forEach(([n, a, b2], i) => {
    const y = 100 + i * 66;
    body += badge(610, y, n) + text(640, y - 2, a, { size: 15, weight: 700 }) + text(640, y + 18, b2, { size: 13, fill: C.muted });
  });
  body += text(40, 346, 'Esquema ilustrativo do app Downloader; a aparência real pode variar conforme a versão.', { size: 13, fill: C.muted });
  writeFileSync(join(outDir, 'downloader.svg'), svg(w, h, body));
  console.log('ok downloader.svg');
}

// =====================================================================================================
// 6. ADB (PC e TV na mesma rede)
// =====================================================================================================
{
  const w = 880, h = 340;
  let body = text(40, 44, 'Método B · computador e TV na mesma rede', { size: 22, weight: 700 });
  body += iconPc(70, 110, 1.25) + lines(145, 252, ['Computador', 'Windows (PowerShell)'], { size: 15, weight: 700, anchor: 'middle' });
  body += `<rect x="360" y="132" width="160" height="56" rx="28" fill="${C.white}" stroke="${C.line}" stroke-width="2"/>` + text(440, 166, 'Wi‑Fi / cabo', { size: 16, weight: 700, anchor: 'middle' });
  body += `<line x1="270" y1="160" x2="360" y2="160" stroke="${C.brand}" stroke-width="3" marker-end="url(#arrow)"/><line x1="520" y1="160" x2="640" y2="160" stroke="${C.brand}" stroke-width="3" marker-end="url(#arrow)"/>`;
  body += iconTv(645, 112, 1.2) + lines(717, 252, ['TV (IP 192.168.0.45)', 'depuração ligada'], { size: 15, weight: 700, anchor: 'middle' });
  body += text(440, 214, 'adb connect IP_DA_TV:5555', { size: 14, weight: 700, fill: C.brand, anchor: 'middle' });
  body += text(440, 236, 'adb install -r player-….apk', { size: 14, weight: 700, fill: C.brand, anchor: 'middle' });
  body += text(40, 312, 'A TV pede para autorizar o computador na primeira vez: marque "Sempre permitir" e escolha OK.', { size: 13, fill: C.muted });
  writeFileSync(join(outDir, 'adb-conexao.svg'), svg(w, h, body));
  console.log('ok adb-conexao.svg');
}

// =====================================================================================================
// 7. Atualização automática
// =====================================================================================================
{
  const w = 1040, h = 330;
  const steps = [
    ['Nova versão', 'publicada no site', C.brand],
    ['A TV descobre', 'a cada 30 min ou no', C.brand],
    ['Baixa e confere', 'o arquivo (SHA‑256)', C.brand],
    ['Android pergunta', 'na TV: Atualizar?', C.orange],
    ['Aperte OK', 'e ela volta sozinha', C.magenta],
  ];
  let body = text(40, 44, 'Como a TV se atualiza', { size: 24, weight: 700 });
  const bw = 170, gap = 36, pad = 36;
  steps.forEach(([a, b, color], i) => {
    const x = pad + i * (bw + gap);
    body += `<rect x="${x}" y="96" width="${bw}" height="112" rx="14" fill="${C.white}" stroke="${color}" stroke-width="3"/>`;
    body += badge(x + 22, 96, i + 1);
    body += text(x + bw / 2, 148, a, { size: 16, weight: 700, anchor: 'middle' }) + text(x + bw / 2, 172, b, { size: 13, fill: C.muted, anchor: 'middle' });
    if (i === 1) body += text(x + bw / 2, 190, 'botão do painel', { size: 13, fill: C.muted, anchor: 'middle' });
    if (i < steps.length - 1) body += arrow(x + bw + 4, 152, x + bw + gap - 6, 152);
  });
  body += `<rect x="36" y="240" width="968" height="58" rx="12" fill="#fff6e6" stroke="${C.orange}" stroke-width="2"/>`;
  body += text(56, 266, 'O Android sempre pede a confirmação na TV (regra de segurança do sistema).', { size: 15, weight: 700 });
  body += text(56, 286, 'Se ninguém confirmar em 10 minutos, o Player cancela e volta a exibir; tenta de novo mais tarde.', { size: 13, fill: C.muted });
  writeFileSync(join(outDir, 'atualizacao-fluxo.svg'), svg(w, h, body));
  console.log('ok atualizacao-fluxo.svg');
}

// =====================================================================================================
// 8. Conceito: mídias → playlist → TVs (manual do painel)
// =====================================================================================================
{
  const w = 1040, h = 350;
  let body = text(40, 44, 'Como as peças se encaixam', { size: 24, weight: 700 });
  const card = (x, title, sub, color) => `<rect x="${x}" y="90" width="260" height="184" rx="16" fill="${C.white}" stroke="${color}" stroke-width="3"/>` + text(x + 130, 128, title, { size: 20, weight: 800, anchor: 'middle' }) + lines(x + 130, 156, sub, { size: 13, fill: C.muted, anchor: 'middle' });
  body += card(40, 'Biblioteca', ['Imagens (JPG, PNG, WEBP)', 'e vídeos (MP4) enviados', 'uma única vez'], C.brand);
  body += `<g transform="translate(70 216)"><rect width="52" height="34" rx="5" fill="#ff7a18"/><rect x="62" width="52" height="34" rx="5" fill="#d946ef"/><rect x="124" width="52" height="34" rx="5" fill="#8b5cf6"/></g>`;
  body += card(390, 'Playlist', ['A ordem e a duração', 'de cada mídia.', 'Reaproveite em várias TVs'], C.orange);
  body += `<g transform="translate(424 216)"><rect width="52" height="34" rx="5" fill="#8b5cf6"/><rect x="62" width="52" height="34" rx="5" fill="#ff7a18"/><rect x="124" width="52" height="34" rx="5" fill="#d946ef"/></g>`;
  body += card(740, 'TV (dispositivo)', ['Cada TV exibe uma', 'playlist, em repetição,', 'mesmo sem internet'], C.magenta);
  body += iconTv(840, 212, 0.5);
  body += arrow(308, 182, 382, 182) + arrow(658, 182, 732, 182);
  body += text(40, 314, 'Mudou a playlist no painel? Clique em "Publicar alterações": as TVs atualizam em até 1 minuto.', { size: 14, fill: C.muted });
  writeFileSync(join(outDir, 'conceito-playlist.svg'), svg(w, h, body));
  console.log('ok conceito-playlist.svg');
}

// =====================================================================================================
// 9. Estados da TV no painel
// =====================================================================================================
{
  const w = 880, h = 330;
  let body = text(40, 44, 'O que significa o status de cada TV', { size: 22, weight: 700 });
  const rows = [
    [C.green, 'Online', 'Falou com o servidor nos últimos 2 minutos. Tudo certo.'],
    [C.muted, 'Offline', 'Sem contato há mais de 2 minutos. Pode ser a internet da TV; ela continua exibindo o que já baixou.'],
    [C.orange, 'Nunca conectou', 'O dispositivo foi criado no painel mas o Player ainda não se conectou.'],
    [C.red, 'Desativado', 'Você desativou a TV no painel. Ela para de exibir até ser reativada.'],
    [C.brand, 'Aguardando ativação', 'A TV mostrou o código, mas ninguém digitou no painel ainda.'],
  ];
  rows.forEach(([color, name, desc], i) => {
    const y = 90 + i * 46;
    body += `<circle cx="58" cy="${y}" r="9" fill="${color}"/>` + text(82, y + 6, name, { size: 16, weight: 700 }) + text(262, y + 6, desc, { size: 13, fill: C.muted });
  });
  writeFileSync(join(outDir, 'estados-tv.svg'), svg(w, h, body));
  console.log('ok estados-tv.svg');
}

// =====================================================================================================
// 10. Campanhas: como a TV decide o que exibir
// =====================================================================================================
{
  const w = 1040, h = 330;
  let body = text(40, 44, 'Como a TV decide o que exibir', { size: 24, weight: 700 });
  const steps = [
    ['1', 'Quais campanhas valem agora?', ['Só as não pausadas, dentro do', 'período, do dia da semana', 'e do horário (fuso da campanha)']],
    ['2', 'Quais alcançam esta TV?', ['Todas as TVs, esta TV,', 'o local dela ou', 'um grupo de que ela faz parte']],
    ['3', 'Quem vence?', ['A de maior prioridade.', 'Empate: alvo mais específico', '(TV, grupo, local, todas); depois a mais nova']],
  ];
  steps.forEach(([n, title, lines], i) => {
    const x = 40 + i * 330;
    body += `<rect x="${x}" y="84" width="300" height="150" rx="14" fill="${C.white}" stroke="${C.brand}" stroke-width="3"/>`;
    body += badge(x + 24, 84, n) + text(x + 150, 124, title, { size: 16, weight: 700, anchor: 'middle' });
    body += lines.map((l, k) => text(x + 150, 156 + k * 22, l, { size: 13, fill: C.muted, anchor: 'middle' })).join('');
    if (i < 2) body += arrow(x + 304, 160, x + 326, 160);
  });
  body += `<rect x="40" y="258" width="960" height="52" rx="12" fill="#eaf8f2" stroke="${C.green}" stroke-width="2"/>`;
  body += text(520, 280, 'Sem nenhuma campanha valendo, a TV exibe a PLAYLIST DELA (a que você escolheu em Dispositivos).', { size: 15, weight: 700, anchor: 'middle' });
  body += text(520, 299, 'A decisão é refeita a cada contato da TV com o servidor (30 s): a troca acontece sozinha, no horário certo.', { size: 13, fill: C.muted, anchor: 'middle' });
  writeFileSync(join(outDir, 'campanha-decisao.svg'), svg(w, h, body));
  console.log('ok campanha-decisao.svg');
}

// =====================================================================================================
// 11. Campanhas: exemplo de um dia (linha do tempo)
// =====================================================================================================
{
  const w = 1040, h = 360;
  const x0 = 250, x1 = 1000, hour = (x1 - x0) / 24;
  const at = (hh) => x0 + hh * hour;
  let body = text(40, 44, 'Exemplo: uma sexta-feira numa TV da Loja Shopping', { size: 22, weight: 700 }) + text(40, 66, 'Entre parênteses, a prioridade de cada campanha.', { size: 13, fill: C.muted });
  const lanes = [
    ['Promoção de verão (50)', 0, 24, C.orange],
    ['Cardápio do almoço (70)', 11, 14, C.magenta],
    ['Happy hour (80)', 18, 23, C.brand],
  ];
  lanes.forEach(([label, from, to, color], i) => {
    const y = 86 + i * 52;
    body += text(40, y + 22, label, { size: 14 });
    body += `<rect x="${x0}" y="${y}" width="${x1 - x0}" height="30" rx="6" fill="#e3e8e6"/>`;
    body += `<rect x="${at(from)}" y="${y}" width="${at(to) - at(from)}" height="30" rx="6" fill="${color}" opacity=".85"/>`;
  });
  // resultado
  const y = 86 + 3 * 52 + 14;
  body += text(40, y + 22, 'O que a TV exibe', { size: 15, weight: 700 });
  const result = [[0, 11, 'Promoção', C.orange], [11, 14, 'Almoço', C.magenta], [14, 18, 'Promoção', C.orange], [18, 23, 'Happy hour', C.brand], [23, 24, 'Promoção', C.orange]];
  result.forEach(([from, to, label, color]) => {
    body += `<rect x="${at(from) + 1}" y="${y}" width="${at(to) - at(from) - 2}" height="34" rx="6" fill="${color}"/>`;
    if (to - from >= 3) body += text((at(from) + at(to)) / 2, y + 22, label, { size: 13, weight: 700, fill: C.white, anchor: 'middle' });
  });
  // eixo
  for (let hh = 0; hh <= 24; hh += 3) {
    body += `<line x1="${at(hh)}" y1="${y + 40}" x2="${at(hh)}" y2="${y + 48}" stroke="${C.muted}"/>` + text(at(hh), y + 64, `${String(hh).padStart(2, '0')}h`, { size: 12, fill: C.muted, anchor: 'middle' });
  }
  body += text(40, h - 14, 'Das 11h às 14h o almoço (prioridade 70) vence a promoção (50); das 18h às 23h o happy hour (80) vence tudo; no resto do dia vale a promoção.', { size: 12, fill: C.muted });
  writeFileSync(join(outDir, 'campanha-linha-do-tempo.svg'), svg(w, h, body));
  console.log('ok campanha-linha-do-tempo.svg');
}
