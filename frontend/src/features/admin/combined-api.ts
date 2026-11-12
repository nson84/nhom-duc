import { api } from "../../services/api-client";

export type CombinedBranch = {
  shopId: number; name: string; periodFrom: string; periodTo: string;
  salesRevenue: number; scrapRevenue: number;
  cashCollected: number; cashIn: number; cashOut: number; netCash: number;
  customerDebt: number; externalPurchases: number;
  materialCost: number | null; payrollCost: number; otherExpenses: number;
  estimatedProfit: number | null; inventoryCountReady: boolean;
};

export type CombinedYear = {
  year: number; distribution: CombinedBranch; manufacturing: CombinedBranch;
  revenue: number; cashCollected: number; cashIn: number; cashOut: number;
  netCash: number; customerDebt: number;
  externalPurchases: number; payrollCost: number; otherExpenses: number;
  estimatedProfit: number | null;
};

export async function getCombinedYear(year: number): Promise<CombinedYear> {
  return (await api.get<{ data: CombinedYear }>(`/api/v1/admin/combined/years/${year}`)).data.data;
}
