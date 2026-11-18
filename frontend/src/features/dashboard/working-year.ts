import { useSyncExternalStore } from "react";
import type { ShopCode } from "../shop/shop-store";

type WorkingYear = { year: number; locked: boolean; month?: string };
const listeners = new Set<() => void>();
const values = new Map<ShopCode, WorkingYear>();
const key = (shop: ShopCode) => `aluminum-working-year:${shop}`;

function read(shop: ShopCode): WorkingYear {
  const cached = values.get(shop);
  if (cached) return cached;
  try {
    const stored = JSON.parse(localStorage.getItem(key(shop)) ?? "null") as WorkingYear | null;
    if (stored && Number.isInteger(stored.year) && stored.year >= 2000 && stored.year <= 2100) {
      const value = { year: stored.year, locked: stored.locked === true,
        month: typeof stored.month === "string" && /^\d{4}-(0[1-9]|1[0-2])$/.test(stored.month) ? stored.month : undefined };
      values.set(shop, value);
      return value;
    }
  } catch { /* Dùng năm hiện tại nếu bộ nhớ trình duyệt hỏng. */ }
  const value = { year: new Date().getFullYear(), locked: false };
  values.set(shop, value);
  return value;
}

export const workingYearStore = {
  get: read,
  set(shop: ShopCode, value: WorkingYear) {
    const previous = read(shop);
    const next = { ...value, month: value.month ?? (value.year === previous.year ? previous.month : undefined) };
    values.set(shop, next);
    localStorage.setItem(key(shop), JSON.stringify(next));
    listeners.forEach((listener) => listener());
  },
  setMonth(shop: ShopCode, month?: string) {
    const next = { ...read(shop), month };
    values.set(shop, next);
    localStorage.setItem(key(shop), JSON.stringify(next));
    listeners.forEach((listener) => listener());
  },
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

window.addEventListener("storage", (event) => {
  if (event.key?.startsWith("aluminum-working-year:") || event.key === null) {
    values.clear();
    listeners.forEach((listener) => listener());
  }
});

export function useWorkingYear(shop: ShopCode): WorkingYear {
  return useSyncExternalStore(workingYearStore.subscribe, () => workingYearStore.get(shop));
}
