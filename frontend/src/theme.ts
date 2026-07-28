import { createTheme } from '@mui/material/styles';
import type { Theme } from '@mui/material/styles';

export type ColorMode = 'light' | 'dark';

/**
 * Builds the app theme for a given color mode. Colors are chosen to work
 * well in both light and dark mode rather than hard-coding a single fixed
 * palette.
 */
export function createAppTheme(mode: ColorMode): Theme {
  return createTheme({
    palette: {
      mode,
      primary: {
        main: mode === 'dark' ? '#60a5fa' : '#2563eb',
      },
      secondary: {
        main: mode === 'dark' ? '#34d399' : '#059669',
      },
    },
  });
}
