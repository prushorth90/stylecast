import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.tsx';
import { queryClient } from './api/queryClient';
import { AuthProvider } from './components/AuthProvider';
import { ColorModeProvider } from './components/ColorModeProvider';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ColorModeProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </ColorModeProvider>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
