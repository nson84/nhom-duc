import { api } from "../../services/api-client";

export type DashboardSummary = {
  from: string;
  to: string;
  revenue: string;
  cashCollected: string;
  cogs: string;
  grossProfit: string;
  operatingExpenses: string;
  inventoryValue: string;
  payroll: string;
};

export type DashboardPoint = {
  day: string;
  revenue: string;
  cashCollected: string;
  cogs: string;
  grossProfit: string;
};

type Envelope<T> = { success: boolean; data: T };

export async function getSummary(from: string, to: string): Promise<DashboardSummary> {
  const response = await api.get<Envelope<DashboardSummary>>("/api/v1/dashboard/summary", {
    params: { from, to },
  });
  return response.data.data;
}

export async function getCharts(from: string, to: string): Promise<DashboardPoint[]> {
  const response = await api.get<Envelope<DashboardPoint[]>>("/api/v1/dashboard/charts", {
    params: { from, to },
  });
  return response.data.data;
}
