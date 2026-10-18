/** EntryTask RN：共享数值格式化：使用字符串处理金额，避免精度损失。 */
export function formatMinor(value: string, unit = 2): string {
  if (!/^-?\d+$/.test(value) || unit < 0 || unit > 6) {
    return '—';
  }
  const negative = value.startsWith('-');
  const digits = value.replace('-', '').padStart(unit + 1, '0');
  const whole = (unit ? digits.slice(0, -unit) : digits).replace(
    /\B(?=(\d{3})+(?!\d))/g,
    ',',
  );
  return `${negative ? '-' : ''}${whole}${
    unit ? '.' + digits.slice(-unit) : ''
  }`;
}
export function formatRatio(ratio: string): string {
  if (!/^-?\d+(\.\d+)?$/.test(ratio)) {
    return '—';
  }
  return `${(Number(ratio) * 100)
    .toFixed(2)
    .replace(/0+$/, '')
    .replace(/\.$/, '')}%`;
}
