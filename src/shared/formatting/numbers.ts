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

/** 只用于绘制比例，不回写金额；按共同数量级缩放，避免超大整数先转Number溢出。 */
export function displayRatio(
  left: string,
  right: string,
  share = false,
): number | null {
  if (!/^\d+$/.test(left) || !/^\d+$/.test(right)) return null;
  const a = left.replace(/^0+/, '') || '0';
  const b = right.replace(/^0+/, '') || '0';
  const length = Math.max(a.length, b.length);
  const scaled = (value: string) =>
    Number(value.slice(0, 12)) *
    10 ** (value.length - Math.min(value.length, 12) - length);
  const numerator = scaled(a),
    other = scaled(b);
  const denominator = share ? numerator + other : other;
  if (denominator === 0) return null;
  return Math.min(1, Math.max(0, numerator / denominator));
}

/** 不同minorUnit先对齐数量级，比例计算不会改变原始金额字符串。 */
export function displayMoneyRatio(
  left: { amountMinor: string; minorUnit: number },
  right: { amountMinor: string; minorUnit: number },
  share = false,
): number | null {
  const common = Math.max(left.minorUnit, right.minorUnit);
  return displayRatio(
    left.amountMinor + '0'.repeat(common - left.minorUnit),
    right.amountMinor + '0'.repeat(common - right.minorUnit),
    share,
  );
}
