import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

export type Category = { id: number; name: string; description?: string | null; active: boolean };

export type ProductUnit = {
  id: number;
  unitName: string;
  factorToBase: number;
  base: boolean;
  defaultSelling: boolean;
  active: boolean;
};

export type Product = {
  id: number;
  productCode: string;
  name: string;
  categoryId: number;
  categoryName: string;
  specification?: string | null;
  baseUnitName: string;
  referencePurchasePrice: number;
  /** Ngày bắt đầu áp dụng giá nhập đang dùng. Null khi chưa có lịch sử giá. */
  purchasePriceEffectiveFrom?: string | null;
  /** Giá bán / kg (từ giá bán / tấn ÷ 1000). */
  defaultSellingPrice: number;
  /** Đồng bộ nội bộ từ giá tấn (đ/tạ = đ/kg × 100); UI không nhập riêng. */
  defaultSellingPriceTa: number;
  imagePath?: string | null;
  description?: string | null;
  active: boolean;
  quantityBase: number;
  averageCostPerBase: number;
  units: ProductUnit[];
};

/** Một mốc giá nhập trong lịch sử. effectiveTo null nghĩa là mốc xa nhất đã ghi. */
export type PurchasePrice = {
  id: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  pricePerBase: number;
  note: string | null;
  /** True khi đây là mốc đang áp dụng hôm nay. */
  open: boolean;
  /** True khi mốc được đặt cho một ngày trong tương lai. */
  future: boolean;
};

export type ProductInput = {
  productCode: string;
  name: string;
  categoryId: number;
  specification?: string;
  baseUnitName: string;
  referencePurchasePrice: number;
  /** Giá bán / kg (= đ/tấn ÷ 1000). */
  defaultSellingPrice: number;
  /** Đồng bộ từ giá tấn; UI không nhập riêng. */
  defaultSellingPriceTa: number;
  imagePath?: string;
  description?: string;
  active: boolean;
};

export type UnitInput = { unitName: string; factorToBase: number; defaultSelling: boolean; active: boolean };

export async function listCategories(scope?: "all"): Promise<Category[]> {
  return (await api.get<Envelope<Category[]>>("/api/v1/categories", { params: scope ? { scope } : undefined })).data.data;
}

export async function saveCategory(input: { id?: number; name: string; description?: string; active: boolean }) {
  const { id, ...body } = input;
  const response = id
    ? await api.put<Envelope<Category>>(`/api/v1/categories/${id}`, body)
    : await api.post<Envelope<Category>>("/api/v1/categories", body);
  return response.data.data;
}

export async function listProducts(params: {
  q?: string;
  categoryId?: number;
  active?: boolean;
  /** Lấy giá nhập có hiệu lực tại ngày này (YYYY-MM-DD). Bỏ trống = hôm nay. */
  asOf?: string;
  scope?: "all";
}): Promise<Product[]> {
  return (await api.get<Envelope<Product[]>>("/api/v1/products", { params })).data.data;
}

export async function getNextProductCode(): Promise<String> {
  return (await api.get<Envelope<string>>("/api/v1/products/next-code")).data.data;
}

export async function saveProduct(id: number | undefined, input: ProductInput): Promise<Product> {
  const response = id
    ? await api.put<Envelope<Product>>(`/api/v1/products/${id}`, input)
    : await api.post<Envelope<Product>>("/api/v1/products", input);
  return response.data.data;
}

export async function saveUnit(productId: number, unitId: number | undefined, input: UnitInput): Promise<Product> {
  const response = unitId
    ? await api.put<Envelope<Product>>(`/api/v1/products/${productId}/units/${unitId}`, input)
    : await api.post<Envelope<Product>>(`/api/v1/products/${productId}/units`, input);
  return response.data.data;
}

export async function listPurchasePrices(productId: number): Promise<PurchasePrice[]> {
  return (await api.get<Envelope<PurchasePrice[]>>(`/api/v1/products/${productId}/purchase-prices`)).data.data;
}

/** Ghi một mốc giá nhập mới. Bỏ trống ngày hiệu lực thì hiệu lực từ hôm nay. */
export async function savePurchasePrice(
  productId: number,
  input: { pricePerBase: number; effectiveFrom?: string; note?: string },
): Promise<PurchasePrice[]> {
  return (await api.post<Envelope<PurchasePrice[]>>(`/api/v1/products/${productId}/purchase-prices`, input)).data.data;
}
