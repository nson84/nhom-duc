import { SearchOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Empty, Input, Row, Select, Space, Spin, Statistic, Typography, message } from "antd";
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
  currentDebt: number;
  payments: { date: string; amount: number }[];
};
type Report = { from: string; to: string; invoices: Invoice[] };
const date = (value: string) => dayjs(value).format("DD/MM/YYYY");
const searchText = (value: string) => value.toLowerCase().normalize("NFD").replace(/\p{M}/gu, "").replace(/đ/g, "d");

function DebtInvoices({ rows, type, canWrite, from, to, onPay }: {
  rows: Invoice[];
  type: Invoice["type"];
  canWrite: boolean;
  from: string;
  to: string;
  onPay: (id: number) => void;
}) {
  const [page, setPage] = useState(1);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("all");
  const keyword = searchText(query.trim());
  const matches = rows.filter(row =>
    searchText(`${row.party ?? ""} ${row.code}`).includes(keyword) &&
    (status === "all" || (status === "outstanding" ? row.currentDebt > 0 : row.currentDebt <= 0)));
  const current = Math.min(page, Math.max(1, Math.ceil(matches.length / PAGE_SIZE)));
  const paidLabel = type === "SALE" ? "Đã thu" : "Đã trả";
  if (!rows.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có công nợ chuyển kỳ" />;

  return <>
    <Space wrap style={{ marginBottom: 12 }}>
      <Input
        allowClear
        prefix={<SearchOutlined />}
        aria-label={type === "SALE" ? "Tìm khách hàng hoặc số phiếu" : "Tìm nhà cung cấp hoặc số phiếu"}
        placeholder={type === "SALE" ? "Tên khách hàng hoặc số phiếu" : "Tên nhà cung cấp hoặc số phiếu"}
        value={query}
        onChange={event => { setQuery(event.target.value); setPage(1); }}
      />
      <Select
        aria-label={type === "SALE" ? "Trạng thái phải thu hiện tại" : "Trạng thái phải trả hiện tại"}
        value={status}
        onChange={value => { setStatus(value); setPage(1); }}
        options={[
          { value: "all", label: "Tất cả trạng thái" },
          { value: "outstanding", label: "Còn nợ hiện tại" },
          { value: "settled", label: "Đã thanh toán đủ" },
        ]}
      />
    </Space>
    {matches.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không tìm thấy phiếu phù hợp" />}
    {matches.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE).map(row => {
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
          {canWrite && row.currentDebt > 0 && <Button onClick={() => onPay(row.id)}>
            {type === "SALE" ? "Thu nợ" : "Trả nhà cung cấp"}
          </Button>}
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 140px), 1fr))", gap: 8, marginTop: 8 }}>
          <span>Nợ đầu kỳ <b>{formatMoney(row.openingDebt)}</b></span>
          <span>{paidLabel} trong kỳ <b>{formatMoney(row.paidInPeriod)}</b></span>
          <span>Còn nợ cuối kỳ <b>{formatMoney(row.remainingDebt)}</b></span>
          <span>Còn nợ hiện tại <b>{formatMoney(row.currentDebt)}</b></span>
        </div>
        {payments.size > 0 && <div style={{ marginTop: 8 }}>
          {Array.from(payments, ([day, amount]) => <div key={day}>
            <Typography.Text type="secondary">{paidLabel} ngày {date(day)} · {formatMoney(amount)}{day > to ? " (sau kỳ)" : day < from ? " (trước kỳ)" : ""}</Typography.Text>
          </div>)}
        </div>}
      </Card>;
    })}
    {matches.length > PAGE_SIZE && <AppPagination current={current} total={matches.length} onChange={setPage} />}
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
      description="Tổng thu hoặc trả trong kỳ dùng ngày thanh toán thuộc kỳ. Lịch sử trên từng phiếu hiển thị cả các lần ngoài kỳ." />
    {error && <Alert type="error" showIcon message={error} />}
    {(["SALE", "PURCHASE"] as const).map((type) => {
      const rows = report?.invoices.filter((row) => row.type === type) ?? [];
      const paidLabel = type === "SALE" ? "Đã thu nợ cũ trong kỳ" : "Đã trả nợ cũ trong kỳ";
      const total = (key: "openingDebt" | "paidInPeriod" | "remainingDebt") =>
        formatMoney(rows.reduce((sum, row) => sum + row[key], 0));
      return <section key={type} style={{ minWidth: 0 }}>
        <h3>{type === "SALE" ? "Phải thu chuyển kỳ" : "Phải trả nhà cung cấp chuyển kỳ"}</h3>
        {report && <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
          <Col xs={24} md={8}><Card size="small" style={{ height: "100%" }}><Statistic title="Nợ đầu kỳ" value={total("openingDebt")} /></Card></Col>
          <Col xs={24} md={8}><Card size="small" style={{ height: "100%" }}><Statistic title={paidLabel} value={total("paidInPeriod")} /></Card></Col>
          <Col xs={24} md={8}><Card size="small" style={{ height: "100%" }}><Statistic title="Nợ cũ cuối kỳ" value={total("remainingDebt")} /></Card></Col>
        </Row>}
        {report ? <DebtInvoices rows={rows} type={type} from={report.from} to={report.to} canWrite={canWrite} onPay={id => void startPayment(id)} />
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
          await addPayment(paying.id, values, { workingYear: null, workingMonth: values.paymentDate.slice(0, 7) });
          setPaying(undefined);
          setRefresh(value => value + 1);
          message.success("Đã ghi thanh toán cho phiếu gốc");
        } catch (caught) { message.error(toApiError(caught).message); throw caught; }
      }} />}
  </div>;
}
