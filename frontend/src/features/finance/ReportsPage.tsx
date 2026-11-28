import { Alert, Button, Card, Col, Row, Segmented, Select, Space, Statistic, Tabs, Tag, message } from "antd";
import { PagedTable, ResponsiveTable as Table } from "../../components/PagedTable";
import dayjs, { type Dayjs } from "dayjs";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { getDocument, listDocuments } from "../stock/stock-api";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { costPerKgToTon, kgToTon } from "../products/product-stock-unit";
import { useSession } from "../auth/auth-store";
import { getYearReport, type YearReport } from "../factory/factory-api";
import { getDistributionYearReport, getYearPeriod, type DistributionYearReport, type YearPeriod } from "../dashboard/dashboard-api";
import { useWorkingYear } from "../dashboard/working-year";
import { useWorkingPeriodInfo, visiblePeriod } from "../dashboard/working-period";
import { CarryoverDebtReport } from "./CarryoverDebtReport";
import { SHOP_LABELS, useShop } from "../shop/shop-store";
import { listCategories, listProducts, type Category, type Product } from "../products/products-api";
import {
  downloadReportCsv,
  getInventoryReport,
  getInvoiceMargins,
  getMonthlyMargins,
  type MonthlyMargin,
  getPayrollReport,
  getPriceVarianceReport,
  getProfitReport,
  getSalesByProductReport,
  type DailyRevenueRow,
  type InventoryReportRow,
  type InvoiceGrossMargin,
  type PayrollReportRow,
  type ProductSalesRow,
  type ProfitReport,
  type PurchasePriceVarianceRow,
} from "./finance-api";

const qtyFormat = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });

function DistributionProfitSummary({ year, reportTo }: { year: number; reportTo: string }) {
  const [report, setReport] = useState<DistributionYearReport | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true);
    void getDistributionYearReport(year).then((value) => { if (active) setReport(value); })
      .catch(() => { if (active) setReport(null); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [year]);
  const money = (amount: number | null | undefined) => amount == null ? "—" : formatMoney(amount);
  const atSeparateCutoff = report != null && report.periodTo !== reportTo;
  const metrics: [string, string][] = report ? [
    [atSeparateCutoff ? "Doanh thu đến mốc quyết toán" : "Doanh thu bán hàng (cả kỳ)", money(report.revenue)],
    ["Đã thu từ phiếu bán trong kỳ", money(report.cashCollected)],
    ["Còn phải thu từ phiếu bán trong kỳ", money(report.customerDebt)],
    ["Tiền hàng đã nhập trong kỳ", money(report.purchaseValue)],
    [report.endingKg != null ? `Giá trị tồn cuối kỳ (${qtyFormat.format(Number(report.endingKg))} kg)` : "Giá trị tồn kho cuối kỳ (sau kiểm kê)", money(report.endingValue)],
    ["Giá vốn hàng bán & hao hụt", money(report.materialCost)],
    [atSeparateCutoff ? "Chi phí hoạt động đến mốc quyết toán" : "Chi phí hoạt động (cả kỳ)", money(report.operatingExpenses)],
    [report.status === "CLOSED" ? "Lãi kỳ đã chốt" : atSeparateCutoff ? "Lãi đến mốc quyết toán" : "Lãi cả kỳ (sau kiểm kê)", money(report.profit)],
  ] : [];
  return <Card size="small" loading={loading} style={{ marginBottom: 16 }}>
    <Space wrap style={{ marginBottom: 12 }}>
      <strong>{atSeparateCutoff ? "Quyết toán lợi nhuận theo mốc kiểm kê" : "Quyết toán lợi nhuận cả kỳ"}</strong>
      {report && <Tag color={report.status === "CLOSED" ? "green" : report.status === "COUNTED" ? "blue" : "orange"}>
        {report.status === "CLOSED" ? "Đã chốt kỳ" : report.status === "COUNTED" ? "Đã kiểm kê đủ — sẵn sàng chốt kỳ" : "Tạm tính theo tồn sổ (chưa kiểm kê đủ)"}
      </Tag>}
      {report && <span>Kỳ {dayjs(report.periodFrom).format("DD/MM/YYYY")} – {dayjs(report.periodTo).format("DD/MM/YYYY")}</span>}
      <Link to="/ton-kho">Mở Tồn kho / Kiểm kê</Link>
      <Link to="/chot-nam">Mở Chốt năm</Link>
    </Space>
    {report && report.status !== "CLOSED" && report.uncountedItems > 0 && (
      <Alert type="warning" showIcon style={{ marginBottom: 12 }}
        message={`Còn ${report.uncountedItems} dòng sản phẩm/loại nhôm chưa kiểm kê hoặc có biến động sau kiểm kê`}
        description="Số lãi bên dưới đang tạm tính theo tồn sổ hiện tại. Vào Tồn kho → Kiểm kê / Hao hụt để cân đủ thực tế, hoặc vào Chốt năm để chốt kỳ." />
    )}
    <div className="report-stat-grid">
      {metrics.map(([label, value]) => (
        <Card key={label} size="small"><Statistic title={label} value={value} /></Card>
      ))}
    </div>
  </Card>;
}

function FactoryProfitSummary({ year }: { year: number }) {
  const [report, setReport] = useState<YearReport | null>(null);
  const [error, setError] = useState<string>();

  useEffect(() => {
    let active = true;
    setReport(null);
    setError(undefined);
    void getYearReport(year).then((data) => {
      if (active) setReport(data);
    }).catch((reason) => {
      if (active) setError(toApiError(reason).message);
    });
    return () => { active = false; };
  }, [year]);

  const money = (amount: number | null) => amount == null ? "—" : formatMoney(amount);
  const metrics: [string, number | null][] = report ? [
    ["Tổng doanh thu", report.finishedRevenue + report.scrapRevenue],
    ["Doanh thu thành phẩm", report.finishedRevenue],
    ["Doanh thu phế liệu", report.scrapRevenue],
    ["Tổng tiền thực thu", report.finishedCashCollected + report.scrapCashCollected],
    ["Chi phí nhôm", report.materialCost],
    ["Chi phí nhân viên", report.payrollCost],
    ["Chi phí khác", report.otherExpenses],
    [report.countStatus === "CLOSED" ? "Lãi quản trị đã chốt" : "Lãi quản trị ước tính", report.estimatedProfit],
  ] : [];

  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <span>Báo cáo cả kỳ</span>
        {report && <Tag color={report.countStatus === "CLOSED" ? "green" : "orange"}>
          {report.countStatus === "CLOSED" ? "Đã chốt" : report.countStatus === "MISSING" ? "Chưa kiểm kê" : "Chưa chốt"}
        </Tag>}
        <Link to="/quyet-toan-xuong">Xem chi tiết quyết toán Xưởng</Link>
      </Space>
      {error && <Alert type="error" showIcon message={error} />}
      {!report && !error && <Card loading />}
      {report?.estimatedProfit == null && report && <Alert type="info" showIcon style={{ marginBottom: 16 }}
        message={report.countStatus === "MISSING" ? "Lợi nhuận sẽ hiển thị khi có số kiểm kê của kỳ."
          : "Cần đối chiếu tồn đầu kỳ và số kiểm kê cuối kỳ trước khi tính lợi nhuận."} />}
      <div className="report-stat-grid">
        {metrics.map(([label, value]) => (
          <Card key={label} size="small"><Statistic title={label} value={money(value)} /></Card>
        ))}
      </div>
    </>
  );
}

export function ReportsPage() {
  const session = useSession();
  const shop = useShop();
  const workingYear = useWorkingYear(shop);
  const [book, setBook] = useState<"shop" | "all">("shop");
  const canViewPayroll = session?.role !== "DISTRIBUTION" && (shop === "MANUFACTURING" || book === "all");
  const asTon = shop === "DISTRIBUTION" && book === "shop";
  const unitLabel = asTon ? "tấn" : "kg";
  const showQty = (kg: number) => qtyFormat.format(asTon ? kgToTon(kg) : kg);
  const showCost = (perKg: number) => formatMoney(asTon ? costPerKgToTon(perKg) : perKg);
  const scope = book === "all" ? "all" as const : undefined;
  const [activeTab, setActiveTab] = useState<"profit" | "invoice-margins" | "price-variance" | "sales-by-product" | "inventory" | "shrinkage" | "payroll" | "carryover-debt">(
    "profit",
  );
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [productId, setProductId] = useState<number | undefined>();
  const year = workingYear.year;
  const periodInfo = useWorkingPeriodInfo(year);
  const [fullPeriod, setFullPeriod] = useState<YearPeriod | null>(null);
  const [periodMode, setPeriodMode] = useState<"year" | "month">("year");
  const period = useMemo(() => {
    if (!fullPeriod) return null;
    return periodMode === "month" && workingYear.month ? visiblePeriod(fullPeriod, workingYear.month) : fullPeriod;
  }, [fullPeriod, periodMode, workingYear.month]);
  const range = useMemo<[Dayjs, Dayjs]>(() => period
    ? [dayjs(period.periodFrom), dayjs(period.periodTo)]
    : [dayjs().startOf("month"), dayjs()], [period]);

  useEffect(() => {
    let active = true;
    setFullPeriod(null);
    getYearPeriod(year).then((value) => {
      if (active) setFullPeriod(value);
    }).catch((error) => { if (active) message.error(toApiError(error).message); });
    return () => { active = false; };
  }, [year, shop]);

  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [profit, setProfit] = useState<ProfitReport | null>(null);
  const [invoiceMargins, setInvoiceMargins] = useState<InvoiceGrossMargin[]>([]);
  const [invoiceGrouping, setInvoiceGrouping] = useState<"day" | "month">("month");
  const [monthlyMargins, setMonthlyMargins] = useState<MonthlyMargin[]>([]);
  const [productSales, setProductSales] = useState<ProductSalesRow[]>([]);
  const [inventoryRows, setInventoryRows] = useState<InventoryReportRow[]>([]);
  const [payrollRows, setPayrollRows] = useState<PayrollReportRow[]>([]);
  const [varianceRows, setVarianceRows] = useState<PurchasePriceVarianceRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [shrinkageRows, setShrinkageRows] = useState<{ key: string; date: string; code: string; product: string; category: string; shortageKg: number; surplusKg: number }[]>([]);

  useEffect(() => {
    if (shop !== "DISTRIBUTION" || book !== "shop" || !period?.started) return;
    let cancelled = false;
    const from = range[0].format("YYYY-MM-DD");
    const to = range[1].format("YYYY-MM-DD");
    Promise.all([
      listDocuments({ type: "ADJUSTMENT_OUT", from, to }),
      listDocuments({ type: "ADJUSTMENT_IN", from, to }),
    ]).then(async ([out, incoming]) => {
      const details = await Promise.all([...out, ...incoming].map((doc) => getDocument(doc.id)));
      if (cancelled) return;
      setShrinkageRows(details
        .filter((doc) => doc.status === "CONFIRMED" && doc.reason === "Kiểm kê tồn kho Phân phối")
        .flatMap((doc) => doc.items.map((item) => ({
          key: `${doc.id}-${item.id}`,
          date: doc.documentDate,
          code: doc.documentCode,
          product: item.productName,
          category: item.categoryName ?? "",
          shortageKg: doc.documentType === "ADJUSTMENT_OUT" ? item.quantityBase : 0,
          surplusKg: doc.documentType === "ADJUSTMENT_IN" ? item.quantityBase : 0,
        }))));
    }).catch((error) => { if (!cancelled) message.error(toApiError(error).message); });
    return () => { cancelled = true; };
  }, [shop, book, range, period?.started]);

  useEffect(() => {
    if (!canViewPayroll && activeTab === "payroll") {
      setActiveTab("profit");
    }
  }, [canViewPayroll, activeTab]);

  useEffect(() => {
    if ((shop === "MANUFACTURING" || book === "all") &&
        (activeTab === "sales-by-product" || activeTab === "inventory") ||
        (shop === "DISTRIBUTION" && activeTab === "sales-by-product")) {
      setActiveTab("profit");
    }
  }, [shop, book, activeTab]);

  useEffect(() => {
    Promise.all([listCategories(scope), listProducts({ scope })])
      .then(([c, p]) => {
        setCategories(c);
        setProducts(p);
      })
      .catch(() => undefined);
  }, [scope]);

  useEffect(() => {
    if (!period?.started) {
      setLoading(false);
      setProfit(null);
      setInvoiceMargins([]);
      setMonthlyMargins([]);
      setProductSales([]);
      setInventoryRows([]);
      setVarianceRows([]);
      setPayrollRows([]);
      setShrinkageRows([]);
      return;
    }
    let active = true;
    async function load() {
      setLoading(true);
      try {
        const from = range[0].format("YYYY-MM-DD");
        const to = range[1].format("YYYY-MM-DD");
        const [pr, sp, inv, pv, pay, invoices, months] = await Promise.all([
          getProfitReport(from, to, scope),
          getSalesByProductReport({ from, to, productId, categoryId, scope }),
          getInventoryReport({ from, to, categoryId, scope }),
          getPriceVarianceReport({ from, to, categoryId, scope }),
          canViewPayroll ? getPayrollReport(year, scope, from, to) : Promise.resolve([] as PayrollReportRow[]),
          book === "shop" ? getInvoiceMargins(from, to) : Promise.resolve([] as InvoiceGrossMargin[]),
          book === "shop" ? getMonthlyMargins(from, to) : Promise.resolve([]),
        ]);
        if (!active) return;
        setProfit(pr);
        setInvoiceMargins(invoices);
        setMonthlyMargins(months);
        setProductSales(sp);
        setInventoryRows(inv);
        setVarianceRows(pv);
        setPayrollRows(pay);
      } catch (error) {
        if (active) message.error(toApiError(error).message);
      } finally {
        if (active) setLoading(false);
      }
    }
    void load();
    return () => { active = false; };
  }, [range, productId, categoryId, year, shop, scope, book, canViewPayroll, period?.started]);

  const invoiceGroups = Object.values(invoiceMargins.reduce<Record<string, { date: string; revenue: number; cost: number; profit: number; missing: boolean; count: number }>>((all, invoice) => {
    if (invoice.internal) return all;
    const date = invoice.documentDate.slice(0, invoiceGrouping === "day" ? 10 : 7);
    const item = all[date] ?? (all[date] = { date, revenue: 0, cost: 0, profit: 0, missing: false, count: 0 });
    item.count += 1;
    item.revenue += Number(invoice.revenue);
    if (!invoice.costComplete || invoice.estimatedMaterialCost == null || invoice.estimatedGrossProfit == null) item.missing = true;
    else {
      item.cost += Number(invoice.estimatedMaterialCost);
      item.profit += Number(invoice.estimatedGrossProfit);
    }
    return all;
  }, {})).sort((a, b) => a.date.localeCompare(b.date));

  const fromStr = range[0].format("YYYY-MM-DD");
  const toStr = range[1].format("YYYY-MM-DD");
  const monthView = periodMode === "month" && !!workingYear.month;
  const selectedProfit = profit?.from === fromStr && profit?.to === toStr ? profit : null;

  return (
    <Card
      className="report-page-card"
      title={canViewPayroll ? "Báo cáo doanh thu, lợi nhuận, tồn kho & tiền công"
        : "Báo cáo doanh thu, lợi nhuận & tồn kho"}
      extra={
        <Space wrap>
          {session?.role === "ADMIN" ? (
            <Select
              style={{ width: 220 }}
              value={book}
              onChange={setBook}
              options={[
                { value: "shop", label: SHOP_LABELS[shop] },
                { value: "all", label: "Cả hai cửa hàng" },
              ]}
            />
          ) : null}
          {workingYear.month && activeTab !== "carryover-debt" && (
            <Segmented
              aria-label="Phạm vi báo cáo"
              value={periodMode}
              onChange={(val) => setPeriodMode(val as "year" | "month")}
              options={[
                { label: "Cả kỳ (Từ đầu kỳ)", value: "year" },
                { label: `Tháng ${dayjs(`${workingYear.month}-01`).format("MM/YYYY")}`, value: "month" },
              ]}
            />
          )}
          {activeTab === "carryover-debt" ? <span>Công nợ theo cả kỳ</span>
            : period && <span>{monthView ? `Tháng ${dayjs(`${workingYear.month}-01`).format("MM/YYYY")}` : periodInfo.name}: {period.started ? `${dayjs(period.periodFrom).format("DD/MM/YYYY")} – ${dayjs(period.periodTo).format("DD/MM/YYYY")}${period.closed ? " (đã chốt)" : ""}` : "chưa bắt đầu"}</span>}
          <Button
            type="primary"
            disabled={activeTab === "carryover-debt" || activeTab === "shrinkage" || activeTab === "invoice-margins" || ((shop === "MANUFACTURING" || book === "all") && activeTab === "profit")}
            onClick={() => {
              if (activeTab === "carryover-debt" || activeTab === "shrinkage" || activeTab === "invoice-margins") return;
              void downloadReportCsv({
                type: activeTab,
                scope,
                from: fromStr,
                to: toStr,
                productId,
                categoryId,
                year,
              });
            }}
          >
            Xuất file CSV (Excel)
          </Button>
        </Space>
      }
    >
      {period && !period.started && <Alert type="info" showIcon style={{ marginBottom: 16 }}
        message="Kỳ này chưa có ngày bắt đầu" description={<Link to="/">Vào Tổng quan để lưu ngày bắt đầu trước khi xem Báo cáo theo kỳ</Link>} />}
      {monthView && fullPeriod?.started && !period && activeTab !== "carryover-debt" &&
        <Alert type="info" showIcon style={{ marginBottom: 16 }} message="Tháng đã chọn nằm ngoài khoảng báo cáo của kỳ này." />}
      <Tabs
        activeKey={activeTab}
        onChange={(k) => setActiveTab(k as typeof activeTab)}
        items={[
          {
            key: "carryover-debt",
            label: "Công nợ chuyển kỳ",
            children: fullPeriod?.started ? <CarryoverDebtReport key={`${shop}-${year}`} year={year} /> : null,
          },
          {
            key: "invoice-margins",
            label: "Lãi gộp hóa đơn",
            children: <>
              <Alert type="info" showIcon style={{ marginBottom: 16 }}
                message="Lãi hóa đơn và lãi tháng là số tạm tính"
                description="Giá vốn dùng giá đã nhập trên dòng hàng hoặc giá nhập tham chiếu nếu để trống. Lãi tháng sau chi phí trừ các khoản đã ghi nhận. Phiếu nội bộ không cộng vào doanh thu/lãi tháng. Lãi quyết toán còn phụ thuộc kiểm kê cuối kỳ."
              />
              <h3>Lãi gộp trước chi phí hoạt động và tiền công</h3>
              <Segmented aria-label="Nhóm lãi gộp hóa đơn" value={invoiceGrouping} onChange={setInvoiceGrouping}
                options={[{ label: "Theo tháng", value: "month" }, { label: "Theo ngày", value: "day" }]} />
              <PagedTable rowKey="date" size="small" loading={loading} dataSource={invoiceGroups} columns={[
                { title: invoiceGrouping === "day" ? "Ngày lập phiếu" : "Tháng", dataIndex: "date", align: "center",
                  render: (value: string) => dayjs(value).format(invoiceGrouping === "day" ? "DD/MM/YYYY" : "MM/YYYY") },
                { title: "Số hóa đơn", dataIndex: "count", align: "center" },
                { title: "Doanh thu", dataIndex: "revenue", align: "center", render: (v: number) => formatMoney(v) },
                { title: "Giá vốn vật tư", dataIndex: "cost", align: "center", render: (v: number, row) => row.missing ? "Thiếu giá vốn" : formatMoney(v) },
                { title: "Lãi gộp ước tính", dataIndex: "profit", align: "center", render: (v: number, row) => row.missing ? "Chưa đủ dữ liệu" : formatMoney(v) },
              ]} />
              <h3 style={{ marginTop: 20 }}>Lãi tháng sau chi phí (tạm tính)</h3>
              <PagedTable<MonthlyMargin> rowKey="month" size="small" loading={loading} dataSource={monthlyMargins} columns={[
                { title: "Tháng", dataIndex: "month" },
                { title: "Lãi gộp", dataIndex: "grossProfit", render: (v: number | null) => v == null ? "Thiếu giá vốn" : formatMoney(v) },
                { title: "Chi hoạt động", dataIndex: "operatingCost", render: (v: number) => formatMoney(v) },
                { title: "Chi phí công thợ", dataIndex: "payrollCost", render: (v: number) => formatMoney(v) },
                { title: "Lãi sau chi phí", dataIndex: "profitAfterExpenses", render: (v: number | null) => v == null ? "Chưa đủ dữ liệu" : formatMoney(v) },
              ]} />
              <h3 style={{ marginTop: 20 }}>Chi tiết từng hóa đơn</h3>
              <PagedTable<InvoiceGrossMargin> rowKey="documentId" size="small" loading={loading}
                dataSource={invoiceMargins} columns={[
                  { title: "Ngày", dataIndex: "documentDate", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY") },
                  { title: "Số phiếu", dataIndex: "documentCode", align: "center" },
                  { title: "Khách / nơi nhận", dataIndex: "customerName", render: (v: string, row) => row.internal ? <Tag color="blue">Nội bộ</Tag> : v },
                  { title: "Tiền bán / giá chuyển", dataIndex: "revenue", align: "center", render: (v: number) => formatMoney(v) },
                  { title: "Giá vật tư / giá chuyển", dataIndex: "estimatedMaterialCost", align: "center", render: (v: number | null) => v == null ? "Thiếu giá" : formatMoney(v) },
                  { title: "Lãi gộp ước tính", dataIndex: "estimatedGrossProfit", align: "center", render: (v: number | null) => v == null ? "Chưa đủ dữ liệu" : formatMoney(v) },
                ]} />
            </>,
          },
          {
            key: "profit",
            label: "Doanh thu & Lợi nhuận",
            children: (
              <>
                {book === "all" ? (
                  <Alert type="info" showIcon message="Báo cáo gộp theo kỳ không còn được sử dụng" />
                ) : shop === "MANUFACTURING" ? (
                  monthView ? <>
                    <Alert type="info" showIcon style={{ marginBottom: 16 }}
                      message="Số liệu phiếu bán theo tháng"
                      description="Xem tab Lãi gộp hóa đơn để đối chiếu lãi tháng tạm tính. Lãi quyết toán Xưởng được tính ở chế độ Cả kỳ." />
                    <div className="report-stat-grid">
                      {([
                        ["Doanh thu thành phẩm", selectedProfit?.revenue],
                        ["Đã thu từ phiếu bán trong tháng", selectedProfit?.cashCollected],
                        ["Còn phải thu từ phiếu bán trong tháng", selectedProfit?.uncollectedRevenue],
                      ] as const).map(([label, value]) => <Card key={label} size="small">
                        <Statistic title={label} value={value == null ? "—" : formatMoney(value)} />
                      </Card>)}
                    </div>
                  </> : <FactoryProfitSummary year={year} />
                ) : (
                <>
                {!monthView && <DistributionProfitSummary year={year} reportTo={toStr} />}
                <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                  <Col xs={24} md={8}>
                    <Card size="small">
                      <Statistic title="Doanh thu bán hàng" value={formatMoney(profit?.revenue ?? 0)} />
                    </Card>
                  </Col>



                  <Col xs={24} md={8}>
                    <Card size="small">
                      <Statistic
                        title={canViewPayroll ? "Chi phí hoạt động & lương" : "Chi phí hoạt động"}
                        value={formatMoney(profit?.operatingExpenses ?? 0)}
                      />
                    </Card>
                  </Col>
                  <Col xs={24} md={8}>
                    <Card size="small">
                      <Statistic title="Đã thu từ hóa đơn trong kỳ" value={formatMoney(profit?.cashCollected ?? 0)} />
                    </Card>
                  </Col>
                  <Col xs={24} md={12}>
                    <Card size="small">
                      <Statistic title="Tiền còn phải thu từ phiếu bán trong kỳ" value={formatMoney(profit?.uncollectedRevenue ?? 0)} />
                    </Card>
                  </Col>
                  <Col xs={24} md={12}>
                    <Card size="small">
                      <Statistic title="Nhập kho trong kỳ (Vào tồn kho)" value={formatMoney(profit?.purchaseStockValue ?? 0)} />
                    </Card>
                  </Col>


                </Row>

                <Alert type="info" showIcon style={{ marginBottom: 12 }}
                  message="Số phiếu bán gồm cả phiếu chuyển nội bộ sang Xưởng. Doanh thu và đã thu chỉ tính phiếu bán cho khách; cột đã thu cộng cả lần khách trả sau ngày bán." />
                <PagedTable<DailyRevenueRow>
                  rowKey="day"
                  loading={loading}
                  dataSource={profit?.dailyRows ?? []}
                  size="small"
                  columns={[
                    { title: "Ngày", dataIndex: "day", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY") },
                    { title: "Số phiếu bán", dataIndex: "saleCount", align: "center" },
                    { title: "Doanh thu", dataIndex: "revenue", align: "center", render: (v: number) => formatMoney(v) },
                    { title: "Đã thu từ phiếu ngày này", dataIndex: "cashCollected", align: "center", render: (v: number) => formatMoney(v) },
                    {
                      title: "Chi phí hoạt động",
                      dataIndex: "operatingExpense",
                      align: "center",
                      render: (v: number) => formatMoney(v),
                    },
                  ]}
                />
                </>
                )}
              </>
            ),
          },
          {
            key: "price-variance",
            label: "Chênh lệch giá nhập",
            children: (
              <>
                <Alert type="info" showIcon style={{ marginBottom: 16 }} message="So sánh giá mua trên phiếu với giá nhập chuẩn tại thời điểm nhập. Chênh lệch dương nghĩa là mua cao hơn giá chuẩn." />
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message="So sánh giá nhập thực tế với giá nhập chuẩn đã đặt cho từng ngày"
                  description="Mỗi dòng là một mốc giá chuẩn có phát sinh nhập hàng trong kỳ. Chênh lệch dương nghĩa là nhập đắt hơn giá chuẩn, lãi thực thấp hơn lãi chuẩn."
                />
                <Space style={{ marginBottom: 16 }} wrap>
                  <Select
                    allowClear
                    placeholder="Lọc loại nhôm"
                    style={{ width: 200 }}
                    value={categoryId}
                    onChange={setCategoryId}
                    options={categories.map((c) => ({ value: c.id, label: c.name }))}
                  />
                  <span>
                    Tổng chênh lệch:{" "}
                    <b style={{ color: varianceRows.reduce((s, r) => s + r.varianceValue, 0) > 0 ? "var(--color-danger)" : "var(--color-success)" }}>
                      {formatMoney(varianceRows.reduce((s, r) => s + r.varianceValue, 0))}
                    </b>
                  </span>
                </Space>
                <PagedTable<PurchasePriceVarianceRow>
                  rowKey={(r) => `${r.productId}-${r.effectiveFrom}-${r.standardPricePerBase}`}
                  loading={loading}
                  dataSource={varianceRows}
                  columns={[
                    { title: "Mã nhôm", dataIndex: "productCode", align: "center" },
                    { title: "Tên sản phẩm", dataIndex: "productName", align: "center" },
                    { title: "Loại nhôm", dataIndex: "categoryName", align: "center" },
                    {
                      title: "Giá chuẩn từ ngày",
                      dataIndex: "effectiveFrom",
                      align: "center",
                      render: (v: string | null) => (v ? dayjs(v).format("DD/MM/YYYY") : "—"),
                    },
                    {
                      title: `Giá nhập chuẩn / ${unitLabel}`,
                      dataIndex: "standardPricePerBase",
                      align: "center",
                      render: (v: number) => showCost(v),
                    },
                    {
                      title: `Giá nhập thực tế BQ / ${unitLabel}`,
                      dataIndex: "actualAverageCostPerBase",
                      align: "center",
                      render: (v: number) => showCost(v),
                    },
                    {
                      title: `SL nhập (${unitLabel})`,
                      dataIndex: "actualQuantityBase",
                      align: "center",
                      render: (v: number) => showQty(v),
                    },
                    {
                      title: "Giá trị nhập thực tế",
                      dataIndex: "actualPurchaseValue",
                      align: "center",
                      render: (v: number) => formatMoney(v),
                    },
                    {
                      title: "Chênh lệch",
                      dataIndex: "varianceValue",
                      align: "center",
                      render: (v: number) => (
                        <b style={{ color: v > 0 ? "var(--color-danger)" : v < 0 ? "var(--color-success)" : undefined }}>
                          {v > 0 ? "+" : ""}
                          {formatMoney(v)}
                        </b>
                      ),
                    },
                    {
                      title: "Chênh lệch %",
                      dataIndex: "variancePercent",
                      align: "center",
                      render: (v: number | null) => (v == null ? "—" : `${v > 0 ? "+" : ""}${v.toFixed(2)}%`),
                    },
                  ]}
                />
              </>
            ),
          },
          {
            key: "sales-by-product",
            label: "Doanh thu theo sản phẩm",
            children: (
              <>
                <Space style={{ marginBottom: 16 }} wrap>
                  <Select
                    allowClear
                    placeholder="Lọc loại nhôm"
                    style={{ width: 200 }}
                    value={categoryId}
                    onChange={setCategoryId}
                    options={categories.map((c) => ({ value: c.id, label: c.name }))}
                  />
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    placeholder="Lọc sản phẩm"
                    style={{ width: 280 }}
                    value={productId}
                    onChange={setProductId}
                    options={products.map((p) => ({ value: p.id, label: `${p.productCode} - ${p.name}` }))}
                  />
                </Space>
                <PagedTable<ProductSalesRow>
                  rowKey={(row) => `${row.productId}-${row.categoryName}`}
                  loading={loading}
                  dataSource={productSales}
                  columns={[
                    { title: "Mã nhôm", dataIndex: "productCode", width: 130, align: "center" },
                    { title: "Tên sản phẩm", dataIndex: "productName", align: "center" },
                    { title: "Loại nhôm", dataIndex: "categoryName", align: "center" },
                    {
                      title: `SL đã bán (${unitLabel})`,
                      align: "center",
                      render: (_, r) => showQty(r.quantityBaseSold),
                    },
                    { title: "Doanh thu", dataIndex: "revenue", align: "center", render: (v: number) => formatMoney(v) },
                                      ]}
                />
              </>
            ),
          },
          {
            key: "inventory",
            label: "Báo cáo nhập - xuất - tồn",
            children: (
              <>
                <Space style={{ marginBottom: 16 }} wrap>
                  <Select
                    allowClear
                    placeholder="Lọc loại nhôm"
                    style={{ width: 200 }}
                    value={categoryId}
                    onChange={setCategoryId}
                    options={categories.map((c) => ({ value: c.id, label: c.name }))}
                  />
                  <span>
                    {shop === "DISTRIBUTION" ? "Tổng giá trị tồn cuối khoảng xem: " : "Tổng giá trị tồn hiện tại: "}
                    <b>{inventoryRows.some(row => row.inventoryValue == null) ? "Chưa đủ dữ liệu giá nhập"
                      : formatMoney(inventoryRows.reduce((sum, row) => sum + Number(row.inventoryValue), 0))}</b>
                  </span>
                </Space>
                <PagedTable<InventoryReportRow>
                  rowKey={(row) => `${row.productId}-${row.categoryName}`}
                  loading={loading}
                  dataSource={inventoryRows}
                  columns={[
                    { title: "Mã nhôm", dataIndex: "productCode", width: 120, align: "center" },
                    { title: "Tên sản phẩm", dataIndex: "productName", align: "center" },
                    { title: "Đơn vị", align: "center", width: 70, render: () => unitLabel },
                    {
                      title: "Tồn đầu kỳ",
                      dataIndex: "openingQuantityBase",
                      align: "center",
                      render: (v: number) => showQty(v),
                    },
                    {
                      title: "Nhập trong kỳ",
                      dataIndex: "inQuantityBase",
                      align: "center",
                      render: (v: number) => showQty(v),
                    },
                    {
                      title: "Xuất trong kỳ",
                      dataIndex: "outQuantityBase",
                      align: "center",
                      render: (v: number) => showQty(v),
                    },
                    {
                      title: "Tồn cuối kỳ",
                      dataIndex: "closingQuantityBase",
                      align: "center",
                      render: (v: number) => <b>{showQty(v)}</b>,
                    },
                  ]}
                />
              </>
            ),
          },
          ...(shop === "DISTRIBUTION" && book === "shop" ? [{
            key: "shrinkage" as const,
            label: "Hao hụt Phân phối",
            children: <>
              <Alert type="info" showIcon style={{ marginBottom: 16 }}
                message="Hao hụt theo các lần kiểm kê trong khoảng ngày đã chọn"
                description={<span>Nhập số cân thực tế tại <Link to="/ton-kho">Tồn kho → Kiểm kê / Hao hụt</Link>. Hệ thống ghi phần chênh lệch thành phiếu điều chỉnh và tổng hợp tại đây.</span>} />
              <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                <Col xs={24} sm={12}>
                  <Card size="small" style={{ height: "100%" }}>
                    <Statistic title="Hao hụt" value={kgToTon(shrinkageRows.reduce((sum, row) => sum + row.shortageKg, 0))} suffix="tấn" precision={3} />
                  </Card>
                </Col>
                <Col xs={24} sm={12}>
                  <Card size="small" style={{ height: "100%" }}>
                    <Statistic title="Thừa kiểm kê" value={kgToTon(shrinkageRows.reduce((sum, row) => sum + row.surplusKg, 0))} suffix="tấn" precision={3} />
                  </Card>
                </Col>
              </Row>
              <PagedTable rowKey="key" dataSource={shrinkageRows} columns={[
                { title: "Ngày kiểm kê", dataIndex: "date", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY") },
                { title: "Phiếu điều chỉnh", dataIndex: "code", align: "center" },
                { title: "Sản phẩm", dataIndex: "product", align: "center" },
                { title: "Loại nhôm", dataIndex: "category", align: "center" },
                { title: "Hao hụt (kg)", dataIndex: "shortageKg", align: "center", render: (v: number) => qtyFormat.format(v) },
                { title: "Thừa (kg)", dataIndex: "surplusKg", align: "center", render: (v: number) => qtyFormat.format(v) },
              ]} />
            </>,
          }] : []),
          ...(canViewPayroll
            ? [
                {
                  key: "payroll" as const,
                  label: "Tổng hợp tiền công",
                  children: (
                    <>
                      <Table<PayrollReportRow>
                        rowKey="periodId"
                        loading={loading}
                        dataSource={payrollRows}
                        pagination={false}
                        columns={[
                          { title: "Kỳ công", render: (_, r) => `Tháng ${r.month}/${r.year}`, align: "center" },
                          {
                            title: "Trạng thái",
                            dataIndex: "status",
                            align: "center",
                            render: (v: string) =>
                              v === "CLOSED" ? <Tag color="green">Đã chốt</Tag> : <Tag color="gold">Đang mở</Tag>,
                          },
                          { title: "Số NV", dataIndex: "employeeCount", align: "center" },
                          { title: "Tổng ngày công", dataIndex: "totalCreditedDays", align: "center" },
                          {
                            title: "Lương cơ bản",
                            dataIndex: "totalBasePay",
                            align: "center",
                            render: (v: number) => formatMoney(v),
                          },
                          {
                            title: "Tăng ca",
                            dataIndex: "totalOvertimePay",
                            align: "center",
                            render: (v: number) => formatMoney(v),
                          },
                          {
                            title: "Phụ cấp",
                            dataIndex: "totalAllowances",
                            align: "center",
                            render: (v: number) => formatMoney(v),
                          },
                          {
                            title: "Khấu trừ",
                            dataIndex: "totalDeductions",
                            align: "center",
                            render: (v: number) => formatMoney(v),
                          },
                          {
                            title: "Tổng thực lĩnh",
                            dataIndex: "grandTotalPay",
                            align: "center",
                            render: (v: number) => <b>{formatMoney(v)}</b>,
                          },
                          {
                            title: "Đã chi trả",
                            dataIndex: "paidAmount",
                            align: "center",
                            render: (v: number) => formatMoney(v),
                          },
                        ]}
                      />
                    </>
                  ),
                },
              ]
            : []),
        ].filter((item) => !(book === "all" && item.key === "invoice-margins") && !((shop === "MANUFACTURING" || book === "all") &&
          (item.key === "sales-by-product" || item.key === "inventory")) &&
          !(shop === "DISTRIBUTION" && item.key === "sales-by-product"))}
      />
    </Card>
  );
}
