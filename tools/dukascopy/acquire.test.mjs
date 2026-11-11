import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  enumerateMonths,
  fingerprintManifest,
  monthBounds,
  normalizeRecords,
  sha256,
  writeIfUnchanged
} from './acquire.mjs';

const row = (timestamp, open = 1, high = 2, low = 0.5, close = 1.5, volume = 10) =>
  [timestamp, open, high, low, close, volume];

test('enumerates UTC months for inclusive start and exclusive end', () => {
  assert.deepEqual(enumerateMonths(new Date('2025-10-01T00:00:00Z'), new Date('2026-10-01T00:00:00Z')),
    ['2025-10', '2025-11', '2025-12', '2026-01', '2026-02', '2026-03', '2026-04', '2026-05', '2026-06', '2026-07', '2026-08', '2026-09']);
  assert.deepEqual(monthBounds('2026-02'), {
    start: new Date('2026-02-01T00:00:00Z'), end: new Date('2026-03-01T00:00:00Z')
  });
});

test('normalizes ordering, exact duplicates and missing minute diagnostics', () => {
  const start = new Date('2025-10-01T00:00:00Z');
  const end = new Date('2025-10-01T00:05:00Z');
  const result = normalizeRecords([
    row(Date.parse('2025-10-01T00:02:00Z')),
    row(Date.parse('2025-10-01T00:00:00Z')),
    row(Date.parse('2025-10-01T00:00:00Z')),
    row(Date.parse('2025-10-01T00:04:00Z'))
  ], start, end);
  assert.deepEqual(result.records.map(value => value.timestamp), [start.getTime(), Date.parse('2025-10-01T00:02:00Z'), Date.parse('2025-10-01T00:04:00Z')]);
  assert.equal(result.diagnostics.exactDuplicates, 1);
  assert.equal(result.diagnostics.conflictingDuplicates, 0);
  assert.equal(result.diagnostics.missingMinuteCount, 2);
});

test('quarantines conflicting duplicates and rejects out-of-bounds records', () => {
  const start = new Date('2025-10-01T00:00:00Z');
  const end = new Date('2025-10-01T00:02:00Z');
  const result = normalizeRecords([
    row(start.getTime(), 1, 2, 0.5, 1.5),
    row(start.getTime(), 1, 3, 0.5, 1.5),
    row(end.getTime())
  ], start, end);
  assert.equal(result.records.length, 0);
  assert.equal(result.diagnostics.conflictingDuplicates, 2);
  assert.equal(result.diagnostics.outOfBounds, 1);
  assert.deepEqual(result.diagnostics.quarantinedTimestamps, ['2025-10-01T00:00:00.000Z']);
});

test('fingerprint is deterministic for identical manifest material', () => {
  const manifest = { provider: 'DUKASCOPY', instruments: ['eurusd'], artifacts: [{ sha256: sha256('x') }] };
  assert.equal(fingerprintManifest(manifest), fingerprintManifest(structuredClone(manifest)));
});

test('validated artifacts are idempotent and changed replacements are rejected', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'trading-os-dukascopy-'));
  const path = join(directory, 'artifact.json');
  assert.equal(await writeIfUnchanged(path, 'stable\n'), true);
  assert.equal(await writeIfUnchanged(path, 'stable\n'), false);
  await assert.rejects(() => writeIfUnchanged(path, 'changed\n'), /refusing to replace changed artifact/);
  assert.equal(await readFile(path, 'utf8'), 'stable\n');
});
