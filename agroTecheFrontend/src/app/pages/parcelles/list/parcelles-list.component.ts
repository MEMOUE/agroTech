import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { ParcelleService } from '../../../core/services/parcelle.service';
import { AuthService } from '../../../core/services/auth.service';
import { Parcelle } from '../../../core/models/parcelle.models';

@Component({
  selector: 'app-parcelles-list',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  templateUrl: './parcelles-list.component.html',
})
export class ParcellesListComponent implements OnInit {
  private parcelleSvc = inject(ParcelleService);
  private auth = inject(AuthService);

  parcelles = signal<Parcelle[]>([]);
  loading = signal(true);
  error = signal('');
  deletingId = signal<number | null>(null);

  ngOnInit(): void {
    const user = this.auth.currentUser();
    if (user) {
      this.load(user.id);
    } else {
      this.auth.getProfile().subscribe({
        next: p => this.load(p.id),
        error: () => { this.error.set('Impossible de charger le profil.'); this.loading.set(false); }
      });
    }
  }

  private load(userId: number): void {
    this.parcelleSvc.getByUser(userId).subscribe({
      next: data => { this.parcelles.set(data); this.loading.set(false); },
      error: () => { this.error.set('Erreur lors du chargement des parcelles.'); this.loading.set(false); }
    });
  }

  delete(id: number): void {
    if (!confirm('Supprimer cette parcelle ?')) return;
    this.deletingId.set(id);
    this.parcelleSvc.delete(id).subscribe({
      next: () => { this.parcelles.update(list => list.filter(p => p.id !== id)); this.deletingId.set(null); },
      error: () => { this.error.set('Erreur lors de la suppression.'); this.deletingId.set(null); }
    });
  }
}
