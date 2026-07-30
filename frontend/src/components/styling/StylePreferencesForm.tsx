import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import FormControl from '@mui/material/FormControl';
import FormHelperText from '@mui/material/FormHelperText';
import InputLabel from '@mui/material/InputLabel';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import type { SelectChangeEvent } from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { EventApiError, type ApiFieldError } from '../../api/eventsApi';
import type {
  EventStylePreferences,
  PreferredStyle,
  SaveEventStylePreferencesInput,
  ShoppingDepartment,
} from '../../api/stylePreferencesApi';
import { useSaveEventStylePreferences } from '../../hooks/useStylePreferences';

const CLOTHING_SIZE_OPTIONS = ['XS', 'S', 'M', 'L', 'XL', 'XXL'];
const SHOE_SIZE_OPTIONS = [
  '6', '6.5', '7', '7.5', '8', '8.5', '9', '9.5', '10', '10.5', '11', '11.5', '12', '13',
];
const PREFERRED_STYLE_OPTIONS: Array<{ value: PreferredStyle; label: string }> = [
  { value: 'CLASSIC', label: 'Classic' },
  { value: 'MODERN', label: 'Modern' },
  { value: 'MINIMAL', label: 'Minimal' },
  { value: 'BOLD', label: 'Bold' },
  { value: 'CASUAL', label: 'Casual' },
  { value: 'FORMAL', label: 'Formal' },
];
const SHOPPING_DEPARTMENT_OPTIONS: Array<{ value: ShoppingDepartment; label: string }> = [
  { value: 'MEN', label: "Men's" },
  { value: 'WOMEN', label: "Women's" },
  { value: 'UNISEX', label: 'Gender-neutral / Unisex' },
  { value: 'NO_PREFERENCE', label: 'No preference' },
];

interface StylePreferencesFormProps {
  eventId: string;
  initialPreferences: EventStylePreferences | null;
}

interface FormState {
  outfitRequest: string;
  maxBudget: string;
  clothingSize: string;
  shoeSize: string;
  preferredStyle: PreferredStyle | '';
  preferredColors: string;
  colorsToAvoid: string;
  shoppingDepartment: ShoppingDepartment | '';
}

type FieldErrors = Partial<Record<keyof FormState, string>>;

function toFormState(preferences: EventStylePreferences | null): FormState {
  if (!preferences) {
    return {
      outfitRequest: '',
      maxBudget: '',
      clothingSize: '',
      shoeSize: '',
      preferredStyle: '',
      preferredColors: '',
      colorsToAvoid: '',
      shoppingDepartment: 'NO_PREFERENCE',
    };
  }

  return {
    outfitRequest: preferences.outfitRequest,
    maxBudget: String(preferences.maxBudget),
    clothingSize: preferences.clothingSize,
    shoeSize: preferences.shoeSize,
    preferredStyle: preferences.preferredStyle,
    preferredColors: preferences.preferredColors.join(', '),
    colorsToAvoid: preferences.colorsToAvoid.join(', '),
    shoppingDepartment: preferences.shoppingDepartment,
  };
}

function parseCommaSeparated(value: string): string[] {
  return value
    .split(',')
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
}

/**
 * Styling preferences form for a single event. Saves via PUT, which the
 * backend treats as an upsert (create on first save, update afterwards), so
 * repeated saves never create duplicate records.
 */
export function StylePreferencesForm({ eventId, initialPreferences }: StylePreferencesFormProps) {
  const [form, setForm] = useState<FormState>(() => toFormState(initialPreferences));
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const saveMutation = useSaveEventStylePreferences(eventId);

  function updateField<K extends keyof FormState>(field: K, value: FormState[K]) {
    setForm((previous) => ({ ...previous, [field]: value }));
    setSuccessMessage(null);
  }

  function validate(): FieldErrors {
    const errors: FieldErrors = {};

    if (!form.outfitRequest.trim()) {
      errors.outfitRequest = 'Outfit request is required.';
    } else if (form.outfitRequest.trim().length > 2000) {
      errors.outfitRequest = 'Must be at most 2000 characters.';
    }

    if (!form.maxBudget.trim()) {
      errors.maxBudget = 'Maximum budget is required.';
    } else if (!(Number(form.maxBudget) > 0)) {
      errors.maxBudget = 'Must be greater than zero.';
    }

    if (!form.clothingSize) {
      errors.clothingSize = 'Clothing size is required.';
    }
    if (!form.shoeSize) {
      errors.shoeSize = 'Shoe size is required.';
    }
    if (!form.preferredStyle) {
      errors.preferredStyle = 'Preferred style is required.';
    }
    if (!form.shoppingDepartment) {
      errors.shoppingDepartment = 'Shop from is required.';
    }

    return errors;
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    setSuccessMessage(null);

    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    const input: SaveEventStylePreferencesInput = {
      outfitRequest: form.outfitRequest.trim(),
      maxBudget: Number(form.maxBudget),
      clothingSize: form.clothingSize,
      shoeSize: form.shoeSize,
      preferredStyle: form.preferredStyle as PreferredStyle,
      preferredColors: parseCommaSeparated(form.preferredColors),
      colorsToAvoid: parseCommaSeparated(form.colorsToAvoid),
      shoppingDepartment: form.shoppingDepartment as ShoppingDepartment,
    };

    saveMutation.mutate(input, {
      onSuccess: (saved) => {
        setForm(toFormState(saved));
        setSuccessMessage('Preferences saved.');
      },
      onError: (error) => {
        if (error instanceof EventApiError && error.fieldErrors) {
          const backendFieldErrors: FieldErrors = {};
          for (const fieldError of error.fieldErrors as ApiFieldError[]) {
            backendFieldErrors[fieldError.field as keyof FormState] = fieldError.message;
          }
          setFieldErrors(backendFieldErrors);
        }
        setSubmitError(error instanceof Error ? error.message : 'Failed to save preferences.');
      },
    });
  }

  return (
    <form onSubmit={handleSubmit} noValidate aria-label="Styling preferences">
      <Stack spacing={2}>
        {submitError && (
          <Alert severity="error" role="alert">
            {submitError}
          </Alert>
        )}
        {successMessage && (
          <Alert severity="success" role="status">
            {successMessage}
          </Alert>
        )}

        <TextField
          label="Outfit request"
          placeholder="Describe the outfit you're looking for"
          value={form.outfitRequest}
          onChange={(event) => updateField('outfitRequest', event.target.value)}
          error={Boolean(fieldErrors.outfitRequest)}
          helperText={fieldErrors.outfitRequest}
          multiline
          minRows={3}
          required
          fullWidth
        />

        <TextField
          label="Maximum budget"
          type="number"
          value={form.maxBudget}
          onChange={(event) => updateField('maxBudget', event.target.value)}
          error={Boolean(fieldErrors.maxBudget)}
          helperText={fieldErrors.maxBudget}
          required
          fullWidth
          slotProps={{ htmlInput: { min: 0.01, step: 0.01 } }}
        />

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <FormControl fullWidth required error={Boolean(fieldErrors.clothingSize)}>
            <InputLabel id="clothing-size-label">Clothing size</InputLabel>
            <Select
              labelId="clothing-size-label"
              label="Clothing size"
              value={form.clothingSize}
              onChange={(event: SelectChangeEvent) => updateField('clothingSize', event.target.value)}
            >
              {CLOTHING_SIZE_OPTIONS.map((size) => (
                <MenuItem key={size} value={size}>
                  {size}
                </MenuItem>
              ))}
            </Select>
            {fieldErrors.clothingSize && <FormHelperText>{fieldErrors.clothingSize}</FormHelperText>}
          </FormControl>

          <FormControl fullWidth required error={Boolean(fieldErrors.shoeSize)}>
            <InputLabel id="shoe-size-label">Shoe size</InputLabel>
            <Select
              labelId="shoe-size-label"
              label="Shoe size"
              value={form.shoeSize}
              onChange={(event: SelectChangeEvent) => updateField('shoeSize', event.target.value)}
            >
              {SHOE_SIZE_OPTIONS.map((size) => (
                <MenuItem key={size} value={size}>
                  {size}
                </MenuItem>
              ))}
            </Select>
            {fieldErrors.shoeSize && <FormHelperText>{fieldErrors.shoeSize}</FormHelperText>}
          </FormControl>
        </Stack>

        <FormControl fullWidth required error={Boolean(fieldErrors.preferredStyle)}>
          <InputLabel id="preferred-style-label">Preferred style</InputLabel>
          <Select
            labelId="preferred-style-label"
            label="Preferred style"
            value={form.preferredStyle}
            onChange={(event: SelectChangeEvent) =>
              updateField('preferredStyle', event.target.value as PreferredStyle)
            }
          >
            {PREFERRED_STYLE_OPTIONS.map((option) => (
              <MenuItem key={option.value} value={option.value}>
                {option.label}
              </MenuItem>
            ))}
          </Select>
          {fieldErrors.preferredStyle && <FormHelperText>{fieldErrors.preferredStyle}</FormHelperText>}
        </FormControl>

        <FormControl fullWidth required error={Boolean(fieldErrors.shoppingDepartment)}>
          <InputLabel id="shopping-department-label">Shop from</InputLabel>
          <Select
            labelId="shopping-department-label"
            label="Shop from"
            value={form.shoppingDepartment}
            onChange={(event: SelectChangeEvent) =>
              updateField('shoppingDepartment', event.target.value as ShoppingDepartment)
            }
          >
            {SHOPPING_DEPARTMENT_OPTIONS.map((option) => (
              <MenuItem key={option.value} value={option.value}>
                {option.label}
              </MenuItem>
            ))}
          </Select>
          {fieldErrors.shoppingDepartment && <FormHelperText>{fieldErrors.shoppingDepartment}</FormHelperText>}
        </FormControl>

        <TextField
          label="Preferred colors"
          placeholder="e.g. navy, cream"
          helperText="Optional. Separate multiple colors with commas."
          value={form.preferredColors}
          onChange={(event) => updateField('preferredColors', event.target.value)}
          fullWidth
        />

        <TextField
          label="Colors to avoid"
          placeholder="e.g. bright red"
          helperText="Optional. Separate multiple colors with commas."
          value={form.colorsToAvoid}
          onChange={(event) => updateField('colorsToAvoid', event.target.value)}
          fullWidth
        />

        <Button
          type="submit"
          variant="contained"
          disabled={saveMutation.isPending}
          sx={{ alignSelf: 'flex-start' }}
        >
          {saveMutation.isPending ? 'Saving…' : 'Save Preferences'}
        </Button>
      </Stack>
    </form>
  );
}
