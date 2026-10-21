#!/usr/bin/env node
// Compila o APK de release do Player, assina e publica no site de downloads (Cloudflare Pages), junto com os
// manuais em PDF. Roda no seu computador ou no GitHub Actions (.github/workflows/publicar-player.yml).
//
//   node scripts/release-player.mjs --notes "O que mudou nesta versão"
//   node scripts/release-player.mjs --site-only        # republica só o site e os manuais (sem versão nova)
//   node scripts/release-player.mjs --skip-build --dry-run   # só monta o site em release-site/, sem publicar
//
// Versão: o versionCode é o número de commits do repositório (sempre cresce) e o versionName é
// <player/VERSION>.<versionCode>, por exemplo 0.4.71. Use --version-code/--version-name para forçar outros valores.
//
// Chave de assinatura: player/keystore.properties (ver scripts/create-release-key.mjs) ou as variáveis
// SIGNAGE_KEYSTORE_FILE, SIGNAGE_KEYSTORE_PASSWORD, SIGNAGE_KEY_ALIAS e SIGNAGE_KEY_PASSWORD (usadas no CI).
// Publicação: estar logado na wrangler (npx wrangler login) ou ter CLOUDFLARE_API_TOKEN (+ CLOUDFLARE_ACCOUNT_ID).
//
// O site é reconstruído a cada publicação: o script baixa as versões anteriores do próprio site (confere o
// SHA-256 de cada uma), acrescenta a nova e republica tudo. Mantém as 5 últimas. Resultado:
//   /                                 lista de versões para baixar
//   /latest.json, /releases.json      o que o Player e o painel consultam (com CORS liberado)
//   /d                                atalho curto que baixa a versão mais recente (fácil de digitar no Downloader)
//   /manual-instalacao-tv.pdf|.html   manual de instalação (TCL e Fire TV Stick)
//   /manual-painel.pdf|.html          manual do painel
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const playerDir = join(root, 'player');
const stagingDir = join(root, 'release-site');
const isWin = process.platform === 'win32';
const KEEP_VERSIONS = 5;

// ---------- argumentos ----------
const args = process.argv.slice(2);
const flag = (name) => args.includes(`--${name}`);
const option = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};
const site = option('site', 'https://signage-downloads.pages.dev').replace(/\/+$/, '');
const projectName = option('project', 'signage-downloads');
const serverUrl = option('server-url', 'https://signage-backend-1l0l.onrender.com');
const notes = option('notes', '');
const siteOnly = flag('site-only');

const log = (message) => console.log(`\n▶ ${message}`);
const fail = (message) => {
  console.error(`\n✖ ${message}`);
  process.exit(1);
};
const sha256 = (file) => createHash('sha256').update(readFileSync(file)).digest('hex');

function run(command, commandArgs, options = {}) {
  const result = spawnSync(command, commandArgs, { encoding: 'utf8', shell: isWin, ...options });
  if (result.status !== 0 && !options.allowFailure) {
    fail(`Falhou: ${command} ${commandArgs.join(' ')}\n${result.stdout ?? ''}${result.stderr ?? ''}`);
  }
  return result;
}

function sdkDir() {
  const props = join(playerDir, 'local.properties');
  if (existsSync(props)) {
    const line = readFileSync(props, 'utf8').split(/\r?\n/).find((l) => l.startsWith('sdk.dir='));
    if (line) return line.slice('sdk.dir='.length).replace(/\\:/g, ':').replace(/\\\\/g, '\\');
  }
  return process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
}

function apksigner() {
  const sdk = sdkDir();
  const buildTools = sdk && join(sdk, 'build-tools');
  if (!buildTools || !existsSync(buildTools)) fail('Android SDK não encontrado (defina sdk.dir em player/local.properties ou ANDROID_HOME).');
  const newest = readdirSync(buildTools).sort((a, b) => a.localeCompare(b, undefined, { numeric: true })).pop();
  return join(buildTools, newest, isWin ? 'apksigner.bat' : 'apksigner');
}

// ---------- 0. versão ----------
function computeVersion() {
  const commits = run('git', ['rev-list', '--count', 'HEAD'], { cwd: root }).stdout.trim();
  const versionCode = Number(option('version-code', commits));
  if (!Number.isInteger(versionCode) || versionCode < 1) fail(`versionCode inválido: ${commits}`);
  const versionFile = join(playerDir, 'VERSION');
  const base = existsSync(versionFile) ? readFileSync(versionFile, 'utf8').trim() : '0.4';
  if (!/^\d+\.\d+$/.test(base)) fail(`player/VERSION deve ter o formato "maior.menor" (achei "${base}")`);
  return { versionCode, versionName: option('version-name', `${base}.${versionCode}`) };
}

let versionCode;
let versionName;
let builtApk;
let certSha256;

if (!siteOnly) {
  ({ versionCode, versionName } = computeVersion());

  // ---------- 1. build ----------
  const hasKeyFile = existsSync(join(playerDir, 'keystore.properties'));
  const hasKeyEnv = Boolean(process.env.SIGNAGE_KEYSTORE_FILE);
  if (!hasKeyFile && !hasKeyEnv) {
    fail('Falta a chave de assinatura: rode antes node scripts/create-release-key.mjs (ou defina SIGNAGE_KEYSTORE_FILE e as senhas).');
  }
  if (!flag('skip-build')) {
    log(`Compilando o APK de release (versão ${versionName}, código ${versionCode})`);
    run(isWin ? `"${join(playerDir, 'gradlew.bat')}"` : join(playerDir, 'gradlew'), ['assembleRelease', `-PserverUrl=${serverUrl}`, `-PversionCode=${versionCode}`, `-PversionName=${versionName}`, '--console=plain'], {
      cwd: playerDir,
      stdio: 'inherit',
    });
  }

  const apkDir = join(playerDir, 'app', 'build', 'outputs', 'apk', 'release');
  builtApk = join(apkDir, 'app-release.apk');
  if (!existsSync(builtApk)) fail(`APK não encontrado: ${builtApk}`);
  const meta = JSON.parse(readFileSync(join(apkDir, 'output-metadata.json'), 'utf8')).elements[0];
  if (meta.versionCode !== versionCode) fail(`O APK compilado é da versão ${meta.versionCode}, mas esperávamos ${versionCode}. Rode sem --skip-build.`);

  // ---------- 2. assinatura ----------
  log('Verificando a assinatura');
  const verify = run(apksigner(), ['verify', '--print-certs', builtApk]);
  certSha256 = /certificate SHA-256 digest: ([0-9a-f]{64})/i.exec(verify.stdout)?.[1]?.toLowerCase();
  if (!certSha256) fail('Não foi possível ler o certificado do APK (ele está assinado?).');
  console.log(`  certificado SHA-256: ${certSha256}`);
}

// ---------- 3. histórico publicado ----------
log(`Lendo as versões já publicadas em ${site}`);
rmSync(stagingDir, { recursive: true, force: true });
mkdirSync(stagingDir, { recursive: true });

let previous = [];
try {
  const response = await fetch(`${site}/releases.json`, { headers: { 'Cache-Control': 'no-cache' } });
  if (response.ok) previous = (await response.json()).releases ?? [];
} catch {
  // primeira publicação: o site ainda não existe
}
if (siteOnly && previous.length === 0) fail('--site-only precisa de pelo menos uma versão já publicada.');

for (const release of previous) {
  if (certSha256 && release.certSha256 && release.certSha256 !== certSha256 && !flag('allow-new-key')) {
    fail(
      `A chave de assinatura mudou!\n  publicada: ${release.certSha256}\n  agora:     ${certSha256}\n` +
        'As TVs já instaladas RECUSARIAM esta atualização. Use a chave original (~/.signage) ou, se a troca for ' +
        'intencional, rode com --allow-new-key (e reinstale cada TV à mão).',
    );
  }
}
if (!siteOnly && previous.some((r) => r.versionCode >= versionCode)) {
  const newest = Math.max(...previous.map((r) => r.versionCode));
  if (newest > versionCode) fail(`Já existe a versão ${newest} publicada, maior que a atual (${versionCode}). Atualize o repositório (git pull) antes de publicar.`);
}

const releases = [];
const kept = previous.filter((r) => siteOnly || r.versionCode !== versionCode).slice(0, siteOnly ? KEEP_VERSIONS : KEEP_VERSIONS - 1);
for (const release of kept) {
  try {
    const response = await fetch(`${site}/${release.apk}`);
    const data = Buffer.from(await response.arrayBuffer());
    if (!response.ok || createHash('sha256').update(data).digest('hex') !== release.sha256) throw new Error('inválido');
    writeFileSync(join(stagingDir, release.apk), data);
    releases.push(release);
    console.log(`  mantida: ${release.apk}`);
  } catch {
    console.warn(`  ! ${release.apk} não pôde ser recuperada e saiu da lista`);
  }
}

// ---------- 4. nova versão ----------
if (!siteOnly) {
  const apkName = `player-${versionName}.apk`;
  copyFileSync(builtApk, join(stagingDir, apkName));
  releases.unshift({
    versionCode,
    versionName,
    apk: apkName,
    sha256: sha256(builtApk),
    size: statSync(builtApk).size,
    minSdk: 21,
    notes,
    publishedAt: new Date().toISOString(),
    certSha256,
  });
}
releases.sort((a, b) => b.versionCode - a.versionCode);
if (releases.length === 0) fail('Nenhuma versão para publicar.');

const latest = releases[0];
writeFileSync(join(stagingDir, 'releases.json'), JSON.stringify({ releases }, null, 2) + '\n');
writeFileSync(
  join(stagingDir, 'latest.json'),
  JSON.stringify(
    {
      versionCode: latest.versionCode,
      versionName: latest.versionName,
      apkUrl: latest.apk,
      sha256: latest.sha256,
      size: latest.size,
      minSdk: latest.minSdk,
      notes: latest.notes,
      publishedAt: latest.publishedAt,
    },
    null,
    2,
  ) + '\n',
);
// ícones do site (mesmo logo do painel)
for (const icon of ['favicon.svg', 'favicon.ico']) copyFileSync(join(root, 'frontend', 'public', icon), join(stagingDir, icon));
writeFileSync(join(stagingDir, '_redirects'), `/d /${latest.apk} 302\n`);
writeFileSync(
  join(stagingDir, '_headers'),
  [
    '/*.apk', '  Content-Type: application/vnd.android.package-archive', '  Content-Disposition: attachment', '',
    // o painel (outro endereço) lê estes dois arquivos para mostrar a versão mais recente
    '/latest.json', '  Cache-Control: no-cache', '  Access-Control-Allow-Origin: *', '',
    '/releases.json', '  Cache-Control: no-cache', '  Access-Control-Allow-Origin: *', '',
    '/*.pdf', '  Content-Type: application/pdf', '  Cache-Control: no-cache', '',
  ].join('\n'),
);

// manuais em PDF e HTML (a partir de docs/INSTALACAO_TV.md e docs/MANUAL_PAINEL.md)
log('Gerando os manuais (PDF e HTML)');
run(process.execPath, [join(root, 'scripts', 'build-manuals.mjs'), '--out', stagingDir, '--apk', latest.apk, '--version', latest.versionName], { stdio: 'inherit', shell: false });
writeFileSync(join(stagingDir, 'index.html'), indexPage(releases));

console.log(`\nSite montado em ${stagingDir}: ${releases.length} versão(ões), a mais recente é a ${latest.versionName}.`);
if (flag('dry-run')) {
  console.log('--dry-run: nada foi publicado.');
  process.exit(0);
}

// ---------- 5. publicação ----------
log(`Publicando em ${site}`);
const wrangler = (wranglerArgs) =>
  // Rodado fora do repositório: a wrangler nova altera package.json/.gitignore do projeto onde roda.
  run('npx', ['--yes', 'wrangler@4', ...wranglerArgs], { cwd: tmpdir(), allowFailure: true });
const commitMessage = `Player-${latest.versionName}`;
let deploy = wrangler(['pages', 'deploy', stagingDir, '--project-name', projectName, '--branch', 'master', '--commit-message', commitMessage]);
if (deploy.status !== 0 && /does not exist|not found/i.test(deploy.stdout + deploy.stderr)) {
  console.log('  projeto ainda não existe: criando');
  run('npx', ['--yes', 'wrangler@4', 'pages', 'project', 'create', projectName, '--production-branch', 'master', '--force'], { cwd: tmpdir() });
  deploy = wrangler(['pages', 'deploy', stagingDir, '--project-name', projectName, '--branch', 'master', '--commit-message', commitMessage]);
}
if (deploy.status !== 0) fail(`Publicação falhou:\n${deploy.stdout}${deploy.stderr}`);

// ---------- 6. conferência no ar ----------
log('Conferindo o que ficou no ar');
await new Promise((r) => setTimeout(r, 6000));
const live = await (await fetch(`${site}/latest.json`, { headers: { 'Cache-Control': 'no-cache' } })).json();
const liveApk = Buffer.from(await (await fetch(new URL(live.apkUrl, `${site}/`))).arrayBuffer());
const ok = live.versionCode === latest.versionCode && createHash('sha256').update(liveApk).digest('hex') === live.sha256;
if (!ok) fail('O que está no ar não confere com o que foi publicado (versão ou SHA-256).');
console.log(`  latest.json -> versão ${live.versionName}; APK baixado e conferido (SHA-256 ok)`);
for (const pdf of ['manual-instalacao-tv.pdf', 'manual-painel.pdf']) {
  const response = await fetch(`${site}/${pdf}`, { method: 'HEAD' });
  if (!response.ok || !/pdf/i.test(response.headers.get('content-type') ?? '')) fail(`O manual ${pdf} não está no ar (HTTP ${response.status}).`);
  console.log(`  ${pdf}: no ar`);
}
const cors = await fetch(`${site}/releases.json`, { headers: { Origin: 'https://signage-painel.pages.dev' } });
if (cors.headers.get('access-control-allow-origin') !== '*') fail('releases.json está sem o cabeçalho CORS: o painel não conseguirá ler as versões.');
console.log('  releases.json: CORS ok');
console.log(`\n✔ Publicado: ${site}\n  Download direto: ${site}/d`);

// ---------- páginas ----------
function escapeHtml(text) {
  return String(text).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
}

function megabytes(bytes) {
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function dateBr(iso) {
  return new Date(iso).toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' });
}

function page(title, body) {
  return `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)} — Vyzzu</title>
<meta name="theme-color" content="#6d28d9">
<link rel="icon" href="favicon.svg" type="image/svg+xml">
<style>
  :root { --brand:#6d28d9; --text:#0d1612; --muted:#6a6a73; --line:#e4e4e7; --bg:#f4f4f5; }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--text); font:16px/1.55 system-ui,-apple-system,'Segoe UI',Roboto,sans-serif; }
  main { max-width:760px; margin:0 auto; padding:28px 16px 60px; }
  h1,h2,h3 { line-height:1.25; } h2 { margin-top:2em; border-bottom:1px solid var(--line); padding-bottom:.3em; }
  a { color:var(--brand); }
  .card { background:#fff; border:1px solid var(--line); border-radius:14px; padding:22px; margin:18px 0; }
  .btn { display:inline-block; background:var(--brand); color:#fff; text-decoration:none; font-weight:600; padding:13px 22px; border-radius:11px; }
  .muted { color:var(--muted); font-size:14px; } code { background:#f5f3ff; padding:1px 6px; border-radius:6px; font-size:.92em; }
  table { width:100%; border-collapse:collapse; background:#fff; } th,td { text-align:left; padding:10px 8px; border-bottom:1px solid var(--line); vertical-align:top; font-size:15px; }
  th { color:var(--muted); font-size:13px; }
  .brand { display:flex; align-items:center; gap:10px; font-weight:700; margin-bottom:6px; } .brand img { width:34px; height:34px; border-radius:9px; }
  .sha { font-family:ui-monospace,Consolas,monospace; font-size:12px; word-break:break-all; color:var(--muted); }
  .manuals a { font-weight:600; }
  .ic { width:20px; height:20px; vertical-align:-4px; margin-right:4px; fill:none; stroke:var(--brand); stroke-width:2; stroke-linecap:round; stroke-linejoin:round; }
  @media (max-width:600px) { th:nth-child(4), td:nth-child(4) { display:none; } }
</style>
</head>
<body><main>
<div class="brand"><img src="favicon.svg" alt=""> Vyzzu</div>
${body}
</main></body></html>
`;
}

function indexPage(list) {
  const top = list[0];
  const rows = list
    .map(
      (r) => `<tr><td><b>${escapeHtml(r.versionName)}</b></td><td>${dateBr(r.publishedAt)}</td><td>${megabytes(r.size)}</td>
<td>${escapeHtml(r.notes || '—')}</td><td><a href="${escapeHtml(r.apk)}" download>Baixar</a></td></tr>
<tr><td colspan="5" class="sha">SHA-256: ${r.sha256}</td></tr>`,
    )
    .join('\n');
  return page(
    'Downloads do Player',
    `<h1>Player para TV</h1>
<p class="muted">Aplicativo para Android TV e Fire TV Stick (Android 5.0 ou mais novo). Instalação direta pelo arquivo APK, sem Google Play. Depois de instalado, ele se atualiza sozinho a partir desta página.</p>
<div class="card">
  <div class="muted">Versão mais recente</div>
  <h2 style="margin:.1em 0 .4em;border:0">${escapeHtml(top.versionName)} <span class="muted" style="font-weight:400">· ${dateBr(top.publishedAt)} · ${megabytes(top.size)}</span></h2>
  ${top.notes ? `<p>${escapeHtml(top.notes)}</p>` : ''}
  <p><a class="btn" href="${escapeHtml(top.apk)}" download>Baixar APK ${escapeHtml(top.versionName)}</a></p>
  <p class="muted">Na TV, com o app <b>Downloader</b>, digite o endereço curto: <code>${new URL(site).host}/d</code></p>
  <p class="sha">SHA-256: ${top.sha256}</p>
</div>
<div class="card manuals">
  <h3 style="margin-top:0">Manuais (PDF)</h3>
  <p><svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v16"/><path d="M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z"/></svg> <a href="manual-instalacao-tv.pdf"><b>Manual de instalação na TV</b></a> <span class="muted">· TCL Android TV e Fire TV Stick, passo a passo ilustrado (<a href="manual-instalacao-tv.html">versão para tela</a>)</span></p>
  <p><svg class="ic" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v16"/><path d="M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z"/></svg> <a href="manual-painel.pdf"><b>Manual do painel</b></a> <span class="muted">· mídias, playlists e dispositivos (<a href="manual-painel.html">versão para tela</a>)</span></p>
</div>
<h2>Todas as versões</h2>
<table><tr><th>Versão</th><th>Data</th><th>Tamanho</th><th>Novidades</th><th></th></tr>
${rows}
</table>
<p class="muted">Confira o SHA-256 se quiser garantir que o arquivo não foi alterado. O Android também recusa qualquer atualização que não venha assinada com a mesma chave das versões anteriores.</p>`,
  );
}
