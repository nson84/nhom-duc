import { Alert, Card, Space, Statistic } from "antd";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { ResponsiveTable } from "../../components/PagedTable";
import { api } from "../../services/api-client";
import { toApiError } from "../../services/error-handler";
import { useShop } from "../shop/shop-store";

type Opening = {
  from: string; sourceTo: string | null; source: "MONTH" | "YEAR" | null; available: boolean;
  rows: { categoryId: number; category: string; rawKg: number; scrapKg: number | null }[];
  totalRawKg: number; totalScrapKg: number | null; message: string;
};
const kg = (value: number) => new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(value) + " kg";

export function OpeningInventory({ year, month }: { year: number; month?: string | null }) {
  const shop = useShop();
  const [opening, setOpening] = useState<Opening>();
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setOpening(undefined);
    setError("");
    api.get<{ data: Opening }>("/api/v1/dashboard/opening-inventory", { params: { year, month }, signal: controller.signal })
      .then(({ data }) => { if (!controller.signal.aborted) setOpening(data.data); })
      .catch(reason => { if (!controller.signal.aborted) setError(toApiError(reason).message); });
    return () => controller.abort();
  }, [shop, year, month]);
  return <Card size="small" title={month ? "Tồn đầu tháng chuyển sang theo từng loại" : "Tồn đầu kỳ chuyển sang theo từng loại"} style={{ marginBottom: 16 }}>
    {error && <Alert type="error" showIcon message={error} />}
    {opening && <>
      <Alert type="info" showIcon message={opening.message} />
      {opening.available && <>
        <p>Chuyển từ bản chốt {opening.source === "YEAR" ? "năm" : "tháng"} ngày {dayjs(opening.sourceTo).format("DD/MM/YYYY")} sang ngày {dayjs(opening.from).format("DD/MM/YYYY")}.</p>
        <ResponsiveTable rowKey="categoryId" pagination={false} dataSource={opening.rows} columns={[
          { title: "Loại nhôm", dataIndex: "category" },
          { title: "Nguyên liệu đầu kỳ", dataIndex: "rawKg", render: kg },
          ...(opening.source === "MONTH" ? [{ title: "Vụn nhôm đầu kỳ", dataIndex: "scrapKg", render: kg }] : []),
        ]} />
        <Space wrap><Statistic title="Tổng nguyên liệu chuyển sang" value={kg(opening.totalRawKg)} />
          {opening.source === "MONTH" && <Statistic title="Tổng vụn chuyển sang" value={kg(opening.totalScrapKg ?? 0)} />}</Space>
      </>}
    </>}
  </Card>;
}
