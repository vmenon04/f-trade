import { TestBed } from '@angular/core/testing';
import { PlainStyleService, isRetroHost } from './plain-style.service';

describe('isRetroHost', () => {
  it('is the site\'s address with retro in front', () => {
    expect(isRetroHost('retro.f-trade.duckdns.org')).toBe(true);
    expect(isRetroHost('retro.localhost')).toBe(true);
    expect(isRetroHost('f-trade.duckdns.org')).toBe(false);
    expect(isRetroHost('jenkins.f-trade.duckdns.org')).toBe(false);
    expect(isRetroHost('localhost')).toBe(false);
  });
});

describe('PlainStyleService', () => {
  let siteSheet: HTMLStyleElement;
  let otherSheet: HTMLStyleElement;

  beforeEach(() => {
    siteSheet = document.head.appendChild(document.createElement('style'));
    // e.g. one a browser extension added
    otherSheet = document.head.appendChild(document.createElement('style'));
    otherSheet.id = 'not-the-sites';
  });

  afterEach(() => {
    const service = TestBed.inject(PlainStyleService);
    if (service.on()) service.toggle();
    siteSheet.remove();
    otherSheet.remove();
  });

  it('starts as the modern site on the usual address', () => {
    const service = TestBed.inject(PlainStyleService);
    expect(service.always).toBe(false);
    expect(service.on()).toBe(false);
    expect(document.documentElement.classList.contains('plain')).toBe(false);
    expect(document.getElementById('plain-style')).toBeNull();
  });

  it('switches the site\'s style sheets off and adds its own', () => {
    TestBed.inject(PlainStyleService).toggle();

    expect(document.documentElement.classList.contains('plain')).toBe(true);
    expect(siteSheet.hasAttribute('data-plain-off')).toBe(true);
    expect(otherSheet.hasAttribute('data-plain-off')).toBe(false);
    expect(document.getElementById('plain-style')?.textContent).toContain('border-collapse');
  });

  it('switches off a style sheet added later, as Angular does for a component shown for the first time', async () => {
    TestBed.inject(PlainStyleService).toggle();
    const late = document.head.appendChild(document.createElement('style'));
    // the observer is told after the current task
    await new Promise((resolve) => setTimeout(resolve));

    expect(late.hasAttribute('data-plain-off')).toBe(true);
    late.remove();
  });

  it('switches a style sheet file off again when it arrives after the switch', () => {
    const link = document.head.appendChild(document.createElement('link'));
    link.rel = 'stylesheet';
    TestBed.inject(PlainStyleService).toggle();
    expect(link.hasAttribute('data-plain-off')).toBe(true);

    // what the browser does when the file has loaded
    link.disabled = false;
    link.dispatchEvent(new Event('load'));

    expect(link.disabled).toBe(true);
    link.remove();
  });

  it('puts everything back', () => {
    const service = TestBed.inject(PlainStyleService);
    service.toggle();
    service.toggle();

    expect(service.on()).toBe(false);
    expect(document.documentElement.classList.contains('plain')).toBe(false);
    expect(siteSheet.hasAttribute('data-plain-off')).toBe(false);
    expect(document.getElementById('plain-style')).toBeNull();
  });
});
