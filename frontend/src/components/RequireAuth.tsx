import CircularProgress from '@mui/material/CircularProgress';
import Box from '@mui/material/Box';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

/**
 * Route guard: redirects an unauthenticated user to `/login`, preserving
 * the originally requested location so login can return them there
 * afterwards. Renders a loading indicator while the initial "am I logged
 * in" check is in flight, so a logged-in user never briefly flashes a
 * login redirect on page refresh.
 */
export function RequireAuth() {
  const { user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
        <CircularProgress aria-label="Checking authentication" />
      </Box>
    );
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <Outlet />;
}
