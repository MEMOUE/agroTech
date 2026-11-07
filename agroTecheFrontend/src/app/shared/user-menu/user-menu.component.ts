import { Component, OnInit, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';

/** Affiche le profil connecté + bouton de déconnexion ; à inclure dans la navbar de chaque page protégée. */
@Component({
  selector: 'app-user-menu',
  standalone: true,
  templateUrl: './user-menu.component.html',
})
export class UserMenuComponent implements OnInit {
  auth = inject(AuthService);

  readonly roleLabels: Record<string, string> = {
    AGRICULTEUR: 'Agriculteur',
    TECHNICIEN: 'Technicien',
    ADMIN: 'Administrateur',
  };

  ngOnInit(): void {
    if (!this.auth.currentUser()) {
      this.auth.getProfile().subscribe({ error: () => {} });
    }
  }

  logout(): void {
    this.auth.logout();
  }
}
