import { useSyncExternalStore } from "react";
import type { ShopCode } from "../shop/shop-store";

export type UserRole = "ADMIN" | "DISTRIBUTION" | "MANUFACTURING";

export type Session = {
  token: string;
  username: string;
  mustChangePassword: boolean;
  role: UserRole;
  shopCode: ShopCode | null;
};

const STORAGE_KEY = "aluminum.session";
const listeners = new Set<() => void>();

function readSession(): Session | null {
  const raw = localStorage.getItem(STORAGE_KEY) ?? sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Session;
    if (!parsed.role) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

let session = readSession();

function emit() {
  listeners.forEach((listener) => listener());
}

export const authStore = {
  get(): Session | null {
    return session;
  },
  set(next: Session) {
    session = next;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    sessionStorage.removeItem(STORAGE_KEY);
    emit();
  },
  clear() {
    session = null;
    localStorage.removeItem(STORAGE_KEY);
    sessionStorage.removeItem(STORAGE_KEY);
    emit();
  },
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

window.addEventListener("storage", (event) => {
  if (event.key === STORAGE_KEY || event.key === null) {
    sessionStorage.removeItem(STORAGE_KEY);
    session = readSession();
    emit();
  }
});

export function useSession() {
  return useSyncExternalStore(authStore.subscribe, authStore.get, authStore.get);
}

export function useCanWrite(): boolean {
  return useSession()?.role !== "ADMIN";
}
