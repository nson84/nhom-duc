import type { ShopCode } from "../shop/shop-store";

const STORAGE_KEY = "aluminum-price-review-alerts";
export const PRICE_REVIEW_CHANGED = "aluminum-price-review-changed";

export type PriceReviewAlert = {
  id: string;
  shop: ShopCode;
  categoryId: number;
  categoryName: string;
  pricePerKg: number;
  createdAt: string;
  seenAt?: string;
};

function readAll(): PriceReviewAlert[] {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "[]");
    return Array.isArray(value) ? value : [];
  } catch {
    return [];
  }
}

function writeAll(alerts: PriceReviewAlert[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(alerts));
  window.dispatchEvent(new Event(PRICE_REVIEW_CHANGED));
}

export function listPriceReviewAlerts(shop: ShopCode): PriceReviewAlert[] {
  return readAll().filter((alert) => alert.shop === shop);
}

export function removeStalePriceReviewAlerts(shop: ShopCode, categories: { id: number; name: string }[]) {
  const valid = new Map(categories.map((category) => [category.id, category]));
  const alerts = readAll();
  const remaining = alerts.filter((alert) => {
    if (alert.shop !== shop) return true;
    const category = valid.get(alert.categoryId);
    return category?.name === alert.categoryName;
  });
  if (remaining.length !== alerts.length) writeAll(remaining);
}

export function addPriceReviewAlert(shop: ShopCode, categoryId: number, categoryName: string, pricePerKg: number) {
  const id = `${shop}:${categoryId}`;
  writeAll([
    { id, shop, categoryId, categoryName, pricePerKg, createdAt: new Date().toISOString() },
    ...readAll().filter((alert) => alert.id !== id),
  ]);
}

export function dismissPriceReviewAlert(id: string) {
  writeAll(readAll().filter((alert) => alert.id !== id));
}

export function markPriceReviewAlertSeen(id: string, createdAt: string) {
  writeAll(readAll().map((alert) =>
    alert.id === id && alert.createdAt === createdAt && !alert.seenAt
      ? { ...alert, seenAt: new Date().toISOString() }
      : alert,
  ));
}
