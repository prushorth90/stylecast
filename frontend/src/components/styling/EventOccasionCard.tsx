import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { OccasionInterpretation } from '../../api/occasionApi';
import { useRegenerateEventOccasionInterpretation } from '../../hooks/useEventOccasion';

interface EventOccasionCardProps {
  eventId: string;
  interpretation: OccasionInterpretation | undefined;
  isLoading: boolean;
  isError: boolean;
}

/** Converts a SCREAMING_SNAKE_CASE enum value into a readable label, e.g. "BUSINESS_CASUAL" -> "Business Casual". */
function formatEnumLabel(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

function formatConfidence(confidence: number): string {
  return `${Math.round(confidence * 100)}%`;
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function sourceLabel(source: OccasionInterpretation['source']): string {
  return source === 'AI' ? 'AI interpretation' : 'Rule-based fallback';
}

/**
 * Displays the event's occasion interpretation (occasion, dress code,
 * formality, categories, colors, special requirements, assumptions,
 * confidence) plus a "Regenerate Interpretation" action. The interpretation
 * loads automatically as soon as this renders - no click is required. This
 * task only classifies the occasion; it never displays or generates
 * products.
 */
export function EventOccasionCard({ eventId, interpretation, isLoading, isError }: EventOccasionCardProps) {
  const regenerate = useRegenerateEventOccasionInterpretation(eventId);
  const regenerateDisabled = isLoading || regenerate.isPending;
  const current = regenerate.data ?? interpretation;

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          sx={{ alignItems: { sm: 'center' }, justifyContent: 'space-between' }}
        >
          <Typography variant="h6" component="h2">
            Occasion Interpretation
          </Typography>
          <Button
            variant="outlined"
            size="small"
            onClick={() => regenerate.mutate()}
            disabled={regenerateDisabled}
          >
            {regenerate.isPending ? 'Regenerating…' : 'Regenerate Interpretation'}
          </Button>
        </Stack>

        <Stack spacing={2} sx={{ mt: 2 }}>
          {isLoading && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} role="status">
              <CircularProgress size={20} />
              <Typography variant="body1">Interpreting occasion…</Typography>
            </Stack>
          )}

          {!isLoading && isError && !current && (
            <Alert severity="error" role="alert">
              Unable to load the occasion interpretation right now. Styling preferences and weather are
              still available above.
            </Alert>
          )}

          {!isLoading && current && (
            <>
              <Chip
                label={sourceLabel(current.source)}
                color={current.source === 'AI' ? 'primary' : 'default'}
                size="small"
                sx={{ alignSelf: 'flex-start' }}
              />

              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={4} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
                <Stack>
                  <Typography variant="body2" color="text.secondary">
                    Occasion
                  </Typography>
                  <Typography variant="h6">{formatEnumLabel(current.occasion)}</Typography>
                </Stack>
                <Stack>
                  <Typography variant="body2" color="text.secondary">
                    Dress code
                  </Typography>
                  <Typography variant="h6">{formatEnumLabel(current.dressCode)}</Typography>
                </Stack>
                <Stack>
                  <Typography variant="body2" color="text.secondary">
                    Formality level
                  </Typography>
                  <Typography variant="h6">{current.formalityLevel} / 10</Typography>
                </Stack>
                <Stack>
                  <Typography variant="body2" color="text.secondary">
                    Confidence
                  </Typography>
                  <Typography variant="h6">{formatConfidence(current.confidence)}</Typography>
                </Stack>
              </Stack>

              <Stack spacing={0.5}>
                <Typography variant="body2" color="text.secondary">
                  Required categories
                </Typography>
                {current.requiredCategories.length > 0 ? (
                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
                    {current.requiredCategories.map((category) => (
                      <Chip key={category} label={formatEnumLabel(category)} size="small" />
                    ))}
                  </Stack>
                ) : (
                  <Typography variant="body2">None identified</Typography>
                )}
              </Stack>

              {(current.requestedItems ?? []).length > 0 && (
                <Stack spacing={0.5}>
                  <Typography variant="body2" color="text.secondary">
                    Requested items
                  </Typography>
                  <Stack component="ul" sx={{ m: 0, pl: 2.5 }}>
                    {current.requestedItems.map((item) => (
                      <Typography key={item.id} component="li" variant="body2">
                        {item.originalPhrase}
                      </Typography>
                    ))}
                  </Stack>
                </Stack>
              )}

              <Stack spacing={0.5}>
                <Typography variant="body2" color="text.secondary">
                  Optional categories
                </Typography>
                {current.optionalCategories.length > 0 ? (
                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
                    {current.optionalCategories.map((category) => (
                      <Chip key={category} label={formatEnumLabel(category)} size="small" variant="outlined" />
                    ))}
                  </Stack>
                ) : (
                  <Typography variant="body2">None identified</Typography>
                )}
              </Stack>

              {current.preferredColors.length > 0 && (
                <Stack spacing={0.5}>
                  <Typography variant="body2" color="text.secondary">
                    Preferred colors
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
                    {current.preferredColors.map((color) => (
                      <Chip key={color} label={color} size="small" />
                    ))}
                  </Stack>
                </Stack>
              )}

              {current.colorsToAvoid.length > 0 && (
                <Stack spacing={0.5}>
                  <Typography variant="body2" color="text.secondary">
                    Colors to avoid
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
                    {current.colorsToAvoid.map((color) => (
                      <Chip key={color} label={color} size="small" variant="outlined" />
                    ))}
                  </Stack>
                </Stack>
              )}

              {current.specialRequirements.length > 0 && (
                <Stack spacing={0.5}>
                  <Typography variant="body2" color="text.secondary">
                    Special requirements
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
                    {current.specialRequirements.map((requirement) => (
                      <Chip key={requirement} label={formatEnumLabel(requirement)} size="small" />
                    ))}
                  </Stack>
                </Stack>
              )}

              {current.assumptions.length > 0 && (
                <Stack spacing={0.5}>
                  <Typography variant="body2" color="text.secondary">
                    Assumptions
                  </Typography>
                  <Stack component="ul" sx={{ m: 0, pl: 2.5 }}>
                    {current.assumptions.map((assumption) => (
                      <Typography key={assumption} component="li" variant="body2">
                        {assumption}
                      </Typography>
                    ))}
                  </Stack>
                </Stack>
              )}

              <Typography variant="caption" color="text.secondary">
                Generated {formatDateTime(current.generatedAt)}
              </Typography>
            </>
          )}

          {regenerate.isError && (
            <Alert severity="error" role="alert">
              {regenerate.error instanceof Error
                ? regenerate.error.message
                : 'Unable to regenerate the interpretation right now. Please try again.'}
            </Alert>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
