import { Card } from "antd";
import { useShop } from "../shop/shop-store";
import { useWorkingYear } from "../dashboard/working-year";
import { useWorkingPeriodInfo } from "../dashboard/working-period";
import { CarryoverDebtReport } from "./CarryoverDebtReport";

export function DebtPage() {
  const shop = useShop();
  const { year } = useWorkingYear(shop);
  const periodInfo = useWorkingPeriodInfo(year);
  return (
    <Card title={`Công nợ chuyển kỳ · ${periodInfo.name}`}>
      <CarryoverDebtReport key={`${shop}-${year}`} year={year} />
    </Card>
  );
}
