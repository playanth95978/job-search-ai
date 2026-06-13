import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access.service';

const conversationRoute: Routes = [
  {
    path: '',
    loadComponent: () => import('./list/conversation').then(m => m.Conversation),
    data: {},
    canActivate: [UserRouteAccessService],
  },
];

export default conversationRoute;
