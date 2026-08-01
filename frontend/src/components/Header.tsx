import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import Brightness4Icon from '@mui/icons-material/Brightness4';
import Brightness7Icon from '@mui/icons-material/Brightness7';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useColorMode } from '../hooks/useColorMode';
import { useAuth } from '../hooks/useAuth';

export function Header() {
  const { mode, toggleColorMode } = useColorMode();
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <AppBar position="static" component="header">
      <Toolbar>
        <Typography
          variant="h6"
          component={RouterLink}
          to="/"
          sx={{ color: 'inherit', textDecoration: 'none', flexGrow: 1 }}
        >
          StyleCast
        </Typography>
        {user && (
          <>
            <Button component={RouterLink} to="/events" color="inherit" sx={{ mr: 1 }}>
              Events
            </Button>
            <Button component={RouterLink} to="/calendar" color="inherit" sx={{ mr: 1 }}>
              Calendar
            </Button>
            <Button component={RouterLink} to="/history" color="inherit" sx={{ mr: 1 }}>
              History
            </Button>
            <Typography variant="body2" sx={{ mr: 1, display: { xs: 'none', sm: 'block' } }}>
              {user.email}
            </Typography>
            <Button color="inherit" onClick={handleLogout} sx={{ mr: 1 }}>
              Log out
            </Button>
          </>
        )}
        <IconButton
          onClick={toggleColorMode}
          color="inherit"
          aria-label={mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
        >
          {mode === 'dark' ? <Brightness7Icon /> : <Brightness4Icon />}
        </IconButton>
      </Toolbar>
    </AppBar>
  );
}
