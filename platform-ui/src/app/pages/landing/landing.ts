import { Component, ElementRef, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PlainStyleService } from '../../services/plain-style.service';
import { FrogGame, SceneView } from './frog-game';
import { CANOPY, FAR_PINES, FROG, GRASS, MID_PINES, TRUNKS } from './landing-scene';

// how many quick clicks on the frog switch between the two versions of the site
export const FROG_CLICKS = 5;

// the logo frog, eyeing a money fly
const ASCII_FROG = String.raw`
                                        $
          _         _                 \ | /
         (o)-------(o)              ~~(_$_)~~
      .-'             '-.
     /   \___________/   \
    |                     |          ribbit.
    |    .           .    |
   _ \    '.       .'    / _
  / '-\     '-----'     /-' \
 |  |  '-.___________.-'  |  |
 /__|\__\               /__/|__\
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
`;

// The public front page: the logo frog catching money flies, what the platform does, and links to log in or
// sign up. Logged-in users never see it; landingGuard sends them to their own home page.
@Component({
  selector: 'app-landing',
  imports: [RouterLink],
  templateUrl: './landing.html',
  styleUrl: './landing.css',
})
export class Landing implements OnInit, OnDestroy {
  protected readonly art = { FAR_PINES, MID_PINES, TRUNKS, CANOPY, GRASS, FROG };
  // on the plain HTML version of the site the scene is a drawing in text instead
  protected readonly plain = inject(PlainStyleService);
  protected readonly asciiFrog = ASCII_FROG;

  private game = new FrogGame();
  // a signal, so each animation frame re-renders the scene (the app runs without zone.js)
  protected readonly scene = signal<SceneView>(this.game.view());
  protected readonly paused = signal(false);
  protected readonly ribbiting = signal(false);

  private host = inject<ElementRef<HTMLElement>>(ElementRef);
  private frame = 0;
  private ribbitTimer?: ReturnType<typeof setTimeout>;
  private frogClicks = 0;
  private frogClickTimer?: ReturnType<typeof setTimeout>;
  private last: number | null = null;
  private readonly onResize = () => this.fitToScreen();

  ngOnInit(): void {
    if (typeof requestAnimationFrame !== 'function') return;
    try {
      this.paused.set(matchMedia('(prefers-reduced-motion: reduce)').matches);
    } catch {
      // no matchMedia (tests): keep the animation running
    }
    this.fitToScreen();
    addEventListener('resize', this.onResize);
    const tick = (now: number) => {
      this.frame = requestAnimationFrame(tick);
      const dt = this.last === null ? 0 : Math.min(0.05, (now - this.last) / 1000);
      this.last = now;
      if (this.paused() || this.plain.on()) return;
      this.game.step(dt);
      this.scene.set(this.game.view());
    };
    this.frame = requestAnimationFrame(tick);
  }

  ngOnDestroy(): void {
    clearTimeout(this.ribbitTimer);
    clearTimeout(this.frogClickTimer);
    if (typeof cancelAnimationFrame === 'function') cancelAnimationFrame(this.frame);
    if (typeof removeEventListener === 'function') removeEventListener('resize', this.onResize);
  }

  // the scene is drawn with "xMidYMax slice", so a narrow screen shows only the middle of its 1440 width;
  // tell the game which part, so the frog stays on perches you can see
  private fitToScreen(): void {
    const svg = this.host.nativeElement.querySelector('.stage > svg');
    if (!svg || !svg.clientWidth || !svg.clientHeight) return;
    const scale = Math.max(svg.clientWidth / 1440, svg.clientHeight / 640);
    const shown = svg.clientWidth / scale;
    this.game.setVisibleRange(720 - shown / 2, 720 + shown / 2);
  }

  // clicking the frog makes it say hello for a moment
  protected ribbit(): void {
    this.ribbiting.set(true);
    clearTimeout(this.ribbitTimer);
    this.ribbitTimer = setTimeout(() => this.ribbiting.set(false), 1600);
    this.countFrogClick();
  }

  // Hidden: five quick clicks on the frog switch to the plain HTML version of the site, and five on the frog
  // drawn in text switch back (like the navbar logo's five clicks)
  protected countFrogClick(): void {
    clearTimeout(this.frogClickTimer);
    this.frogClicks++;
    if (this.frogClicks >= FROG_CLICKS) {
      this.frogClicks = 0;
      this.ribbiting.set(false);
      this.plain.toggle();
      return;
    }
    this.frogClickTimer = setTimeout(() => (this.frogClicks = 0), 1500);
  }

  protected togglePause(): void {
    this.paused.update((p) => !p);
    this.last = null;
  }
}
