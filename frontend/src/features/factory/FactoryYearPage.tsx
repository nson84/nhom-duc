import { reopenYear } from "../dashboard/dashboard-api";
import { useWorkingPeriodInfo } from "../dashboard/working-period";
import { CheckOutlined, CloseOutlined, DollarOutlined, PlusOutlined, SaveOutlined, UnlockOutlined } from "@ant-design/icons";
import { useSession } from "../auth/auth-store";
import { Alert, Card, Col, DatePicker, Form, Input, InputNumber, Modal, Row, Select, Space, Statistic, Tag, Typography, message } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { AppActionButton } from "../../components/AppButtons";
import { PagedTable, ResponsiveTable as Table } from "../../components/PagedTable";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { qtyInputNumberProps, vndInputNumberProps } from "../../utils/vnd-input";
import { useCanWrite } from "../auth/auth-store";
import { YearEndBalanceCard } from "../dashboard/YearEndBalanceCard";
import { useShop } from "../shop/shop-store";
import { useWorkingYear } from "../dashboard/working-year";
import { DeliveredByCategory } from "../stock/DeliveredByCategory";
import { OpeningInventory } from "../stock/OpeningInventory";
import { listCategories, type Category } from "../products/products-api";
import {
  createScrapSale,
  getYearCount,
  getYearReport,
  getRawCounts,
  getSaleCostReport,
  getSettlement,
  listScrapSales,
  payScrapSale,
  saveInventory,
  voidScrapSale,
  type ScrapSale,
  type YearCount,
  type YearCountInput,
  type YearReport,
  type RawCount,
  type SaleCostReport,
  type SettlementReport,
} from "./factory-api";

const qty = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });
const showKg = (value: number | null | undefined) => value == null ? "—" : `${qty.format(value)} kg`;
const showMoney = (value: number | null | undefined) => value == null ? "—" : formatMoney(value);

function formatCountKg(value: number | string | undefined): string {
  if (value === undefined || value === null || value === "") return "";
  const [whole, fraction] = String(value).replace(/,/g, "").split(".");
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return fraction === undefined ? grouped : `${grouped}.${fraction}`;
}

function roundKg(value: number): number {
  return Math.max(0, Math.round(value * 1000) / 1000);
}

type ScrapForm = {
  saleDate: Dayjs;
  quantity: number;
  unit: "kg" | "tạ" | "tấn";
  unitPrice: number;
};

type PaymentForm = {
  paymentDate: Dayjs;
  amount: number;
  paymentMethod: string;
  collectedBy: string;
};

export function FactoryYearPage() {
  const workingYear = useWorkingYear(useShop());
  const location = useLocation();
  const session = useSession();
  const canWrite = useCanWrite();
  const year = workingYear.year;
  const [periodFrom, setPeriodFrom] = useState<Dayjs>(dayjs().startOf("year"));
  const [periodTo, setPeriodTo] = useState<Dayjs>(dayjs().endOf("year"));
  const [report, setReport] = useState<YearReport | null>(null);
  const [settlementMonth, setSettlementMonth] = useState<string | undefined>();
  const [settlement, setSettlement] = useState<SettlementReport | null>(null);
  const [count, setCount] = useState<YearCount | null>(null);
  const [rawCounts, setRawCounts] = useState<RawCount[]>([]);
  const [rawInputs, setRawInputs] = useState<{ categoryId: number; categoryName: string; quantityKg: number | null; openingKg: number; purchasedKg: number; deliveredKg: number }[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [saleCosts, setSaleCosts] = useState<SaleCostReport | null>(null);
  const [saleCategoryId, setSaleCategoryId] = useState<number | undefined>();
  const [sales, setSales] = useState<ScrapSale[]>([]);
  const [loading, setLoading] = useState(false);
  const [scrapOpen, setScrapOpen] = useState(false);
  const [scrapKey, setScrapKey] = useState(crypto.randomUUID());
  const [paying, setPaying] = useState<ScrapSale | null>(null);
  const [paymentKey, setPaymentKey] = useState(crypto.randomUUID());
  const [saving, setSaving] = useState(false);
  const [clampTick, setClampTick] = useState(0);
  const [countForm] = Form.useForm<YearCountInput>();
  const watchedScrapKg = Number(Form.useWatch("scrapKg", countForm) ?? 0);
  const [scrapForm] = Form.useForm<ScrapForm>();
  const scrapUnit = Form.useWatch("unit", scrapForm) ?? "kg";
  const [paymentForm] = Form.useForm<PaymentForm>();

  const selectedMonth = workingYear.month;
  const periodInfo = useWorkingPeriodInfo(year, count?.status);
  useEffect(() => { setSettlementMonth(selectedMonth); }, [selectedMonth, year]);

  const visibleSaleRows = selectedMonth
    ? (saleCosts?.rows ?? []).filter((row) => dayjs(row.documentDate).format("YYYY-MM") === selectedMonth)
    : (saleCosts?.rows ?? []);
  const visibleScrapSales = selectedMonth
    ? sales.filter((sale) => dayjs(sale.saleDate).format("YYYY-MM") === selectedMonth)
    : sales;

  const latestLoad = useRef(0);
  const load = useCallback(async () => {
    const request = ++latestLoad.current;
    setLoading(true);
    try {
      const [nextReport, nextCount, nextSales, nextSettlement, nextRaw, nextCategories, nextSaleCosts] = await Promise.all([
        getYearReport(year), getYearCount(year), listScrapSales(year), getSettlement(year, settlementMonth),
        getRawCounts(year), listCategories(), getSaleCostReport(year, saleCategoryId),
      ]);
      if (request !== latestLoad.current) return;
      setReport(nextReport);
      setCount(nextCount);
      setPeriodFrom(dayjs(nextCount.periodFrom));
      setPeriodTo(dayjs(nextCount.periodTo));
      setSales(nextSales);
      setSettlement(nextSettlement);
      setRawCounts(nextRaw);
      setRawInputs(nextRaw.map((row) => ({
        categoryId: row.categoryId,
        categoryName: row.categoryName,
        quantityKg: row.quantityKg == null ? null : Number(row.quantityKg),
        openingKg: Number(row.openingKg ?? 0),
        purchasedKg: Number(row.purchasedKg),
        deliveredKg: Number(row.deliveredKg),
      })));
      setCategories(nextCategories);
      setSaleCosts(nextSaleCosts);
      countForm.setFieldsValue(nextCount);
    } catch (error) {
      if (request === latestLoad.current) message.error(toApiError(error).message);
    } finally {
      if (request === latestLoad.current) setLoading(false);
    }
  }, [year, settlementMonth, saleCategoryId, countForm]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (!loading && location.hash === "#kiem-ke-cuoi-nam") {
      document.getElementById("kiem-ke-cuoi-nam")?.scrollIntoView({ block: "start" });
    }
  }, [loading, location.hash]);

  const closed = count?.status === "CLOSED";
  const canEdit = canWrite && !loading && !closed && count?.year === year && !dayjs(count.periodFrom).isAfter(dayjs(), "day");

  const totalEnteredRawKg = rawInputs.reduce((sum, item) => sum + Number(item.quantityKg ?? 0), 0);
  const totalAvailableBeforeUnsoldScrap = roundKg(Number(report?.initialDiscrepancyKg ?? 0) - Number(report?.scrapSoldKg ?? 0));
  const allRawCounted = rawInputs.length > 0 && rawInputs.every(item => item.quantityKg != null);
  const categoryAvailable = (row: typeof rawInputs[number]) => Math.max(0, roundKg(row.openingKg + row.purchasedKg - row.deliveredKg));
  const maxUnsoldScrapKg = roundKg(totalAvailableBeforeUnsoldScrap - totalEnteredRawKg);

  function maxRawForCategory(categoryId: number): number {
    const row = rawInputs.find(item => item.categoryId === categoryId);
    const categoryNet = row ? categoryAvailable(row) : 0;
    const otherRaw = rawInputs.reduce((sum, item) => item.categoryId === categoryId ? sum : sum + Number(item.quantityKg ?? 0), 0);
    const remainingTotal = roundKg(totalAvailableBeforeUnsoldScrap - watchedScrapKg - otherRaw);
    return Math.max(0, roundKg(Math.min(categoryNet, remainingTotal)));
  }

  async function persistInventory(close = false) {
    const values = await countForm.validateFields();
    const enteredScrap = Number(values.scrapKg ?? 0);
    for (const row of rawInputs) {
      const categoryNet = categoryAvailable(row);
      if (Number(row.quantityKg ?? 0) > categoryNet) {
        throw new Error(`${row.categoryName}: số kg còn trong kho không được lớn hơn (tồn đầu kỳ + nhập − giao = ${formatCountKg(categoryNet)} kg).`);
      }
    }
    if (roundKg(totalEnteredRawKg + enteredScrap) > totalAvailableBeforeUnsoldScrap) {
      throw new Error(`Tổng nguyên liệu còn trong kho và phế liệu (${formatCountKg(roundKg(totalEnteredRawKg + enteredScrap))} kg) không được lớn hơn lượng nhôm có thể còn (${formatCountKg(totalAvailableBeforeUnsoldScrap)} kg).`);
    }
    return saveInventory(year, {
      ...values, periodFrom: periodFrom.format("YYYY-MM-DD"), periodTo: periodTo.format("YYYY-MM-DD"),
      openingKg: count?.openingKg ?? 0, openingValue: count?.openingValue ?? 0,
      rawKg: count?.rawKg ?? 0, rawValue: count?.rawValue ?? 0,
      scrapKg: values.scrapKg ?? 0, scrapValue: values.scrapValue ?? 0,
      workInProgressKg: 0, workInProgressValue: 0, finishedKg: 0, finishedValue: 0,
    }, rawInputs.map((r) => ({ categoryId: r.categoryId, quantityKg: Number(r.quantityKg ?? 0) })), close);
  }

  async function saveCount() {
    try {
      setSaving(true);
      await persistInventory();
      message.success("Đã lưu kiểm kê cuối năm");
      await load();
    } catch (error) {
      if (error instanceof Error) message.error(toApiError(error).message);
    } finally {
      setSaving(false);
    }
  }

  async function saveRawInventory() {
    setSaving(true);
    try {
      await persistInventory();
      await load();
      message.success("Đã lưu số kg nguyên liệu còn trong kho");
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setSaving(false);
    }
  }

  function confirmClose() {
    Modal.confirm({
      title: "Xác nhận chốt kỳ quyết toán?",
      content: `Kỳ ${periodFrom.format("DD/MM/YYYY")} – ${periodTo.format("DD/MM/YYYY")} sẽ được khóa. Kỳ tiếp theo bắt đầu ngày ${periodTo.add(1, "day").format("DD/MM/YYYY")}. Hãy kiểm tra số cân đã nhập trước khi xác nhận.`,
      okText: "Đã kiểm kê, chốt năm",
      autoFocusButton: "cancel",
      cancelText: "Quay lại",
      onOk: async () => {
        try {
          await persistInventory(true);
          message.success("Đã chốt năm");
          await load();
        } catch (error) {
          message.error(toApiError(error).message);
          throw error;
        }
      },
    });
  }

  async function submitScrap() {
    try {
      const values = await scrapForm.validateFields();
      setSaving(true);
      const factor = values.unit === "tấn" ? 1000 : values.unit === "tạ" ? 100 : 1;
      await createScrapSale({
        saleDate: values.saleDate.format("YYYY-MM-DDTHH:mm:ss"),
        buyerName: "Không ghi tên",
        sellerName: session?.username ?? "Xưởng",
        quantityKg: values.quantity * factor,
        unitPrice: values.unitPrice / factor,
        idempotencyKey: scrapKey,
      });
      setScrapOpen(false);
      scrapForm.resetFields();
      message.success("Đã lưu phiếu bán phế liệu");
      await load();
    } catch (error) {
      if (error instanceof Error) message.error(toApiError(error).message);
    } finally {
      setSaving(false);
    }
  }

  async function submitPayment() {
    if (!paying) return;
    try {
      const values = await paymentForm.validateFields();
      setSaving(true);
      await payScrapSale(paying.id, {
        paymentDate: values.paymentDate.format("YYYY-MM-DDTHH:mm:ss"),
        amount: values.amount,
        paymentMethod: values.paymentMethod,
        collectedBy: values.collectedBy,
        idempotencyKey: paymentKey,
      });
      setPaying(null);
      paymentForm.resetFields();
      message.success("Đã ghi thu tiền phế liệu");
      await load();
    } catch (error) {
      if (error instanceof Error) message.error(toApiError(error).message);
    } finally {
      setSaving(false);
    }
  }

  function confirmVoid(sale: ScrapSale) {
    Modal.confirm({
      title: `Hủy phiếu phế liệu #${sale.id}?`,
      content: "Phiếu chưa thu tiền sẽ được loại khỏi báo cáo. Phiếu đã thu phải đối soát tiền trước.",
      okText: "Hủy phiếu",
      cancelText: "Quay lại",
      onOk: async () => {
        try {
          await voidScrapSale(sale.id, "Ghi sai phiếu phế liệu");
          message.success("Đã hủy phiếu");
          await load();
        } catch (error) { message.error(toApiError(error).message); }
      },
    });
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card title="Quyết toán nhôm xưởng" extra={<Typography.Text>{periodInfo.label}</Typography.Text>}>
        <Alert type="info" showIcon message="Phiếu nhập và phiếu bán thành phẩm không tự trừ tồn xưởng. Khối lượng còn lại được kiểm kê một lần khi chốt năm." />
        {report && (
          <>
            <Row gutter={[12, 12]} style={{ marginTop: 16 }}>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Nhôm đầu kỳ" value={showKg(report.openingKg)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Nhôm nhập" value={showKg(report.purchasedKg)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Thành phẩm đã bán" value={showKg(report.finishedSoldKg)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Phế liệu đã bán" value={showKg(report.scrapSoldKg)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Chênh lệch ban đầu" value={showKg(report.initialDiscrepancyKg)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Còn cuối kỳ" value={showKg(report.closingKg)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title={report.estimatedLossKg != null && report.estimatedLossKg < 0 ? "Tồn kiểm kê vượt số tính theo sổ" : "Hao hụt ước tính"} value={showKg(report.estimatedLossKg == null ? null : Math.abs(report.estimatedLossKg))} />
                {report.estimatedLossKg != null && report.estimatedLossKg < 0 && <Typography.Text type="secondary">Cần đối chiếu tồn đầu kỳ và số cân cuối kỳ.</Typography.Text>}
              </Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title={report.countStatus === "CLOSED" ? "Lãi quản trị đã chốt" : "Lãi quản trị ước tính"} value={showMoney(report.estimatedProfit)} /></Card></Col>
            </Row>
            <Row gutter={[12, 12]} style={{ marginTop: 12 }}>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Tiền nhôm nhập" value={showMoney(report.purchaseValue)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Giá trị tồn đầu kỳ" value={showMoney(report.openingValue)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Giá trị tồn cuối kỳ" value={showMoney(report.closingValue)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Chi phí nhôm trong kỳ" value={showMoney(report.materialCost)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Doanh thu thành phẩm" value={showMoney(report.finishedRevenue)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Doanh thu phế liệu" value={showMoney(report.scrapRevenue)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Tiền công theo kỳ" value={showMoney(report.payrollCost)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Chi phí khác đã ghi" value={showMoney(report.otherExpenses)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Đã thu từ bán thành phẩm" value={showMoney(report.finishedCashCollected)} /></Card></Col>
              <Col xs={12} md={6}><Card size="small"><Statistic title="Đã thu từ bán phế liệu" value={showMoney(report.scrapCashCollected)} /></Card></Col>
            </Row>
            {report.countStatus === "MISSING" && <Alert style={{ marginTop: 12 }} type="warning" showIcon message="Chưa kiểm kê cuối kỳ: chưa thể tính hao hụt và lãi." />}
          </>
        )}
      </Card>

      <YearEndBalanceCard key={year} year={year} refreshKey={closed ? 1 : 0} />

      <OpeningInventory year={year} />
      <div id="kiem-ke-cuoi-nam" style={{ scrollMarginTop: 16 }}><Card title="Nhập số kg còn trong kho cuối kỳ" loading={loading} extra={<Space>
        <Tag color={closed ? "green" : "orange"}>{closed ? "Đã chốt" : "Chưa chốt"}</Tag>
        {closed && canWrite && <AppActionButton tone="edit" icon={<UnlockOutlined />} onClick={() => {
          Modal.confirm({
            title: "Mở lại kiểm kê Xưởng?",
            content: "Kỳ sẽ trở về trạng thái chưa chốt. Sửa số kiểm kê rồi chốt lại để cập nhật số mang sang kỳ sau.",
            okText: "Mở lại kiểm kê",
            cancelText: "Hủy",
            autoFocusButton: "cancel",
            onOk: async () => {
              try {
                await reopenYear(year);
                await load();
                message.success("Đã mở lại kiểm kê");
              } catch (error) {
                message.error(toApiError(error).message);
              }
            },
          });
        }}>Mở lại kiểm kê</AppActionButton>}
      </Space>}>
        <Space wrap style={{ marginBottom: 12 }}>
          <label>Đầu kỳ <DatePicker value={periodFrom} format="DD/MM/YYYY" disabled /></label>
          <label>Ngày chốt <DatePicker value={periodTo} format="DD/MM/YYYY" disabled /></label>
        </Space>
        <Typography.Paragraph type="secondary">Đổi ngày bắt đầu hoặc kết thúc tại Tổng quan, rồi quay lại kiểm kê.</Typography.Paragraph>
        <Typography.Paragraph type="secondary">Có thể chốt theo lịch Tết âm hoặc ngày cửa hàng chọn. Kỳ sau bắt đầu ngay sau ngày chốt.</Typography.Paragraph>
        <Typography.Paragraph type="secondary">Nhập số nhôm nguyên liệu còn trong kho. Phế liệu chưa bán có thể để trống nếu không còn.</Typography.Paragraph>
        <Typography.Paragraph type="secondary">
          Tồn đầu năm lấy từ số đã chốt cuối năm trước. Nếu chưa có số chốt và chưa có số dư đầu năm đã lưu, hệ thống dùng 0.
        </Typography.Paragraph>
        <Form form={countForm} layout="vertical" disabled={!canEdit} initialValues={{ openingKg: 0, openingValue: 0, rawKg: 0, rawValue: 0, scrapKg: 0, scrapValue: 0, workInProgressKg: 0, workInProgressValue: 0, finishedKg: 0, finishedValue: 0 }}>
          <Card size="small" title="Nguyên liệu còn theo từng loại nhôm" style={{ marginBottom: 16 }}>
            <Typography.Paragraph type="secondary">
              Nhập số kg cân được của từng loại (tối đa theo tồn đầu kỳ + nhập trong kỳ − giao trong kỳ). Nếu không còn hàng, để 0. Giá trị được tính từ các phiếu nhập gần nhất.
            </Typography.Paragraph>
            <Table size="small" rowKey="categoryId" pagination={false} dataSource={count?.year === year ? rawInputs : []}
              columns={[
                { title: "Loại nhôm", dataIndex: "categoryName" },
                { title: "Tồn đầu kỳ (kg)", align: "center", render: (_, row) => formatCountKg(roundKg(row.openingKg)) },
                { title: "Nhập trong kỳ (kg)", align: "center", render: (_, row) => formatCountKg(roundKg(row.purchasedKg)) },
                { title: "Giao trong kỳ (kg)", align: "center", render: (_, row) => formatCountKg(roundKg(row.deliveredKg)) },
                { title: "Còn trong kho (kg)", align: "center", render: (_, row) => {
                  const maxKg = maxRawForCategory(row.categoryId);
                  return (
                    <InputNumber
                      key={`${row.categoryId}-${clampTick}`}
                      min={0}
                      max={maxKg}
                      step={1}
                      formatter={formatCountKg}
                      parser={(v) => {
                        const raw = (v ?? "").replace(/,/g, "");
                        if (!raw) return "" as unknown as number;
                        const parsed = Number(raw);
                        if (!Number.isFinite(parsed)) return 0;
                        return parsed > maxKg ? maxKg : parsed;
                      }}
                      onInput={(text) => {
                        const raw = (text ?? "").replace(/,/g, "");
                        const parsed = Number(raw);
                        if (raw !== "" && Number.isFinite(parsed) && parsed > maxKg) {
                          setRawInputs((rows) => rows.map((item) =>
                            item.categoryId === row.categoryId ? { ...item, quantityKg: maxKg } : item,
                          ));
                          setClampTick((t) => t + 1);
                        }
                      }}
                      style={{ width: 160 }}
                      aria-label={`Số kg còn lại của ${row.categoryName}`}
                      placeholder={`≤ ${formatCountKg(maxKg)}`}
                      value={row.quantityKg}
                      onChange={(quantityKg) => {
                        const next = quantityKg == null ? null : Math.min(Math.max(0, Number(quantityKg)), maxKg);
                        setRawInputs((rows) => rows.map((item) =>
                          item.categoryId === row.categoryId ? { ...item, quantityKg: next } : item,
                        ));
                      }} />
                  );
                } },
                { title: "Hao hụt / Thiếu (kg)", align: "center", render: (_, row) => {
                  if (row.quantityKg == null) return "—";
                  const net = roundKg(row.openingKg + row.purchasedKg - row.deliveredKg);
                  const diff = Math.round((net - row.quantityKg) * 1000) / 1000;
                  return <span style={{ color: diff > 0 ? "var(--color-danger)" : undefined }}>{formatCountKg(diff)}</span>;
                } },
                { title: "Giá trị đã lưu", align: "center", render: (_, row) =>
                  showMoney(rawCounts.find((saved) => saved.categoryId === row.categoryId)?.valueAmount) },
              ]}
              expandable={{ rowExpandable: (row) => rawCounts.some((saved) => saved.categoryId === row.categoryId && saved.layers.length > 0),
                expandedRowRender: (row) => <Table size="small" rowKey="purchaseItemId" pagination={false}
                dataSource={rawCounts.find((saved) => saved.categoryId === row.categoryId)?.layers} columns={[
                  { title: "Phiếu nhập", dataIndex: "documentCode" },
                  { title: "Ngày", dataIndex: "documentDate", render: (v: string) => dayjs(v).format("DD/MM/YYYY") },
                  { title: "Lấy từ phiếu", dataIndex: "quantityKg", render: showKg },
                  { title: "Giá / kg", dataIndex: "unitCostPerKg", render: showMoney },
                  { title: "Thành tiền", dataIndex: "valueAmount", render: showMoney },
                ]} /> }} />
            {canEdit && <div style={{ marginBlock: 12 }}>
              <AppActionButton tone="edit" icon={<SaveOutlined />} loading={saving}
                onClick={() => void saveRawInventory()}>Lưu tồn nguyên liệu</AppActionButton>
            </div>}
            <Row gutter={[12, 12]} style={{ marginTop: 8 }}>
              <Col xs={24} sm={8}>
                <Card size="small"><Statistic title="Tổng nguyên liệu còn trong kho" value={`${formatCountKg(roundKg(totalEnteredRawKg))} kg`} /></Card>
              </Col>
              <Col xs={24} sm={8}>
                <Card size="small"><Statistic title="Phế liệu chưa bán đang nhập" value={`${formatCountKg(roundKg(watchedScrapKg))} kg`} /></Card>
              </Col>
              <Col xs={24} sm={8}>
                <Card size="small">
                  <Statistic
                    title="Hao hụt thuần ước tính"
                    value={allRawCounted ? `${formatCountKg(roundKg(totalAvailableBeforeUnsoldScrap - totalEnteredRawKg - watchedScrapKg))} kg` : "Chưa cân đủ"}
                    valueStyle={{ color: roundKg(totalAvailableBeforeUnsoldScrap - totalEnteredRawKg - watchedScrapKg) > 0 ? "var(--color-danger)" : undefined }}
                  />
                </Card>
              </Col>
            </Row>
            <Typography.Text style={{ display: "block", marginTop: 8 }}>Giá trị nguyên liệu đã lưu: {showKg(count?.rawKg)} · {showMoney(count?.rawValue)}</Typography.Text>
          </Card>
          <Row gutter={12}>
            {([
              ["Phế liệu chưa bán", "scrapKg", "scrapValue"],
            ] as const).map(([label, kgField, valueField]) => <Col xs={24} md={12} lg={8} key={kgField}>
              <Card size="small" title={label} style={{ marginBottom: 12 }}>
                <Form.Item name={kgField} label="Khối lượng (kg)" rules={[{ type: "number", min: 0, max: maxUnsoldScrapKg }]}>
                  <InputNumber
                    key={`scrap-${clampTick}`}
                    min={0}
                    max={maxUnsoldScrapKg}
                    step={1}
                    formatter={formatCountKg}
                    parser={(v) => {
                      const raw = (v ?? "").replace(/,/g, "");
                      if (!raw) return "" as unknown as number;
                      const parsed = Number(raw);
                      if (!Number.isFinite(parsed)) return 0;
                      return parsed > maxUnsoldScrapKg ? maxUnsoldScrapKg : parsed;
                    }}
                    onInput={(text) => {
                      const raw = (text ?? "").replace(/,/g, "");
                      const parsed = Number(raw);
                      if (raw !== "" && Number.isFinite(parsed) && parsed > maxUnsoldScrapKg) {
                        countForm.setFieldValue(kgField, maxUnsoldScrapKg);
                        setClampTick((t) => t + 1);
                      }
                    }}
                    placeholder={`≤ ${formatCountKg(maxUnsoldScrapKg)}`}
                    style={{ width: "100%" }}
                  />
                </Form.Item>
                <Form.Item name={valueField} label="Giá trị (đ)" rules={[{ type: "number", min: 0 }]}><InputNumber {...vndInputNumberProps} min={0} style={{ width: "100%" }} /></Form.Item>
              </Card>
            </Col>)}
          </Row>
          <Form.Item name="notes" label="Ghi chú kiểm kê"><Input.TextArea rows={2} maxLength={500} /></Form.Item>
        </Form>
        {canEdit && <Space>
          <AppActionButton tone="edit" icon={<SaveOutlined />} loading={saving} onClick={() => void saveCount()}>Lưu kiểm kê</AppActionButton>
          {count?.status === "DRAFT" && <AppActionButton tone="confirm" icon={<CheckOutlined />} onClick={confirmClose}>Chốt năm</AppActionButton>}
        </Space>}
      </Card></div>

      <Card
        title="Quyết toán công nợ Xưởng"
        loading={loading}
        extra={<Select
          value={settlementMonth ?? "ALL"}
          disabled={!!selectedMonth}
          onChange={(value) => setSettlementMonth(value === "ALL" ? undefined : value)}
          style={{ width: 170 }}
          options={[{ value: "ALL", label: "Cả kỳ" }, ...Array.from({ length: periodTo.startOf("month").diff(periodFrom.startOf("month"), "month") + 1 }, (_, i) => {
            const month = periodFrom.startOf("month").add(i, "month");
            return { value: month.format("YYYY-MM"), label: `Tháng ${month.format("MM/YYYY")}` };
          })]}
        />}
      >
        {settlement && <>
          {settlement.unlinkedSalesCount > 0 && <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={`${settlement.unlinkedSalesCount} phiếu bán cũ chưa gắn khách (${showMoney(settlement.unlinkedSalesValue)})`} description="Gắn khách mua vào các phiếu này để báo cáo công nợ theo người đầy đủ." />}
          <Typography.Paragraph type="secondary">
            Kỳ công nợ từ {dayjs(settlement.from).format("DD/MM/YYYY")} đến hết ngày {dayjs(settlement.toExclusive).subtract(1, "day").format("DD/MM/YYYY")}
            {dayjs(settlement.toExclusive).isAfter(dayjs()) ? " (tạm tính đến hiện tại)" : ""}.
            Tiền thu trong kỳ có thể thuộc phiếu bán của kỳ trước.
          </Typography.Paragraph>
          <Typography.Title level={5}>Phải thu khách hàng</Typography.Title>
          <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
            <Col xs={12} md={6}><Card size="small"><Statistic title="Còn phải thu cuối kỳ" value={showMoney(settlement.totalClosingDebt)} /></Card></Col>
            <Col xs={12} md={6}><Card size="small">
              <Statistic title="Nợ cũ còn phải thu cuối kỳ" value={showMoney(settlement.customers.reduce((total, customer) =>
                total + customer.invoices.reduce((subtotal, invoice) => invoice.documentDate < settlement.from
                  ? subtotal + Math.max(0, Number(invoice.remainingAsOf)) : subtotal, 0), 0))} />
              <Typography.Text type="secondary">Nợ cũ lúc mở kỳ: {showMoney(settlement.totalOpeningDebt)}</Typography.Text>
            </Card></Col>
            <Col xs={12} md={6}><Card size="small"><Statistic title="Bán trong kỳ" value={showMoney(settlement.totalPeriodSales)} /></Card></Col>
            <Col xs={12} md={6}><Card size="small"><Statistic title="Thu trong kỳ" value={showMoney(settlement.totalPeriodReceipts)} /></Card></Col>
          </Row>
          <PagedTable
            rowKey="customerId"
            dataSource={settlement.customers}
            columns={[
              { title: "Khách hàng", render: (_, row) => <Link to={`/khach-mua/${row.customerId}`}>{row.customerName}</Link> },
              { title: "Còn phải thu cuối kỳ", dataIndex: "closingDebt", render: (value) => <strong>{showMoney(value)}</strong> },
              { title: "Phải thu lúc mở kỳ", dataIndex: "openingDebt", render: showMoney },
              { title: "Bán trong kỳ", dataIndex: "periodSales", render: showMoney },
              { title: "Đã thu", dataIndex: "periodReceipts", render: showMoney },
            ]}
            expandable={{ expandedRowRender: (row) => <Table
              rowKey="documentId" size="small" pagination={false} dataSource={row.invoices}
              columns={[
                { title: "Số phiếu", dataIndex: "documentCode" },
                { title: "Ngày", dataIndex: "documentDate", render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm") },
                { title: "Sản phẩm", dataIndex: "products" },
                { title: "Khối lượng", dataIndex: "quantityKg", render: showKg },
                { title: "Người bán", dataIndex: "sellerName" },
                { title: "Tiền hàng", dataIndex: "totalAmount", render: showMoney },
                { title: "Đã thu đến cuối kỳ", dataIndex: "paidAsOf", render: showMoney },
                { title: "Còn nợ cuối kỳ", dataIndex: "remainingAsOf", render: showMoney },
              ]}
            /> }}
          />
          <Typography.Title level={5}>Phải trả nhà cung cấp</Typography.Title>
          {settlement.supplier ? <Row gutter={[12, 12]}>
            <Col xs={12} md={6}><Card size="small"><Statistic title="Còn phải trả cuối kỳ" value={showMoney(settlement.supplier.closingDebt)} /></Card></Col>
            <Col xs={12} md={6}><Card size="small"><Statistic title="Phải trả lúc mở kỳ" value={showMoney(settlement.supplier.openingDebt)} /></Card></Col>
            <Col xs={12} md={6}><Card size="small"><Statistic title="Nhập mua trong kỳ" value={showMoney(settlement.supplier.purchases)} /></Card></Col>
            <Col xs={12} md={6}><Card size="small"><Statistic title="Đã trả trong kỳ" value={showMoney(settlement.supplier.payments)} /></Card></Col>
          </Row> : <Alert type="info" message="Khởi động lại backend để xem số phải trả nhà cung cấp." />}
        </>}
      </Card>

      <Card title={`Phiếu bán phế liệu ${selectedMonth ? `tháng ${dayjs(`${selectedMonth}-01`).format("MM/YYYY")}` : "trong kỳ"}`} loading={loading} extra={canEdit && <AppActionButton tone="create" icon={<PlusOutlined />} onClick={() => {
        const suggested = selectedMonth ? dayjs(`${selectedMonth}-01`) : dayjs();
        const saleDate = suggested.isBefore(periodFrom, "day") ? periodFrom : suggested.isAfter(periodTo, "day") ? periodTo : suggested;
        setScrapKey(crypto.randomUUID());
        scrapForm.setFieldsValue({ saleDate, unit: "kg" });
        setScrapOpen(true);
      }}>Thêm phiếu bán phế liệu</AppActionButton>}>
        <Typography.Paragraph type="secondary">Bấm vào một dòng để xem chi tiết phiếu và các lần thu tiền.</Typography.Paragraph>
        <PagedTable<ScrapSale>
          rowKey="id" dataSource={visibleScrapSales} size="middle"
          columns={[
            { title: "Thời gian", dataIndex: "saleDate", render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm") },
            { title: "Người bán", dataIndex: "sellerName" },
            { title: "Khối lượng", dataIndex: "quantityKg", render: showKg },
            { title: "Đơn giá/kg", dataIndex: "unitPrice", render: showMoney },
            { title: "Thành tiền", dataIndex: "totalAmount", render: showMoney },
          ]}
          expandable={{ showExpandColumn: false, expandRowByClick: true, expandedRowRender: (row) => <>
            <Space wrap style={{ marginBottom: 12 }}>
              <span>Người mua: {row.buyerName}</span>
              <span>Đã thu: {showMoney(row.paidAmount)}</span>
              <span>Còn nợ: {showMoney(row.totalAmount - row.paidAmount)}</span>
              <Tag color={row.status === "CONFIRMED" ? "green" : "default"}>{row.status === "CONFIRMED" ? "Đã bán" : "Đã hủy"}</Tag>
            </Space>
            {canWrite && row.status === "CONFIRMED" ? <Space style={{ marginBottom: 12, marginInlineStart: 12 }}>
              {row.totalAmount > row.paidAmount && <AppActionButton tone="pay" icon={<DollarOutlined />} onClick={() => { setPaymentKey(crypto.randomUUID()); paymentForm.setFieldsValue({ paymentDate: selectedMonth ? (dayjs().format("YYYY-MM") === selectedMonth ? dayjs() : dayjs(`${selectedMonth}-01`)) : dayjs(), amount: row.totalAmount - row.paidAmount, paymentMethod: "CASH" }); setPaying(row); }}>Ghi thu</AppActionButton>}
              {canEdit && row.paidAmount === 0 && <AppActionButton tone="delete" icon={<CloseOutlined />} onClick={() => confirmVoid(row)}>Hủy</AppActionButton>}
            </Space> : null}
            <Table rowKey="id" size="small" pagination={false} dataSource={row.payments} columns={[
            { title: "Ngày thu", dataIndex: "paymentDate", render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm") },
            { title: "Số tiền", dataIndex: "amount", render: showMoney },
            { title: "Hình thức", dataIndex: "paymentMethod" },
            { title: "Người thu", dataIndex: "collectedBy" },
          ]} /></> }}
        />
      </Card>

      <Card title="Phiếu bán thành phẩm" loading={loading} style={{ marginBottom: 16 }}>
        <Space wrap style={{ marginBottom: 12 }}>
          <Select allowClear placeholder="Tất cả loại nhôm" style={{ minWidth: 190 }} value={saleCategoryId}
            options={categories.map((c) => ({ value: c.id, label: c.name }))}
            onChange={setSaleCategoryId} />
        </Space>
        <Row gutter={12} style={{ marginBottom: 12 }}>
          <Col xs={24} md={8}><Statistic title="Kg thành phẩm đã giao" value={showKg(selectedMonth ? visibleSaleRows.reduce((sum, row) => sum + row.quantityKg, 0) : saleCosts?.quantityKg)} />
            {count && <DeliveredByCategory from={selectedMonth ? (dayjs(count.periodFrom).isAfter(dayjs(`${selectedMonth}-01`)) ? count.periodFrom : `${selectedMonth}-01`) : count.periodFrom}
              to={selectedMonth ? (dayjs(count.periodTo).isBefore(dayjs(`${selectedMonth}-01`).endOf("month")) ? count.periodTo : dayjs(`${selectedMonth}-01`).endOf("month").format("YYYY-MM-DD")) : count.periodTo}
              categoryId={saleCategoryId} shopKey={`factory-${year}`} />}
          </Col>
          <Col xs={24} md={8}><Statistic title="Tiền bán trên phiếu" value={showMoney(selectedMonth ? visibleSaleRows.reduce((sum, row) => sum + row.salesAmount, 0) : saleCosts?.salesAmount)} /></Col>
        </Row>
        <Table size="small" rowKey="itemId" dataSource={visibleSaleRows}
          columns={[
            { title: "Phiếu", dataIndex: "documentCode" },
            { title: "Khách", dataIndex: "customerName" },
            { title: "Loại", dataIndex: "categoryName" },
            { title: "Số lượng bán", dataIndex: "quantityDocument", render: (v: number, row) => `${qty.format(v)} ${row.unitName}` },
            { title: "Đơn giá bán", dataIndex: "unitPrice", render: (v: number, row) => `${showMoney(v)} / ${row.unitName}` },
            { title: "Thành tiền bán", dataIndex: "salesAmount", render: showMoney },
          ]} />
      </Card>



      <Modal title="Bán phế liệu" open={scrapOpen} onCancel={() => setScrapOpen(false)} onOk={() => void submitScrap()} confirmLoading={saving} okText="Lưu phiếu">
        <Form form={scrapForm} layout="vertical">
          <Form.Item name="saleDate" label="Ngày giờ bán" rules={[{ required: true }]}><DatePicker showTime format="DD/MM/YYYY HH:mm" style={{ width: "100%" }} /></Form.Item>
          <Space align="start" style={{ width: "100%" }}>
            <Form.Item name="quantity" label="Khối lượng" rules={[{ required: true }, { type: "number", min: 0.000001 }]}><InputNumber {...qtyInputNumberProps} /></Form.Item>
            <Form.Item name="unit" label="Đơn vị"><Select style={{ width: 85 }} options={[{ value: "kg", label: "kg" }, { value: "tạ", label: "tạ" }, { value: "tấn", label: "tấn" }]} /></Form.Item>
          </Space>
          <Form.Item name="unitPrice" label={`Đơn giá bán mỗi ${scrapUnit}`} rules={[{ required: true }, { type: "number", min: 0 }]}><InputNumber {...vndInputNumberProps} style={{ width: "100%" }} /></Form.Item>
        </Form>
      </Modal>

      <Modal title={`Ghi thu phiếu phế liệu #${paying?.id ?? ""}`} open={paying !== null} onCancel={() => setPaying(null)} onOk={() => void submitPayment()} confirmLoading={saving} okText="Ghi thu">
        <Form form={paymentForm} layout="vertical">
          <Form.Item name="paymentDate" label="Ngày giờ thu" rules={[{ required: true }]}><DatePicker showTime format="DD/MM/YYYY HH:mm" style={{ width: "100%" }} /></Form.Item>
          <Form.Item name="amount" label="Số tiền" rules={[{ required: true }, { type: "number", min: 1 }]}><InputNumber {...vndInputNumberProps} style={{ width: "100%" }} max={paying ? paying.totalAmount - paying.paidAmount : undefined} /></Form.Item>
          <Form.Item name="paymentMethod" label="Hình thức" rules={[{ required: true }]}><Select options={[{ value: "CASH", label: "Tiền mặt" }, { value: "TRANSFER", label: "Chuyển khoản" }]} /></Form.Item>
          <Form.Item name="collectedBy" label="Người thu" rules={[{ required: true }]}><Input maxLength={80} /></Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
