import { httpResource } from '@angular/common/http';
import { Component, NO_ERRORS_SCHEMA, computed, inject } from '@angular/core';
import { InstrumentQuote } from '../../models/account.models';
import { AuthService } from '../../services/auth.service';

// how many instruments scroll past
const TICKER_SIZE = 20;

// The plain HTML version's price ticker: the first instruments and their current prices, scrolling across the top
// of the page in a <marquee>, as sites of that age did. Prices need a login, so it is empty until there is one.
@Component({
  selector: 'app-price-ticker',
  // <marquee> is long obsolete (browsers still draw it), so Angular doesn't know it
  schemas: [NO_ERRORS_SCHEMA],
  template: `
    @if (line(); as line) {
      <marquee scrollamount="4">{{ line }}</marquee>
    }
  `,
})
export class PriceTicker {
  private readonly auth = inject(AuthService);

  private readonly quotes = httpResource<InstrumentQuote[]>(() =>
    this.auth.isLoggedIn() ? { url: '/v1/instruments', params: { q: '', limit: TICKER_SIZE, offset: 0 } } : undefined);

  // e.g. "AAPL 305.84 *** ABNB 61.60"; instruments without a price right now are left out
  protected readonly line = computed(() =>
    (this.quotes.value() ?? [])
      .filter((quote) => quote.price !== null)
      .map((quote) => `${quote.ticker} ${quote.price!.toFixed(2)}`)
      .join(' *** '));
}
