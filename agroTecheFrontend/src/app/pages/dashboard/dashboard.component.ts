import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { AuthService } from '../../core/services/auth.service';
import { ParcelleService } from '../../core/services/parcelle.service';
import { UserProfile } from '../../core/models/auth.models';
import { Parcelle } from '../../core/models/parcelle.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent implements OnInit {
  private auth = inject(AuthService);
  private parcelleSvc = inject(ParcelleService);

  profile  = signal<UserProfile | null>(null);
  parcelles = signal<Parcelle[]>([]);
  loading   = signal(true);
  totalSuperficie = computed(() =>
    this.parcelles().reduce((acc, p) => acc + p.superficie, 0)
  );

  readonly roleLabels: Record<string, string> = {
    AGRICULTEUR: 'Agriculteur',
    TECHNICIEN: 'Technicien',
    ADMIN: 'Administrateur',
  };

  ngOnInit(): void {
    const cached = this.auth.currentUser();
    if (cached) {
      this.profile.set(cached);
      this.loadParcelles(cached.id);
    } else {
      this.auth.getProfile().subscribe({
        next: p => { this.profile.set(p); this.loadParcelles(p.id); },
        error: () => this.loading.set(false),
      });
    }
  }

  private loadParcelles(userId: number): void {
    this.parcelleSvc.getByUser(userId).subscribe({
      next: data => { this.parcelles.set(data); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  logout(): void { this.auth.logout(); }
}
