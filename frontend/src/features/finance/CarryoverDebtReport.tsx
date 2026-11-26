import { Alert, Button, Card, Empty, Spin, Typography, message } from "antd";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { AppPagination, PAGE_SIZE } from "../../components/PagedTable";
import { api } from "../../services/api-client";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { PaymentModal } from "../stock/DocumentsPage";
import { addPayment, getCarryoverDocument, type StockDocument } from "../stock/stock-api";
import { useCanWrite } from "../auth/auth-store";

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

function DebtInvoices({ rows, type, canWrite, onPay }: {
  rows: Invoice[];
  type: Invoice["type"];
  canWrite: boolean;
  onPay: (id: number) => void;
}) {
  const [page, setPage] = useState(1);
  const current = Math.min(page, Math.max(1, Math.ceil(rows.length / PAGE_SIZE)));
  const paidLabel = type === "SALE" ? "Đã thu" : "Đã trả";
  if (!rows.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có công nợ chuyển kỳ" />;

  return <>
    {rows.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE).map(row => {
      const payments = new Map<string, number>();
      for (const payment of row.payments) {
        const day = dayjs(payment.date).format("YYYY-MM-DD");
        payments.set(day, (payments.get(day) ?? 0) + Number(payment.amount));
      }
      return <Card size="small" key={row.id} style={{ marginBottom: 8, overflowWrap: "anywhere" }}>
        <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", justifyContent: "space-between", gap: 8 }}>
          <div style={{ minWidth: 0 }}>
            <Typography.Text strong>{row.party || "—"}</Typography.Text>
            <div><Typography.Text type="secondary">{row.code} · {date(row.date)}</Typography.Text></div>
          </div>
          {canWrite && row.remainingDebt > 0 && <Button onClick={() => onPay(row.id)}>
            {type === "SALE" ? "Thu nợ" : "Trả nhà cung cấp"}
          </Button>}
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 140px), 1fr))", gap: 8, marginTop: 8 }}>
          <span>Nợ đầu kỳ <b>{formatMoney(row.openingDebt)}</b></span>
          <span>{paidLabel} trong kỳ <b>{formatMoney(row.paidInPeriod)}</b></span>
          <span>Còn nợ <b>{formatMoney(row.remainingDebt)}</b></span>
        </div>
        {payments.size > 0 && <div style={{ marginTop: 8 }}>
          {Array.from(payments, ([day, amount]) => <div key={day}>
            <Typography.Text type="secondary">{paidLabel} ngày {date(day)} · {formatMoney(amount)}</Typography.Text>
          </div>)}
        </div>}
      </Card>;
    })}
    {rows.length > PAGE_SIZE && <AppPagination current={current} total={rows.length} onChange={setPage} />}
  </>;
}

export function CarryoverDebtReport({ year }: { year: number }) {
  const [report, setReport] = useState<Report>();
  const [error, setError] = useState("");
  const [paying, setPaying] = useState<StockDocument>();
  const [refresh, setRefresh] = useState(0);
  const canWrite = useCanWrite();
  useEffect(() => {
    const controller = new AbortController();
    setReport(undefined);
    setError("");
    api.get<{ data: Report }>("/api/v1/reports/carryover-debt", { params: { year }, signal: controller.signal })
      .then((response) => { if (!controller.signal.aborted) setReport(response.data.data); })
      .catch((caught) => { if (!controller.signal.aborted) setError(toApiError(caught).message); });
    return () => controller.abort();
  }, [year, refresh]);

  async function startPayment(id: number) {
    try {
      const document = await getCarryoverDocument(id);
      if (document.totalAmount <= document.paidAmount) {
        message.info("Phiếu đã thanh toán đủ tính đến hiện tại");
        return;
      }
      setPaying(document);
    } catch (caught) {
      message.error(toApiError(caught).message);
    }
  }

  return <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr)", gap: 16, minWidth: 0 }}>
    <Alert showIcon type="info" message={report ? `Cả kỳ từ ${date(report.from)} đến ${date(report.to)}` : "Công nợ của cả kỳ năm đang chọn"}
      description="Theo dõi phiếu lập trước ngày đầu kỳ còn nợ tại mốc đó. Các lần thu hoặc trả bên dưới tính theo ngày thanh toán trong kỳ." />
    {error && <Alert type="error" showIcon message={error} />}
    {(["SALE", "PURCHASE"] as const).map((type) => {
      const rows = report?.invoices.filter((row) => row.type === type) ?? [];
      const paidLabel = type === "SALE" ? "Đã thu nợ cũ trong kỳ" : "Đã trả nợ cũ trong kỳ";
      const total = (key: "openingDebt" | "paidInPeriod" | "remainingDebt") =>
        formatMoney(rows.reduce((sum, row) => sum + row[key], 0));
      return <section key={type} style={{ minWidth: 0 }}>
        <h3>{type === "SALE" ? "Khách còn nợ" : "Còn nợ nhà cung cấp"}</h3>
        {report && <p>Nợ đầu kỳ <b>{total("openingDebt")}</b> · {paidLabel} <b>{total("paidInPeriod")}</b> · Nợ cũ cuối kỳ <b>{total("remainingDebt")}</b></p>}
        {report ? <DebtInvoices rows={rows} type={type} canWrite={canWrite} onPay={id => void startPayment(id)} />
          : !error && <Spin />}
      </section>;
    })}
    {paying && <PaymentModal label={paying.documentType === "SALE" ? "Thu nợ cũ" : "Trả nhà cung cấp"}
      carryoverYear={year}
      remaining={paying.totalAmount - paying.paidAmount}
      documentDate={paying.documentDate}
      kind={paying.documentType === "SALE" ? "SALE" : "PURCHASE"}
      onClose={() => setPaying(undefined)}
      onSubmit={async (values) => {
        try {
          await addPayment(paying.id, values, { workingYear: year, workingMonth: values.paymentDate.slice(0, 7) });
          setPaying(undefined);
          setRefresh(value => value + 1);
          message.success("Đã ghi thanh toán cho phiếu gốc");
        } catch (caught) { message.error(toApiError(caught).message); throw caught; }
      }} />}
  </div>;
}
