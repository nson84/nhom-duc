import { api } from "../../services/api-client";

export type LoginResult = {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  mustChangePassword: boolean;
  username: string;
};

type Envelope<T> = {
  success: boolean;
  data: T;
  message?: string;
};

export async function login(username: string, password: string): Promise<LoginResult> {
  const response = await api.post<Envelope<LoginResult>>("/api/v1/auth/login", { username, password });
  return response.data.data;
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<LoginResult> {
  const response = await api.put<Envelope<LoginResult>>("/api/v1/auth/password", {
    currentPassword,
    newPassword,
  });
  return response.data.data;
}

export async function logout(): Promise<void> {
  await api.post("/api/v1/auth/logout");
}
