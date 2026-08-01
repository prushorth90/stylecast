import { createContext, useContext } from 'react';
import type { AuthUser } from '../api/authApi';

export interface AuthContextValue {
  user: AuthUser | null;
  /** True only while the initial "am I logged in" check is in flight - never true again afterwards, even while login/register/logout mutations are pending. */
  isLoading: boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  register: (email: string, password: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
