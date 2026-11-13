import { DOCUMENT, Injectable, inject, signal } from '@angular/core';

// the plain site's own address is the site's, with this in front: retro.f-trade.duckdns.org
const RETRO_LABEL = 'retro';
const SHEET_ID = 'plain-style';
// marks the style sheets this service switched off, so it switches the same ones back on
const SWITCHED_OFF = 'data-plain-off';

// Everything the plain site has for styling: the browser's own defaults do the rest.
const PLAIN_CSS = `
/* the pages put links, buttons and labels side by side with no space in between, and rely on CSS to part them */
a, button, label, select, span, strong, small { margin-right: 0.6em; }
/* without their CSS sizes, pictures and drawings are as large as the browser likes */
img { max-height: 3em; }
svg[role="img"]:not([width]) { width: 40em; max-width: 100%; height: 12em; border: 1px solid; }
/* a drawing that is only decoration (the allocation ring) is a black disc without its CSS colours */
svg[aria-hidden="true"]:not([width]) { display: none; }
table { border-collapse: collapse; }
th, td { border: 1px solid; padding: 2px 8px; text-align: left; }
/* the chart colours (src/analytics.css), which the charts read from here */
:root {
  --chart-primary: #177c5b; --chart-secondary: #2a78d6; --chart-buy: #177c5b; --chart-sell: #eb6834;
  --chart-grid: #ddd; --chart-axis: #999; --muted: #444; --ink: #000; --nav: #000;
  --chart-client-1: #2a78d6; --chart-client-2: #eb6834; --chart-client-3: #1baf7a; --chart-client-4: #eda100;
  --chart-client-5: #e87ba4; --chart-client-6: #008300; --chart-client-7: #6250d6; --chart-client-8: #e34948;
}
`;

/** Whether a host name is the plain site's own address, e.g. retro.f-trade.duckdns.org or retro.localhost. */
export function isRetroHost(hostname: string): boolean {
  return hostname.toLowerCase().startsWith(`${RETRO_LABEL}.`);
}

// The plain HTML version of the site: every style sheet switched off, so the same pages are drawn the way a
// browser draws bare HTML, plus the few rules above. It has an address of its own, where it is always on. On the
// site's usual address it is a hidden extra (the landing page's frog switches it on and off), and off again after
// a reload. Neither has a visible switch.
@Injectable({ providedIn: 'root' })
export class PlainStyleService {
  private readonly doc = inject(DOCUMENT);
  private observer?: MutationObserver;

  /** On the plain site's own address, where there is nothing to switch. */
  readonly always = isRetroHost(this.doc.location?.hostname ?? '');
  readonly on = signal(this.always);

  constructor() {
    this.apply(this.on());
  }

  toggle(): void {
    if (this.always) return;
    const on = !this.on();
    this.on.set(on);
    this.apply(on);
  }

  private apply(on: boolean): void {
    this.doc.documentElement.classList.toggle('plain', on);
    this.observer?.disconnect();
    this.observer = undefined;

    if (!on) {
      this.doc.getElementById(SHEET_ID)?.remove();
      for (const sheet of this.sheets(`[${SWITCHED_OFF}]`)) {
        sheet.disabled = false;
        sheet.removeAttribute(SWITCHED_OFF);
      }
      return;
    }

    if (!this.doc.getElementById(SHEET_ID)) {
      const own = this.doc.createElement('style');
      own.id = SHEET_ID;
      own.textContent = PLAIN_CSS;
      this.doc.head.appendChild(own);
    }
    this.switchOff();
    // Angular adds a component's styles to <head> the first time the component is shown, so keep watching
    if (typeof MutationObserver === 'function') {
      this.observer = new MutationObserver(() => this.switchOff());
      this.observer.observe(this.doc.head, { childList: true });
    }
  }

  private switchOff(): void {
    // only the site's own: Angular's style elements have no id, the ones browser extensions add usually do
    for (const sheet of this.sheets(`:not([id]):not([${SWITCHED_OFF}])`)) {
      sheet.disabled = true;
      sheet.setAttribute(SWITCHED_OFF, '');
      // a style sheet file that is still on its way is switched back on by the browser when it arrives, which is
      // what happens to the site's main one on a first visit to the plain site's address
      if (sheet instanceof HTMLLinkElement && !sheet.sheet) {
        sheet.addEventListener('load', () => {
          if (this.on()) sheet.disabled = true;
        }, { once: true });
      }
    }
  }

  private sheets(filter: string): (HTMLStyleElement | HTMLLinkElement)[] {
    return Array.from(this.doc.querySelectorAll<HTMLStyleElement | HTMLLinkElement>(
      `style${filter}, link[rel~="stylesheet"]${filter}`));
  }
}
