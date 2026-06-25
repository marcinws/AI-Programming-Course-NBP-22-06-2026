import { Routes } from '@angular/router';
import { IntakeFormComponent } from './features/form/intake-form.component';
import { ChatComponent } from './features/chat/chat.component';

export const routes: Routes = [
  { path: '', component: IntakeFormComponent },
  { path: 'chat/:sessionId', component: ChatComponent },
  { path: '**', redirectTo: '' },
];
