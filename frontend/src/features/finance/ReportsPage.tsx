import { Alert, Button, Card, Col, DatePicker, Row, Select, Space, Statistic, Table, Tabs, Tag, message } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { costPerKgToTon, kgToTon } from "../products/product-stock-unit";
import { useSession } from "../auth/auth-store";
import { SHOP_LABELS, useShop } from "../shop/shop-store";
import { listCategories, listProducts, type Category, type Product } from "../products/products-api";
import {
  downloadReportCsv,
  getInventoryReport,
  getPayrollReport,
  getPriceVarianceReport,
  getProfitReport,
  getSalesByProductReport,
  type DailyRevenueRow,
  type InventoryReportRow,
  type PayrollReportRow,
  type ProductSalesRow,
  type ProfitReport,
  type PurchasePriceVarianceRow,
} from "./finance-api";

const { RangePicker } = DatePicker;
const qtyFormat = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });

export function ReportsPage() {
  const session = useSession();
  const shop = useShop();
  const canViewPayroll = session?.role !== "DISTRIBUTION";
  const [book, setBook] = useState<"shop" | "all">("shop");
  const asTon = shop === "DISTRIBUTION" && book === "shop";
  const unitLabel = asTon ? "tấn" : "kg";
  const showQty = (kg: number) => qtyFormat.format(asTon ? kgToTon(kg) : kg);
  const showCost = (perKg: number) => formatMoney(asTon ? costPerKgToTon(perKg) : perKg);
  const scope = book === "all" ? "all" as const : undefined;
  const [activeTab, setActiveTab] = useState<"profit" | "price-variance" | "sales-by-product" | "inventory" | "payroll">(
    "profit",
  );
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [dayjs().startOf("month"), dayjs()]);
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [productId, setProductId] = useState<number | undefined>();
  const [year, setYear] = useState<number>(() => dayjs().year());

  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [profit, setProfit] = useState<ProfitReport | null>(null);
  const [productSales, setProductSales] = useState<ProductSalesRow[]>([]);
  const [inventoryRows, setInventoryRows] = useState<InventoryReportRow[]>([]);
  const [payrollRows, setPayrollRows] = useState<PayrollReportRow[]>([]);
  const [varianceRows, setVarianceRows] = useState<PurchasePriceVarianceRow[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!canViewPayroll && activeTab === "payroll") {
      setActiveTab("profit");
    }
  }, [canViewPayroll, activeTab]);

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
      const [pr, sp, inv, pv, pay] = await Promise.all([
        getProfitReport(from, to, scope),
        getSalesByProductReport({ from, to, productId, categoryId, scope }),
        getInventoryReport({ from, to, categoryId, scope }),
        getPriceVarianceReport({ from, to, categoryId, scope }),
        canViewPayroll ? getPayrollReport(year, scope) : Promise.resolve([] as PayrollReportRow[]),
      ]);
      setProfit(pr);
      setProductSales(sp);
      setInventoryRows(inv);
      setVarianceRows(pv);
      setPayrollRows(pay);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [range, productId, categoryId, year, scope, canViewPayroll]);

  useEffect(() => {
    void load();
  }, [load]);

  const fromStr = range[0].format("YYYY-MM-DD");
  const toStr = range[1].format("YYYY-MM-DD");

  return (
    <Card
      title="Báo cáo doanh thu, lợi nhuận, tồn kho & tiền công"
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
          <RangePicker
            value={range}
            allowClear={false}
            format="DD/MM/YYYY"
            onChange={(vals) => {
              if (vals?.[0] && vals[1]) setRange([vals[0], vals[1]]);
            }}
          />
          <Button
            type="primary"
            onClick={() =>
              void downloadReportCsv({
                type: activeTab,
                scope,
                from: fromStr,
                to: toStr,
                productId,
                categoryId,
                year,
              })
            }
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
            key: "profit",
            label: "Doanh thu & Lợi nhuận",
            children: (
              <>
                <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic title="Doanh thu bán hàng" value={formatMoney(profit?.revenue ?? 0)} />
                    </Card>
                  </Col>
                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic title="Giá vốn hàng bán" value={formatMoney(profit?.cogs ?? 0)} />
                    </Card>
                  </Col>
                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic title="Lãi gộp (Doanh thu - Giá vốn)" value={formatMoney(profit?.grossProfit ?? 0)} />
                    </Card>
                  </Col>
                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic
                        title="Lợi nhuận thuần dự kiến"
                        value={formatMoney(profit?.netProfit ?? 0)}
                        valueStyle={{ color: (profit?.netProfit ?? 0) >= 0 ? "#389e0d" : "#cf1322" }}
                      />
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
                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic
                        title="Lãi gộp theo giá nhập chuẩn"
                        value={formatMoney(profit?.standardGrossProfit ?? 0)}
                        valueStyle={{ color: (profit?.standardGrossProfit ?? 0) >= 0 ? "#389e0d" : "#cf1322" }}
                      />
                    </Card>
                  </Col>
                  <Col xs={24} md={6}>
                    <Card size="small">
                      <Statistic
                        title="Chênh lệch giá nhập (thực tế − chuẩn)"
                        value={formatMoney(profit?.priceVariance ?? 0)}
                        valueStyle={{ color: (profit?.priceVariance ?? 0) > 0 ? "#cf1322" : "#389e0d" }}
                      />
                    </Card>
                  </Col>
                </Row>
                {profit && !profit.standardCostComplete ? (
                  <Alert
                    type="warning"
                    showIcon
                    style={{ marginBottom: 16 }}
                    message="Một số dòng bán cũ chưa có giá nhập chuẩn"
                    description="Những dòng bán phát sinh trước khi bật lịch sử giá nhập không có chi phí chuẩn, nên chúng đang góp 0 đ vào giá vốn chuẩn. Lãi chuẩn và chênh lệch giá nhập chỉ chính xác từ mốc bắt đầu ghi lịch sử giá."
                  />
                ) : null}
                <Table<DailyRevenueRow>
                  rowKey="day"
                  loading={loading}
                  dataSource={profit?.dailyRows ?? []}
                  size="small"
                  pagination={{ pageSize: 31 }}
                  columns={[
                    { title: "Ngày", dataIndex: "day", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY") },
                    { title: "Số phiếu bán", dataIndex: "saleCount", align: "center" },
                    { title: "Doanh thu", dataIndex: "revenue", align: "center", render: (v: number) => formatMoney(v) },
                    { title: "Giá vốn", dataIndex: "cogs", align: "center", render: (v: number) => formatMoney(v) },
                    {
                      title: "Lãi gộp",
                      dataIndex: "grossProfit",
                      align: "center",
                      render: (v: number) => <b>{formatMoney(v)}</b>,
                    },
                    {
                      title: "Giá vốn chuẩn",
                      dataIndex: "standardCogs",
                      align: "center",
                      render: (v: number) => formatMoney(v),
                    },
                    {
                      title: "Lãi gộp chuẩn",
                      dataIndex: "standardGrossProfit",
                      align: "center",
                      render: (v: number) => formatMoney(v),
                    },
                    {
                      title: "Chênh lệch giá nhập",
                      dataIndex: "priceVariance",
                      align: "center",
                      render: (v: number) => (
                        <span style={{ color: v > 0 ? "#cf1322" : v < 0 ? "#389e0d" : undefined }}>
                          {v > 0 ? "+" : ""}
                          {formatMoney(v)}
                        </span>
                      ),
                    },
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
            ),
          },
          {
            key: "price-variance",
            label: "Chênh lệch giá nhập",
            children: (
              <>
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
                    <b style={{ color: varianceRows.reduce((s, r) => s + r.varianceValue, 0) > 0 ? "#cf1322" : "#389e0d" }}>
                      {formatMoney(varianceRows.reduce((s, r) => s + r.varianceValue, 0))}
                    </b>
                  </span>
                </Space>
                <Table<PurchasePriceVarianceRow>
                  rowKey={(r) => `${r.productId}-${r.effectiveFrom}-${r.standardPricePerBase}`}
                  loading={loading}
                  dataSource={varianceRows}
                  pagination={{ pageSize: 20 }}
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
                        <b style={{ color: v > 0 ? "#cf1322" : v < 0 ? "#389e0d" : undefined }}>
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
                <Table<ProductSalesRow>
                  rowKey="productId"
                  loading={loading}
                  dataSource={productSales}
                  pagination={{ pageSize: 20 }}
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
                    { title: "Giá vốn", dataIndex: "cogs", align: "center", render: (v: number) => formatMoney(v) },
                    {
                      title: "Lãi gộp",
                      dataIndex: "grossProfit",
                      align: "center",
                      render: (v: number) => <b>{formatMoney(v)}</b>,
                    },
                    {
                      title: "Giá vốn chuẩn",
                      dataIndex: "standardCogs",
                      align: "center",
                      render: (v: number) => formatMoney(v),
                    },
                    {
                      title: "Lãi gộp chuẩn",
                      dataIndex: "standardGrossProfit",
                      align: "center",
                      render: (v: number) => formatMoney(v),
                    },
                    {
                      title: "Chênh lệch giá nhập",
                      dataIndex: "priceVariance",
                      align: "center",
                      render: (v: number) => (
                        <span style={{ color: v > 0 ? "#cf1322" : v < 0 ? "#389e0d" : undefined }}>
                          {v > 0 ? "+" : ""}
                          {formatMoney(v)}
                        </span>
                      ),
                    },
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
                <Space style={{ marginBottom: 16 }}>
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
                <Table<InventoryReportRow>
                  rowKey="productId"
                  loading={loading}
                  dataSource={inventoryRows}
                  pagination={{ pageSize: 20 }}
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
                    {
                      title: `Giá vốn BQ / ${unitLabel}`,
                      dataIndex: "averageCostPerBase",
                      align: "center",
                      render: (v: number) => showCost(v),
                    },
                    { title: "Giá trị tồn", dataIndex: "inventoryValue", align: "center", render: (v: number) => <b>{formatMoney(v)}</b> },
                  ]}
                />
              </>
            ),
          },
          ...(canViewPayroll
            ? [
                {
                  key: "payroll" as const,
                  label: "Tổng hợp tiền công",
                  children: (
                    <>
                      <Space style={{ marginBottom: 16 }}>
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
        ]}
      />
    </Card>
  );
}
