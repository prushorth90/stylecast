import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { ColorModeContext, type ColorModeContextValue } from '../hooks/useColorMode';
import { createAppTheme, type ColorMode } from '../theme';

const STORAGE_KEY = 'stylecast-color-mode';

function getInitialMode(): ColorMode {
  if (typeof window === 'undefined') {
    return 'light';
  }

  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

interface ColorModeProviderProps {
  children: ReactNode;
}

/**
 * Provides the current color mode (light/dark) plus a way to toggle it, and
 * renders the MUI theme + CssBaseline for that mode. Defaults to the
 * browser/OS preference, but an explicit user choice is persisted in
 * localStorage and takes priority on future visits.
 */
export function ColorModeProvider({ children }: ColorModeProviderProps) {
  const [mode, setMode] = useState<ColorMode>(getInitialMode);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, mode);
  }, [mode]);

  const contextValue = useMemo<ColorModeContextValue>(
    () => ({
      mode,
      toggleColorMode: () => setMode((previous) => (previous === 'light' ? 'dark' : 'light')),
    }),
    [mode],
  );

  const theme = useMemo(() => createAppTheme(mode), [mode]);

  return (
    <ColorModeContext.Provider value={contextValue}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
}
