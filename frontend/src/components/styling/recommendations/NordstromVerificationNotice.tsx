import Alert from '@mui/material/Alert';

/**
 * Single, non-repeated Nordstrom verification notice for the whole live
 * recommendation section - must never be duplicated per product tile or
 * per board.
 */
export function NordstromVerificationNotice() {
  return <Alert severity="info">Confirm current product details, sizes, prices, and availability on Nordstrom.</Alert>;
}
