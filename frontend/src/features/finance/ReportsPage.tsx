import { Alert, Button, Card, Col, DatePicker, Row, Select, Space, Statistic, Tabs, Tag, message } from "antd";
import { PagedTable, ResponsiveTable as Table } from "../../components/PagedTable";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getDocument, listDocuments } from "../stock/stock-api";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { costPerKgToTon, kgToTon } from "../products/product-stock-unit";
import { useSession } from "../auth/auth-store";
import { getYearReport, type YearReport } from "../factory/factory-api";
import { getYearEndBalance, getYearPeriod, type YearEndBalance, type YearPeriod } from "../dashboard/dashboard-api";
import { SHOP_LABELS, useShop } from "../shop/shop-store";
import { listCategories, listProducts, type Category, type Product } from "../products/products-api";
import {
  downloadReportCsv,
  getInventoryReport,
  getInvoiceMargins,
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

function DistributionProfitSummary({ year }: { year: number }) {
  const [balance, setBalance] = useState<YearEndBalance | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true);
    void getYearEndBalance(year).then((value) => { if (active) setBalance(value); })
      .catch(() => { if (active) setBalance(null); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [year]);
  return <Card size="small" loading={loading} style={{ marginBottom: 16 }}>
    {balance ? <>
      <Statistic title={`Lãi năm ${year} đã chốt`} value={formatMoney(balance.profit)} />
      <span>Kỳ {dayjs(balance.periodFrom).format("DD/MM/YYYY")} – {dayjs(balance.periodTo).format("DD/MM/YYYY")}; đã trừ giá vốn hàng bán, hao hụt kiểm kê và chi phí hoạt động.</span>
    </> : <Alert type="info" showIcon message={`Năm ${year} chưa chốt — chưa có lãi năm chính xác`} description="Kiểm kê tồn thực tế và chốt kỳ ở Tổng quan. Lãi hóa đơn/tháng ở tab bên cạnh chỉ là giá tham khảo." />}
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
    ["Lãi quản trị ước tính", report.estimatedProfit],
  ] : [];

  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <span>Báo cáo năm {year}</span>
        {report && <Tag color={report.countStatus === "CLOSED" ? "green" : "orange"}>
          {report.countStatus === "CLOSED" ? "Đã chốt" : report.countStatus === "MISSING" ? "Chưa kiểm kê" : "Chưa chốt"}
        </Tag>}
        <Link to="/quyet-toan-xuong">Xem chi tiết quyết toán Xưởng</Link>
      </Space>
      {error && <Alert type="error" showIcon message={error} />}
      {!report && !error && <Card loading />}
      {report?.estimatedProfit == null && report && <Alert type="info" showIcon style={{ marginBottom: 16 }}
        message="Lợi nhuận sẽ hiển thị khi có số kiểm kê của năm." />}
      <Row gutter={[16, 16]}>
        {metrics.map(([label, value]) => (
          <Col key={label} xs={24} sm={12} xl={6}>
            <Card size="small"><Statistic title={label} value={money(value)} /></Card>
          </Col>
        ))}
      </Row>
    </>
  );
}

export function ReportsPage() {
  const session = useSession();
  const shop = useShop();
  const [book, setBook] = useState<"shop" | "all">("shop");
  const canViewPayroll = session?.role !== "DISTRIBUTION" && (shop === "MANUFACTURING" || book === "all");
  const asTon = shop === "DISTRIBUTION" && book === "shop";
  const unitLabel = asTon ? "tấn" : "kg";
  const showQty = (kg: number) => qtyFormat.format(asTon ? kgToTon(kg) : kg);
  const showCost = (perKg: number) => formatMoney(asTon ? costPerKgToTon(perKg) : perKg);
  const scope = book === "all" ? "all" as const : undefined;
  const [activeTab, setActiveTab] = useState<"profit" | "invoice-margins" | "price-variance" | "sales-by-product" | "inventory" | "shrinkage" | "payroll">(
    "profit",
  );
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [dayjs().startOf("month"), dayjs()]);
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [productId, setProductId] = useState<number | undefined>();
  const [year, setYear] = useState<number>(() => dayjs().year());
  const [period, setPeriod] = useState<YearPeriod | null>(null);

  useEffect(() => {
    let active = true;
    setPeriod(null);
    setProfit(null);
    getYearPeriod(year).then((value) => {
      if (!active) return;
      setPeriod(value);
      setRange([dayjs(value.periodFrom), dayjs(value.periodTo)]);
    }).catch((error) => { if (active) message.error(toApiError(error).message); });
    return () => { active = false; };
  }, [year, shop]);

  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [profit, setProfit] = useState<ProfitReport | null>(null);
  const [invoiceMargins, setInvoiceMargins] = useState<InvoiceGrossMargin[]>([]);
  const [productSales, setProductSales] = useState<ProductSalesRow[]>([]);
  const [inventoryRows, setInventoryRows] = useState<InventoryReportRow[]>([]);
  const [payrollRows, setPayrollRows] = useState<PayrollReportRow[]>([]);
  const [varianceRows, setVarianceRows] = useState<PurchasePriceVarianceRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [shrinkageRows, setShrinkageRows] = useState<{ key: string; date: string; code: string; product: string; category: string; shortageKg: number; surplusKg: number }[]>([]);

  useEffect(() => {
    if (shop !== "DISTRIBUTION" || book !== "shop") return;
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
  }, [shop, book, range]);

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

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const from = range[0].format("YYYY-MM-DD");
      const to = range[1].format("YYYY-MM-DD");
      const [pr, sp, inv, pv, pay, invoices] = await Promise.all([
        getProfitReport(from, to, scope),
        getSalesByProductReport({ from, to, productId, categoryId, scope }),
        getInventoryReport({ from, to, categoryId, scope }),
        getPriceVarianceReport({ from, to, categoryId, scope }),
        canViewPayroll ? getPayrollReport(year, scope) : Promise.resolve([] as PayrollReportRow[]),
        book === "shop" ? getInvoiceMargins(from, to) : Promise.resolve([] as InvoiceGrossMargin[]),
      ]);
      setProfit(pr);
      setInvoiceMargins(invoices);
      setProductSales(sp);
      setInventoryRows(inv);
      setVarianceRows(pv);
      setPayrollRows(pay);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [range, productId, categoryId, year, scope, book, canViewPayroll]);

  useEffect(() => {
    void load();
  }, [load]);

  const invoiceMonths = Object.values(invoiceMargins.reduce<Record<string, { month: string; revenue: number; cost: number; profit: number; missing: boolean; count: number }>>((all, invoice) => {
    const month = invoice.documentDate.slice(0, 7);
    const item = all[month] ?? (all[month] = { month, revenue: 0, cost: 0, profit: 0, missing: false, count: 0 });
    item.count += 1;
    item.revenue += Number(invoice.revenue);
    if (!invoice.costComplete || invoice.estimatedMaterialCost == null || invoice.estimatedGrossProfit == null) item.missing = true;
    else {
      item.cost += Number(invoice.estimatedMaterialCost);
      item.profit += Number(invoice.estimatedGrossProfit);
    }
    return all;
  }, {})).sort((a, b) => a.month.localeCompare(b.month));

  const fromStr = range[0].format("YYYY-MM-DD");
  const toStr = range[1].format("YYYY-MM-DD");

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
          <DatePicker picker="year" aria-label="Năm xem báo cáo" allowClear={false}
            value={dayjs(`${year}-01-01`)}
            onChange={(value) => value && setYear(value.year())} />
          {period && <span>Kỳ {year}: {dayjs(period.periodFrom).format("DD/MM/YYYY")} – {dayjs(period.periodTo).format("DD/MM/YYYY")}</span>}
          <Button
            type="primary"
            disabled={activeTab === "shrinkage" || activeTab === "invoice-margins" || ((shop === "MANUFACTURING" || book === "all") && activeTab === "profit")}
            onClick={() => {
              if (activeTab === "shrinkage" || activeTab === "invoice-margins") return;
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
      <Tabs
        activeKey={activeTab}
        onChange={(k) => setActiveTab(k as typeof activeTab)}
        items={[
          {
            key: "invoice-margins",
            label: "Lãi gộp hóa đơn / tháng",
            children: <>
              <Alert type="info" showIcon style={{ marginBottom: 16 }}
                message="Lãi gộp vật tư tham khảo — chưa phải lãi quyết toán"
                description="Lãi = tiền phiếu sau giảm giá − khối lượng × giá nhập tham khảo tại ngày giao. Không bao gồm tiền công, phụ kiện, phế liệu và hao hụt kiểm kê. Nếu thiếu dữ liệu giá tham khảo, hệ thống để trống lãi, không tự tính bằng 0. Lãi cuối kỳ vẫn lấy từ bản chốt."
              />
              <h3>Lãi gộp theo tháng (chưa trừ chi phí hoạt động)</h3>
              <PagedTable rowKey="month" size="small" loading={loading} dataSource={invoiceMonths} columns={[
                { title: "Tháng", dataIndex: "month", align: "center" },
                { title: "Số hóa đơn", dataIndex: "count", align: "center" },
                { title: "Doanh thu", dataIndex: "revenue", align: "center", render: (v: number) => formatMoney(v) },
                { title: "Giá vật tư tham khảo", dataIndex: "cost", align: "center", render: (v: number, row) => row.missing ? "Thiếu giá tham khảo" : formatMoney(v) },
                { title: "Lãi gộp tham khảo", dataIndex: "profit", align: "center", render: (v: number, row) => row.missing ? "Chưa đủ dữ liệu" : formatMoney(v) },
              ]} />
              <h3 style={{ marginTop: 20 }}>Chi tiết từng hóa đơn</h3>
              <PagedTable<InvoiceGrossMargin> rowKey="documentId" size="small" loading={loading}
                dataSource={invoiceMargins} columns={[
                  { title: "Ngày", dataIndex: "documentDate", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY") },
                  { title: "Số phiếu", dataIndex: "documentCode", align: "center" },
                  { title: "Khách", dataIndex: "customerName" },
                  { title: "Tiền bán", dataIndex: "revenue", align: "center", render: (v: number) => formatMoney(v) },
                  { title: "Giá vật tư", dataIndex: "estimatedMaterialCost", align: "center", render: (v: number | null) => v == null ? "Thiếu giá" : formatMoney(v) },
                  { title: "Lãi gộp tham khảo", dataIndex: "estimatedGrossProfit", align: "center", render: (v: number | null) => v == null ? "Chưa đủ dữ liệu" : formatMoney(v) },
                ]} />
            </>,
          },
          {
            key: "profit",
            label: shop === "DISTRIBUTION" ? "Doanh thu & Công nợ" : "Doanh thu & Lợi nhuận",
            children: (
              <>
                {book === "all" ? (
                  <Alert type="info" showIcon message="Báo cáo gộp theo năm" description={<Link to="/tong-hop-hai-ben">Xem Tổng hợp hai bên</Link>} />
                ) : shop === "MANUFACTURING" ? (
                  <FactoryProfitSummary year={year} />
                ) : (
                <>
                <DistributionProfitSummary year={year} />
                <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic title="Doanh thu bán hàng" value={formatMoney(profit?.revenue ?? 0)} />
                    </Card>
                  </Col>



                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic
                        title={canViewPayroll ? "Chi phí hoạt động & lương" : "Chi phí hoạt động"}
                        value={formatMoney(profit?.operatingExpenses ?? 0)}
                      />
                    </Card>
                  </Col>
                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic title="Tiền thực thu" value={formatMoney(profit?.cashCollected ?? 0)} />
                    </Card>
                  </Col>
                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic title="Doanh thu chưa thu tiền" value={formatMoney(profit?.uncollectedRevenue ?? 0)} />
                    </Card>
                  </Col>
                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic title="Nhập kho trong kỳ (Vào tồn kho)" value={formatMoney(profit?.purchaseStockValue ?? 0)} />
                    </Card>
                  </Col>


                </Row>

                <PagedTable<DailyRevenueRow>
                  rowKey="day"
                  loading={loading}
                  dataSource={profit?.dailyRows ?? []}
                  size="small"
                  columns={[
                    { title: "Ngày", dataIndex: "day", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY") },
                    { title: "Số phiếu bán", dataIndex: "saleCount", align: "center" },
                    { title: "Doanh thu", dataIndex: "revenue", align: "center", render: (v: number) => formatMoney(v) },
                                        { title: "Tiền thực thu", dataIndex: "cashCollected", align: "center", render: (v: number) => formatMoney(v) },
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
                  rowKey="productId"
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
                    Tổng giá trị tồn hiện tại:{" "}
                    <b>{formatMoney(inventoryRows.reduce((s, r) => s + r.inventoryValue, 0))}</b>
                  </span>
                </Space>
                <PagedTable<InventoryReportRow>
                  rowKey="productId"
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
              <Space style={{ marginBottom: 16 }} wrap>
                <Statistic title="Hao hụt" value={kgToTon(shrinkageRows.reduce((sum, row) => sum + row.shortageKg, 0))} suffix="tấn" precision={3} />
                <Statistic title="Thừa kiểm kê" value={kgToTon(shrinkageRows.reduce((sum, row) => sum + row.surplusKg, 0))} suffix="tấn" precision={3} />
              </Space>
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
                      <Space style={{ marginBottom: 16 }} wrap>
                        <DatePicker
                          picker="year"
                          allowClear={false}
                          value={dayjs(`${year}-01-01`)}
                          onChange={(val) => val && setYear(val.year())}
                        />
                      </Space>
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
