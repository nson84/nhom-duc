import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

export type DocumentType = "PURCHASE" | "SALE" | "ADJUSTMENT_IN" | "ADJUSTMENT_OUT";

export type DocumentItem = {
  id: number;
  productId: number;
  categoryId: number | null;
  categoryName?: string | null;
  unitId: number | null;
  productCode: string;
  productName: string;
  unitName: string;
  billingUnit?: string | null;
  widthM?: number | null;
  heightM?: number | null;
  /** Khi bán m²: khối lượng trừ tồn (kg) = quantityBase. */
  quantityDocument: number;
  factorToBase: number;
  quantityBase: number;
  unitPrice: number;
  lineDiscount: number;
  lineTotal: number;
  costPerBase: number | null;
  costTotal: number | null;
  /** Giá vốn theo bảng giá nhập chuẩn đúng ngày phiếu; null khi chưa có lịch sử giá. */
  purchasePriceId?: number | null;
  standardCostPerBase?: number | null;
  standardCostTotal?: number | null;
  factoryPurchaseItemId?: number | null;
  factoryReferenceCostPerKg?: number | null;
};

export type Payment = {
  id: number;
  paymentDate: string;
  amount: number;
  paymentMethod: string | null;
  note: string | null;
  /** Tài khoản ghi thu. Trống với lần thu cũ không ghép được nhật ký. */
  collectedBy?: string | null;
};

export type StockDocument = {
  id: number;
  documentCode: string;
  documentType: DocumentType;
  status: "DRAFT" | "CONFIRMED" | "CANCELLED";
  documentDate: string;
  counterpartyName: string | null;
  customerId?: number | null;
  issuedBy: string | null;
  receivedBy: string | null;
  reason: string | null;
  subtotal: number;
  discountAmount: number;
  totalAmount: number;
  paidAmount: number;
  paymentStatus: "UNPAID" | "PARTIAL" | "PAID";
  paymentMethod: string | null;
  reversalOfDocumentId: number | null;
  reversedByDocumentId: number | null;
  confirmedAt: string | null;
  notes: string | null;
  items: DocumentItem[];
  payments: Payment[];
  internal?: boolean;
  internalPairDocumentId?: number | null;
  internalPairDocumentCode?: string | null;
  trashedAt?: string | null;
};

export type DocumentSummary = {
  id: number;
  documentCode: string;
  documentType: DocumentType;
  status: StockDocument["status"];
  documentDate: string;
  counterpartyName: string | null;
  customerId?: number | null;
  totalAmount: number;
  paymentStatus: StockDocument["paymentStatus"];
  internal?: boolean;
  internalPairDocumentId?: number | null;
  internalPairDocumentCode?: string | null;
  trashedAt?: string | null;
};

/** Bộ lọc danh sách phiếu bán / nhập. */
export type DocumentListFilter = "all" | "unpaid" | "partial" | "paid" | "draft" | "voided" | "trash";

export type DocumentInput = {
  documentType: "PURCHASE" | "SALE";
  documentDate: string;
  counterpartyName?: string;
  customerId?: number | null;
  issuedBy?: string;
  receivedBy?: string;
  reason?: string;
  discountAmount: number;
  paymentMethod?: string;
  notes?: string;
  idempotencyKey?: string;
  internal?: boolean;
  items: {
    productId: number;
    categoryId: number;
    unitId: number;
    quantityDocument: number;
    unitPrice: number;
    lineDiscount: number;
    billingUnit?: string;
    widthM?: number;
    heightM?: number;
    /** Bắt buộc khi billingUnit = m2 — kg trừ tồn. */
    stockQuantityKg?: number;
    factoryPurchaseItemId?: number;
  }[];
};

export type StockBalance = {
  productId: number;
  productCode: string;
  productName: string;
  categoryId: number;
  categoryName: string;
  baseUnitName: string;
  quantityBase: number;
  averageCostPerBase: number;
  stockValue: number;
  purchasedKg: number;
  purchasedValue: number;
  deliveredKg: number;
  latestCountScrapKg: number;
  latestCountLossKg: number;
};

export type InventoryCountResult = {
  adjustedItems: number;
  shortageKg: number;
  surplusKg: number;
  documentCodes: string[];
};

export type Movement = {
  id: number;
  movementAt: string;
  documentCode: string;
  movementType: "IN" | "OUT";
  quantityDeltaBase: number;
  unitCost: number;
};

export async function listDocuments(params: {
  type?: string;
  filter?: DocumentListFilter | string;
  status?: string;
  q?: string;
  from?: string;
  to?: string;
}) {
  return (await api.get<Envelope<DocumentSummary[]>>("/api/v1/stock-documents", { params })).data.data;
}

export async function trashDocument(id: number) {
  return (await api.post<Envelope<StockDocument>>(`/api/v1/stock-documents/${id}/trash`)).data.data;
}

export async function restoreDocument(id: number) {
  return (await api.post<Envelope<StockDocument>>(`/api/v1/stock-documents/${id}/restore`)).data.data;
}

export async function getDocument(id: number) {
  return (await api.get<Envelope<StockDocument>>(`/api/v1/stock-documents/${id}`)).data.data;
}

export async function saveDocument(id: number | undefined, input: DocumentInput, confirm = false) {
  const response = id
    ? await api.put<Envelope<StockDocument>>(`/api/v1/stock-documents/${id}`, input, { params: { confirm } })
    : await api.post<Envelope<StockDocument>>("/api/v1/stock-documents", input, { params: { confirm } });
  return response.data.data;
}

export async function documentAction(id: number, action: "confirm" | "cancel") {
  return (await api.post<Envelope<StockDocument>>(`/api/v1/stock-documents/${id}/${action}`)).data.data;
}

export async function reverseDocument(id: number, reason: string) {
  return (await api.post<Envelope<StockDocument>>(`/api/v1/stock-documents/${id}/reverse`, { reason })).data.data;
}

export async function addPayment(
  id: number,
  input: {
    amount: number;
    paymentMethod: string;
    paymentDate?: string;
    note?: string;
    idempotencyKey: string;
    collectedBy?: string;
    paidBy?: string;
  },
) {
  return (await api.post<Envelope<StockDocument>>(`/api/v1/stock-documents/${id}/payments`, input)).data.data;
}

export async function listBalances(params: { q?: string; onlyInStock?: boolean }) {
  return (await api.get<Envelope<StockBalance[]>>("/api/v1/stock-balances", { params })).data.data;
}

export async function saveInventoryCount(items: { productId: number; categoryId: number; countedQuantityKg: number; scrapKg?: number }[]) {
  return (await api.post<Envelope<InventoryCountResult>>("/api/v1/stock-balances/count", { items })).data.data;
}

export async function listMovements(productId: number) {
  return (await api.get<Envelope<Movement[]>>("/api/v1/stock-movements", { params: { productId } })).data.data;
}
