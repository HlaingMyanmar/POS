import assert from 'node:assert/strict';
import test from 'node:test';
import { csvCell, neutralizeCsvFormula, toCsv } from './csv.js';

test('neutralizes formula payloads in untrusted strings', () => {
  for (const value of ['=HYPERLINK("https://example.test")', '+cmd', '-1+2', '@SUM(A1:A2)']) {
    assert.equal(neutralizeCsvFormula(value), `'${value}`);
  }
});

test('neutralizes formula bypasses after whitespace and control characters', () => {
  for (const value of [' =1+1', '\t+cmd', '\u0000-1+2', '\u200B@SUM(A1:A2)']) {
    assert.equal(neutralizeCsvFormula(value), `'${value}`);
  }
});

test('escapes CSV syntax without changing ordinary and already-safe strings', () => {
  assert.equal(csvCell('normal text'), 'normal text');
  assert.equal(csvCell("'=already safe"), "'=already safe");
  assert.equal(csvCell('comma,value'), '"comma,value"');
  assert.equal(csvCell('a "quote"'), '"a ""quote"""');
  assert.equal(csvCell('line 1\r\nline 2'), '"line 1\r\nline 2"');
});

test('preserves typed numbers and renders nullish cells empty', () => {
  assert.equal(csvCell(-12.5), '-12.5');
  assert.equal(csvCell(0), '0');
  assert.equal(csvCell(null), '');
  assert.equal(csvCell(undefined), '');
});

test('serializes rows with requested line endings, quoting, and BOM', () => {
  assert.equal(
    toCsv([['Name', 'Value'], ['=HYPERLINK("x")', -1]], {
      alwaysQuote: true,
      bom: true,
      lineEnding: '\r\n'
    }),
    '\uFEFF"Name","Value"\r\n"\'=HYPERLINK(""x"")","-1"'
  );
});
