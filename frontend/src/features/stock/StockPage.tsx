import { Alert, Card, Col, InputNumber, Modal, Row, Space, Statistic, Tag, message } from "antd";
import { PagedTable } from "../../components/PagedTable";
import { LiveSearch } from "../../components/LiveSearch";
import dayjs from "dayjs";
import { useEffect, useMemo, useState } from "react";
import { AuditOutlined } from "@ant-design/icons";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { costPerKgToTon, kgToTon } from "../products/product-stock-unit";
import { useShop } from "../shop/shop-store";
import { listBalances, listMovements, saveInventoryCount, type Movement, type StockBalance } from "./stock-api";
import { AppActionButton } from "../../components/AppButtons";
import { useCanWrite } from "../auth/auth-store";
import { getYearCount, getYearReport, type YearCount, type YearReport } from "../factory/factory-api";
import { Link } from "react-router-dom";

const qtyFormat = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });

export function StockPage() {
  const shop = useShop();
  const canWrite = useCanWrite();
  const asTon = shop === "DISTRIBUTION";
  const [rows, setRows] = useState<StockBalance[]>([]);
  const [allBalances, setAllBalances] = useState<StockBalance[]>([]);
  const [factoryReport, setFactoryReport] = useState<YearReport | null>(null);
  const [factoryCount, setFactoryCount] = useState<YearCount | null>(null);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<StockBalance | null>(null);
  const [movements, setMovements] = useState<Movement[]>([]);
  const [countOpen, setCountOpen] = useState(false);
  const [countSaving, setCountSaving] = useState(false);
  const [countBalances, setCountBalances] = useState<StockBalance[]>([]);
  const [countedKg, setCountedKg] = useState<Record<string, number | null>>({});
  const [scrapKg, setScrapKg] = useState<Record<string, number | null>>({});

  useEffect(() => {
    setLoading(true);
    listBalances({ q: query || undefined })
      .then(setRows)
      .catch((error) => message.error(toApiError(error).message))
      .finally(() => setLoading(false));
  }, [query]);

  useEffect(() => {
    listBalances({ onlyInStock: false }).then(setAllBalances).catch(() => setAllBalances([]));
  }, [shop]);

  useEffect(() => {
    if (asTon) { setFactoryReport(null); setFactoryCount(null); return; }
    getYearReport(dayjs().year()).then(setFactoryReport).catch(() => setFactoryReport(null));
    getYearCount(dayjs().year()).then(setFactoryCount).catch(() => setFactoryCount(null));
  }, [asTon]);

  useEffect(() => {
    if (!selected) return;
    listMovements(selected.productId)
      .then(setMovements)
      .catch((error) => message.error(toApiError(error).message));
  }, [selected]);


  const countRows = useMemo(() => countBalances.map((row) => {
    const key = `${row.productId}-${row.categoryId}`;
    const actualKg = countedKg[key] ?? null;
    return { ...row, key, actualKg, scrapKg: scrapKg[key] ?? 0 };
  }), [countBalances, countedKg, scrapKg]);

  async function saveCount() {
    const items = countRows.flatMap((row) => row.actualKg == null ? [] : [{
      productId: row.productId,
      categoryId: row.categoryId,
      countedQuantityKg: row.actualKg,
      scrapKg: row.scrapKg,
    }]);
    if (!items.length) {
      message.warning("Nhập số kg cân thực tế cho ít nhất một dòng.");
      return;
    }
    setCountSaving(true);
    try {
      await saveInventoryCount(items);
      message.success("Đã lưu số cân kiểm kê.");
      setCountOpen(false);
      setCountedKg({});
      setScrapKg({});
      const next = await listBalances({ q: query || undefined });
      setRows(next);
      setAllBalances(await listBalances({ onlyInStock: false }));
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setCountSaving(false);
    }
  }

  const total = allBalances.reduce((acc, row) => ({
    quantity: acc.quantity + Number(row.quantityBase || 0),
    value: acc.value + Number(row.stockValue || 0),
    purchased: acc.purchased + Number(row.purchasedKg || 0),
    delivered: acc.delivered + Number(row.deliveredKg || 0),
    scrap: acc.scrap + Number(row.latestCountScrapKg || 0),
    loss: acc.loss + Number(row.latestCountLossKg || 0),
  }), { quantity: 0, value: 0, purchased: 0, delivered: 0, scrap: 0, loss: 0 });

  return (
    <Card title="Tồn kho" extra={<Space>
      {asTon && canWrite && <AppActionButton tone="secondary" icon={<AuditOutlined />} onClick={() => {
        void listBalances({ onlyInStock: false }).then((allRows) => {
          setCountBalances(allRows.filter((row) => row.purchasedKg > 0 || row.quantityBase > 0)
            .sort((a, b) => a.categoryName.localeCompare(b.categoryName, "vi")));
          setCountedKg({});
          setScrapKg({});
          setCountOpen(true);
        }).catch((error) => message.error(toApiError(error).message));
      }}>Kiểm kê / Hao hụt</AppActionButton>}
    </Space>}>
      {asTon ? <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} lg={6}><Card size="small"><Statistic title="Giá trị tồn sổ hiện tại" value={formatMoney(total.value)} /></Card></Col>
        <Col xs={24} sm={12} lg={6}><Card size="small"><Statistic title="Tổng đã nhập (kg)" value={qtyFormat.format(total.purchased)} /></Card></Col>
        <Col xs={24} sm={12} lg={6}><Card size="small"><Statistic title="Tổng đã giao (kg)" value={qtyFormat.format(total.delivered)} /></Card></Col>
        <Col xs={24} sm={12} lg={6}><Card size="small"><Statistic title="Tồn sổ (kg)" value={qtyFormat.format(total.quantity)} /></Card></Col>
        <Col xs={24} sm={12} lg={6}><Card size="small"><Statistic title="Vụn nhôm kiểm kê gần nhất (kg)" value={qtyFormat.format(total.scrap)} /></Card></Col>
        <Col xs={24} sm={12} lg={6}><Card size="small"><Statistic title="Hao hụt thực kiểm gần nhất (kg)" value={qtyFormat.format(total.loss)} /></Card></Col>
      </Row> : <>
        <Alert style={{ marginBottom: 12 }} type="info" showIcon message="Xưởng không trừ kho khi giao thành phẩm; tồn và hao hụt được quyết toán theo kiểm kê cuối kỳ." description={<Link to="/quyet-toan-xuong">Mở quyết toán Xưởng để kiểm kê từng loại nhôm và chốt kỳ</Link>} />
        <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
          <Col xs={24} sm={12} lg={8}><Card size="small"><Statistic title="Tiền nhập nguyên liệu trong kỳ" value={formatMoney(factoryReport?.purchaseValue ?? 0)} /></Card></Col>
          <Col xs={24} sm={12} lg={8}><Card size="small"><Statistic title="Khối lượng nhập (kg)" value={qtyFormat.format(factoryReport?.purchasedKg ?? 0)} /></Card></Col>
          <Col xs={24} sm={12} lg={8}><Card size="small"><Statistic title="Đã giao khách (kg)" value={qtyFormat.format(factoryReport?.finishedSoldKg ?? 0)} /></Card></Col>
          <Col xs={24} sm={12} lg={8}><Card size="small"><Statistic title="Vụn nhôm đã bán (kg)" value={qtyFormat.format(factoryReport?.scrapSoldKg ?? 0)} /></Card></Col>
          <Col xs={24} sm={12} lg={8}><Card size="small"><Statistic title="Vụn chưa bán kiểm kê (kg)" value={factoryCount?.status === "MISSING" || factoryCount == null ? "Chưa kiểm kê" : qtyFormat.format(factoryCount.scrapKg)} /></Card></Col>
          <Col xs={24} sm={12} lg={8}><Card size="small"><Statistic title="Hao hụt ước tính (kg)" value={factoryReport?.estimatedLossKg == null ? "Chưa kiểm kê" : qtyFormat.format(factoryReport.estimatedLossKg)} /></Card></Col>
          <Col xs={24} sm={12} lg={8}><Card size="small"><Statistic title="Giá trị nguyên liệu cuối kỳ" value={factoryReport?.closingValue == null ? "Chưa kiểm kê" : formatMoney(factoryReport.closingValue)} /></Card></Col>
        </Row>
      </>}
      {asTon && <Alert type="info" showIcon style={{ marginBottom: 12 }} message="Số vụn và hao hụt chỉ là lần kiểm kê gần nhất của từng loại, không cộng dồn theo năm. Muốn chốt kỳ phải cân đủ các loại đã phát sinh." />}
      {asTon && <Space style={{ marginBottom: 16 }} wrap>
        <LiveSearch value={query} onChange={setQuery} placeholder="Tìm mã hoặc tên" style={{ width: 360 }} suggestions={rows.flatMap((r) => [r.productCode, r.productName, r.categoryName])} />
      </Space>}
      {asTon && <PagedTable<StockBalance>
        rowKey={(r) => `${r.productId}-${r.categoryId}`}
        loading={loading}
        dataSource={rows}
        onRow={(record) => ({
          onClick: () => setSelected(record),
          className: "app-row-clickable",
        })}
        columns={[
          { title: "Mã", dataIndex: "productCode", align: "center" },
          { title: "Tên", dataIndex: "productName", align: "center" },
          { title: "Loại nhôm", dataIndex: "categoryName", align: "center" },
          {
            title: "Tồn",
            align: "center",
            render: (_, r) => {
              const area = r.baseUnitName === "m²" || r.baseUnitName?.toLowerCase() === "m2";
              if (area) return `${qtyFormat.format(r.quantityBase)} m²`;
              if (asTon) return `${qtyFormat.format(kgToTon(r.quantityBase))} tấn`;
              return `${qtyFormat.format(r.quantityBase)} kg`;
            },
          },
        ]}
      />}
      <Modal
        open={countOpen}
        title="Kiểm kê tồn kho và tính hao hụt"
        width={1000}
        okText="Lưu kiểm kê"
        cancelText="Hủy"
        confirmLoading={countSaving}
        onCancel={() => setCountOpen(false)}
        onOk={() => void saveCount()}
      >
        <Alert
          showIcon
          type="info"
          style={{ marginBottom: 16 }}
          message="Chênh lệch = tồn sổ − nguyên liệu thực cân; hao hụt thuần = chênh lệch thiếu − vụn nhôm"
          description="Nhập số kg nguyên liệu còn trong kho và kg vụn nằm trong phần thiếu so với tồn sổ. Vụn không được lớn hơn phần thiếu. Để trống khối lượng nguyên liệu nếu loại chưa kiểm kê; nhập 0 nếu đã cân và không còn hàng. Thống kê giữ lần kiểm kê mới nhất theo từng loại."
        />
        <PagedTable
          rowKey="key"
          size="small"
          dataSource={countRows}
          pagination={false}
          scroll={{ y: 420 }}
          columns={[
            { title: "Loại nhôm", dataIndex: "categoryName", align: "center" },
            { title: "Sản phẩm", dataIndex: "productName", align: "center" },
            { title: "Đã nhập (kg)", align: "center", render: (_, row) => qtyFormat.format(row.purchasedKg) },
            { title: "Đã giao (kg)", align: "center", render: (_, row) => qtyFormat.format(row.deliveredKg) },
            { title: "Còn trong kho (kg)", align: "center", render: (_, row) => (
              <InputNumber min={0} step={0.001} precision={3} value={row.actualKg} style={{ width: 150 }}
                onChange={(value) => setCountedKg((current) => ({ ...current, [row.key]: value }))} />
            ) },
            { title: "Vụn nhôm trong chênh lệch (kg)", align: "center", render: (_, row) => (
              <InputNumber min={0} max={row.actualKg == null ? undefined : Math.max(0, row.quantityBase - row.actualKg)}
                step={0.001} precision={3} value={row.scrapKg} style={{ width: 145 }}
                onChange={(value) => setScrapKg((current) => ({ ...current, [row.key]: value }))} />
            ) },
            { title: "Hao hụt thuần (kg)", align: "center", render: (_, row) => {
              if (row.actualKg == null) return "—";
              const rounded = Math.round((row.quantityBase - row.actualKg - row.scrapKg) * 1000) / 1000;
              return <span style={{ color: rounded > 0 ? "var(--color-danger)" : rounded < 0 ? "var(--color-success)" : undefined }}>
                {rounded < 0 ? `Thừa ${qtyFormat.format(-rounded)}` : qtyFormat.format(rounded)}
              </span>;
            } },
          ]}
        />
      </Modal>
      <Modal
        open={selected !== null}
        title={selected ? `Biến động ${selected.productCode} - ${selected.productName}` : ""}
        footer={null}
        width={720}
        onCancel={() => {
          setSelected(null);
          setMovements([]);
        }}
      >
        <PagedTable<Movement>
          rowKey="id"
          size="small"
          dataSource={movements}
          columns={[
            { title: "Thời gian", dataIndex: "movementAt", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm") },
            { title: "Phiếu", dataIndex: "documentCode", align: "center" },
            {
              title: "Loại",
              dataIndex: "movementType",
              align: "center",
              render: (v: string) => (v === "IN" ? <Tag color="green">Nhập</Tag> : <Tag color="volcano">Xuất</Tag>),
            },
            {
              title: asTon ? "Số lượng (tấn)" : "Số lượng (kg)",
              dataIndex: "quantityDeltaBase",
              align: "center",
              render: (v: number) => {
                const qty = asTon ? kgToTon(v) : v;
                return `${qty > 0 ? "+" : ""}${qtyFormat.format(qty)}`;
              },
            },
            {
              title: asTon ? "Giá vốn / tấn" : "Giá vốn / kg",
              dataIndex: "unitCost",
              align: "center",
              render: (v: number) => formatMoney(asTon ? costPerKgToTon(v) : v),
            },
          ]}
        />
      </Modal>
    </Card>
  );
}
