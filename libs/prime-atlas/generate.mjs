import {writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';

const target = Number(process.argv[2] ?? 9_961_224);
const header = '// Eight decimal ASCII digits per prime; reproduce with generate.mjs.\n';
const prefix = 'pub static PRIME_DIGITS: &[u8] = b"\\\n';
const suffix = '";\n';
const fixed = Buffer.byteLength(header + prefix + suffix);
if (!Number.isSafeInteger(target) || target < fixed + 640) throw new Error(`Target must be an integer of at least ${fixed + 640} source bytes`);
const available = target - fixed;
const count = Math.floor((available - 128) / 8);
const limit = 22_000_000;
const composite = new Uint8Array(limit + 1);
for (let p = 2; p * p <= limit; p++) {
  if (composite[p]) continue;
  for (let multiple = p * p; multiple <= limit; multiple += p) composite[multiple] = 1;
}
const primes = new Uint32Array(count);
let stored = 0;
for (let n = 2; n <= limit && stored < count; n++) if (!composite[n]) primes[stored++] = n;
if (stored !== count) throw new Error(`Target needs ${count} primes, beyond the sieve limit ${limit}`);
const rows = Math.min(64, count);
const parts = [header, prefix];
for (let row = 0; row < rows; row++) {
  const start = Math.floor(row * count / rows);
  const end = Math.floor((row + 1) * count / rows);
  parts.push(Array.from(primes.subarray(start, end), n => String(n).padStart(8, '0')).join('') + '\\\n');
}
parts.push(suffix);
const source = Buffer.from(parts.join(''));
writeFileSync(new URL('src/data.rs', import.meta.url), source);
console.log(JSON.stringify({sourceBytes: source.length, targetSourceBytes: target, unusedBytes: target - source.length, records: count, maxPrime: primes[count - 1], sha256: createHash('sha256').update(source).digest('hex')}, null, 2));
