import { api } from "../../services/api-client";
import type { ShopCode } from "../shop/shop-store";

type Money = number;
export type AnnualBook = {
  year: number;
  periodFrom: string;
  periodTo: string;
  shopCode: ShopCode;
  shopName: string;
  customers: { id: number; name: string; phone: string | null; note: string | null;
    openingDebt: Money; sales: Money; receipts: Money; closingDebt: Money }[];
  sales: { id: number; customerId: number | null; customerName: string; code: string; date: string;
    seller: string | null; receiver: string | null; total: Money; paidAsOf: Money;
    openingPaid: Money; notes: string | null }[];
  saleItems: { documentId: number; product: string; category: string; quantity: number;
    unit: string; unitPrice: Money; amount: Money }[];
  payments: { documentId: number; date: string; amount: Money; method: string;
    collector: string | null; note: string | null }[];
  purchases: { id: number; supplier: string | null; code: string; date: string;
    total: Money; paidAsOf: Money; notes: string | null }[];
  purchaseItems: AnnualBook["saleItems"];
  receipts: Transaction[];
  expenses: Transaction[];
  stock: { product: string; category: string; quantityKg: number; value: Money | null }[];
  wages: { year: number; month: number; employee: string; days: number; basePay: Money; overtimePay: Money;
    bonusPay: Money; allowances: Money; deductions: Money; totalPay: Money }[];
  inventoryValue: Money | null;
  profit: Money | null;
  closed: boolean;
};

type Transaction = { code: string; date: string; category: string; amount: Money;
  counterparty: string; spentBy: string | null; description: string | null; reference: string };

export async function getAnnualBook(year: number, shopCode: ShopCode): Promise<AnnualBook> {
  return (await api.get<{ data: AnnualBook }>("/api/v1/reports/annual-book", { params: { year, shopCode }, timeout: 120000 })).data.data;
}
