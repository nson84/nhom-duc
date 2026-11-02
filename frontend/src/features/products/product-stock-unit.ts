import type { Product, ProductUnit } from "./products-api";

/** Đơn vị tồn kho mặc định — theo kg; bán lẻ thường theo tạ (100 kg). */
export const DEFAULT_STOCK_UNIT = "kg";

export const RETAIL_UNIT_TA = "tạ";

export const KG_PER_TA = 100;

export const KG_PER_TON = 1000;

export type PurchaseWeightUnit = "kg" | "tấn";

export function activeUnits(product: Product): ProductUnit[] {
  return product.units.filter((u) => u.active);
}

export function defaultDocumentUnit(product: Product): ProductUnit | undefined {
  return (
    product.units.find((u) => u.active && u.defaultSelling) ??
    product.units.find((u) => u.active && u.base)
  );
}

/** Có đơn vị bán/nhập khác ngoài đơn vị cơ sở (cần màn quy đổi). */
export function hasExtraUnits(product: Product): boolean {
  return product.units.some((u) => u.active && !u.base);
}

export function usesSingleActiveUnit(product: Product | undefined): boolean {
  if (!product) return false;
  return activeUnits(product).length === 1;
}

export function kgUnit(product: Product): ProductUnit | undefined {
  return product.units.find((u) => u.active && u.base) ?? product.units.find((u) => u.active && u.unitName === DEFAULT_STOCK_UNIT);
}

export function tonUnit(product: Product): ProductUnit | undefined {
  return product.units.find((u) => u.active && u.unitName === "tấn");
}

export function purchaseUnit(product: Product, weightUnit: PurchaseWeightUnit): ProductUnit | undefined {
  return weightUnit === "tấn" ? tonUnit(product) : kgUnit(product);
}

export function formatTonFromKg(kg: number): string {
  if (!Number.isFinite(kg) || kg <= 0) return "—";
  const tons = kg / KG_PER_TON;
  return Number.isInteger(tons) ? String(tons) : tons.toLocaleString("vi-VN", { maximumFractionDigits: 3 });
}

/** Hiển thị số lượng tồn/biến động từ kg nội bộ → tấn. */
export function kgToTon(kg: number): number {
  if (!Number.isFinite(kg)) return 0;
  return kg / KG_PER_TON;
}

/** Giá vốn / kg nội bộ → giá vốn / tấn để hiện UI. */
export function costPerKgToTon(costPerKg: number): number {
  if (!Number.isFinite(costPerKg)) return 0;
  return costPerKg * KG_PER_TON;
}

function roundToThousand(value: number): number {
  if (!Number.isFinite(value) || value <= 0) return 0;
  return Math.round(value / 1000) * 1000;
}

export function suggestPurchasePricePerUnit(product: Product, unit: PurchaseWeightUnit): number {
  const base = product.referencePurchasePrice;
  const raw = unit === "tấn" ? Math.round(base * KG_PER_TON) : Math.round(base);
  return roundToThousand(raw);
}

/** Gợi ý đơn giá bán = giá / kg (từ giá bán / tấn) × hệ số đơn vị dòng. */
export function suggestSalePricePerUnit(product: Product, unit: ProductUnit): number {
  const raw = Math.round((Number(product.defaultSellingPrice) || 0) * unit.factorToBase);
  return roundToThousand(raw);
}

/** Giá user nhập (theo priceUnit) → giá lưu trên phiếu (theo đơn vị khối lượng). */
export function documentUnitPrice(
  priceInInputUnit: number,
  priceUnit: PurchaseWeightUnit,
  quantityUnit: PurchaseWeightUnit,
): number {
  if (priceUnit === quantityUnit) return priceInInputUnit;
  if (priceUnit === "kg" && quantityUnit === "tấn") return priceInInputUnit * KG_PER_TON;
  return Math.round(priceInInputUnit / KG_PER_TON);
}

export function displayUnitPrice(
  storedDocumentPrice: number,
  priceUnit: PurchaseWeightUnit,
  quantityUnit: PurchaseWeightUnit,
): number {
  if (priceUnit === quantityUnit) return storedDocumentPrice;
  if (priceUnit === "kg" && quantityUnit === "tấn") return Math.round(storedDocumentPrice / KG_PER_TON);
  return storedDocumentPrice * KG_PER_TON;
}
