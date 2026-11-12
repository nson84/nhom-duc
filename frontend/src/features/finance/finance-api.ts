import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

export type FinanceTransaction = {
  id: number;
  transactionCode: string;
  transactionDate: string;
  transactionType: "INCOME" | "EXPENSE";
  category:
    | "SALE_RECEIPT"
    | "PURCHASE_PAYMENT"
    | "OPERATING_EXPENSE" | "MAINTENANCE"
    | "PAYROLL"
    | "OTHER_INCOME"
    | "OTHER_EXPENSE";
  amount: number;
  description: string | null;
  spentBy?: string | null;
  referenceType: string | null;
  referenceId: number | null;
  status: "CONFIRMED" | "VOID";
  createdAt: string;
  internal?: boolean;
  shopCode?: string;
};

export type FinanceSummary = {
  from: string;
  to: string;
  totalIncome: number;
  saleReceiptIncome: number;
  otherIncome: number;
  totalExpense: number;
  operatingExpense: number;
  payrollExpense: number;
  otherExpense: number;
  purchasePaymentOutflow: number;
  netCashflow: number;
  internalIncome?: number;
  internalExpense?: number;
};

export type DailyRevenueRow = {
  day: string;
  saleCount: number;
  revenue: number;
  cogs: number;
  grossProfit: number;
  cashCollected: number;
  operatingExpense: number;
  standardCogs: number;
  standardGrossProfit: number;
  /** Giá vốn thực tế − giá vốn chuẩn. Dương: nhập đắt hơn giá chuẩn. */
  priceVariance: number;
};

export type ProfitReport = {
  from: string;
  to: string;
  revenue: number;
  cogs: number;
  grossProfit: number;
  operatingExpenses: number;
  netProfit: number;
  cashCollected: number;
  uncollectedRevenue: number;
  purchaseStockValue: number;
  purchasePaymentOutflow: number;
  standardCogs: number;
  standardGrossProfit: number;
  standardNetProfit: number;
  priceVariance: number;
  /** False khi còn dòng bán chưa xác định được giá chuẩn (thiếu lịch sử giá nhập). */
  standardCostComplete: boolean;
  dailyRows: DailyRevenueRow[];
};

export type ProductSalesRow = {
  productId: number;
  productCode: string;
  productName: string;
  categoryName: string;
  baseUnitName: string;
  quantityBaseSold: number;
  revenue: number;
  cogs: number;
  grossProfit: number;
  standardCogs: number;
  standardGrossProfit: number;
  priceVariance: number;
};

/** Soát chênh lệch giữa giá nhập chuẩn và giá nhập thực tế đã ghi trên phiếu nhập. */
export type PurchasePriceVarianceRow = {
  productId: number;
  productCode: string;
  productName: string;
  categoryName: string;
  baseUnitName: string;
  effectiveFrom: string | null;
  standardPricePerBase: number;
  actualQuantityBase: number;
  actualPurchaseValue: number;
  actualAverageCostPerBase: number;
  varianceValue: number;
  variancePercent: number | null;
};

export type InventoryReportRow = {
  productId: number;
  productCode: string;
  productName: string;
  categoryName: string;
  baseUnitName: string;
  openingQuantityBase: number;
  inQuantityBase: number;
  outQuantityBase: number;
  closingQuantityBase: number;
  averageCostPerBase: number;
  inventoryValue: number;
};

export type PayrollReportRow = {
  periodId: number;
  year: number;
  month: number;
  status: "OPEN" | "CLOSED";
  employeeCount: number;
  totalCreditedDays: number;
  totalBasePay: number;
  totalOvertimePay: number;
  totalAllowances: number;
  totalDeductions: number;
  grandTotalPay: number;
  paidAmount: number;
};

export async function listFinanceTransactions(params: {
  from?: string;
  to?: string;
  type?: string;
  category?: string;
  status?: string;
  q?: string;
  scope?: "all";
}): Promise<FinanceTransaction[]> {
  return (await api.get<Envelope<FinanceTransaction[]>>("/api/v1/finance/transactions", { params })).data.data;
}

export async function getFinanceSummary(from: string, to: string, scope?: "all"): Promise<FinanceSummary> {
  return (await api.get<Envelope<FinanceSummary>>("/api/v1/finance/summary", { params: { from, to, scope } })).data.data;
}

export async function createFinanceTransaction(input: {
  transactionDate?: string;
  transactionType: "INCOME" | "EXPENSE";
  category: string;
  amount: number;
  description?: string;
  spentBy?: string;
  idempotencyKey: string;
}): Promise<FinanceTransaction> {
  return (await api.post<Envelope<FinanceTransaction>>("/api/v1/finance/transactions", input)).data.data;
}

export async function voidFinanceTransaction(id: number, reason: string): Promise<FinanceTransaction> {
  return (await api.post<Envelope<FinanceTransaction>>(`/api/v1/finance/transactions/${id}/void`, { reason })).data.data;
}

export async function getProfitReport(from: string, to: string, scope?: "all"): Promise<ProfitReport> {
  return (await api.get<Envelope<ProfitReport>>("/api/v1/reports/profit", { params: { from, to, scope } })).data.data;
}

export async function getSalesByProductReport(params: {
  from: string;
  to: string;
  productId?: number;
  categoryId?: number;
  scope?: "all";
}): Promise<ProductSalesRow[]> {
  return (await api.get<Envelope<ProductSalesRow[]>>("/api/v1/reports/sales-by-product", { params })).data.data;
}

export async function getInventoryReport(params: {
  from: string;
  to: string;
  categoryId?: number;
  scope?: "all";
}): Promise<InventoryReportRow[]> {
  return (await api.get<Envelope<InventoryReportRow[]>>("/api/v1/reports/inventory", { params })).data.data;
}

export async function getPayrollReport(year?: number, scope?: "all"): Promise<PayrollReportRow[]> {
  return (await api.get<Envelope<PayrollReportRow[]>>("/api/v1/reports/payroll", { params: { year, scope } })).data.data;
}

export async function getPriceVarianceReport(params: {
  from: string;
  to: string;
  categoryId?: number;
  scope?: "all";
}): Promise<PurchasePriceVarianceRow[]> {
  return (await api.get<Envelope<PurchasePriceVarianceRow[]>>("/api/v1/reports/price-variance", { params })).data.data;
}

export async function downloadReportCsv(params: {
  type: "profit" | "sales-by-product" | "inventory" | "payroll" | "price-variance";
  from?: string;
  to?: string;
  productId?: number;
  categoryId?: number;
  year?: number;
  scope?: "all";
}) {
  const response = await api.get<Blob>("/api/v1/reports/export", {
    params,
    responseType: "blob",
  });
  const url = URL.createObjectURL(response.data);
  const link = document.createElement("a");
  link.href = url;
  link.download = `bao-cao-${params.type}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
