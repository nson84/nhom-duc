import { Alert, Space } from "antd";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { PagedTable } from "../../components/PagedTable";
import { api } from "../../services/api-client";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";

type Invoice = {
  id: number;
  code: string;
  type: "SALE" | "PURCHASE";
  party: string | null;
  date: string;
  openingDebt: number;
  paidInPeriod: number;
  remainingDebt: number;
  payments: { date: string; amount: number }[];
};
type Report = { from: string; to: string; invoices: Invoice[] };
const date = (value: string) => dayjs(value).format("DD/MM/YYYY");

export function CarryoverDebtReport({ year }: { year: number }) {
  const [report, setReport] = useState<Report>();
  const [error, setError] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setReport(undefined);
    setError("");
    api.get<{ data: Report }>("/api/v1/reports/carryover-debt", { params: { year }, signal: controller.signal })
      .then((response) => { if (!controller.signal.aborted) setReport(response.data.data); })
      .catch((caught) => { if (!controller.signal.aborted) setError(toApiError(caught).message); });
    return () => controller.abort();
  }, [year]);

  return <Space direction="vertical" style={{ width: "100%" }}>
    <Alert showIcon type="info" message={report ? `Cả kỳ từ ${date(report.from)} đến ${date(report.to)}` : "Công nợ của cả kỳ năm đang chọn"}
      description="Theo dõi phiếu lập trước ngày đầu kỳ còn nợ tại mốc đó. Các lần thu hoặc trả bên dưới tính theo ngày thanh toán trong kỳ." />
    {error && <Alert type="error" showIcon message={error} />}
    {(["SALE", "PURCHASE"] as const).map((type) => {
      const rows = report?.invoices.filter((row) => row.type === type) ?? [];
      const paidLabel = type === "SALE" ? "Đã thu nợ cũ trong kỳ" : "Đã trả nợ cũ trong kỳ";
      const total = (key: "openingDebt" | "paidInPeriod" | "remainingDebt") =>
        formatMoney(rows.reduce((sum, row) => sum + row[key], 0));
      return <section key={type}>
        <h3>{type === "SALE" ? "Khách còn nợ" : "Còn nợ nhà cung cấp"}</h3>
        {report && <p>Nợ đầu kỳ <b>{total("openingDebt")}</b> · {paidLabel} <b>{total("paidInPeriod")}</b> · Nợ cũ cuối kỳ <b>{total("remainingDebt")}</b></p>}
        <PagedTable<Invoice> rowKey="id" dataSource={rows} loading={!report && !error} size="small" columns={[
          { title: "Số phiếu", dataIndex: "code" },
          { title: "Ngày lập", dataIndex: "date", render: date },
          { title: type === "SALE" ? "Khách" : "Nhà cung cấp", dataIndex: "party", render: (value) => value || "—" },
          { title: "Nợ đầu kỳ", dataIndex: "openingDebt", render: formatMoney },
          { title: paidLabel, dataIndex: "paidInPeriod", render: formatMoney },
          { title: "Nợ cũ cuối kỳ", dataIndex: "remainingDebt", render: formatMoney },
          { title: "Ngày thanh toán và số tiền", render: (_, row) => row.payments.length
            ? row.payments.map((payment, i) => <div key={i}>{dayjs(payment.date).format("DD/MM/YYYY HH:mm")} · {formatMoney(payment.amount)}</div>)
            : "—" },
        ]} />
      </section>;
    })}
  </Space>;
}
