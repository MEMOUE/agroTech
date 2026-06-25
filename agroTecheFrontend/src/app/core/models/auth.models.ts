export type Role = 'AGRICULTEUR' | 'TECHNICIEN' | 'ADMIN';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  name: string;
  email: string;
  password: string;
  role?: Role;
}

export interface AuthResponse {
  token: string;
  username: string;
  name: string;
  email: string;
  role: Role;
}

export interface UserProfile {
  id: number;
  username: string;
  name: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}
