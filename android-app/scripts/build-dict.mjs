// Builds the offline English dictionary from WordNet 3.1 (Princeton University) into small JSON files:
//   dict/w/<xx>.json   word -> [[partOfSpeech, [meaningId, ...]], ...]  (sharded by the word's first two letters)
//   dict/m/<n>.json    meanings, 1000 per file: [definition, example, [synonyms]]
//   dict/exc.json      irregular forms per part of speech, e.g. "went" -> ["go"]
// A lookup reads one word file and one or two meaning files, so nothing large is ever loaded.
import { readFileSync, writeFileSync, mkdirSync, copyFileSync } from 'node:fs';
import { join } from 'node:path';

const POS_FILES = [['noun', 'n'], ['verb', 'v'], ['adj', 'a'], ['adv', 'r']];
const SENSES_PER_POS = 12;
const SYNONYMS = 6;
const CHUNK = 1000;

export const shardKey = (w) => {
  const k = w.toLowerCase().slice(0, 2).replace(/[^a-z0-9]/g, '_');
  return k.length === 2 ? k : (k + '_').slice(0, 2);
};

export function buildDictionary(nm, outDir) {
  const wn = join(nm, 'wordnet-db/dict');
  const excDir = join(nm, 'wndb-with-exceptions/dict');
  mkdirSync(join(outDir, 'w'), { recursive: true });
  mkdirSync(join(outDir, 'm'), { recursive: true });

  // 1. Meanings (synsets), keyed by "<offset><pos>".
  const synsets = new Map();
  for (const [file, pos] of POS_FILES) {
    for (const line of readFileSync(join(wn, 'data.' + file), 'latin1').split('\n')) {
      if (!line || line.startsWith('  ')) continue;
      const bar = line.indexOf(' | ');
      const head = line.slice(0, bar).split(' ');
      const gloss = line.slice(bar + 3).trim();
      const count = parseInt(head[3], 16);
      const words = [];
      for (let i = 0; i < count; i++) words.push(head[4 + i * 2].replace(/\(.*\)$/, '').replace(/_/g, ' '));
      const examples = [...gloss.matchAll(/"([^"]+)"/g)].map((m) => m[1].trim());
      const definition = gloss.replace(/;?\s*"[^"]*"/g, '').replace(/[\s;:]+$/, '').trim();
      synsets.set(head[0] + pos, { definition, example: examples[0] || '', words });
    }
  }

  // 2. Words, with their meanings in WordNet's frequency order; parts of speech ordered by how often
  //    each is used in tagged text (so "ball" shows the noun first and "weep" the verb first).
  const entries = new Map();
  for (const [file, pos] of POS_FILES) {
    for (const line of readFileSync(join(wn, 'index.' + file), 'latin1').split('\n')) {
      if (!line || line.startsWith('  ')) continue;
      const p = line.trim().split(' ');
      const lemma = p[0].replace(/_/g, ' ');
      const ptrCount = parseInt(p[3], 10);
      const tagged = parseInt(p[5 + ptrCount], 10) || 0;
      const offsets = p.slice(6 + ptrCount).slice(0, SENSES_PER_POS);
      if (!entries.has(lemma)) entries.set(lemma, []);
      entries.get(lemma).push({ pos, tagged, offsets });
    }
  }
  const ORDER = { n: 0, v: 1, a: 2, r: 3 };
  const ids = new Map();
  const meanings = [];
  const shards = new Map();
  for (const [lemma, groups] of entries) {
    groups.sort((a, b) => b.tagged - a.tagged || ORDER[a.pos] - ORDER[b.pos]);
    const out = groups.map((g) => [g.pos, g.offsets.map((o) => {
      const key = o + g.pos;
      if (!ids.has(key)) {
        const s = synsets.get(key);
        ids.set(key, meanings.length);
        meanings.push(s ? [s.definition, s.example, s.words] : ['', '', []]);
      }
      return ids.get(key);
    })]);
    const k = shardKey(lemma);
    if (!shards.has(k)) shards.set(k, {});
    shards.get(k)[lemma] = out;
  }
  // Synonyms are stored per meaning; the reader drops the word being looked up.
  for (const m of meanings) m[2] = m[2].slice(0, SYNONYMS + 1);

  let bytes = 0;
  for (const [k, data] of shards) { const s = JSON.stringify(data); bytes += s.length; writeFileSync(join(outDir, 'w', k + '.json'), s); }
  for (let i = 0; i * CHUNK < meanings.length; i++) {
    const s = JSON.stringify(meanings.slice(i * CHUNK, (i + 1) * CHUNK)); bytes += s.length;
    writeFileSync(join(outDir, 'm', i + '.json'), s);
  }

  // 3. Irregular forms.
  const exc = {};
  for (const [file, pos] of POS_FILES) {
    exc[pos] = {};
    for (const line of readFileSync(join(excDir, file + '.exc'), 'latin1').split('\n')) {
      const [form, ...bases] = line.trim().split(' ');
      if (form && bases.length) exc[pos][form.replace(/_/g, ' ')] = bases.map((b) => b.replace(/_/g, ' '));
    }
  }
  const excStr = JSON.stringify(exc); bytes += excStr.length;
  writeFileSync(join(outDir, 'exc.json'), excStr);
  writeFileSync(join(outDir, 'meta.json'), JSON.stringify({ source: 'WordNet 3.1', words: entries.size, meanings: meanings.length, chunk: CHUNK }));
  copyFileSync(join(nm, 'wndb-with-exceptions/LICENSE'), join(outDir, 'LICENSE.txt'));
  return { words: entries.size, meanings: meanings.length, shards: shards.size, mb: +(bytes / 1e6).toFixed(1) };
}
