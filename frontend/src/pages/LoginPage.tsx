import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate, Link as RouterLink } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { AuthApiError } from '../api/authApi';
import { useAuth } from '../hooks/useAuth';

interface LocationState {
  from?: { pathname: string };
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const redirectTo = (location.state as LocationState | null)?.from?.pathname ?? '/';

  function validate(): boolean {
    let valid = true;

    if (!email.trim()) {
      setEmailError('Email is required');
      valid = false;
    } else {
      setEmailError(null);
    }

    if (!password) {
      setPasswordError('Password is required');
      valid = false;
    } else {
      setPasswordError(null);
    }

    return valid;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);

    if (!validate()) {
      return;
    }

    setIsSubmitting(true);
    try {
      await login(email, password);
      navigate(redirectTo, { replace: true });
    } catch (error) {
      setFormError(error instanceof AuthApiError ? error.message : 'Something went wrong. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Box sx={{ maxWidth: 400, mx: 'auto', mt: 6 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Log in
      </Typography>
      <Box component="form" onSubmit={handleSubmit} noValidate>
        <Stack spacing={2}>
          {formError && <Alert severity="error">{formError}</Alert>}
          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            error={Boolean(emailError)}
            helperText={emailError}
            required
            fullWidth
          />
          <TextField
            label="Password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            error={Boolean(passwordError)}
            helperText={passwordError}
            required
            fullWidth
          />
          <Button type="submit" variant="contained" disabled={isSubmitting} fullWidth>
            Log in
          </Button>
          <Typography variant="body2">
            Don&apos;t have an account?{' '}
            <Link component={RouterLink} to="/register">
              Register
            </Link>
          </Typography>
        </Stack>
      </Box>
    </Box>
  );
}
