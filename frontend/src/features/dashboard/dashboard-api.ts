import { api } from "../../services/api-client";

export type DashboardSummary = {
  from: string;
  to: string;
  revenue: string;
  cashCollected: string;
  purchaseAmount: string;
  deliveredKg: string;
  purchaseKg: string;
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

export type YearPeriod = { year: number; periodName: string; periodFrom: string; periodTo: string; closed: boolean; started: boolean; endSaved: boolean; plannedEnd: string | null };

export async function getYearPeriod(year: number): Promise<YearPeriod> {
  return (await api.get<Envelope<YearPeriod>>("/api/v1/dashboard/year-period", { params: { year } })).data.data;
}

export async function getMonthClosed(month: string): Promise<boolean> {
  return (await api.get<Envelope<boolean>>("/api/v1/dashboard/month-close", { params: { month } })).data.data;
}

export type MonthStatus = "NOT_OPEN" | "OPEN" | "CLOSED";

export async function getMonthStatus(month: string): Promise<MonthStatus> {
  return (await api.get<Envelope<MonthStatus>>("/api/v1/dashboard/month-status", { params: { month } })).data.data;
}

export async function openMonth(year: number, month: string): Promise<MonthStatus> {
  return (await api.post<Envelope<MonthStatus>>("/api/v1/dashboard/month-open", null, { params: { year, month } })).data.data;
}

export async function closeMonth(year: number, month: string): Promise<boolean> {
  return (await api.post<Envelope<boolean>>("/api/v1/dashboard/month-close", null, { params: { year, month } })).data.data;
}

export async function reopenMonth(year: number, month: string): Promise<boolean> {
  return (await api.post<Envelope<boolean>>("/api/v1/dashboard/month-reopen", null, { params: { year, month } })).data.data;
}

export async function saveYearPeriod(year: number, from: string, to: string): Promise<YearPeriod> {
  return (await api.post<Envelope<YearPeriod>>("/api/v1/dashboard/year-period", null, { params: { year, from, to } })).data.data;
}

export async function saveYearStart(year: number, from: string, name: string): Promise<YearPeriod> {
  return (await api.post<Envelope<YearPeriod>>("/api/v1/dashboard/year-period/start", null, { params: { year, from, name } })).data.data;
}

export async function deleteYearPeriod(year: number, from: string): Promise<YearPeriod> {
  return (await api.delete<Envelope<YearPeriod>>("/api/v1/dashboard/year-period", { params: { year, from } })).data.data;
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
