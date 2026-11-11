#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { getHistoricalRates } from 'dukascopy-node';

export const TOOL_VERSION = 'trading-os-dukascopy-acquisition-v1';
export const PROVIDER = 'DUKASCOPY';
export const TIMEFRAME = 'M1';
export const DEFAULT_PRICE_TYPE = 'bid';

const MINUTE_MS = 60_000;
const MONTH_PATTERN = /^\d{4}-\d{2}$/;

export function parseDate(value, name) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) throw new Error(`${name} must be an ISO date`);
  return date;
}

export function monthKey(date) {
  return date.toISOString().slice(0, 7);
}

export function monthBounds(key) {
  if (!MONTH_PATTERN.test(key)) throw new Error(`invalid month: ${key}`);
  const start = new Date(`${key}-01T00:00:00.000Z`);
  const end = new Date(start);
  end.setUTCMonth(end.getUTCMonth() + 1);
  return { start, end };
}

export function enumerateMonths(start, end) {
  const from = new Date(start);
  const to = new Date(end);
  if (Number.isNaN(from.getTime()) || Number.isNaN(to.getTime()) || from >= to) {
    throw new Error('start must be before end');
  }
  const first = new Date(Date.UTC(from.getUTCFullYear(), from.getUTCMonth(), 1));
  const result = [];
  for (const cursor = first; cursor < to; cursor.setUTCMonth(cursor.getUTCMonth() + 1)) {
    result.push(monthKey(cursor));
  }
  return result;
}

function number(value, field, index) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new Error(`record ${index} has invalid ${field}`);
  }
  return value;
}

function recordFromArray(value, index) {
  if (!Array.isArray(value) || value.length < 6) throw new Error(`record ${index} is malformed`);
  const timestamp = number(value[0], 'timestamp', index);
  if (!Number.isInteger(timestamp) || timestamp % MINUTE_MS !== 0) {
    throw new Error(`record ${index} timestamp is not an exact UTC minute`);
  }
  const open = number(value[1], 'open', index);
  const high = number(value[2], 'high', index);
  const low = number(value[3], 'low', index);
  const close = number(value[4], 'close', index);
  const volume = number(value[5], 'volume', index);
  if (open <= 0 || high <= 0 || low <= 0 || close <= 0 || volume < 0
      || high < Math.max(open, close) || low > Math.min(open, close) || low > high) {
    throw new Error(`record ${index} has invalid OHLCV`);
  }
  return { timestamp, open, high, low, close, volume };
}

function sameRecord(left, right) {
  return left.open === right.open && left.high === right.high && left.low === right.low
    && left.close === right.close && left.volume === right.volume;
}

function missingIntervals(records, start, end) {
  const present = new Set(records.map(value => value.timestamp));
  const intervals = [];
  let gapStart = null;
  for (let cursor = start.getTime(); cursor < end.getTime(); cursor += MINUTE_MS) {
    if (!present.has(cursor)) {
      if (gapStart === null) gapStart = cursor;
      continue;
    }
    if (gapStart !== null) {
      intervals.push({ start: new Date(gapStart).toISOString(), end: new Date(cursor).toISOString() });
      gapStart = null;
    }
  }
  if (gapStart !== null) intervals.push({ start: new Date(gapStart).toISOString(), end: end.toISOString() });
  return intervals;
}

export function normalizeRecords(raw, start, end) {
  const from = new Date(start);
  const to = new Date(end);
  const parsed = raw.map(recordFromArray);
  const groups = new Map();
  let outOfBounds = 0;
  for (const record of parsed) {
    if (record.timestamp < from.getTime() || record.timestamp >= to.getTime()) {
      outOfBounds++;
      continue;
    }
    const values = groups.get(record.timestamp) ?? [];
    values.push(record);
    groups.set(record.timestamp, values);
  }
  const records = [];
  let exactDuplicates = 0;
  let conflictingDuplicates = 0;
  const quarantinedTimestamps = [];
  for (const [timestamp, values] of groups) {
    if (values.length === 1) {
      records.push(values[0]);
      continue;
    }
    if (values.every(value => sameRecord(values[0], value))) {
      exactDuplicates += values.length - 1;
      records.push(values[0]);
    } else {
      conflictingDuplicates += values.length;
      quarantinedTimestamps.push(new Date(timestamp).toISOString());
    }
  }
  records.sort((left, right) => left.timestamp - right.timestamp);
  return {
    records,
    diagnostics: {
      rawRecords: raw.length,
      normalizedRecords: records.length,
      outOfBounds,
      exactDuplicates,
      conflictingDuplicates,
      quarantinedTimestamps: quarantinedTimestamps.sort(),
      missingMinuteIntervals: missingIntervals(records, from, to),
      missingMinuteCount: Math.max(0, Math.floor((to - from) / MINUTE_MS) - records.length),
      firstTimestamp: records[0]?.timestamp ?? null,
      lastTimestamp: records.at(-1)?.timestamp ?? null
    }
  };
}

export function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

function stableJson(value) {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

export function fingerprintManifest(manifest) {
  const copy = structuredClone(manifest);
  delete copy.datasetFingerprint;
  return sha256(stableJson(copy));
}

export async function writeIfUnchanged(path, content) {
  try {
    const existing = await readFile(path, 'utf8');
    if (existing !== content) throw new Error(`refusing to replace changed artifact: ${path}`);
    return false;
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }
  await mkdir(dirname(path), { recursive: true });
  const temporary = `${path}.tmp-${process.pid}`;
  await writeFile(temporary, content, 'utf8');
  await rename(temporary, path);
  return true;
}

async function writeDerived(path, content) {
  await mkdir(dirname(path), { recursive: true });
  const temporary = `${path}.tmp-${process.pid}`;
  await writeFile(temporary, content, 'utf8');
  await rename(temporary, path);
}

function csv(records) {
  return ['timestamp,open,high,low,close,volume', ...records.map(value => [
    new Date(value.timestamp).toISOString(), value.open, value.high, value.low, value.close, value.volume
  ].join(','))].join('\n') + '\n';
}

function args(argv) {
  const result = {
    instruments: ['eurusd', 'gbpusd'],
    from: '2025-10-01T00:00:00Z',
    to: '2026-10-01T00:00:00Z',
    output: './histData/dukascopy-20251001-20261001',
    priceType: DEFAULT_PRICE_TYPE,
    batchSize: 2,
    batchPause: 2000,
    requestInterval: 1000,
    retries: 3,
    retryPause: 5000
  };
  for (let index = 0; index < argv.length; index++) {
    const value = argv[index];
    const next = () => argv[++index];
    if (value === '--instrument' || value === '-i') result.instruments = [next()];
    else if (value === '--instruments') result.instruments = next().split(',').filter(Boolean);
    else if (value === '--from') result.from = next();
    else if (value === '--to') result.to = next();
    else if (value === '--output') result.output = next();
    else if (value === '--price-type') result.priceType = next();
    else if (value === '--batch-size') result.batchSize = Number(next());
    else if (value === '--batch-pause') result.batchPause = Number(next());
    else if (value === '--request-interval') result.requestInterval = Number(next());
    else if (value === '--retries') result.retries = Number(next());
    else if (value === '--retry-pause') result.retryPause = Number(next());
    else if (value === '--help') {
      console.log('Usage: node acquire.mjs [--instruments eurusd,gbpusd] [--from ISO] [--to ISO] [--output DIR]');
      process.exit(0);
    } else throw new Error(`unknown argument: ${value}`);
  }
  if (!['bid', 'ask'].includes(result.priceType)) throw new Error('price type must be bid or ask');
  return result;
}

async function acquireMonth(instrument, key, options) {
  const month = monthBounds(key);
  const requestedStart = parseDate(options.from, 'from');
  const requestedEnd = parseDate(options.to, 'to');
  const bounds = {
    start: requestedStart > month.start ? requestedStart : month.start,
    end: requestedEnd < month.end ? requestedEnd : month.end
  };
  const rawPath = join(options.output, 'raw', instrument, `${key}.json`);
  const normalizedPath = join(options.output, 'normalized', instrument, `${key}.csv`);
  let rawText;
  try {
    rawText = await readFile(rawPath, 'utf8');
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
    const raw = await getHistoricalRates({
      instrument,
      dates: { from: bounds.start, to: bounds.end },
      timeframe: 'm1',
      priceType: options.priceType,
      format: 'array',
      batchSize: options.batchSize,
      pauseBetweenBatchesMs: options.batchPause,
      requestIntervalMs: options.requestInterval,
      rateLimitRetryCount: options.retries,
      retryCount: options.retries,
      pauseBetweenRetriesMs: options.retryPause,
      // Empty daily artifacts can be legitimate non-trading days. The monthly
      // result is still rejected when the entire requested month is empty.
      retryOnEmpty: false,
      failAfterRetryCount: true,
      useCache: true,
      cacheFolderPath: join(options.output, 'provider-cache')
    });
    if (!Array.isArray(raw) || raw.length === 0) throw new Error(`empty provider artifact: ${instrument} ${key}`);
    rawText = JSON.stringify(raw) + '\n';
    await writeIfUnchanged(rawPath, rawText);
  }
  const raw = JSON.parse(rawText);
  const normalized = normalizeRecords(raw, bounds.start, bounds.end);
  const normalizedText = csv(normalized.records);
  await writeIfUnchanged(normalizedPath, normalizedText);
  return {
    instrument,
    month: key,
    rawPath: relative(options.output, rawPath),
    normalizedPath: relative(options.output, normalizedPath),
    rawSha256: sha256(rawText),
    normalizedSha256: sha256(normalizedText),
    ...normalized.diagnostics
  };
}

async function crossFileDiagnostics(artifacts, output) {
  const result = {};
  for (const instrument of [...new Set(artifacts.map(value => value.instrument))].sort()) {
    const files = artifacts.filter(value => value.instrument === instrument)
      .sort((left, right) => left.month.localeCompare(right.month));
    const overlaps = [];
    const gaps = [];
    let previousLast = null;
    for (const artifact of files) {
      const text = await readFile(join(output, artifact.normalizedPath), 'utf8');
      const timestamps = text.split('\n').slice(1).filter(Boolean)
        .map(line => Date.parse(line.split(',')[0]));
      if (timestamps.length === 0) continue;
      const first = timestamps[0];
      const last = timestamps.at(-1);
      if (previousLast !== null) {
        if (first <= previousLast) overlaps.push(new Date(first).toISOString());
        else if (first > previousLast + MINUTE_MS) {
          gaps.push({
            start: new Date(previousLast + MINUTE_MS).toISOString(),
            end: new Date(first).toISOString()
          });
        }
      }
      previousLast = Math.max(previousLast ?? last, last);
    }
    result[instrument] = {
      crossFileOverlapCount: overlaps.length,
      crossFileOverlapTimestamps: overlaps,
      crossMonthGapCount: gaps.length,
      crossMonthGaps: gaps
    };
  }
  return result;
}

export async function run(options) {
  const from = parseDate(options.from, 'from');
  const to = parseDate(options.to, 'to');
  const months = enumerateMonths(from, to);
  const artifacts = [];
  const failures = [];
  for (const instrument of options.instruments) {
    for (const month of months) {
      try {
        artifacts.push(await acquireMonth(instrument.toLowerCase(), month, { ...options, output: options.output }));
        console.log(`validated ${instrument} ${month}`);
      } catch (error) {
        failures.push({ instrument, month, error: error.message });
        console.error(`failed ${instrument} ${month}: ${error.message}`);
      }
    }
  }
  const manifest = {
    toolVersion: TOOL_VERSION,
    provider: PROVIDER,
    timeframe: TIMEFRAME,
    priceType: options.priceType,
    startInclusive: from.toISOString(),
    endExclusive: to.toISOString(),
    instruments: [...options.instruments].sort(),
    expectedMonths: months,
    artifacts,
    crossFileDiagnostics: await crossFileDiagnostics(artifacts, options.output),
    failures,
    complete: failures.length === 0 && artifacts.length === options.instruments.length * months.length,
    datasetFingerprint: null
  };
  manifest.datasetFingerprint = fingerprintManifest(manifest);
  await writeDerived(join(options.output, 'acquisition-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  if (failures.length > 0) process.exitCode = 2;
  return manifest;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) run(args(process.argv.slice(2))).catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
