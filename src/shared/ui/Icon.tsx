/** 两端共用完整矢量路径；未知图标回退more，不叠加与原型无关的装饰线。 */
import React from 'react';
import Svg, { Path } from 'react-native-svg';
import iconPaths from '@shared/assets/icons.json';
import { theme } from './theme';
const paths: Record<string, string> = iconPaths;
export function Icon({
  name,
  size = 26,
  color = theme.colors.text,
}: {
  name: string;
  size?: number;
  color?: string;
}) {
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
      <Path d={paths[name] ?? paths.more ?? 'M6 12h1m4 0h1m4 0h1'} />
    </Svg>
  );
}
