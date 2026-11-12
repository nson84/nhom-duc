import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

/** Loại nhôm: tên, trạng thái và bộ giá (API lưu theo đ/kg). */
export type Category = {
  id: number;
  name: string;
  description?: string | null;
  active: boolean;
  /** Giá nhập đang áp dụng (đ/kg). */
  referencePurchasePrice: number;
  /** Ngày bắt đầu áp dụng giá nhập đang dùng. Null khi chưa có lịch sử giá. */
  purchasePriceEffectiveFrom?: string | null;
  /** Giá bán gợi ý / kg. */
  defaultSellingPrice: number;
  /** Giá bán gợi ý / tạ. */
  defaultSellingPriceTa: number;
  /** Giá bán gợi ý / tấn. */
  defaultSellingPriceTon: number;
  /** Giá bán gợi ý / m². */
  defaultSellingPriceM2: number;
};

export type CategoryInput = {
  id?: number;
  name: string;
  description?: string;
  active: boolean;
  /** đ/kg */
  referencePurchasePrice: number;
  /** đ/kg */
  defaultSellingPrice: number;
  defaultSellingPriceTa: number;
  defaultSellingPriceTon: number;
  defaultSellingPriceM2: number;
};

export type ProductUnit = {
  id: number;
  unitName: string;
  factorToBase: number;
  base: boolean;
  defaultSelling: boolean;
  active: boolean;
};

/** Gợi ý đơn vị tính tiền trên phiếu bán: WEIGHT=kg, AREA=m². Tồn luôn kg. */
export type SellMode = "WEIGHT" | "AREA";

/** Sản phẩm chỉ có mã, tên, ảnh, trạng thái; giá nằm ở loại nhôm. */
export type Product = {
  id: number;
  productCode: string;
  name: string;
  categoryId: number | null;
  baseUnitName: string;
  imagePath?: string | null;
  description?: string | null;
  active: boolean;
  /** Tổng tồn kg gộp mọi loại nhôm. */
  quantityBase: number;
  units: ProductUnit[];
};

/** Một mốc giá nhập trong lịch sử (của loại nhôm). effectiveTo null nghĩa là mốc xa nhất đã ghi. */
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
  categoryId?: number | null;
  imagePath?: string;
  description?: string;
  active: boolean;
};

export type UnitInput = { unitName: string; factorToBase: number; defaultSelling: boolean; active: boolean };

export async function listCategories(scope?: "all"): Promise<Category[]> {
  return (await api.get<Envelope<Category[]>>("/api/v1/categories", { params: scope ? { scope } : undefined })).data.data;
}

export async function saveCategory(input: CategoryInput) {
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
  /** Ngày tham chiếu (YYYY-MM-DD). Bỏ trống = hôm nay. */
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

export async function listPurchasePrices(categoryId: number): Promise<PurchasePrice[]> {
  return (await api.get<Envelope<PurchasePrice[]>>(`/api/v1/categories/${categoryId}/purchase-prices`)).data.data;
}

/** Ghi một mốc giá nhập mới (đ/kg). Bỏ trống ngày hiệu lực thì hiệu lực từ hôm nay. */
export async function savePurchasePrice(
  categoryId: number,
  input: { pricePerBase: number; effectiveFrom?: string; note?: string },
): Promise<PurchasePrice[]> {
  return (await api.post<Envelope<PurchasePrice[]>>(`/api/v1/categories/${categoryId}/purchase-prices`, input)).data.data;
}

/** Kết quả đẩy danh mục (SP + loại) sang shop kia — không đụng tồn. */
export type CatalogSyncResult = {
  targetShopCode: string;
  targetShopName: string;
  categoriesCreated: number;
  categoriesUpdated: number;
  productsCreated: number;
  productsUpdated: number;
};

/** Đồng bộ SP + loại nhôm từ shop hiện tại sang shop còn lại. */
export async function syncCatalogToOtherShop(): Promise<CatalogSyncResult> {
  return (await api.post<Envelope<CatalogSyncResult>>("/api/v1/catalog/sync-to-other-shop")).data.data;
}
