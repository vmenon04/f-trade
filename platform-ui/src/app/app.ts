import { Component, inject, signal } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { Navbar } from './components/navbar/navbar';
import { PlainStyleService } from './services/plain-style.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Navbar],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('platform-ui');
  // the plain HTML version of the site: a line at the top of every page says so
  protected readonly plain = inject(PlainStyleService);

  constructor(protected readonly router: Router) {}

  // the landing, login and registration pages fill the window, without the app's navbar
  protected fullPage(): boolean {
    const path = this.router.url.split(/[?#]/)[0];
    return path === '/' || path.startsWith('/login') || path.startsWith('/registration');
  }
}
