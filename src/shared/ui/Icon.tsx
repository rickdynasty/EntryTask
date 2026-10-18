/** EntryTask RN：跨业务域共享 RN UI：集中呈现可复用控件，不承载业务请求与业务规则。 */
import React from 'react';
import Svg, { Path, Circle, Rect } from 'react-native-svg';
import { theme } from './theme';
const paths: Record<string, string> = {
  search: 'M20 20l-5-5',
  scan: 'M3 8V3h5M16 3h5v5M21 16v5h-5M8 21H3v-5M3 12h18',
  message: 'M3 3h18v14H9l-6 4zM7 9h1m3 0h1m3 0h1',
  settings: 'M12 2l9 5v10l-9 5-9-5V7z',
  deposit: 'M12 2a10 10 0 1 0 10 10M7 7l5 5 5-5M12 12v7M8 13h8M8 16h8',
  assets: 'M12 2v10h10M9 3a10 10 0 1 0 12 12M12 2a10 10 0 0 1 10 10',
  wallet: 'M3 6h17v15H3zM3 6l13-4v4M15 11h7v6h-7z',
  transfer: 'M6 9h13l-4-4M18 15H5l4 4',
  card: 'M3 5h18v15H3zM3 9h18M6 16h5',
  fund: 'M3 3h18v18H3zM7 15l4-5 3 2 4-5',
  shield: 'M12 2l9 3v11l-9 6-9-6V5zM12 8v8M8 12h8',
  exchange:
    'M19 5a9 9 0 1 0 2 11M16 3l4 2-1 4M12 5v13M16 8c-5-4-10 3-4 4s5 7-4 4',
  diamond: 'M6 3h12l5 7-11 12L1 10zM7 10l5 6 5-6',
  city: 'M3 22V4l8-2v20M11 10l9 4v8M6 7v5',
  gold: 'M9 3h6l2 8H7zM4 13h7l2 9H2zM14 13h6l3 9H12z',
  family: 'M1 11l11-9 11 9M4 9v13h8M17 14a3 3 0 1 0 0-.1M12 23c0-5 10-5 10 0',
  more: 'M6 12h1m4 0h1m4 0h1',
  pig: 'M4 8L3 4l5 2c13-6 18 11 6 15H6l-2-5H1V9zM8 2h6M10 10h7M13.5 7v6',
  hexagon: 'M12 2l10 6v9l-10 6-10-6V8zM9 9h6v6H9z',
  ledger: 'M3 3l9 1 9-1v18l-9-1-9 1zM8 8v8M16 8v8',
  globe: 'M2 12h20M12 2c-7 7-7 13 0 20 7-7 7-13 0-20',
  eye: 'M2 12s4-6 10-6 10 6 10 6-4 6-10 6S2 12 2 12M3 21L21 3',
  back: 'M15 4l-8 8 8 8',
  chevron: 'M9 5l7 7-7 7',
  crown: 'M3 6l4 4 5-7 5 7 4-4-2 15H5zM12 12v5',
  user: 'M3 22c0-10 18-10 18 0',
  volume: 'M3 9h5l7-5v16l-7-5H3zM18 8c4 3 4 5 0 8',
};
/** 按名称绘制本地 SVG 图标，未知名称回退为 more 图标。 */
export function Icon({
  name,
  size = 26,
  color = theme.colors.text,
}: {
  name: string;
  size?: number;
  color?: string;
}) {
  const outlinedCircle = [
    'search',
    'more',
    'globe',
    'settings',
    'user',
  ].includes(name);
  return (
    <Svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth={1.25}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {outlinedCircle && (
        <Circle
          cx={name === 'search' ? 10 : 12}
          cy={name === 'search' ? 10 : name === 'user' ? 6 : 12}
          r={
            name === 'search'
              ? 7
              : name === 'user'
              ? 4
              : name === 'settings'
              ? 3
              : 10
          }
        />
      )}
      <Path d={paths[name] ?? paths.more ?? 'M6 12h1m4 0h1m4 0h1'} />
      {![
        'search',
        'scan',
        'message',
        'settings',
        'eye',
        'back',
        'chevron',
        'volume',
        'user',
      ].includes(name) && (
        <Path d="M9 3h6" stroke={theme.colors.accent} strokeWidth={1.7} />
      )}
      {name === 'more' && (
        <Rect
          x={5}
          y={11}
          width={2}
          height={2}
          fill={theme.colors.accent}
          stroke="none"
        />
      )}
    </Svg>
  );
}
