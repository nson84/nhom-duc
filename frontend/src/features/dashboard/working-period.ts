import dayjs from "dayjs";
import type { YearPeriod } from "./dashboard-api";

export function visiblePeriod(period: YearPeriod, month?: string): YearPeriod | null {
  if (!month || !period.started) return period;
  const start = dayjs(`${month}-01`);
  if (!start.isValid()) return period;
  const from = dayjs(period.periodFrom).isAfter(start) ? dayjs(period.periodFrom) : start;
  const endOfMonth = start.endOf("month");
  const to = dayjs(period.periodTo).isBefore(endOfMonth) ? dayjs(period.periodTo) : endOfMonth;
  if (from.isAfter(to, "day")) return null;
  return { ...period, periodFrom: from.format("YYYY-MM-DD"), periodTo: to.format("YYYY-MM-DD") };
}
