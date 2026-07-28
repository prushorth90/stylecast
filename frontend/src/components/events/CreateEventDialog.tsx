import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormLabel from '@mui/material/FormLabel';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { useCreateEvent } from '../../hooks/useEvents';
import { EventApiError, type EventSetting } from '../../api/eventsApi';

interface CreateEventDialogProps {
  open: boolean;
  onClose: () => void;
}

interface FormState {
  title: string;
  description: string;
  location: string;
  startTime: string;
  endTime: string;
  setting: EventSetting;
  dressCode: string;
}

const initialFormState: FormState = {
  title: '',
  description: '',
  location: '',
  startTime: '',
  endTime: '',
  setting: 'INDOOR',
  dressCode: '',
};

type FieldErrors = Partial<Record<keyof FormState, string>>;

/**
 * Dialog form for manually creating an event. Validates required fields
 * and the start/end time order client-side, then submits to the backend,
 * which performs its own authoritative validation.
 */
export function CreateEventDialog({ open, onClose }: CreateEventDialogProps) {
  const [form, setForm] = useState<FormState>(initialFormState);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const createEventMutation = useCreateEvent();

  function updateField<K extends keyof FormState>(field: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [field]: value }));
  }

  function resetAndClose() {
    setForm(initialFormState);
    setFieldErrors({});
    setSubmitError(null);
    createEventMutation.reset();
    onClose();
  }

  function validate(): FieldErrors {
    const errors: FieldErrors = {};

    if (!form.title.trim()) {
      errors.title = 'Title is required.';
    }
    if (!form.location.trim()) {
      errors.location = 'Location is required.';
    }
    if (!form.startTime) {
      errors.startTime = 'Start date and time are required.';
    }
    if (!form.endTime) {
      errors.endTime = 'End date and time are required.';
    }
    if (form.startTime && form.endTime) {
      const start = new Date(form.startTime);
      const end = new Date(form.endTime);
      if (end.getTime() <= start.getTime()) {
        errors.endTime = 'End time must be after start time.';
      }
    }

    return errors;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);

    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    createEventMutation.mutate(
      {
        title: form.title.trim(),
        description: form.description.trim() || null,
        location: form.location.trim(),
        startTime: new Date(form.startTime).toISOString(),
        endTime: new Date(form.endTime).toISOString(),
        setting: form.setting,
        dressCode: form.dressCode.trim() || null,
      },
      {
        onSuccess: () => {
          resetAndClose();
        },
        onError: (error) => {
          if (error instanceof EventApiError && error.fieldErrors) {
            const backendFieldErrors: FieldErrors = {};
            for (const fieldError of error.fieldErrors) {
              backendFieldErrors[fieldError.field as keyof FormState] = fieldError.message;
            }
            setFieldErrors(backendFieldErrors);
          }
          setSubmitError(error instanceof Error ? error.message : 'Failed to create event.');
        },
      },
    );
  }

  return (
    <Dialog open={open} onClose={resetAndClose} fullWidth maxWidth="sm">
      <DialogTitle>Create Event</DialogTitle>
      <form onSubmit={handleSubmit} noValidate>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {submitError && <Alert severity="error">{submitError}</Alert>}

            <TextField
              label="Title"
              value={form.title}
              onChange={(event) => updateField('title', event.target.value)}
              error={Boolean(fieldErrors.title)}
              helperText={fieldErrors.title}
              required
              fullWidth
            />

            <TextField
              label="Description"
              value={form.description}
              onChange={(event) => updateField('description', event.target.value)}
              multiline
              minRows={2}
              fullWidth
            />

            <TextField
              label="Location"
              value={form.location}
              onChange={(event) => updateField('location', event.target.value)}
              error={Boolean(fieldErrors.location)}
              helperText={fieldErrors.location}
              required
              fullWidth
            />

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                label="Start date and time"
                type="datetime-local"
                value={form.startTime}
                onChange={(event) => updateField('startTime', event.target.value)}
                error={Boolean(fieldErrors.startTime)}
                helperText={fieldErrors.startTime}
                required
                fullWidth
                slotProps={{ inputLabel: { shrink: true } }}
              />

              <TextField
                label="End date and time"
                type="datetime-local"
                value={form.endTime}
                onChange={(event) => updateField('endTime', event.target.value)}
                error={Boolean(fieldErrors.endTime)}
                helperText={fieldErrors.endTime}
                required
                fullWidth
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Stack>

            <FormControl>
              <FormLabel id="event-setting-label">Setting</FormLabel>
              <RadioGroup
                row
                aria-labelledby="event-setting-label"
                value={form.setting}
                onChange={(event) => updateField('setting', event.target.value as EventSetting)}
              >
                <FormControlLabel value="INDOOR" control={<Radio />} label="Indoor" />
                <FormControlLabel value="OUTDOOR" control={<Radio />} label="Outdoor" />
              </RadioGroup>
            </FormControl>

            <TextField
              label="Dress code"
              value={form.dressCode}
              onChange={(event) => updateField('dressCode', event.target.value)}
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={resetAndClose} disabled={createEventMutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={createEventMutation.isPending}>
            {createEventMutation.isPending ? 'Saving…' : 'Save Event'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
