import { useEffect, useState } from "react";
import { authStore } from "../auth/auth-store";

export type ShopCode = "DISTRIBUTION" | "MANUFACTURING";

const KEY = "aluminum-shop";
const EVENT = "aluminum-shop";

export const SHOP_LABELS: Record<ShopCode, string> = {
  DISTRIBUTION: "Phân phối nhôm khối",
  MANUFACTURING: "Xưởng sản xuất",
};

function resolveShop(): ShopCode {
  const session = authStore.get();
  if (session?.shopCode === "DISTRIBUTION" || session?.shopCode === "MANUFACTURING") {
    return session.shopCode;
  }
  return localStorage.getItem(KEY) === "DISTRIBUTION" ? "DISTRIBUTION" : "MANUFACTURING";
}

export const shopStore = {
  get(): ShopCode {
    return resolveShop();
  },
  set(code: ShopCode) {
    const session = authStore.get();
    if (session && session.role !== "ADMIN") {
      return;
    }
    localStorage.setItem(KEY, code);
    window.dispatchEvent(new Event(EVENT));
  },
  syncFromSession() {
    const session = authStore.get();
    if (session?.shopCode) {
      localStorage.setItem(KEY, session.shopCode);
      window.dispatchEvent(new Event(EVENT));
    }
  },
};

export function useShop(): ShopCode {
  const [shop, setShop] = useState<ShopCode>(shopStore.get());
  useEffect(() => {
    const sync = () => setShop(shopStore.get());
    window.addEventListener(EVENT, sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(EVENT, sync);
      window.removeEventListener("storage", sync);
    };
  }, []);
  return shop;
}
