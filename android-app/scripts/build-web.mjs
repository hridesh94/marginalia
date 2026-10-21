// Builds android-app/www from the shared reader page (../reader/index.html):
// wraps it in a full document, swaps CDN scripts and Google Fonts for bundled copies,
// and adds the native bridge. The reader source itself stays artifact-ready.
import { readFileSync, writeFileSync, mkdirSync, rmSync, copyFileSync, existsSync, cpSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const nm = join(root, 'node_modules');
const out = join(root, 'www');
const src = readFileSync(resolve(root, '../reader/index.html'), 'utf8');

rmSync(out, { recursive: true, force: true });
mkdirSync(join(out, 'vendor'), { recursive: true });
mkdirSync(join(out, 'fonts'), { recursive: true });

// 1. Libraries
copyFileSync(join(nm, 'jszip/dist/jszip.min.js'), join(out, 'vendor/jszip.min.js'));
copyFileSync(join(nm, 'epubjs/dist/epub.min.js'), join(out, 'vendor/epub.min.js'));
// Converters, loaded on demand when a MOBI/AZW3 or PDF is imported. Module files keep a .js extension
// so the Android web server serves them with a JavaScript MIME type.
copyFileSync(join(nm, 'foliate-js/mobi.js'), join(out, 'vendor/mobi.js'));
copyFileSync(join(nm, 'fflate/esm/browser.js'), join(out, 'vendor/fflate.js'));
mkdirSync(join(out, 'vendor/pdfjs'), { recursive: true });
copyFileSync(join(nm, 'pdfjs-dist/legacy/build/pdf.min.mjs'), join(out, 'vendor/pdfjs/pdf.min.js'));
copyFileSync(join(nm, 'pdfjs-dist/legacy/build/pdf.worker.min.mjs'), join(out, 'vendor/pdfjs/pdf.worker.min.js'));
for (const dir of ['cmaps', 'standard_fonts', 'wasm', 'iccs']) {
  if (existsSync(join(nm, 'pdfjs-dist', dir))) cpSync(join(nm, 'pdfjs-dist', dir), join(out, 'vendor/pdfjs', dir), { recursive: true });
}

// 2. Fonts: Charis SIL (Charter design), Literata, Gelasio (Georgia metrics). woff2 only, all subsets via unicode-range.
const FONT_SETS = {
  'charis-sil': ['400', '400-italic', '700', '700-italic'],
  literata: ['400', '400-italic', '600', '700', '700-italic'],
  gelasio: ['400', '400-italic', '700', '700-italic'],
};
let fontCss = '';
for (const [pkg, styles] of Object.entries(FONT_SETS)) {
  for (const st of styles) {
    const css = readFileSync(join(nm, '@fontsource', pkg, `${st}.css`), 'utf8');
    fontCss += css.replace(/src:\s*url\(\.\/files\/([^)]+\.woff2)\)\s*format\('woff2'\)[^;]*;/g, (_, file) => {
      copyFileSync(join(nm, '@fontsource', pkg, 'files', file), join(out, 'fonts', file));
      return `src: url(./${file}) format('woff2');`;
    });
  }
}
if (/\.woff'\)|url\(\.\/files/.test(fontCss)) throw new Error('Unexpected @fontsource CSS format');
writeFileSync(join(out, 'fonts/fonts.css'), fontCss);

// 3. Native bridge bundle
await build({
  entryPoints: [join(root, 'src/native.js')],
  bundle: true,
  format: 'iife',
  minify: true,
  target: ['chrome100'],
  outfile: join(out, 'native.js'),
  logLevel: 'warning',
});

// 4. Page
let page = src;
const swaps = [
  [/<link rel="stylesheet" href="https:\/\/fonts\.googleapis\.com\/[^"]+">/, '<link rel="stylesheet" href="fonts/fonts.css">'],
  [/<script src="https:\/\/cdnjs\.cloudflare\.com\/ajax\/libs\/jszip\/[^"]+"><\/script>/,
    '<script>window.MG_FONT_CSS_URL = new URL("fonts/fonts.css", location.href).href;\n' +
    'window.MG_LIBS = (function(){ const u = (p) => new URL(p, location.href).href; return { mobi: u("vendor/mobi.js"), fflate: u("vendor/fflate.js"), pdf: u("vendor/pdfjs/pdf.min.js"), pdfWorker: u("vendor/pdfjs/pdf.worker.min.js"),' +
    ' pdfAssets: { cMapUrl: u("vendor/pdfjs/cmaps/"), cMapPacked: true, standardFontDataUrl: u("vendor/pdfjs/standard_fonts/"), wasmUrl: u("vendor/pdfjs/wasm/"), iccUrl: u("vendor/pdfjs/iccs/") } }; })();</script>\n' +
    '<script src="native.js"></script>\n<script src="vendor/jszip.min.js"></script>'],
  [/<script src="https:\/\/cdn\.jsdelivr\.net\/npm\/epubjs@[^"]+"><\/script>/, '<script src="vendor/epub.min.js"></script>'],
];
for (const [re, rep] of swaps) {
  if (!re.test(page)) throw new Error(`build-web: pattern not found in reader/index.html: ${re}`);
  page = page.replace(re, rep);
}
const leftovers = page.match(/https:\/\/(cdnjs|cdn\.jsdelivr|unpkg|fonts\.googleapis)[^"' )]*/g) || [];
// Runtime fallbacks only: MG_FONT_CSS_URL and MG_LIBS above point the page at the bundled copies.
const allowed = leftovers.filter((u) => !u.startsWith('https://fonts.googleapis.com/css2?family=Literata') && !/^https:\/\/cdn\.jsdelivr\.net\/npm\/(foliate-js|fflate|pdfjs-dist)@/.test(u));
if (allowed.length) throw new Error('build-web: remote assets left in page: ' + allowed.join(', '));

// Same skeleton the Artifact host wraps the page in, so layout matches what was tested there.
const html =
  '<!doctype html><html lang="en"><head><meta charset="utf-8">' +
  '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">' +
  '<meta name="color-scheme" content="light dark">' +
  '<style>:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}body{margin:0;font:14px system-ui}img{max-width:100%}[hidden]{display:none!important}</style>' +
  '</head><body>' + page + '</body></html>';
writeFileSync(join(out, 'index.html'), html);

const kb = (f) => Math.round(readFileSync(f).length / 1024);
console.log(`www built: index.html ${kb(join(out, 'index.html'))} KB, native.js ${kb(join(out, 'native.js'))} KB, fonts.css ${kb(join(out, 'fonts/fonts.css'))} KB`);
if (!existsSync(join(out, 'vendor/epub.min.js'))) throw new Error('epub.js missing');
