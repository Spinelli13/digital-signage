#!/usr/bin/env node
// Gera os ícones de marca do Vyzzu. O símbolo é um V "varrido" por linhas de tela: a silhueta de um V cortada em faixas
// horizontais de LED, que vão da lavanda ao magenta, sobre o preto da marca. É desenhado aqui, em código, uma vez só:
//   frontend/public/favicon.svg                                   o símbolo vetorial (painel, manuais, site de downloads)
//   frontend/public/favicon.ico                                   48x48 (PNG dentro do ICO), para navegadores antigos
//   frontend/public/apple-touch-icon.png                          180x180, tela cheia (o iOS arredonda os cantos)
//   player/app/src/main/res/drawable/ic_launcher.xml              o ícone do Player (vetor Android, cores sólidas)
//   player/app/src/main/res/drawable-xhdpi/banner.png             640x360, o banner da Android TV
//
// É uma ferramenta de quem mantém o projeto: precisa do puppeteer-core e de um Edge/Chrome instalado (não é dependência do
// painel nem do Player). Use:
//   NODE_PATH=<pasta com node_modules/puppeteer-core> node scripts/gerar-icones-marca.cjs
//   (CHROME_PATH aponta outro navegador, se o Edge não estiver no caminho padrão do Windows)
//
// As cores seguem o guia da marca (docs/MARCA_VYZZU.md). O preto do fundo é o do menu lateral (#0C0811); o do Player, #08050C.
const fs = require('node:fs');
const path = require('node:path');
const puppeteer = require('puppeteer-core');

const root = path.resolve(__dirname, '..');
const PUBLIC = path.join(root, 'frontend', 'public');
const RES = path.join(root, 'player', 'app', 'src', 'main', 'res');
const BROWSER = process.env.CHROME_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

const TILE = '#0c0811';
/** Da lavanda ao magenta: a luz desce do alto do V até a ponta. */
const BAND_COLORS = ['#c4b5fd', '#a78bfa', '#8b5cf6', '#a855f7', '#d946ef'];
const BANDS = 5;
const GAP = 2.8;
/** O V (viewBox 64): largura de 4 a 60, do alto (y 9) até a ponta (y 59); o vão do meio termina em y 34. */
const TOP = 9;
const BOTTOM = 59;
const NOTCH_Y = 34;
const SCALE = 0.84; // respiro dentro do ícone

const outerLeft = (y) => 4 + (y - TOP) * (28 / 50);
const outerRight = (y) => 60 - (y - TOP) * (28 / 50);
const innerLeft = (y) => 19.5 + (y - TOP) * 0.5;
const innerRight = (y) => 44.5 - (y - TOP) * 0.5;

const place = ([x, y]) => [32 + (x - 32) * SCALE, 32 + (y - NOTCH_Y) * SCALE];

/** Os polígonos de cada faixa (um ou dois, conforme a faixa corta os dois braços ou já os junta na ponta). */
function bandShapes() {
  const height = (BOTTOM - TOP - (BANDS - 1) * GAP) / BANDS;
  return Array.from({ length: BANDS }, (_, i) => {
    const y0 = TOP + i * (height + GAP);
    const y1 = y0 + height;
    if (y1 <= NOTCH_Y) {
      return [
        [[outerLeft(y0), y0], [innerLeft(y0), y0], [innerLeft(y1), y1], [outerLeft(y1), y1]],
        [[innerRight(y0), y0], [outerRight(y0), y0], [outerRight(y1), y1], [innerRight(y1), y1]],
      ].map((shape) => shape.map(place));
    }
    if (y0 >= NOTCH_Y) {
      return [[[outerLeft(y0), y0], [outerRight(y0), y0], [outerRight(y1), y1], [outerLeft(y1), y1]]].map((shape) => shape.map(place));
    }
    // a faixa que cruza o fundo do vão
    return [[[outerLeft(y0), y0], [innerLeft(y0), y0], [32, NOTCH_Y], [innerRight(y0), y0], [outerRight(y0), y0], [outerRight(y1), y1], [outerLeft(y1), y1]]].map((shape) => shape.map(place));
  });
}

const fmt = (n) => Number(n.toFixed(2));
const points = (shape) => shape.map(([x, y]) => `${fmt(x)},${fmt(y)}`).join(' ');

function svgSymbol() {
  const bands = bandShapes()
    .map((shapes, i) => shapes.map((shape) => `  <polygon points="${points(shape)}" fill="${BAND_COLORS[i]}"/>`).join('\n'))
    .join('\n');
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" role="img" aria-label="Vyzzu">
  <!-- GERADO por scripts/gerar-icones-marca.cjs: o V do Vyzzu, varrido por linhas de tela (faixas de LED, da lavanda ao magenta), sobre o preto da marca -->
  <rect width="64" height="64" rx="15" fill="${TILE}"/>
${bands}
</svg>
`;
}

function androidVector() {
  const pathData = (shape) => `M${shape.map(([x, y]) => `${fmt(x)},${fmt(y)}`).join(' L')} Z`;
  const bands = bandShapes()
    .map((shapes, i) => shapes.map((shape) => `    <path\n        android:fillColor="${BAND_COLORS[i].toUpperCase()}"\n        android:pathData="${pathData(shape)}" />`).join('\n'))
    .join('\n');
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- GERADO por scripts/gerar-icones-marca.cjs. Mesmo símbolo do painel (frontend/public/favicon.svg), em vetor, com cores sólidas
     (degradê em vetor só existe a partir do Android 7 e o app aceita Android 5). O fundo é o preto do Player (#08050C). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="64"
    android:viewportHeight="64">

    <!-- fundo -->
    <path
        android:fillColor="#08050C"
        android:pathData="M15,0 H49 A15,15 0 0 1 64,15 V49 A15,15 0 0 1 49,64 H15 A15,15 0 0 1 0,49 V15 A15,15 0 0 1 15,0 Z" />

    <!-- o V em faixas de LED -->
${bands}
</vector>
`;
}

const symbolSvg = svgSymbol();
const sized = (markup, size) => markup.replace('<svg ', `<svg width="${size}" height="${size}" `);

async function symbol(browser, size, fullBleed) {
  const page = await browser.newPage();
  await page.setViewport({ width: size, height: size, deviceScaleFactor: 1 });
  const markup = fullBleed ? symbolSvg.replace('rx="15"', 'rx="0"') : symbolSvg;
  await page.setContent(`<html><body style="margin:0;background:${fullBleed ? TILE : 'transparent'}">${sized(markup, size)}</body></html>`);
  const png = await page.screenshot({ type: 'png', omitBackground: !fullBleed });
  await page.close();
  return png;
}

/** ICO com uma imagem só (PNG embutido). */
function ico(png, size) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(0, 0);
  header.writeUInt16LE(1, 2);
  header.writeUInt16LE(1, 4);
  const entry = Buffer.alloc(16);
  entry.writeUInt8(size, 0);
  entry.writeUInt8(size, 1);
  entry.writeUInt16LE(1, 4);
  entry.writeUInt16LE(32, 6);
  entry.writeUInt32LE(png.length, 8);
  entry.writeUInt32LE(22, 12);
  return Buffer.concat([header, entry, png]);
}

async function banner(browser) {
  const page = await browser.newPage();
  await page.setViewport({ width: 640, height: 360, deviceScaleFactor: 1 });
  await page.setContent(`<html><body style="margin:0;width:640px;height:360px;background:#08050c;display:flex;align-items:center;font-family:'Segoe UI',Arial,sans-serif">
    <div style="margin-left:56px">${sized(symbolSvg, 180)}</div>
    <div style="margin-left:36px;font-size:84px;font-weight:800;letter-spacing:-3px;color:#f4f4f5">Vy<span style="color:#c4b5fd">zz</span>u</div>
  </body></html>`);
  fs.writeFileSync(path.join(RES, 'drawable-xhdpi', 'banner.png'), await page.screenshot({ type: 'png' }));
  await page.close();
}

(async () => {
  fs.writeFileSync(path.join(PUBLIC, 'favicon.svg'), symbolSvg);
  fs.writeFileSync(path.join(RES, 'drawable', 'ic_launcher.xml'), androidVector());
  const browser = await puppeteer.launch({ executablePath: BROWSER, headless: 'new', args: ['--no-sandbox'] });
  fs.writeFileSync(path.join(PUBLIC, 'favicon.ico'), ico(await symbol(browser, 48, false), 48));
  fs.writeFileSync(path.join(PUBLIC, 'apple-touch-icon.png'), await symbol(browser, 180, true));
  await banner(browser);
  await browser.close();
  console.log('favicon.svg, favicon.ico, apple-touch-icon.png, ic_launcher.xml e banner.png gerados');
})();
