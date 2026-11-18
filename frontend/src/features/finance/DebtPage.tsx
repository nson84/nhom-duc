import { Card } from "antd";
import { useShop } from "../shop/shop-store";
import { useWorkingYear } from "../dashboard/working-year";
import { CarryoverDebtReport } from "./CarryoverDebtReport";

export function DebtPage() {
  const shop = useShop();
  const { year } = useWorkingYear(shop);
  return (
    <Card title={`Công nợ chuyển kỳ · ${year}`}>
      <CarryoverDebtReport key={`${shop}-${year}`} year={year} />
    </Card>
  );
}
