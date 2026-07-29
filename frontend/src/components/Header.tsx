import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import Brightness4Icon from '@mui/icons-material/Brightness4';
import Brightness7Icon from '@mui/icons-material/Brightness7';
import { Link as RouterLink } from 'react-router-dom';
import { useColorMode } from '../hooks/useColorMode';

export function Header() {
  const { mode, toggleColorMode } = useColorMode();

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
        <Button component={RouterLink} to="/catalog" color="inherit" sx={{ mr: 1 }}>
          Catalog
        </Button>
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
