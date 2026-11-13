import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { PlainStyleService } from './services/plain-style.service';

@Component({ template: '' })
class Blank {}

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([{ path: '', component: Blank }, { path: 'dashboard', component: Blank }]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should show the navbar outside the landing, login and registration pages', async () => {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/dashboard');
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-navbar')).toBeTruthy();
  });

  it('should hide the navbar on the landing page', async () => {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/');
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-navbar')).toBeNull();
  });

  it('should say so on the plain HTML version, with the way back', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    const plain = TestBed.inject(PlainStyleService);
    const back = () => Array.from(compiled.querySelectorAll('button')).find((b) => b.textContent?.includes('modern site'));
    expect(back()).toBeUndefined();

    plain.toggle();
    await fixture.whenStable();
    expect(compiled.textContent).toContain('the plain HTML edition');

    back()!.click();
    await fixture.whenStable();
    expect(plain.on()).toBe(false);
    expect(back()).toBeUndefined();
  });
});
