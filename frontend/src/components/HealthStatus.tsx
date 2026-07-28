import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useHealth } from '../hooks/useHealth';

/**
 * Displays the current backend connectivity status, with explicit
 * loading, error, and success states.
 */
export function HealthStatus() {
  const { data, isPending, isError } = useHealth();

  if (isPending) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
        <CircularProgress size={16} />
        <Typography variant="body2">Checking backend status…</Typography>
      </Stack>
    );
  }

  if (isError) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="alert">
        <ErrorIcon fontSize="small" color="error" />
        <Typography variant="body2" color="error.main">
          Backend unavailable
        </Typography>
      </Stack>
    );
  }

  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <CheckCircleIcon fontSize="small" color="success" />
      <Typography variant="body2">
        Backend {data.service}: {data.status}
      </Typography>
    </Stack>
  );
}
