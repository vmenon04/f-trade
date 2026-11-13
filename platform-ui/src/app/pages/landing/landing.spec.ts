import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { PlainStyleService } from '../../services/plain-style.service';
import { FLY_COUNT, FrogGame } from './frog-game';
import { FROG_CLICKS, Landing } from './landing';

describe('Landing', () => {
  let fixture: ComponentFixture<Landing>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Landing],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
  });

  it('links to the login and registration pages', () => {
    const hrefs = Array.from(fixture.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>)
      .map((a) => a.getAttribute('href'));
    expect(hrefs).toContain('/login');
    expect(hrefs).toContain('/registration');
  });

  it('draws every fly', () => {
    expect(fixture.nativeElement.querySelectorAll('svg g[opacity]').length).toBe(FLY_COUNT);
  });

  it('says ribbit when the frog is clicked', async () => {
    expect(fixture.nativeElement.querySelector('.ribbit')).toBeNull();
    fixture.nativeElement.querySelector('g.frog').dispatchEvent(new MouseEvent('click'));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('.ribbit').textContent).toContain('Ribbit');
  });

  it('switches to the plain HTML version after enough quick clicks on the frog, and back the same way', async () => {
    const plain = TestBed.inject(PlainStyleService);
    const click = (selector: string) => fixture.nativeElement.querySelector(selector).dispatchEvent(new MouseEvent('click'));

    for (let i = 1; i < FROG_CLICKS; i++) click('g.frog');
    expect(plain.on()).toBe(false);
    click('g.frog');
    await fixture.whenStable();
    expect(plain.on()).toBe(true);

    for (let i = 0; i < FROG_CLICKS; i++) click('pre');
    await fixture.whenStable();
    expect(plain.on()).toBe(false);
    expect(fixture.nativeElement.querySelector('g.frog')).toBeTruthy();
  });

  it('draws the frog in text on the plain HTML version, with the same links', async () => {
    const plain = TestBed.inject(PlainStyleService);
    plain.toggle();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('svg')).toBeNull();
    expect(fixture.nativeElement.querySelector('pre').textContent).toContain('(o)-------(o)');
    const hrefs = Array.from(fixture.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>)
      .map((a) => a.getAttribute('href'));
    expect(hrefs).toContain('/login');
    expect(hrefs).toContain('/registration');

    plain.toggle();
  });
});

describe('FrogGame', () => {
  it('catches flies and keeps a running total', () => {
    let seed = 1;
    const random = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    const game = new FrogGame(random);
    let tongueSeen = false;
    for (let i = 0; i < 60 * 30; i++) {
      game.step(1 / 60);
      if (game.view().tongue) tongueSeen = true;
    }
    const view = game.view();
    expect(tongueSeen).toBe(true);
    expect(view.caught).toBeGreaterThan(0);
    expect(Number(view.total)).toBeGreaterThan(0);
  });

  it('stays on screen and keeps catching when a phone crops the scene', () => {
    let seed = 7;
    const random = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    const game = new FrogGame(random);
    game.setVisibleRange(392, 1048);
    for (let i = 0; i < 60 * 30; i++) game.step(1 / 60);
    const view = game.view();
    const frogX = Number(/translate\(([-\d.]+)/.exec(view.frog)![1]);
    expect(frogX).toBeGreaterThanOrEqual(392);
    expect(frogX).toBeLessThanOrEqual(1048);
    expect(view.caught).toBeGreaterThan(0);
  });
});
