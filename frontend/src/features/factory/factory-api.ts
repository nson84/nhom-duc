import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T };

export type ScrapPayment = {
  id: number;
  paymentDate: string;
  amount: number;
  paymentMethod: string;
  collectedBy: string;
};

export type ScrapSale = {
  id: number;
  saleDate: string;
  buyerName: string;
  sellerName: string;
  quantityKg: number;
  unitPrice: number;
  totalAmount: number;
  paidAmount: number;
  status: "CONFIRMED" | "VOID";
  notes: string | null;
  payments: ScrapPayment[];
};

export type YearCountInput = {
  periodFrom: string;
  periodTo: string;
  openingKg: number;
  openingValue: number;
  rawKg: number;
  rawValue: number;
  scrapKg: number;
  scrapValue: number;
  workInProgressKg: number;
  workInProgressValue: number;
  finishedKg: number;
  finishedValue: number;
  notes?: string;
};

export type YearCount = YearCountInput & {
  year: number;
  status: "MISSING" | "DRAFT" | "CLOSED";
  closedAt: string | null;
};

export type RawCount = {
  categoryId: number;
  categoryName: string;
  quantityKg: number;
  valueAmount: number;
  layers: { purchaseItemId: number; documentCode: string; documentDate: string;
    quantityKg: number; unitCostPerKg: number; valueAmount: number }[];
};

export type PurchaseCostOption = { purchaseItemId: number; categoryId: number; categoryName: string;
  documentCode: string; documentDate: string; quantityKg: number; costPerKg: number };

export async function listPurchaseCosts(): Promise<PurchaseCostOption[]> {
  return (await api.get<Envelope<PurchaseCostOption[]>>("/api/v1/factory/purchase-costs")).data.data;
}

export type SaleCostReport = { quantityKg: number; referenceValue: number; salesAmount: number;
  rows: { itemId: number; documentId: number; documentCode: string; documentDate: string; customerName: string;
    productName: string; categoryId: number; categoryName: string; quantityKg: number; referenceCostPerKg: number | null;
    referenceValue: number | null; salesAmount: number; quantityDocument: number; unitName: string; unitPrice: number }[] };

export async function getSaleCostReport(year: number, categoryId?: number, costPerKg?: number): Promise<SaleCostReport> {
  return (await api.get<Envelope<SaleCostReport>>(`/api/v1/factory/years/${year}/sale-costs`,
    { params: { categoryId, costPerKg } })).data.data;
}

export async function saveInventory(year: number, count: YearCountInput,
  items: { categoryId: number; quantityKg: number }[], close = false): Promise<YearCount> {
  const path = `/api/v1/factory/years/${year}/${close ? "finalize" : "inventory-count"}`;
  const response = close ? await api.post<Envelope<YearCount>>(path, { count, items })
    : await api.put<Envelope<YearCount>>(path, { count, items });
  return response.data.data;
}

export async function getRawCounts(year: number): Promise<RawCount[]> {
  return (await api.get<Envelope<RawCount[]>>(`/api/v1/factory/years/${year}/raw-counts`)).data.data;
}

export async function saveRawCounts(year: number, items: { categoryId: number; quantityKg: number }[]): Promise<RawCount[]> {
  return (await api.put<Envelope<RawCount[]>>(`/api/v1/factory/years/${year}/raw-counts`, { items })).data.data;
}

export type YearReport = {
  year: number;
  countStatus: YearCount["status"];
  openingKg: number;
  purchasedKg: number;
  finishedSoldKg: number;
  scrapSoldKg: number;
  closingKg: number | null;
  initialDiscrepancyKg: number;
  estimatedLossKg: number | null;
  openingValue: number;
  purchaseValue: number;
  closingValue: number | null;
  materialCost: number | null;
  finishedRevenue: number;
  scrapRevenue: number;
  payrollCost: number;
  otherExpenses: number;
  estimatedProfit: number | null;
  finishedCashCollected: number;
  scrapCashCollected: number;
};

export type CustomerYearRow = {
  documentId: number;
  documentCode: string;
  documentDate: string;
  productName: string;
  unitName: string;
  quantityKg: number;
  unitPrice: number;
  lineTotal: number;
  sellerName: string | null;
  documentTotal: number;
  documentPaid: number;
};

export type CustomerYear = {
  customerId: number;
  customerName: string;
  year: number;
  quantityKg: number;
  revenue: number;
  paidAmount: number;
  outstandingAmount: number;
  documentCount: number;
  rows: CustomerYearRow[];
};

export type SettlementInvoice = {
  documentId: number;
  documentCode: string;
  documentDate: string;
  products: string;
  sellerName: string | null;
  quantityKg: number;
  totalAmount: number;
  paidAsOf: number;
  remainingAsOf: number;
};

export type CustomerSettlement = {
  customerId: number;
  customerName: string;
  openingDebt: number;
  periodSales: number;
  periodReceipts: number;
  closingDebt: number;
  invoices: SettlementInvoice[];
};

export type SettlementReport = {
  year: number;
  month: number | null;
  from: string;
  toExclusive: string;
  totalOpeningDebt: number;
  totalPeriodSales: number;
  totalPeriodReceipts: number;
  totalClosingDebt: number;
  unlinkedSalesCount: number;
  unlinkedSalesValue: number;
  customers: CustomerSettlement[];
};

export async function getYearReport(year: number) {
  return (await api.get<Envelope<YearReport>>(`/api/v1/factory/years/${year}/report`)).data.data;
}

export async function getYearCount(year: number) {
  return (await api.get<Envelope<YearCount>>(`/api/v1/factory/years/${year}/count`)).data.data;
}

export async function saveYearCount(year: number, input: YearCountInput) {
  return (await api.put<Envelope<YearCount>>(`/api/v1/factory/years/${year}/count`, input)).data.data;
}

export async function closeYear(year: number) {
  return (await api.post<Envelope<YearCount>>(`/api/v1/factory/years/${year}/close`)).data.data;
}

export async function listScrapSales(year: number) {
  return (await api.get<Envelope<ScrapSale[]>>(`/api/v1/factory/years/${year}/scrap-sales`)).data.data;
}

export async function createScrapSale(input: {
  saleDate: string;
  buyerName: string;
  sellerName: string;
  quantityKg: number;
  unitPrice: number;
  notes?: string;
  idempotencyKey?: string;
}) {
  return (await api.post<Envelope<ScrapSale>>("/api/v1/factory/scrap-sales", input)).data.data;
}

export async function payScrapSale(id: number, input: {
  paymentDate: string;
  amount: number;
  paymentMethod: string;
  collectedBy: string;
  idempotencyKey?: string;
}) {
  return (await api.post<Envelope<ScrapSale>>(`/api/v1/factory/scrap-sales/${id}/payments`, input)).data.data;
}

export async function voidScrapSale(id: number, reason: string) {
  return (await api.post<Envelope<ScrapSale>>(`/api/v1/factory/scrap-sales/${id}/void`, { reason })).data.data;
}

export async function getCustomerYear(customerId: number, year: number) {
  return (await api.get<Envelope<CustomerYear>>(`/api/v1/factory/customers/${customerId}/years/${year}`)).data.data;
}

export async function getSettlement(year: number, monthKey?: string) {
  const [monthYear, month] = monthKey?.split("-").map(Number) ?? [];
  return (await api.get<Envelope<SettlementReport>>(`/api/v1/factory/years/${year}/settlement`, { params: { month, monthYear } })).data.data;
}
