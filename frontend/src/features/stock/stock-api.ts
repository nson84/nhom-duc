import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

export type DocumentType = "PURCHASE" | "SALE" | "ADJUSTMENT_IN" | "ADJUSTMENT_OUT";

export type DocumentItem = {
  id: number;
  productId: number;
  unitId: number | null;
  productCode: string;
  productName: string;
  unitName: string;
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
};

export type Payment = {
  id: number;
  paymentDate: string;
  amount: number;
  paymentMethod: string | null;
  note: string | null;
};

export type StockDocument = {
  id: number;
  documentCode: string;
  documentType: DocumentType;
  status: "DRAFT" | "CONFIRMED" | "CANCELLED";
  documentDate: string;
  counterpartyName: string | null;
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
};

export type DocumentSummary = {
  id: number;
  documentCode: string;
  documentType: DocumentType;
  status: StockDocument["status"];
  documentDate: string;
  counterpartyName: string | null;
  totalAmount: number;
  paymentStatus: StockDocument["paymentStatus"];
  internal?: boolean;
  internalPairDocumentId?: number | null;
  internalPairDocumentCode?: string | null;
};

export type DocumentInput = {
  documentType: "PURCHASE" | "SALE";
  documentDate: string;
  counterpartyName?: string;
  issuedBy?: string;
  receivedBy?: string;
  reason?: string;
  discountAmount: number;
  paymentMethod?: string;
  notes?: string;
  idempotencyKey?: string;
  internal?: boolean;
  items: { productId: number; unitId: number; quantityDocument: number; unitPrice: number; lineDiscount: number }[];
};

export type StockBalance = {
  productId: number;
  productCode: string;
  productName: string;
  categoryName: string;
  baseUnitName: string;
  quantityBase: number;
  averageCostPerBase: number;
  stockValue: number;
};

export type Movement = {
  id: number;
  movementAt: string;
  documentCode: string;
  movementType: "IN" | "OUT";
  quantityDeltaBase: number;
  unitCost: number;
};

export async function listDocuments(params: { type?: string; status?: string; q?: string; from?: string; to?: string }) {
  return (await api.get<Envelope<DocumentSummary[]>>("/api/v1/stock-documents", { params })).data.data;
}

export async function getDocument(id: number) {
  return (await api.get<Envelope<StockDocument>>(`/api/v1/stock-documents/${id}`)).data.data;
}

export async function saveDocument(id: number | undefined, input: DocumentInput) {
  const response = id
    ? await api.put<Envelope<StockDocument>>(`/api/v1/stock-documents/${id}`, input)
    : await api.post<Envelope<StockDocument>>("/api/v1/stock-documents", input);
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
  },
) {
  return (await api.post<Envelope<StockDocument>>(`/api/v1/stock-documents/${id}/payments`, input)).data.data;
}

export async function listBalances(params: { q?: string; onlyInStock?: boolean }) {
  return (await api.get<Envelope<StockBalance[]>>("/api/v1/stock-balances", { params })).data.data;
}

export async function listMovements(productId: number) {
  return (await api.get<Envelope<Movement[]>>("/api/v1/stock-movements", { params: { productId } })).data.data;
}
