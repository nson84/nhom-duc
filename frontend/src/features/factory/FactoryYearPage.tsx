import { CheckOutlined, CloseOutlined, DollarOutlined, PlusOutlined, SaveOutlined } from "@ant-design/icons";
import { useSession } from "../auth/auth-store";
import { Alert, Card, Col, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Row, Select, Space, Statistic, Tag, Typography, message } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AppActionButton } from "../../components/AppButtons";
import { PagedTable, ResponsiveTable as Table } from "../../components/PagedTable";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { qtyInputNumberProps, vndInputNumberProps } from "../../utils/vnd-input";
import { useCanWrite } from "../auth/auth-store";
import { YearEndBalanceCard } from "../dashboard/YearEndBalanceCard";
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
  const session = useSession();
  const canWrite = useCanWrite();
  const [year, setYear] = useState(dayjs().subtract(1, "year").year());
  const [periodFrom, setPeriodFrom] = useState<Dayjs>(dayjs().startOf("year"));
  const [periodTo, setPeriodTo] = useState<Dayjs>(dayjs().endOf("year"));
  const [report, setReport] = useState<YearReport | null>(null);
  const [settlementMonth, setSettlementMonth] = useState<string | undefined>();
  const [settlement, setSettlement] = useState<SettlementReport | null>(null);
  const [count, setCount] = useState<YearCount | null>(null);
  const [rawCounts, setRawCounts] = useState<RawCount[]>([]);
  const [rawInputs, setRawInputs] = useState<{ categoryId: number; categoryName: string; quantityKg: number }[]>([]);
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
  const [countForm] = Form.useForm<YearCountInput>();
  const [scrapForm] = Form.useForm<ScrapForm>();
  const scrapUnit = Form.useWatch("unit", scrapForm) ?? "kg";
  const [paymentForm] = Form.useForm<PaymentForm>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [nextReport, nextCount, nextSales, nextSettlement, nextRaw, nextCategories, nextSaleCosts] = await Promise.all([
        getYearReport(year), getYearCount(year), listScrapSales(year), getSettlement(year, settlementMonth),
        getRawCounts(year), listCategories(), getSaleCostReport(year, saleCategoryId),
      ]);
      setReport(nextReport);
      setCount(nextCount);
      setPeriodFrom(dayjs(nextCount.periodFrom));
      setPeriodTo(dayjs(nextCount.periodTo));
      setSales(nextSales);
      setSettlement(nextSettlement);
      setRawCounts(nextRaw);
      const savedRaw = new Map(nextRaw.map((row) => [row.categoryId, row]));
      setRawInputs([
        ...nextCategories.map((category) => ({
          categoryId: category.id, categoryName: category.name,
          quantityKg: savedRaw.get(category.id)?.quantityKg ?? 0,
        })),
        ...nextRaw.filter((row) => !nextCategories.some((category) => category.id === row.categoryId)),
      ]);
      setCategories(nextCategories);
      setSaleCosts(nextSaleCosts);
      countForm.setFieldsValue(nextCount);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [year, settlementMonth, saleCategoryId, countForm]);

  useEffect(() => { void load(); }, [load]);

  const closed = count?.status === "CLOSED";
  const canEdit = canWrite && !closed && year <= dayjs().year();

  async function persistInventory(close = false) {
    const values = await countForm.validateFields();
    return saveInventory(year, {
      ...values, periodFrom: periodFrom.format("YYYY-MM-DD"), periodTo: periodTo.format("YYYY-MM-DD"),
      openingKg: count?.openingKg ?? 0, openingValue: count?.openingValue ?? 0,
      rawKg: count?.rawKg ?? 0, rawValue: count?.rawValue ?? 0,
      scrapKg: values.scrapKg ?? 0, scrapValue: values.scrapValue ?? 0,
      workInProgressKg: 0, workInProgressValue: 0, finishedKg: 0, finishedValue: 0,
    }, rawInputs, close);
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
      title: `Chốt kỳ ${year} đến ${periodTo.format("DD/MM/YYYY")}?`,
      content: `Kỳ từ ${periodFrom.format("DD/MM/YYYY")} đến ${periodTo.format("DD/MM/YYYY")} sẽ bị khóa. Tồn và công nợ tiếp tục từ ngày kế tiếp.`,
      okText: "Chốt năm",
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
      <Card title="Quyết toán nhôm xưởng" extra={<DatePicker picker="year" value={dayjs(`${year}-01-01`)} onChange={(value) => { if (value) { setYear(value.year()); setSettlementMonth(undefined); } }} />}>
        <Alert type="info" showIcon message="Phiếu nhập và phiếu bán thành phẩm không tự trừ tồn xưởng. Khối lượng còn lại được kiểm kê một lần khi chốt năm." />
        {report && (
          <>
            <Row gutter={[12, 12]} style={{ marginTop: 16 }}>
              <Col xs={12} md={6}><Statistic title="Nhôm đầu năm" value={showKg(report.openingKg)} /></Col>
              <Col xs={12} md={6}><Statistic title="Nhôm nhập" value={showKg(report.purchasedKg)} /></Col>
              <Col xs={12} md={6}><Statistic title="Thành phẩm đã bán" value={showKg(report.finishedSoldKg)} /></Col>
              <Col xs={12} md={6}><Statistic title="Phế liệu đã bán" value={showKg(report.scrapSoldKg)} /></Col>
              <Col xs={12} md={6}><Statistic title="Chênh lệch ban đầu" value={showKg(report.initialDiscrepancyKg)} /></Col>
              <Col xs={12} md={6}><Statistic title="Còn cuối năm" value={showKg(report.closingKg)} /></Col>
              <Col xs={12} md={6}><Statistic title="Hao hụt ước tính" value={showKg(report.estimatedLossKg)} valueStyle={report.estimatedLossKg != null && report.estimatedLossKg < 0 ? { color: "var(--color-danger)" } : undefined} /></Col>
              <Col xs={12} md={6}><Statistic title="Lãi quản trị ước tính" value={showMoney(report.estimatedProfit)} /></Col>
            </Row>
            <Descriptions size="small" column={{ xs: 1, md: 3 }} style={{ marginTop: 20 }}>
              <Descriptions.Item label="Tiền nhôm nhập">{showMoney(report.purchaseValue)}</Descriptions.Item>
              <Descriptions.Item label="Giá trị tồn đầu năm">{showMoney(report.openingValue)}</Descriptions.Item>
              <Descriptions.Item label="Giá trị tồn cuối năm">{showMoney(report.closingValue)}</Descriptions.Item>
              <Descriptions.Item label="Chi phí nhôm trong năm">{showMoney(report.materialCost)}</Descriptions.Item>
              <Descriptions.Item label="Doanh thu thành phẩm">{showMoney(report.finishedRevenue)}</Descriptions.Item>
              <Descriptions.Item label="Doanh thu phế liệu">{showMoney(report.scrapRevenue)}</Descriptions.Item>
              <Descriptions.Item label="Tiền công theo kỳ">{showMoney(report.payrollCost)}</Descriptions.Item>
              <Descriptions.Item label="Chi phí khác đã ghi">{showMoney(report.otherExpenses)}</Descriptions.Item>
              <Descriptions.Item label="Đã thu từ bán thành phẩm">{showMoney(report.finishedCashCollected)}</Descriptions.Item>
              <Descriptions.Item label="Đã thu từ bán phế liệu">{showMoney(report.scrapCashCollected)}</Descriptions.Item>
            </Descriptions>
            {report.countStatus === "MISSING" && <Alert style={{ marginTop: 12 }} type="warning" showIcon message="Chưa kiểm kê cuối năm: chưa thể tính hao hụt và lãi năm." />}
          </>
        )}
      </Card>

      <YearEndBalanceCard year={year} refreshKey={closed ? 1 : 0} />

      <Card title={`Nhập số kg còn trong kho cuối năm ${year}`} loading={loading} extra={<Tag color={closed ? "green" : "orange"}>{closed ? "Đã chốt" : "Chưa chốt"}</Tag>}>
        <Space wrap style={{ marginBottom: 12 }}>
          <label>Đầu kỳ <DatePicker value={periodFrom} format="DD/MM/YYYY" disabled={!canEdit} onChange={(value) => value && setPeriodFrom(value)} /></label>
          <label>Ngày chốt <DatePicker value={periodTo} format="DD/MM/YYYY" disabled={!canEdit} onChange={(value) => value && setPeriodTo(value)} /></label>
        </Space>
        <Typography.Paragraph type="secondary">Có thể chốt theo lịch Tết âm hoặc ngày cửa hàng chọn. Kỳ sau bắt đầu ngay sau ngày chốt.</Typography.Paragraph>
        <Typography.Paragraph type="secondary">Nhập số nhôm nguyên liệu còn trong kho. Phế liệu chưa bán có thể để trống nếu không còn.</Typography.Paragraph>
        <Typography.Paragraph type="secondary">
          Tồn đầu năm lấy từ số đã chốt cuối năm trước. Nếu chưa có số chốt và chưa có số dư đầu năm đã lưu, hệ thống dùng 0.
        </Typography.Paragraph>
        <Form form={countForm} layout="vertical" disabled={!canEdit} initialValues={{ openingKg: 0, openingValue: 0, rawKg: 0, rawValue: 0, scrapKg: 0, scrapValue: 0, workInProgressKg: 0, workInProgressValue: 0, finishedKg: 0, finishedValue: 0 }}>
          <Card size="small" title="Nguyên liệu còn theo từng loại nhôm" style={{ marginBottom: 16 }}>
            <Typography.Paragraph type="secondary">
              Nhập số kg cân được của từng loại. Nếu không còn hàng, để 0. Giá trị được tính từ các phiếu nhập gần nhất.
            </Typography.Paragraph>
            <Table size="small" rowKey="categoryId" pagination={false} dataSource={rawInputs}
              columns={[
                { title: "Loại nhôm", dataIndex: "categoryName" },
                { title: "Số kg còn trong kho", render: (_, row) => (
                  <InputNumber {...qtyInputNumberProps} min={0} style={{ width: 160 }}
                    aria-label={`Số kg còn lại của ${row.categoryName}`}
                    value={row.quantityKg} addonAfter="kg"
                    onChange={(quantityKg) => setRawInputs((rows) => rows.map((item) =>
                      item.categoryId === row.categoryId ? { ...item, quantityKg: quantityKg ?? 0 } : item,
                    ))} />
                ) },
                { title: "Giá trị đã lưu", render: (_, row) =>
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
            <Typography.Text>Tổng nguyên liệu: {showKg(count?.rawKg)} · {showMoney(count?.rawValue)}</Typography.Text>
          </Card>
          <Row gutter={12}>
            {([
              ["Phế liệu chưa bán", "scrapKg", "scrapValue"],
            ] as const).map(([label, kgField, valueField]) => <Col xs={24} md={12} lg={8} key={kgField}>
              <Card size="small" title={label} style={{ marginBottom: 12 }}>
                <Form.Item name={kgField} label="Khối lượng (kg)" rules={[{ type: "number", min: 0 }]}><InputNumber {...qtyInputNumberProps} min={0} style={{ width: "100%" }} /></Form.Item>
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
      </Card>

      <Card
        title="Quyết toán công nợ theo khách"
        loading={loading}
        extra={<Select
          value={settlementMonth ?? "ALL"}
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
            Công nợ tại hết ngày {dayjs(settlement.toExclusive).subtract(1, "day").format("DD/MM/YYYY")}
            {dayjs(settlement.toExclusive).isAfter(dayjs()) ? " (tạm tính đến hiện tại)" : ""}.
            Tiền thu trong kỳ có thể thuộc phiếu bán của kỳ trước.
          </Typography.Paragraph>
          <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
            <Col xs={12} md={6}><Statistic title="Nợ đầu kỳ" value={showMoney(settlement.totalOpeningDebt)} /></Col>
            <Col xs={12} md={6}><Statistic title="Bán trong kỳ" value={showMoney(settlement.totalPeriodSales)} /></Col>
            <Col xs={12} md={6}><Statistic title="Thu trong kỳ" value={showMoney(settlement.totalPeriodReceipts)} /></Col>
            <Col xs={12} md={6}><Statistic title="Nợ cuối kỳ" value={showMoney(settlement.totalClosingDebt)} /></Col>
          </Row>
          <PagedTable
            rowKey="customerId"
            dataSource={settlement.customers}
            columns={[
              { title: "Khách hàng", render: (_, row) => <Link to={`/khach-mua/${row.customerId}`}>{row.customerName}</Link> },
              { title: "Nợ đầu kỳ", dataIndex: "openingDebt", render: showMoney },
              { title: "Bán trong kỳ", dataIndex: "periodSales", render: showMoney },
              { title: "Đã thu", dataIndex: "periodReceipts", render: showMoney },
              { title: "Nợ cuối kỳ", dataIndex: "closingDebt", render: showMoney },
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
        </>}
      </Card>

      <Card title={`Phiếu bán phế liệu năm ${year}`} loading={loading} extra={canEdit && <AppActionButton tone="create" icon={<PlusOutlined />} onClick={() => { setScrapKey(crypto.randomUUID()); scrapForm.setFieldsValue({ saleDate: dayjs().year(year), unit: "kg" }); setScrapOpen(true); }}>Thêm phiếu bán phế liệu</AppActionButton>}>
        <Typography.Paragraph type="secondary">Bấm vào một dòng để xem chi tiết phiếu và các lần thu tiền.</Typography.Paragraph>
        <PagedTable<ScrapSale>
          rowKey="id" dataSource={sales} size="middle"
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
              {row.totalAmount > row.paidAmount && <AppActionButton tone="pay" icon={<DollarOutlined />} onClick={() => { setPaymentKey(crypto.randomUUID()); paymentForm.setFieldsValue({ paymentDate: dayjs(), amount: row.totalAmount - row.paidAmount, paymentMethod: "CASH" }); setPaying(row); }}>Ghi thu</AppActionButton>}
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
          <Col xs={24} md={8}><Statistic title="Kg thành phẩm đã giao" value={showKg(saleCosts?.quantityKg)} /></Col>
          <Col xs={24} md={8}><Statistic title="Tiền bán trên phiếu" value={showMoney(saleCosts?.salesAmount)} /></Col>
        </Row>
        <Table size="small" rowKey="itemId" dataSource={saleCosts?.rows ?? []}
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
