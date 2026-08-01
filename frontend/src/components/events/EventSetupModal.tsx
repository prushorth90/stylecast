import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControl from '@mui/material/FormControl';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormHelperText from '@mui/material/FormHelperText';
import FormLabel from '@mui/material/FormLabel';
import InputLabel from '@mui/material/InputLabel';
import MenuItem from '@mui/material/MenuItem';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import Select from '@mui/material/Select';
import type { SelectChangeEvent } from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useCreateEvent, useUpdateEvent } from '../../hooks/useEvents';
import { useRegenerateEventOccasionInterpretation } from '../../hooks/useEventOccasion';
import { useInvalidateStaleLiveEventRecommendations } from '../../hooks/useLiveEventRecommendations';
import { useSaveEventStylePreferences } from '../../hooks/useStylePreferences';
import { EventApiError, type ApiFieldError, type Event, type EventSetting } from '../../api/eventsApi';
import type {
  EventStylePreferences,
  PreferredStyle,
  SaveEventStylePreferencesInput,
  ShoppingDepartment,
} from '../../api/stylePreferencesApi';

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

interface EventDetailsFormState {
  title: string;
  description: string;
  location: string;
  startTime: string;
  endTime: string;
  setting: EventSetting;
  dressCode: string;
}

const blankEventDetailsForm: EventDetailsFormState = {
  title: '',
  description: '',
  location: '',
  startTime: '',
  endTime: '',
  setting: 'INDOOR',
  dressCode: '',
};

type EventDetailsFieldErrors = Partial<Record<keyof EventDetailsFormState, string>>;

interface PreferencesFormState {
  outfitRequest: string;
  maxBudget: string;
  clothingSize: string;
  shoeSize: string;
  preferredStyle: PreferredStyle | '';
  preferredColors: string;
  colorsToAvoid: string;
  shoppingDepartment: ShoppingDepartment | '';
}

const blankPreferencesForm: PreferencesFormState = {
  outfitRequest: '',
  maxBudget: '',
  clothingSize: '',
  shoeSize: '',
  preferredStyle: '',
  preferredColors: '',
  colorsToAvoid: '',
  shoppingDepartment: 'NO_PREFERENCE',
};

type PreferencesFieldErrors = Partial<Record<keyof PreferencesFormState, string>>;

type ModalPhase = 'idle' | 'saving-event' | 'saving-preferences' | 'updating-interpretation';

interface EventSetupModalProps {
  open: boolean;
  onClose: () => void;
  /** Existing event id - when provided, the modal starts on Step 2. */
  eventId?: string;
  /** Required to prefill Step 1 (so "Back" works) when `eventId` is provided. */
  initialEvent?: Event;
  /** Prefills Step 2; `null`/`undefined` means no saved preferences yet. */
  initialPreferences?: EventStylePreferences | null;
  /**
   * ISO-8601 start/end time to prefill Step 1's date/time fields when
   * creating a brand-new event (no `eventId`/`initialEvent`) - used by the
   * calendar's "click an empty date/time to create an event" interaction.
   * Ignored when `initialEvent` is provided.
   */
  initialStartTime?: string;
  initialEndTime?: string;
  /** Called after "Save and view recommendations" succeeds, with the saved event's id. */
  onCompleted: (eventId: string) => void;
}

function toDatetimeLocalValue(iso: string): string {
  const date = new Date(iso);
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function toEventDetailsForm(event: Event | undefined): EventDetailsFormState {
  if (!event) {
    return blankEventDetailsForm;
  }
  return {
    title: event.title,
    description: event.description ?? '',
    location: event.location,
    startTime: toDatetimeLocalValue(event.startTime),
    endTime: toDatetimeLocalValue(event.endTime),
    setting: event.setting,
    dressCode: event.dressCode ?? '',
  };
}

function toPreferencesForm(preferences: EventStylePreferences | null | undefined): PreferencesFormState {
  if (!preferences) {
    return blankPreferencesForm;
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

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message : fallback;
}

/**
 * Two-step event setup modal: Step 1 collects event details, Step 2
 * collects styling preferences. Both steps live in the same dialog so the
 * user never leaves the Events page and never loses entered values moving
 * between them ("Back" always returns to Step 1 with values preserved).
 *
 * When `eventId` is supplied (editing an existing event's preferences, or
 * styling an existing event that has none saved yet), the modal opens
 * directly on Step 2 - Step 1 is still fully populated from `initialEvent`
 * so "Back" behaves consistently either way, and "Continue" always updates
 * (never re-creates) that same event.
 */
export function EventSetupModal({
  open,
  onClose,
  eventId,
  initialEvent,
  initialPreferences,
  initialStartTime,
  initialEndTime,
  onCompleted,
}: EventSetupModalProps) {
  const [step, setStep] = useState<1 | 2>(eventId ? 2 : 1);
  const [savedEventId, setSavedEventId] = useState<string | null>(eventId ?? null);
  const [phase, setPhase] = useState<ModalPhase>('idle');

  const [eventForm, setEventForm] = useState<EventDetailsFormState>(() => toEventDetailsForm(initialEvent));
  const [eventFieldErrors, setEventFieldErrors] = useState<EventDetailsFieldErrors>({});
  const [eventSubmitError, setEventSubmitError] = useState<string | null>(null);

  const [preferencesForm, setPreferencesForm] = useState<PreferencesFormState>(() =>
    toPreferencesForm(initialPreferences));
  const [preferencesFieldErrors, setPreferencesFieldErrors] = useState<PreferencesFieldErrors>({});
  const [preferencesSubmitError, setPreferencesSubmitError] = useState<string | null>(null);

  const wasOpenRef = useRef(false);

  useEffect(() => {
    if (open && !wasOpenRef.current) {
      setStep(eventId ? 2 : 1);
      setSavedEventId(eventId ?? null);
      setPhase('idle');
      setEventForm(
        initialEvent
          ? toEventDetailsForm(initialEvent)
          : {
              ...toEventDetailsForm(initialEvent),
              ...(initialStartTime ? { startTime: toDatetimeLocalValue(initialStartTime) } : {}),
              ...(initialEndTime ? { endTime: toDatetimeLocalValue(initialEndTime) } : {}),
            },
      );
      setEventFieldErrors({});
      setEventSubmitError(null);
      setPreferencesForm(toPreferencesForm(initialPreferences));
      setPreferencesFieldErrors({});
      setPreferencesSubmitError(null);
    }
    wasOpenRef.current = open;
    // Only re-initialize on the open transition - never mid-session, so in-progress
    // edits are never clobbered by an unrelated background refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const createEventMutation = useCreateEvent();
  const updateEventMutation = useUpdateEvent(savedEventId ?? undefined);
  const savePreferencesMutation = useSaveEventStylePreferences(savedEventId ?? undefined);
  const regenerateInterpretationMutation = useRegenerateEventOccasionInterpretation(savedEventId ?? undefined);
  const invalidateStaleMutation = useInvalidateStaleLiveEventRecommendations(savedEventId ?? undefined);

  const eventBusy = createEventMutation.isPending || updateEventMutation.isPending;
  const preferencesBusy = phase === 'saving-preferences' || phase === 'updating-interpretation';

  function updateEventField<K extends keyof EventDetailsFormState>(field: K, value: EventDetailsFormState[K]) {
    setEventForm((previous) => ({ ...previous, [field]: value }));
  }

  function updatePreferencesField<K extends keyof PreferencesFormState>(field: K, value: PreferencesFormState[K]) {
    setPreferencesForm((previous) => ({ ...previous, [field]: value }));
  }

  function validateEventForm(): EventDetailsFieldErrors {
    const errors: EventDetailsFieldErrors = {};
    if (!eventForm.title.trim()) {
      errors.title = 'Title is required.';
    }
    if (!eventForm.location.trim()) {
      errors.location = 'Location is required.';
    }
    if (!eventForm.startTime) {
      errors.startTime = 'Start date and time are required.';
    }
    if (!eventForm.endTime) {
      errors.endTime = 'End date and time are required.';
    }
    if (eventForm.startTime && eventForm.endTime) {
      const start = new Date(eventForm.startTime);
      const end = new Date(eventForm.endTime);
      if (end.getTime() <= start.getTime()) {
        errors.endTime = 'End time must be after start time.';
      }
    }
    return errors;
  }

  function validatePreferencesForm(): PreferencesFieldErrors {
    const errors: PreferencesFieldErrors = {};
    if (!preferencesForm.outfitRequest.trim()) {
      errors.outfitRequest = 'Outfit request is required.';
    } else if (preferencesForm.outfitRequest.trim().length > 2000) {
      errors.outfitRequest = 'Must be at most 2000 characters.';
    }
    if (!preferencesForm.maxBudget.trim()) {
      errors.maxBudget = 'Maximum budget is required.';
    } else if (!(Number(preferencesForm.maxBudget) > 0)) {
      errors.maxBudget = 'Must be greater than zero.';
    }
    if (!preferencesForm.clothingSize) {
      errors.clothingSize = 'Clothing size is required.';
    }
    if (!preferencesForm.shoeSize) {
      errors.shoeSize = 'Shoe size is required.';
    }
    if (!preferencesForm.preferredStyle) {
      errors.preferredStyle = 'Preferred style is required.';
    }
    if (!preferencesForm.shoppingDepartment) {
      errors.shoppingDepartment = 'Shop from is required.';
    }
    return errors;
  }

  function resetForNextOpen() {
    setStep(1);
    setSavedEventId(null);
    setPhase('idle');
    setEventForm(blankEventDetailsForm);
    setEventFieldErrors({});
    setEventSubmitError(null);
    setPreferencesForm(blankPreferencesForm);
    setPreferencesFieldErrors({});
    setPreferencesSubmitError(null);
  }

  function handleCancel() {
    resetForNextOpen();
    onClose();
  }

  function handleContinue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setEventSubmitError(null);

    const errors = validateEventForm();
    setEventFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    const input = {
      title: eventForm.title.trim(),
      description: eventForm.description.trim() || null,
      location: eventForm.location.trim(),
      startTime: new Date(eventForm.startTime).toISOString(),
      endTime: new Date(eventForm.endTime).toISOString(),
      setting: eventForm.setting,
      dressCode: eventForm.dressCode.trim() || null,
    };

    const mutation = savedEventId ? updateEventMutation : createEventMutation;
    mutation.mutate(input, {
      onSuccess: (saved) => {
        setSavedEventId(saved.id);
        setStep(2);
      },
      onError: (error) => {
        if (error instanceof EventApiError && error.fieldErrors) {
          const backendFieldErrors: EventDetailsFieldErrors = {};
          for (const fieldError of error.fieldErrors as ApiFieldError[]) {
            backendFieldErrors[fieldError.field as keyof EventDetailsFormState] = fieldError.message;
          }
          setEventFieldErrors(backendFieldErrors);
        }
        setEventSubmitError(errorMessage(error, 'Failed to save event details.'));
      },
    });
  }

  function handleBack() {
    setStep(1);
  }

  async function handleSaveAndViewRecommendations(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPreferencesSubmitError(null);

    const errors = validatePreferencesForm();
    setPreferencesFieldErrors(errors);
    if (Object.keys(errors).length > 0 || !savedEventId) {
      return;
    }

    const input: SaveEventStylePreferencesInput = {
      outfitRequest: preferencesForm.outfitRequest.trim(),
      maxBudget: Number(preferencesForm.maxBudget),
      clothingSize: preferencesForm.clothingSize,
      shoeSize: preferencesForm.shoeSize,
      preferredStyle: preferencesForm.preferredStyle as PreferredStyle,
      preferredColors: parseCommaSeparated(preferencesForm.preferredColors),
      colorsToAvoid: parseCommaSeparated(preferencesForm.colorsToAvoid),
      shoppingDepartment: preferencesForm.shoppingDepartment as ShoppingDepartment,
    };

    setPhase('saving-preferences');
    try {
      const saved = await savePreferencesMutation.mutateAsync(input);

      if (saved.interpretationRefreshRecommended) {
        setPhase('updating-interpretation');
        await Promise.all([
          regenerateInterpretationMutation.mutateAsync(),
          invalidateStaleMutation.mutateAsync(),
        ]);
      }

      const completedEventId = savedEventId;
      resetForNextOpen();
      onCompleted(completedEventId);
      onClose();
    } catch (error) {
      setPhase('idle');
      setPreferencesSubmitError(errorMessage(error, 'Failed to save preferences.'));
    }
  }

  function labelForPhase(): string | null {
    if (phase === 'saving-preferences') {
      return 'Saving preferences…';
    }
    if (phase === 'updating-interpretation') {
      return 'Updating interpretation…';
    }
    return null;
  }

  const progressLabel = labelForPhase();

  return (
    <Dialog open={open} onClose={handleCancel} fullWidth maxWidth="sm">
      {step === 1 && (
        <>
          <DialogTitle>Event details</DialogTitle>
          <form onSubmit={handleContinue} noValidate>
            <DialogContent>
              <Stack spacing={2} sx={{ mt: 1 }}>
                {eventSubmitError && <Alert severity="error">{eventSubmitError}</Alert>}

                <TextField
                  label="Title"
                  value={eventForm.title}
                  onChange={(event) => updateEventField('title', event.target.value)}
                  error={Boolean(eventFieldErrors.title)}
                  helperText={eventFieldErrors.title}
                  required
                  fullWidth
                />

                <TextField
                  label="Description"
                  value={eventForm.description}
                  onChange={(event) => updateEventField('description', event.target.value)}
                  multiline
                  minRows={2}
                  fullWidth
                />

                <TextField
                  label="Location"
                  value={eventForm.location}
                  onChange={(event) => updateEventField('location', event.target.value)}
                  error={Boolean(eventFieldErrors.location)}
                  helperText={eventFieldErrors.location}
                  required
                  fullWidth
                />

                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                  <TextField
                    label="Start date and time"
                    type="datetime-local"
                    value={eventForm.startTime}
                    onChange={(event) => updateEventField('startTime', event.target.value)}
                    error={Boolean(eventFieldErrors.startTime)}
                    helperText={eventFieldErrors.startTime}
                    required
                    fullWidth
                    slotProps={{ inputLabel: { shrink: true } }}
                  />

                  <TextField
                    label="End date and time"
                    type="datetime-local"
                    value={eventForm.endTime}
                    onChange={(event) => updateEventField('endTime', event.target.value)}
                    error={Boolean(eventFieldErrors.endTime)}
                    helperText={eventFieldErrors.endTime}
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
                    value={eventForm.setting}
                    onChange={(event) => updateEventField('setting', event.target.value as EventSetting)}
                  >
                    <FormControlLabel value="INDOOR" control={<Radio />} label="Indoor" />
                    <FormControlLabel value="OUTDOOR" control={<Radio />} label="Outdoor" />
                  </RadioGroup>
                </FormControl>

                <TextField
                  label="Dress code"
                  value={eventForm.dressCode}
                  onChange={(event) => updateEventField('dressCode', event.target.value)}
                  fullWidth
                />
              </Stack>
            </DialogContent>
            <DialogActions>
              <Button onClick={handleCancel} disabled={eventBusy}>
                Cancel
              </Button>
              <Button type="submit" variant="contained" disabled={eventBusy}>
                {eventBusy ? 'Saving…' : 'Continue'}
              </Button>
            </DialogActions>
          </form>
        </>
      )}

      {step === 2 && (
        <>
          <DialogTitle>Styling preferences</DialogTitle>
          <form onSubmit={handleSaveAndViewRecommendations} noValidate aria-label="Styling preferences">
            <DialogContent>
              <Stack spacing={2} sx={{ mt: 1 }}>
                {preferencesSubmitError && (
                  <Alert severity="error" role="alert">
                    {preferencesSubmitError}
                  </Alert>
                )}
                {progressLabel && (
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
                    <CircularProgress size={20} />
                    <Alert severity="info" icon={false} sx={{ flex: 1 }}>
                      {progressLabel}
                    </Alert>
                  </Stack>
                )}

                <TextField
                  label="Outfit request"
                  placeholder="Describe the outfit you're looking for"
                  value={preferencesForm.outfitRequest}
                  onChange={(event) => updatePreferencesField('outfitRequest', event.target.value)}
                  error={Boolean(preferencesFieldErrors.outfitRequest)}
                  helperText={preferencesFieldErrors.outfitRequest}
                  multiline
                  minRows={3}
                  required
                  fullWidth
                />

                <TextField
                  label="Maximum budget"
                  type="number"
                  value={preferencesForm.maxBudget}
                  onChange={(event) => updatePreferencesField('maxBudget', event.target.value)}
                  error={Boolean(preferencesFieldErrors.maxBudget)}
                  helperText={preferencesFieldErrors.maxBudget}
                  required
                  fullWidth
                  slotProps={{ htmlInput: { min: 0.01, step: 0.01 } }}
                />

                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                  <FormControl fullWidth required error={Boolean(preferencesFieldErrors.clothingSize)}>
                    <InputLabel id="clothing-size-label">Clothing size</InputLabel>
                    <Select
                      labelId="clothing-size-label"
                      label="Clothing size"
                      value={preferencesForm.clothingSize}
                      onChange={(event: SelectChangeEvent) =>
                        updatePreferencesField('clothingSize', event.target.value)
                      }
                    >
                      {CLOTHING_SIZE_OPTIONS.map((size) => (
                        <MenuItem key={size} value={size}>
                          {size}
                        </MenuItem>
                      ))}
                    </Select>
                    {preferencesFieldErrors.clothingSize && (
                      <FormHelperText>{preferencesFieldErrors.clothingSize}</FormHelperText>
                    )}
                  </FormControl>

                  <FormControl fullWidth required error={Boolean(preferencesFieldErrors.shoeSize)}>
                    <InputLabel id="shoe-size-label">Shoe size</InputLabel>
                    <Select
                      labelId="shoe-size-label"
                      label="Shoe size"
                      value={preferencesForm.shoeSize}
                      onChange={(event: SelectChangeEvent) => updatePreferencesField('shoeSize', event.target.value)}
                    >
                      {SHOE_SIZE_OPTIONS.map((size) => (
                        <MenuItem key={size} value={size}>
                          {size}
                        </MenuItem>
                      ))}
                    </Select>
                    {preferencesFieldErrors.shoeSize && (
                      <FormHelperText>{preferencesFieldErrors.shoeSize}</FormHelperText>
                    )}
                  </FormControl>
                </Stack>

                <FormControl fullWidth required error={Boolean(preferencesFieldErrors.preferredStyle)}>
                  <InputLabel id="preferred-style-label">Preferred style</InputLabel>
                  <Select
                    labelId="preferred-style-label"
                    label="Preferred style"
                    value={preferencesForm.preferredStyle}
                    onChange={(event: SelectChangeEvent) =>
                      updatePreferencesField('preferredStyle', event.target.value as PreferredStyle)
                    }
                  >
                    {PREFERRED_STYLE_OPTIONS.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </Select>
                  {preferencesFieldErrors.preferredStyle && (
                    <FormHelperText>{preferencesFieldErrors.preferredStyle}</FormHelperText>
                  )}
                </FormControl>

                <FormControl fullWidth required error={Boolean(preferencesFieldErrors.shoppingDepartment)}>
                  <InputLabel id="shopping-department-label">Shop from</InputLabel>
                  <Select
                    labelId="shopping-department-label"
                    label="Shop from"
                    value={preferencesForm.shoppingDepartment}
                    onChange={(event: SelectChangeEvent) =>
                      updatePreferencesField('shoppingDepartment', event.target.value as ShoppingDepartment)
                    }
                  >
                    {SHOPPING_DEPARTMENT_OPTIONS.map((option) => (
                      <MenuItem key={option.value} value={option.value}>
                        {option.label}
                      </MenuItem>
                    ))}
                  </Select>
                  {preferencesFieldErrors.shoppingDepartment && (
                    <FormHelperText>{preferencesFieldErrors.shoppingDepartment}</FormHelperText>
                  )}
                </FormControl>

                <TextField
                  label="Preferred colors"
                  placeholder="e.g. navy, cream"
                  helperText="Optional. Separate multiple colors with commas."
                  value={preferencesForm.preferredColors}
                  onChange={(event) => updatePreferencesField('preferredColors', event.target.value)}
                  fullWidth
                />

                <TextField
                  label="Colors to avoid"
                  placeholder="e.g. bright red"
                  helperText="Optional. Separate multiple colors with commas."
                  value={preferencesForm.colorsToAvoid}
                  onChange={(event) => updatePreferencesField('colorsToAvoid', event.target.value)}
                  fullWidth
                />
              </Stack>
            </DialogContent>
            <DialogActions>
              <Button onClick={handleBack} disabled={preferencesBusy}>
                Back
              </Button>
              <Button type="submit" variant="contained" disabled={preferencesBusy}>
                {progressLabel ?? 'Save and view recommendations'}
              </Button>
            </DialogActions>
          </form>
        </>
      )}
    </Dialog>
  );
}
