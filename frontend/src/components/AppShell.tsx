import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import type { ReactNode } from 'react';
import { Header } from './Header';
import { HealthStatus } from './HealthStatus';

interface AppShellProps {
  children: ReactNode;
}

/**
 * Top-level application layout: header, main content area, and a footer
 * that surfaces backend connectivity status.
 */
export function AppShell({ children }: AppShellProps) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flexGrow: 1, minHeight: '100vh' }}>
      <Header />
      <Container component="main" sx={{ flexGrow: 1, py: 4 }}>
        {children}
      </Container>
      <Box
        component="footer"
        sx={{ borderTop: 1, borderColor: 'divider', py: 2 }}
      >
        <Container>
          <HealthStatus />
        </Container>
      </Box>
    </Box>
  );
}
