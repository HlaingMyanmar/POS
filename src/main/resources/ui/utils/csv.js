const FORMULA_PREFIX = /^[\p{Cc}\p{Cf}\p{Zs}]*[=+\-@]/u;

/**
 * Prevent spreadsheet applications from evaluating an untrusted string as a
 * formula. Non-string values are deliberately left alone so typed numbers,
 * including negative numbers, keep their numeric CSV representation.
 *
 * @param {unknown} value
 * @returns {unknown}
 */
export const neutralizeCsvFormula = (value) => {
  if (typeof value !== 'string' || !FORMULA_PREFIX.test(value)) return value;
  return `'${value}`;
};

/**
 * @param {unknown} value
 * @param {{ alwaysQuote?: boolean }} [options]
 */
export const csvCell = (value, options = {}) => {
  const safeValue = neutralizeCsvFormula(value);
  const text = safeValue == null ? '' : String(safeValue);
  if (options.alwaysQuote || /[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
};

/**
 * @param {unknown[][]} rows
 * @param {{ alwaysQuote?: boolean, bom?: boolean, lineEnding?: '\n' | '\r\n' }} [options]
 */
export const toCsv = (rows, options = {}) => {
  const { alwaysQuote = false, bom = false, lineEnding = '\n' } = options;
  const content = rows
    .map((row) => row.map((value) => csvCell(value, { alwaysQuote })).join(','))
    .join(lineEnding);
  return `${bom ? '\uFEFF' : ''}${content}`;
};
