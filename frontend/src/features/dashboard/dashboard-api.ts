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
  customerDebt: number | null;
  supplierDebt: number | null;
  cashExpenses: number | null;
  closing: { quantityKg: number; inventoryValue: number; profit: number } | null;
};

export type DashboardPoint = {
  day: string;
  revenue: string;
  cashCollected: string;
  cogs: string;
  grossProfit: string;
};

type Envelope<T> = { success: boolean; data: T };

export type YearEndBalance = {
  year: number;
  periodFrom: string;
  periodTo: string;
  inventoryKg: number;
  inventoryValue: number;
  customerDebt: number;
  supplierDebt: number;
  profit: number;
  closedAt: string;
};

export type YearPeriod = { year: number; periodFrom: string; periodTo: string; closed: boolean };

export async function getYearPeriod(year: number): Promise<YearPeriod> {
  return (await api.get<Envelope<YearPeriod>>("/api/v1/dashboard/year-period", { params: { year } })).data.data;
}

export async function getYearEndBalance(year: number): Promise<YearEndBalance | null> {
  return (await api.get<Envelope<YearEndBalance | null>>( "/api/v1/dashboard/year-end", { params: { year } })).data.data;
}

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

export async function closeDistribution(from: string, to: string, year: number) {
  return (await api.post<Envelope<NonNullable<DashboardSummary["closing"]>>>("/api/v1/dashboard/distribution-close", null, { params: { from, to, annual: true, year } })).data.data;
}
