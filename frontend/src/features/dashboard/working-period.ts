import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { useShop } from "../shop/shop-store";
import { listYearPeriods, type YearPeriod } from "./dashboard-api";

export function useWorkingPeriodInfo(year: number, refresh?: string) {
  const shop = useShop();
  const [loaded, setLoaded] = useState<{ shop: string; periods: YearPeriod[] }>();
  useEffect(() => {
    let active = true;
    listYearPeriods().then(periods => { if (active) setLoaded({ shop, periods }); })
      .catch(() => { if (active) setLoaded(undefined); });
    return () => { active = false; };
  }, [shop, year, refresh]);
  const periods = loaded?.shop === shop ? loaded.periods : [];
  const index = periods.findIndex(period => period.year === year);
  const period = periods[index];
  return {
    name: period ? periodDisplayName(period, index) : "Kỳ làm việc đã chọn",
    label: period ? formatPeriodLabel(period, index) : "Kỳ làm việc đã chọn ở Tổng quan",
  };
}

function periodDisplayName(item: YearPeriod, index: number): string {
  return /^Năm \d{4}$/i.test(item.periodName.trim()) ? `Kỳ ${index + 1}` : item.periodName;
}

export function formatPeriodLabel(item: YearPeriod, index: number): string {
  const name = periodDisplayName(item, index);
  const from = dayjs(item.periodFrom).format("DD/MM/YYYY");
  const end = item.closed ? item.periodTo : item.plannedEnd;
  return `${name} · ${from}${end ? ` – ${dayjs(end).format("DD/MM/YYYY")}${item.closed ? " (đã chốt)" : " (dự kiến)"}` : " · Chưa đặt ngày kết thúc"}`;
}

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
