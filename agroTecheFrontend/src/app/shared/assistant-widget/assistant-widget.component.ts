import { Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AssistantService } from '../../core/services/assistant.service';
import { ChatMessage } from '../../core/models/parcelle.models';

@Component({
  selector: 'app-assistant-widget',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './assistant-widget.component.html',
  styleUrl: './assistant-widget.component.css',
})
export class AssistantWidgetComponent {
  private router = inject(Router);
  private assistantService = inject(AssistantService);
  protected auth = inject(AuthService);

  /** 'ferme' = robot fermé ; 'menu' = 3 icônes (vocal/photo/chat) ; 'chat' = fenêtre de discussion. */
  protected vue = signal<'ferme' | 'menu' | 'chat'>('ferme');
  protected messages = signal<ChatMessage[]>([]);
  protected saisie = signal('');
  protected envoiEnCours = signal(false);
  protected photoEnCours = signal(false);
  protected enregistrement = signal(false);
  protected erreur = signal<string | null>(null);

  @ViewChild('fichierPhoto') private fichierPhotoRef?: ElementRef<HTMLInputElement>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private recognition: any = null;

  protected basculerMenu(): void {
    this.vue.update(v => (v === 'ferme' ? 'menu' : 'ferme'));
  }

  protected ouvrirChat(): void {
    this.vue.set('chat');
  }

  protected ouvrirPhoto(): void {
    this.vue.set('chat');
    setTimeout(() => this.fichierPhotoRef?.nativeElement.click());
  }

  protected ouvrirVocal(): void {
    this.vue.set('chat');
    setTimeout(() => this.basculerVocal());
  }

  protected fermer(): void {
    this.vue.set('ferme');
    this.arreterVocal();
  }

  protected envoyer(): void {
    const texte = this.saisie().trim();
    if (!texte || this.envoiEnCours()) return;

    const historique = this.messages();
    this.messages.update(m => [...m, { role: 'user', content: texte }]);
    this.saisie.set('');
    this.erreur.set(null);
    this.envoiEnCours.set(true);

    const parcelleId = this.idParcelleCourante();
    const appel$ = parcelleId
      ? this.assistantService.chatParcelle(parcelleId, texte, historique)
      : this.assistantService.chatGeneral(texte, historique);

    appel$.subscribe({
      next: (res) => {
        this.messages.update(m => [...m, { role: 'assistant', content: res.reponse }]);
        this.envoiEnCours.set(false);
      },
      error: () => {
        this.erreur.set("L'assistant est momentanément indisponible. Réessayez plus tard.");
        this.envoiEnCours.set(false);
      },
    });
  }

  /** Dictée vocale (reconnaissance du navigateur) : remplit le champ de saisie, l'envoi reste manuel. */
  protected basculerVocal(): void {
    if (this.enregistrement()) {
      this.arreterVocal();
      return;
    }
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const SR: any = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SR) {
      this.erreur.set("La dictée vocale n'est pas disponible sur ce navigateur.");
      return;
    }
    this.erreur.set(null);
    this.recognition = new SR();
    this.recognition.lang = 'fr-FR';
    this.recognition.interimResults = false;
    this.recognition.maxAlternatives = 1;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    this.recognition.onresult = (e: any) => {
      const texte = e.results[0][0].transcript as string;
      this.saisie.set(this.saisie() ? `${this.saisie()} ${texte}` : texte);
    };
    this.recognition.onerror = () => this.enregistrement.set(false);
    this.recognition.onend = () => this.enregistrement.set(false);
    this.recognition.start();
    this.enregistrement.set(true);
  }

  private arreterVocal(): void {
    this.recognition?.stop();
    this.enregistrement.set(false);
  }

  /** Photo de la culture : seulement possible avec une parcelle identifiée (via l'URL courante). */
  protected surPhotoSelectionnee(event: Event): void {
    const input = event.target as HTMLInputElement;
    const fichier = input.files?.[0];
    if (!fichier) return;

    const parcelleId = this.idParcelleCourante();
    if (!parcelleId) {
      this.messages.update(m => [...m, {
        role: 'assistant',
        content: "Ouvrez d'abord une parcelle pour analyser une photo de culture.",
      }]);
      input.value = '';
      return;
    }

    this.messages.update(m => [...m, { role: 'user', content: `📷 ${fichier.name}` }]);
    this.photoEnCours.set(true);
    this.erreur.set(null);

    this.assistantService.analyserPhoto(parcelleId, fichier).subscribe({
      next: (a) => {
        const hypotheses = a.hypotheses.map(h => `• ${h.cause} (confiance ${h.confiance})`).join('\n');
        const texte = [a.observations, hypotheses, a.recommandation && `👉 ${a.recommandation}`, `⚠️ ${a.avertissement}`]
          .filter(Boolean).join('\n');
        this.messages.update(m => [...m, { role: 'assistant', content: texte }]);
        this.photoEnCours.set(false);
      },
      error: () => {
        this.erreur.set("Analyse de la photo indisponible pour le moment.");
        this.photoEnCours.set(false);
      },
    });
    input.value = '';
  }

  private idParcelleCourante(): number | null {
    const m = this.router.url.match(/^\/parcelles\/(\d+)(?:\/|$)/);
    return m ? Number(m[1]) : null;
  }
}
