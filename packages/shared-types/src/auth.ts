export interface UserResponse {
  id: string;
  email: string;
  displayName: string;
  locale: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: UserResponse;
}

export interface RegisterFields {
  email: string;
  password: string;
  displayName: string;
  locale: string;
}
