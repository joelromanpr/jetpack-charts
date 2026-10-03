import { readdir, readFile, stat } from 'node:fs/promises';
import { resolve, join } from 'node:path';

const root = resolve('dist');
const origin = 'https://joelromanpr.github.io';
const base = '/jetpack-charts';
async function files(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  return (await Promise.all(entries.map(entry => entry.isDirectory()
    ? files(join(directory, entry.name)) : join(directory, entry.name)))).flat();
}
const pages = (await files(root)).filter(file => file.endsWith('.html'));
for (const page of pages) {
  const url = new URL(`${base}/${page.slice(root.length + 1).replace(/index\.html$/, '')}`, origin);
  const html = await readFile(page, 'utf8');
  for (const match of html.matchAll(/\b(?:href|src)=["']([^"']+)["']/g)) {
    if (/^(?:mailto:|tel:|data:|javascript:)/.test(match[1])) continue;
    const target = new URL(match[1].replaceAll('&amp;', '&'), url);
    if (target.origin !== origin) continue;
    if (page.endsWith('/404.html') && target.pathname === `${base}/404/`) continue;
    if (target.pathname.includes('//')) throw new Error(`Repeated path separator in ${page}: ${target.pathname}`);
    if (!target.pathname.startsWith(`${base}/`)) throw new Error(`Link escapes site base in ${page}: ${target}`);
    const local = join(root, decodeURIComponent(target.pathname.slice(base.length)));
    let present = await stat(local).catch(() => null);
    if (present?.isDirectory()) present = await stat(join(local, 'index.html')).catch(() => null);
    if (!present?.isFile()) throw new Error(`Broken link in ${page}: ${target.pathname}`);
  }
}
console.log(`Checked local links and assets in ${pages.length} documentation pages.`);
