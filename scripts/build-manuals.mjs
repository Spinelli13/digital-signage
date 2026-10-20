#!/usr/bin/env node
// Gera os manuais (PDF ilustrado + HTML) a partir de docs/INSTALACAO_TV.md e docs/MANUAL_PAINEL.md.
//
//   node scripts/build-manuals.mjs --out release-site [--apk player-0.4.7.apk] [--version 0.4.7]
//
// Precisa de um navegador Chromium (Edge ou Chrome) só para imprimir o PDF; ele é procurado automaticamente
// (ou use a variável CHROME_PATH). As imagens vêm de docs/manual/img (capturas reais + esquemas gerados por
// scripts/manual-illustrations.mjs). O texto depois de <!-- fim-do-manual --> não entra no manual.
import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const isWin = process.platform === 'win32';
const args = process.argv.slice(2);
const option = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};

const outDir = resolve(option('out', join(root, 'release-site')));
const apkName = option('apk', '');
const version = option('version', '');
const imgDir = join(root, 'docs', 'manual', 'img');

const manuals = [
  {
    source: 'INSTALACAO_TV.md',
    base: 'manual-instalacao-tv',
    title: 'Manual de instalação do Player',
    subtitle: 'TV TCL com Android TV e Fire TV Stick',
    audience: 'Passo a passo ilustrado: instalar, ativar e manter a TV exibindo as propagandas.',
    // TCL e Fire TV começam em página nova: quem usa só um dos dois imprime só aquelas páginas
    chapters: [/^2\./, /^3\./],
  },
  {
    source: 'MANUAL_PAINEL.md',
    base: 'manual-painel',
    title: 'Manual do painel',
    subtitle: 'Mídias, playlists e dispositivos',
    audience: 'Como usar o painel de administração no dia a dia.',
  },
];

function fail(message) {
  console.error(`\n✖ ${message}`);
  process.exit(1);
}

function findBrowser() {
  const candidates = [
    process.env.CHROME_PATH,
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
    '/usr/bin/google-chrome',
    '/usr/bin/google-chrome-stable',
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
  ].filter(Boolean);
  return candidates.find((candidate) => existsSync(candidate));
}

const escapeHtml = (text) => String(text).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
const slug = (text) =>
  text
    .replace(/<[^>]+>/g, '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

function markdownToHtml(markdown) {
  const inFile = join(tmpdir(), 'signage-manual-in.md');
  const outFile = join(tmpdir(), 'signage-manual-out.html');
  writeFileSync(inFile, markdown);
  const result = spawnSync('npx', ['--yes', 'marked@14', '--gfm', '-i', inFile, '-o', outFile], { cwd: tmpdir(), shell: isWin, encoding: 'utf8' });
  if (result.status !== 0) fail(`Falha ao converter o Markdown:\n${result.stdout}${result.stderr}`);
  return readFileSync(outFile, 'utf8');
}

/** Coloca ids nos títulos (para links internos) e monta o sumário (títulos de nível 2 e 3). */
function addIdsAndToc(html, chapters = []) {
  const toc = [];
  const used = new Set();
  const withIds = html.replace(/<h([23])>([\s\S]*?)<\/h\1>/g, (match, level, inner) => {
    const label = inner.replace(/<[^>]+>/g, '');
    let id = slug(inner) || `secao-${toc.length}`;
    while (used.has(id)) id += '-x';
    used.add(id);
    toc.push({ level: Number(level), id, label: inner.replace(/<[^>]+>/g, '') });
    const chapter = level === '2' && chapters.some((pattern) => pattern.test(label));
    return `<h${level} id="${id}"${chapter ? ' class="chapter"' : ''}>${inner}</h${level}>`;
  });
  const items = toc.map((entry) => `<li class="l${entry.level}"><a href="#${entry.id}">${escapeHtml(entry.label)}</a></li>`).join('\n');
  return { html: withIds, toc: `<ol class="toc">\n${items}\n</ol>` };
}

/** Imagem sozinha num parágrafo vira figura com legenda (o texto alternativo). */
function figures(html, imageBase) {
  return html
    .replace(/<p><img src="manual\/img\/([^"]+)" alt="([^"]*)"[^>]*><\/p>/g, (m, file, alt) => `<figure><img src="${imageBase}${file}" alt="${escapeHtml(alt)}"><figcaption>${alt}</figcaption></figure>`)
    .replace(/<img src="manual\/img\/([^"]+)"/g, (m, file) => `<img src="${imageBase}${file}"`);
}

const CSS = `
  :root { --blue:#3563e9; --navy:#111a2c; --text:#172033; --muted:#6d7788; --line:#dfe5ef; --bg:#f5f7fb; }
  * { box-sizing: border-box; }
  html { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body { margin:0; color:var(--text); font:10.5pt/1.55 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; }
  @page { size: A4; margin: 18mm 16mm 20mm; @bottom-center { content: "Digital Signage  ·  " attr(data-title) "  ·  página " counter(page); font: 8pt 'Segoe UI', Arial, sans-serif; color: #8a94a6; } }
  @page :first { margin: 0; @bottom-center { content: none; } }
  .cover { height: 297mm; page-break-after: always; background: linear-gradient(160deg, #111a2c 0%, #1c2c52 55%, #3563e9 130%); color:#fff; padding: 34mm 24mm 24mm; display:flex; flex-direction:column; }
  .cover img.logo { width: 30mm; height: 30mm; border-radius: 7mm; box-shadow: 0 6px 24px rgba(0,0,0,.35); }
  .cover .brand { margin-top: 10mm; letter-spacing: .28em; text-transform: uppercase; font-size: 10pt; color:#9fb6ff; font-weight:700; }
  .cover h1 { font-size: 34pt; line-height: 1.1; margin: 8mm 0 4mm; border:0; color:#fff; }
  .cover .sub { font-size: 16pt; color:#cfdcff; margin:0; }
  .cover .aud { margin-top: 12mm; font-size: 11.5pt; color:#b9c7ea; max-width: 120mm; }
  .cover .meta { margin-top:auto; font-size: 10pt; color:#9fb0d6; }
  .cover .meta b { color:#fff; }
  .toc-page { page-break-after: always; }
  .toc-page h2 { margin-top:0; }
  ol.toc { list-style:none; padding:0; margin:0; }
  ol.toc li { padding: 3px 0; border-bottom: 1px dotted var(--line); }
  ol.toc li.l2 { font-weight:700; margin-top: 8px; border-bottom-color: var(--line); }
  ol.toc li.l3 { padding-left: 8mm; font-size: 10pt; }
  ol.toc a { color: var(--text); text-decoration:none; }
  h1 { font-size: 22pt; color: var(--navy); margin: 0 0 6mm; border-bottom: 3px solid var(--blue); padding-bottom: 3mm; }
  h2 { font-size: 17pt; color: var(--navy); border-bottom: 2px solid var(--blue); padding-bottom: 2mm; margin: 12mm 0 5mm; break-after: avoid; }
  h2.chapter { break-before: page; margin-top: 0; }
  h2:first-of-type, .toc-page h2 { margin-top: 0; }
  h3 { font-size: 12.5pt; color: var(--blue); margin: 7mm 0 2mm; break-after: avoid; }
  p, li { orphans: 3; widows: 3; }
  a { color: var(--blue); text-decoration: none; }
  code { font-family: Consolas, 'Courier New', monospace; font-size: .92em; background:#eef1f7; padding: 1px 5px; border-radius: 4px; }
  pre { background: var(--navy); color:#e8eefc; padding: 3.5mm 4.5mm; border-radius: 6px; overflow:hidden; white-space: pre-wrap; break-inside: avoid; }
  pre code { background:none; padding:0; color:inherit; }
  blockquote { margin: 4mm 0; padding: 2.5mm 4.5mm; border-left: 4px solid var(--blue); background:#eef3ff; border-radius: 0 6px 6px 0; break-inside: avoid; }
  blockquote p { margin: 1mm 0; }
  table { width:100%; border-collapse: collapse; margin: 3mm 0 5mm; font-size: 9.6pt; }
  th, td { text-align:left; vertical-align: top; padding: 2mm 2.5mm; border: 1px solid var(--line); }
  th { background: var(--bg); color: var(--navy); }
  tr { break-inside: avoid; }
  hr { border:0; border-top:1px solid var(--line); margin: 6mm 0; }
  ul.contains-task-list, ul:has(input[type=checkbox]) { list-style:none; padding-left: 2mm; }
  input[type=checkbox] { width: 3.4mm; height: 3.4mm; margin-right: 2mm; vertical-align: -0.5mm; }
  figure { margin: 4mm 0 6mm; text-align:center; break-inside: avoid; }
  figure img { max-width: 100%; max-height: 118mm; border: 1px solid var(--line); border-radius: 8px; box-shadow: 0 2px 10px rgba(16,32,74,.12); }
  figure img[src$=".svg"] { border: 0; box-shadow: none; }
  figcaption { margin-top: 2mm; font-size: 9pt; color: var(--muted); font-style: italic; }
  .screen-note { font-size: 9pt; color: var(--muted); }
  /* versão para tela (HTML): largura de leitura confortável */
  @media screen { body { background: var(--bg); } main.content { max-width: 880px; margin: 0 auto; padding: 24px 18px 60px; background:#fff; } .cover { height:auto; min-height: 60vh; page-break-after: auto; } }
`;

function renderHtml(manual, bodyHtml, toc, { imageBase, forPdf }) {
  const logo = readFileSync(join(root, 'frontend', 'public', 'favicon.svg'));
  const logoUri = `data:image/svg+xml;base64,${logo.toString('base64')}`;
  const date = new Date().toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo', day: '2-digit', month: 'long', year: 'numeric' });
  return `<!doctype html>
<html lang="pt-BR" data-title="${escapeHtml(manual.title)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(manual.title)} — Digital Signage</title>
<style>${CSS}</style>
</head>
<body data-title="${escapeHtml(manual.title)}">
<section class="cover">
  <img class="logo" src="${logoUri}" alt="">
  <div class="brand">Digital Signage</div>
  <h1>${escapeHtml(manual.title)}</h1>
  <p class="sub">${escapeHtml(manual.subtitle)}</p>
  <p class="aud">${escapeHtml(manual.audience)}</p>
  <div class="meta">
    ${version ? `Versão do aplicativo: <b>${escapeHtml(version)}</b><br>` : ''}Atualizado em <b>${escapeHtml(date)}</b><br>
    Sempre a versão mais recente em <b>signage-downloads.pages.dev</b> e no painel (menu "Downloads e manuais")
  </div>
</section>
<section class="toc-page"><h2>Sumário</h2>${toc}</section>
<main class="content">
${bodyHtml}
</main>
</body></html>`;
}

// ---------- execução ----------
mkdirSync(outDir, { recursive: true });
const browser = findBrowser();
if (!browser) fail('Não encontrei o Edge nem o Chrome para gerar o PDF. Instale um deles ou defina CHROME_PATH.');
if (!existsSync(imgDir)) fail(`Faltam as imagens dos manuais em ${imgDir}`);

// imagens para a versão HTML pública
rmSync(join(outDir, 'manual-img'), { recursive: true, force: true });
cpSync(imgDir, join(outDir, 'manual-img'), { recursive: true });

const buildDir = join(tmpdir(), 'signage-manuals');
rmSync(buildDir, { recursive: true, force: true });
mkdirSync(buildDir, { recursive: true });

for (const manual of manuals) {
  const sourcePath = join(root, 'docs', manual.source);
  if (!existsSync(sourcePath)) fail(`Fonte do manual não encontrada: ${sourcePath}`);
  let markdown = readFileSync(sourcePath, 'utf8').replace(/\r\n/g, '\n');
  markdown = markdown.split('<!-- fim-do-manual -->')[0];
  // exemplos de comandos acompanham a versão publicada
  if (apkName) markdown = markdown.replace(/player-\d+\.\d+\.\d+(?:-[\w.]+)?\.apk/g, apkName);

  const { html, toc } = addIdsAndToc(markdownToHtml(markdown), manual.chapters);

  // HTML público (imagens em manual-img/)
  writeFileSync(join(outDir, `${manual.base}.html`), renderHtml(manual, figures(html, 'manual-img/'), toc, { forPdf: false }));

  // HTML temporário para o PDF (imagens por caminho absoluto)
  const pdfHtmlPath = join(buildDir, `${manual.base}.html`);
  writeFileSync(pdfHtmlPath, renderHtml(manual, figures(html, `${pathToFileURL(imgDir).href}/`), toc, { forPdf: true }));

  const pdfPath = join(outDir, `${manual.base}.pdf`);
  rmSync(pdfPath, { force: true });
  const result = spawnSync(
    browser,
    ['--headless=new', '--disable-gpu', '--no-sandbox', '--no-pdf-header-footer', '--run-all-compositor-stages-before-draw', '--virtual-time-budget=20000', `--print-to-pdf=${pdfPath}`, pathToFileURL(pdfHtmlPath).href],
    { encoding: 'utf8', timeout: 120000 },
  );
  if (!existsSync(pdfPath)) fail(`O navegador não gerou o PDF de "${manual.title}":\n${result.stdout}${result.stderr}`);
  console.log(`ok ${manual.base}.pdf (${Math.round(readFileSync(pdfPath).length / 1024)} KB)`);
}

// manual.html: endereço antigo, aponta para o novo
writeFileSync(join(outDir, 'manual.html'), '<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0; url=manual-instalacao-tv.html"><a href="manual-instalacao-tv.html">Manual de instalação</a>');
console.log(`Manuais gerados em ${outDir}`);
