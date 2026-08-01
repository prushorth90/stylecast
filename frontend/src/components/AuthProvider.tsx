import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, type ReactNode } from 'react';
import {
  getCurrentUser,
  login as loginRequest,
  logout as logoutRequest,
  register as registerRequest,
  type AuthUser,
} from '../api/authApi';
import { setUnauthorizedHandler } from '../api/httpClient';
import { AuthContext } from '../hooks/useAuth';

const currentUserQueryKey = ['currentUser'] as const;

interface AuthProviderProps {
  children: ReactNode;
}

/**
 * Central authentication state. Restores the session across a page
 * refresh by checking `GET /api/auth/me` on mount (a plain cookie-backed
 * session, so there's no token to read from local storage - the cookie
 * itself is sent automatically by the browser).
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const queryClient = useQueryClient();

  const { data: user, isLoading } = useQuery({
    queryKey: currentUserQueryKey,
    queryFn: getCurrentUser,
    staleTime: Infinity,
    retry: false,
  });

  useEffect(() => {
    setUnauthorizedHandler(() => {
      queryClient.setQueryData(currentUserQueryKey, null);
    });
    return () => setUnauthorizedHandler(null);
  }, [queryClient]);

  const value = useMemo(
    () => ({
      user: user ?? null,
      isLoading,
      async login(email: string, password: string): Promise<AuthUser> {
        const loggedInUser = await loginRequest({ email, password });
        queryClient.setQueryData(currentUserQueryKey, loggedInUser);
        return loggedInUser;
      },
      async register(email: string, password: string): Promise<AuthUser> {
        await registerRequest({ email, password });
        // Auto-login immediately after a successful registration so the
        // user doesn't have to re-enter their credentials on a separate
        // login screen.
        const loggedInUser = await loginRequest({ email, password });
        queryClient.setQueryData(currentUserQueryKey, loggedInUser);
        return loggedInUser;
      },
      async logout(): Promise<void> {
        await logoutRequest();
        queryClient.setQueryData(currentUserQueryKey, null);
        // Drop every other cached query too - otherwise a different user
        // logging in on the same browser tab could momentarily see the
        // previous user's cached events/preferences/recommendations.
        queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'currentUser' });
      },
    }),
    [user, isLoading, queryClient],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
