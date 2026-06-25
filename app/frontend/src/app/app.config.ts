import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection, SecurityContext } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideMarkdown } from 'ngx-markdown';

import { routes } from './app.routes';
import { httpErrorInterceptor } from './core/http-error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(
      withInterceptors([httpErrorInterceptor]),
    ),
    // ngx-markdown: sanitization ON (SecurityContext.HTML — strips XSS)
    provideMarkdown({
      sanitize: SecurityContext.HTML,
    }),
  ],
};
