import { Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access.service';

const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./chat'),
    canActivate: [UserRouteAccessService],
  },
  {
    path: ':id',
    loadComponent: () => import('./chat'),
    canActivate: [UserRouteAccessService],
  },
];

export default routes;
