import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { PlainStyleService } from './plain-style.service';

describe('PlainStyleService', () => {
  let siteSheet: HTMLStyleElement;
  let otherSheet: HTMLStyleElement;

  beforeEach(() => {
    // the test runner has no localStorage of its own
    const stored = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => stored.get(key) ?? null,
      setItem: (key: string, value: string) => void stored.set(key, value),
    });
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
    vi.unstubAllGlobals();
  });

  it('starts as the modern site', () => {
    expect(TestBed.inject(PlainStyleService).on()).toBe(false);
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

  it('puts everything back', () => {
    const service = TestBed.inject(PlainStyleService);
    service.toggle();
    service.toggle();

    expect(service.on()).toBe(false);
    expect(document.documentElement.classList.contains('plain')).toBe(false);
    expect(siteSheet.hasAttribute('data-plain-off')).toBe(false);
    expect(document.getElementById('plain-style')).toBeNull();
  });

  it('remembers the choice', () => {
    TestBed.inject(PlainStyleService).toggle();
    expect(localStorage.getItem('ftrade.plain')).toBe('true');

    TestBed.resetTestingModule();
    expect(TestBed.inject(PlainStyleService).on()).toBe(true);
  });
});
