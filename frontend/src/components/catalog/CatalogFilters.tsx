import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Paper from '@mui/material/Paper';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import type { CatalogFilters, OccasionTag, ProductCategory, StyleTag, WeatherTag } from '../../api/catalogApi';

const CATEGORY_OPTIONS: ProductCategory[] = [
  'BLAZER',
  'SUIT',
  'SHIRT',
  'POLO',
  'TROUSERS',
  'DRESS',
  'SKIRT',
  'SHOES',
  'OUTERWEAR',
  'ACCESSORY',
];

const SIZE_OPTIONS = ['XS', 'S', 'M', 'L', 'XL', '8', '9', '10', 'ONE_SIZE'];

const COLOR_OPTIONS = [
  'Navy',
  'Charcoal',
  'Black',
  'White',
  'Ivory',
  'Olive',
  'Burgundy',
  'Camel',
  'Slate Blue',
  'Forest Green',
  'Blush',
  'Sand',
  'Graphite',
  'Rust',
  'Stone',
];

const STYLE_OPTIONS: StyleTag[] = ['CLASSIC', 'MODERN', 'MINIMAL', 'BOLD', 'CASUAL', 'FORMAL'];

const OCCASION_OPTIONS: OccasionTag[] = [
  'WEDDING',
  'INTERVIEW',
  'DINNER',
  'NETWORKING',
  'CONCERT',
  'CASUAL',
  'FORMAL_EVENT',
];

const WEATHER_OPTIONS: WeatherTag[] = ['HOT', 'MILD', 'COLD', 'RAIN', 'WIND'];

const FORMALITY_OPTIONS = Array.from({ length: 10 }, (_, index) => index + 1);

interface DraftFilters {
  category: ProductCategory | '';
  clothingSize: string;
  color: string;
  maxPrice: string;
  preferredStyle: StyleTag | '';
  occasion: OccasionTag | '';
  weather: WeatherTag | '';
  minimumFormality: string;
  maximumFormality: string;
  inStock: boolean;
}

const emptyDraft: DraftFilters = {
  category: '',
  clothingSize: '',
  color: '',
  maxPrice: '',
  preferredStyle: '',
  occasion: '',
  weather: '',
  minimumFormality: '',
  maximumFormality: '',
  inStock: false,
};

function draftToFilters(draft: DraftFilters): CatalogFilters {
  const filters: CatalogFilters = {};

  if (draft.category) filters.category = draft.category;
  if (draft.clothingSize) filters.clothingSize = draft.clothingSize;
  if (draft.color) filters.color = draft.color;
  if (draft.maxPrice) filters.maxPrice = Number(draft.maxPrice);
  if (draft.preferredStyle) filters.preferredStyle = draft.preferredStyle;
  if (draft.occasion) filters.occasion = draft.occasion;
  if (draft.weather) filters.weather = draft.weather;
  if (draft.minimumFormality) filters.minimumFormality = Number(draft.minimumFormality);
  if (draft.maximumFormality) filters.maximumFormality = Number(draft.maximumFormality);
  if (draft.inStock) filters.inStock = true;

  return filters;
}

interface FilterSelectProps {
  id: string;
  label: string;
  value: string;
  options: readonly (string | number)[];
  onChange: (value: string) => void;
}

/**
 * A dropdown filter with a plain label rendered above the box (not MUI's
 * default floating/notched label, which sits half-inside the border and
 * can overlap the selected value). The `<label htmlFor>` / `id` pair keeps
 * it fully accessible without that visual overlap.
 */
function FilterSelect({ id, label, value, options, onChange }: FilterSelectProps) {
  return (
    <Stack spacing={1} sx={{ minWidth: 140 }}>
      <Typography component="label" htmlFor={id} variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
        {label}
      </Typography>
      <Select native id={id} size="small" value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">Any</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </Select>
    </Stack>
  );
}

interface CatalogFiltersPanelProps {
  onApply: (filters: CatalogFilters) => void;
  onClear: () => void;
}

/**
 * Filter controls for the catalog page. Keeps a local "draft" of the
 * selected filters that's only applied (turned into an actual query) when
 * the user clicks "Apply Filters" - so adjusting several filters doesn't
 * trigger a new request after every keystroke/selection.
 */
export function CatalogFiltersPanel({ onApply, onClear }: CatalogFiltersPanelProps) {
  const [draft, setDraft] = useState<DraftFilters>(emptyDraft);

  function updateField<K extends keyof DraftFilters>(field: K, value: DraftFilters[K]) {
    setDraft((previous) => ({ ...previous, [field]: value }));
  }

  function handleApply() {
    onApply(draftToFilters(draft));
  }

  function handleClear() {
    setDraft(emptyDraft);
    onClear();
  }

  return (
    <Paper
      variant="outlined"
      component="form"
      onSubmit={(event) => {
        event.preventDefault();
        handleApply();
      }}
      sx={{ p: 2, mb: 3 }}
    >
      <Stack spacing={2}>
        <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
          <FilterSelect
            id="catalog-filter-category"
            label="Category"
            value={draft.category}
            options={CATEGORY_OPTIONS}
            onChange={(value) => updateField('category', value as ProductCategory | '')}
          />

          <FilterSelect
            id="catalog-filter-size"
            label="Clothing size"
            value={draft.clothingSize}
            options={SIZE_OPTIONS}
            onChange={(value) => updateField('clothingSize', value)}
          />

          <FilterSelect
            id="catalog-filter-color"
            label="Color"
            value={draft.color}
            options={COLOR_OPTIONS}
            onChange={(value) => updateField('color', value)}
          />

          <Stack spacing={1} sx={{ width: 140 }}>
            <Typography component="label" htmlFor="catalog-filter-max-price" variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
              Maximum price
            </Typography>
            <TextField
              id="catalog-filter-max-price"
              type="number"
              size="small"
              placeholder="Any"
              value={draft.maxPrice}
              onChange={(event) => updateField('maxPrice', event.target.value)}
              slotProps={{ htmlInput: { min: 0 } }}
            />
          </Stack>

          <FilterSelect
            id="catalog-filter-style"
            label="Preferred style"
            value={draft.preferredStyle}
            options={STYLE_OPTIONS}
            onChange={(value) => updateField('preferredStyle', value as StyleTag | '')}
          />

          <FilterSelect
            id="catalog-filter-occasion"
            label="Occasion"
            value={draft.occasion}
            options={OCCASION_OPTIONS}
            onChange={(value) => updateField('occasion', value as OccasionTag | '')}
          />

          <FilterSelect
            id="catalog-filter-weather"
            label="Weather"
            value={draft.weather}
            options={WEATHER_OPTIONS}
            onChange={(value) => updateField('weather', value as WeatherTag | '')}
          />

          <FilterSelect
            id="catalog-filter-min-formality"
            label="Minimum formality"
            value={draft.minimumFormality}
            options={FORMALITY_OPTIONS}
            onChange={(value) => updateField('minimumFormality', value)}
          />

          <FilterSelect
            id="catalog-filter-max-formality"
            label="Maximum formality"
            value={draft.maximumFormality}
            options={FORMALITY_OPTIONS}
            onChange={(value) => updateField('maximumFormality', value)}
          />

          <FormControlLabel
            sx={{ alignSelf: 'flex-end', ml: 0 }}
            control={
              <Checkbox
                checked={draft.inStock}
                onChange={(event) => updateField('inStock', event.target.checked)}
              />
            }
            label="In stock only"
          />
        </Stack>

        <Stack direction="row" spacing={2}>
          <Button type="submit" variant="contained">
            Apply Filters
          </Button>
          <Button variant="outlined" onClick={handleClear}>
            Clear Filters
          </Button>
        </Stack>
      </Stack>
    </Paper>
  );
}
