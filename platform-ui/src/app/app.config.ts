import { ApplicationConfig, inject, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withViewTransitions } from '@angular/router';

import { routes } from './app.routes';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { authInterceptor } from './services/auth.interceptor';
import { PlainStyleService } from './services/plain-style.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // pages slide and fade into each other (see the view-transition rules in styles.css)
    // but not on the plain HTML version of the site, where a page just appears
    provideRouter(routes, withViewTransitions({
      onViewTransitionCreated: ({ transition }) => {
        if (inject(PlainStyleService).on()) transition.skipTransition();
      },
    })),
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
};
