import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  loading = signal(false);
  error = signal('');
  showPassword = signal(false);

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.error.set('');

    // Permet de se connecter avec username, téléphone ou email
    // Le backend résout automatiquement grâce à CustomUserDetailsService
    const raw = this.form.getRawValue();
    this.auth.login(raw).subscribe({
      error: err => {
        this.error.set(err.error?.message ?? 'Identifiants incorrects. Vérifiez votre nom d\'utilisateur ou téléphone.');
        this.loading.set(false);
      },
    });
  }
}
