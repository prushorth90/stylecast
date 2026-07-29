import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CatalogPage } from './CatalogPage';
import type { ProductDetail, ProductPage } from '../api/catalogApi';

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/catalog']}>
        <CatalogPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const sampleProducts: ProductPage = {
  content: [
    {
      id: '11111111-1111-1111-1111-111111111111',
      brand: 'Harbor & Finch',
      name: 'Tailored Wool Blazer',
      category: 'BLAZER',
      startingPrice: 220,
      imageUrl: '/catalog-placeholders/blazer.svg',
      formalityLevel: 8,
      availableSizes: ['S', 'M', 'L'],
      availableColors: ['Navy', 'Charcoal'],
      occasionTags: ['NETWORKING', 'DINNER'],
      styleTags: ['CLASSIC'],
      weatherTags: ['MILD'],
      inStock: true,
    },
    {
      id: '22222222-2222-2222-2222-222222222222',
      brand: 'Ember & Oak',
      name: 'Sold Out Statement Coat',
      category: 'OUTERWEAR',
      startingPrice: 250,
      imageUrl: null,
      formalityLevel: 6,
      availableSizes: ['S', 'M'],
      availableColors: ['Rust'],
      occasionTags: ['NETWORKING'],
      styleTags: ['BOLD'],
      weatherTags: ['COLD'],
      inStock: false,
    },
  ],
  page: 0,
  pageSize: 20,
  totalElements: 2,
  totalPages: 1,
};

const sampleDetail: ProductDetail = {
  id: '11111111-1111-1111-1111-111111111111',
  brand: 'Harbor & Finch',
  name: 'Tailored Wool Blazer',
  description: 'A tailored wool blazer for networking and dinner occasions.',
  category: 'BLAZER',
  basePrice: 220,
  imageUrl: '/catalog-placeholders/blazer.svg',
  formalityLevel: 8,
  occasionTags: ['NETWORKING', 'DINNER'],
  styleTags: ['CLASSIC'],
  weatherTags: ['MILD'],
  inStock: true,
  createdAt: '2026-07-01T00:00:00Z',
  updatedAt: '2026-07-01T00:00:00Z',
  variants: [
    {
      id: 'aaaaaaaa-1111-1111-1111-111111111111',
      sku: 'BLAZER-S-NAVY',
      clothingSize: 'S',
      color: 'Navy',
      effectivePrice: 220,
      quantityAvailable: 5,
      inStock: true,
    },
    {
      id: 'bbbbbbbb-1111-1111-1111-111111111111',
      sku: 'BLAZER-M-CHARCOAL',
      clothingSize: 'M',
      color: 'Charcoal',
      effectivePrice: 220,
      quantityAvailable: 0,
      inStock: false,
    },
  ],
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function mockFetch(list: ProductPage | { status: number; body: unknown } = sampleProducts) {
  vi.mocked(fetch).mockImplementation((input: string | URL | Request) => {
    const url = typeof input === 'string' ? input : input.toString();
    if (url.includes('/api/products/')) {
      return Promise.resolve(jsonResponse(sampleDetail));
    }
    if ('status' in list) {
      return Promise.resolve(jsonResponse(list.body, list.status));
    }
    return Promise.resolve(jsonResponse(list));
  });
}

function latestFetchUrl(): string {
  const calls = vi.mocked(fetch).mock.calls;
  const lastCall = calls[calls.length - 1][0];
  return typeof lastCall === 'string' ? lastCall : lastCall.toString();
}

describe('CatalogPage', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    cleanup();
  });

  it('shows a loading state before the request resolves', () => {
    vi.mocked(fetch).mockReturnValue(new Promise(() => {}));

    renderPage();

    expect(screen.getByRole('status')).toHaveTextContent('Loading products');
  });

  it('shows an empty state when no products match', async () => {
    mockFetch({ ...sampleProducts, content: [], totalElements: 0, totalPages: 0 });

    renderPage();

    expect(await screen.findByText('No products match the selected filters.')).toBeInTheDocument();
  });

  it('shows an error state when the API request fails', async () => {
    mockFetch({ status: 500, body: { message: 'Server error' } });

    renderPage();

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });

  it('renders seeded product cards', async () => {
    mockFetch();

    renderPage();

    expect(await screen.findByText('Tailored Wool Blazer')).toBeInTheDocument();
    expect(screen.getByText('Sold Out Statement Coat')).toBeInTheDocument();
    expect(screen.getByText('In stock')).toBeInTheDocument();
    expect(screen.getByText('Out of stock')).toBeInTheDocument();
  });

  it('shows the demo-data disclaimer', async () => {
    mockFetch();

    renderPage();

    expect(await screen.findByText('Demo catalog using fictional products.')).toBeInTheDocument();
  });

  it('translates a selected filter into the correct query parameters', async () => {
    mockFetch();
    renderPage();
    await screen.findByText('Tailored Wool Blazer');

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'SUIT' } });
    fireEvent.click(screen.getByText('Apply Filters'));

    await waitFor(() => {
      expect(latestFetchUrl()).toContain('category=SUIT');
    });
  });

  it('applies a combination of filters together in a single request', async () => {
    mockFetch();
    renderPage();
    await screen.findByText('Tailored Wool Blazer');

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'OUTERWEAR' } });
    fireEvent.change(screen.getByLabelText('Color'), { target: { value: 'Rust' } });
    fireEvent.click(screen.getByLabelText('In stock only'));
    fireEvent.click(screen.getByText('Apply Filters'));

    await waitFor(() => {
      const url = latestFetchUrl();
      expect(url).toContain('category=OUTERWEAR');
      expect(url).toContain('color=Rust');
      expect(url).toContain('inStock=true');
    });
  });

  it('resets controls and query when Clear Filters is clicked', async () => {
    mockFetch();
    renderPage();
    await screen.findByText('Tailored Wool Blazer');

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: 'SUIT' } });
    fireEvent.click(screen.getByText('Apply Filters'));
    await waitFor(() => expect(latestFetchUrl()).toContain('category=SUIT'));

    fireEvent.click(screen.getByText('Clear Filters'));

    await waitFor(() => {
      expect(latestFetchUrl()).not.toContain('category=');
    });
    expect(screen.getByLabelText('Category')).toHaveValue('');
  });

  it('shows product detail with variants and stock when View details is clicked', async () => {
    mockFetch();
    renderPage();
    await screen.findByText('Tailored Wool Blazer');

    const card = screen.getByText('Tailored Wool Blazer').closest('.MuiCard-root') as HTMLElement;
    fireEvent.click(within(card).getByText('View details'));

    expect(await screen.findByText(/A tailored wool blazer/)).toBeInTheDocument();
    const dialogContent = document.querySelector('.MuiDialogContent-root') as HTMLElement;
    expect(within(dialogContent).getByText('Navy')).toBeInTheDocument();
    expect(within(dialogContent).getByText('In stock (5)')).toBeInTheDocument();
    expect(within(dialogContent).getByText('Out of stock')).toBeInTheDocument();
  });

  it('shows pagination controls and requests the next page', async () => {
    mockFetch({ ...sampleProducts, totalPages: 3, page: 0 });
    renderPage();
    await screen.findByText('Tailored Wool Blazer');

    const pagination = document.querySelector('.MuiPagination-ul') as HTMLElement;
    const pageTwoButton = within(pagination).getByText('2').closest('button') as HTMLButtonElement;
    fireEvent.click(pageTwoButton);

    await waitFor(() => {
      expect(latestFetchUrl()).toContain('page=1');
    });
  });
});
