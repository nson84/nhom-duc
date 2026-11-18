import axios from "axios";
import { authStore } from "../features/auth/auth-store";
import { shopStore } from "../features/shop/shop-store";
import { workingYearStore } from "../features/dashboard/working-year";
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
  config.headers["X-Shop-Code"] = shopStore.get();
  const workingYear = workingYearStore.get(shopStore.get());
  if (config.url?.startsWith("/api/v1/stock-documents") || config.url?.startsWith("/api/v1/finance/transactions") || config.url?.startsWith("/api/v1/attendance/") || config.url?.startsWith("/api/v1/payroll/") || config.url?.startsWith("/api/v1/factory/")) {
    config.params = { ...config.params, workingYear: workingYear.year, workingMonth: workingYear.month };
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
