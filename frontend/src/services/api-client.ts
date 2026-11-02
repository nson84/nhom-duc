import axios from "axios";
import { authStore } from "../features/auth/auth-store";
import { toApiError } from "./error-handler";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080",
  timeout: 15000,
});

api.interceptors.request.use((config) => {
  const session = authStore.get();
  if (session) {
    config.headers.Authorization = `Bearer ${session.token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = toApiError(error);
    if (apiError.status === 401) {
      authStore.clear();
    }
    if (apiError.code === "PASSWORD_CHANGE_REQUIRED") {
      const session = authStore.get();
      if (session) {
        authStore.set({ ...session, mustChangePassword: true });
      }
    }
    return Promise.reject(apiError);
  },
);
