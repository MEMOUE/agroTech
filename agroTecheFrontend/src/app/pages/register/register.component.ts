import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { RegisterRequest } from '../../core/models/auth.models';

function phoneOrEmail(control: AbstractControl): ValidationErrors | null {
  const v: string = (control.value ?? '').trim();
  if (!v) return null;
  const isEmail = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
  const isPhone = /^\+?[0-9]{8,15}$/.test(v.replace(/\s/g, ''));
  return isEmail || isPhone ? null : { phoneOrEmail: true };
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);

  form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
    contact: ['', [Validators.required, phoneOrEmail]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  loading = signal(false);
  error = signal('');
  showPassword = signal(false);
  success = signal(false);

  private buildEmail(contact: string): string {
    const v = contact.trim();
    if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)) return v;
    return `${v.replace(/\s/g, '')}@agro.tel`;
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const { username, contact, password } = this.form.getRawValue();
    const payload: RegisterRequest = {
      username,
      name: username,
      email: this.buildEmail(contact),
      password,
      role: 'AGRICULTEUR',
    };
    this.loading.set(true);
    this.error.set('');
    this.auth.register(payload).subscribe({
      next: () => this.success.set(true),
      error: err => {
        this.error.set(err.error?.message ?? 'Une erreur est survenue');
        this.loading.set(false);
      },
    });
  }
}
