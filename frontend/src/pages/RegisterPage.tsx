import { useState, type FormEvent } from 'react';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { AuthApiError } from '../api/authApi';
import { useAuth } from '../hooks/useAuth';

const MIN_PASSWORD_LENGTH = 8;

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function validate(): boolean {
    let valid = true;

    if (!email.trim()) {
      setEmailError('Email is required');
      valid = false;
    } else if (!/^\S+@\S+\.\S+$/.test(email)) {
      setEmailError('Enter a valid email address');
      valid = false;
    } else {
      setEmailError(null);
    }

    if (!password) {
      setPasswordError('Password is required');
      valid = false;
    } else if (password.length < MIN_PASSWORD_LENGTH) {
      setPasswordError(`Password must be at least ${MIN_PASSWORD_LENGTH} characters`);
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
      await register(email, password);
      navigate('/', { replace: true });
    } catch (error) {
      setFormError(error instanceof AuthApiError ? error.message : 'Something went wrong. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Box sx={{ maxWidth: 400, mx: 'auto', mt: 6 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Create an account
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
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            error={Boolean(passwordError)}
            helperText={passwordError ?? `At least ${MIN_PASSWORD_LENGTH} characters`}
            required
            fullWidth
          />
          <Button type="submit" variant="contained" disabled={isSubmitting} fullWidth>
            Register
          </Button>
          <Typography variant="body2">
            Already have an account?{' '}
            <Link component={RouterLink} to="/login">
              Log in
            </Link>
          </Typography>
        </Stack>
      </Box>
    </Box>
  );
}
