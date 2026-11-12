import { Alert, Card, InputNumber, Modal, Space, Tag, message } from "antd";
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

const qtyFormat = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });

export function StockPage() {
  const shop = useShop();
  const canWrite = useCanWrite();
  const asTon = shop === "DISTRIBUTION";
  const [rows, setRows] = useState<StockBalance[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<StockBalance | null>(null);
  const [movements, setMovements] = useState<Movement[]>([]);
  const [countOpen, setCountOpen] = useState(false);
  const [countSaving, setCountSaving] = useState(false);
  const [countBalances, setCountBalances] = useState<StockBalance[]>([]);
  const [countedKg, setCountedKg] = useState<Record<string, number | null>>({});

  useEffect(() => {
    setLoading(true);
    listBalances({ q: query || undefined })
      .then(setRows)
      .catch((error) => message.error(toApiError(error).message))
      .finally(() => setLoading(false));
  }, [query]);

  useEffect(() => {
    if (!selected) return;
    listMovements(selected.productId)
      .then(setMovements)
      .catch((error) => message.error(toApiError(error).message));
  }, [selected]);


  const countRows = useMemo(() => countBalances.map((row) => {
    const key = `${row.productId}-${row.categoryId}`;
    const actualKg = countedKg[key] ?? null;
    return { ...row, key, actualKg };
  }), [countBalances, countedKg]);

  async function saveCount() {
    const items = countRows.flatMap((row) => row.actualKg == null ? [] : [{
      productId: row.productId,
      categoryId: row.categoryId,
      countedQuantityKg: row.actualKg,
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
      const next = await listBalances({ q: query || undefined });
      setRows(next);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setCountSaving(false);
    }
  }

  return (
    <Card title="Tồn kho" extra={<Space>
      {asTon && canWrite && <AppActionButton tone="secondary" icon={<AuditOutlined />} onClick={() => {
        void listBalances({ onlyInStock: false }).then((allRows) => {
          setCountBalances(allRows.filter((row) => row.purchasedKg > 0 || row.quantityBase > 0)
            .sort((a, b) => a.categoryName.localeCompare(b.categoryName, "vi")));
          setCountedKg({});
          setCountOpen(true);
        }).catch((error) => message.error(toApiError(error).message));
      }}>Kiểm kê / Hao hụt</AppActionButton>}
    </Space>}>
      <Space style={{ marginBottom: 16 }} wrap>
        <LiveSearch value={query} onChange={setQuery} placeholder="Tìm mã hoặc tên" style={{ width: 360 }} suggestions={rows.flatMap((r) => [r.productCode, r.productName, r.categoryName])} />
      </Space>
      <PagedTable<StockBalance>
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
      />
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
          message="Hao hụt = tồn sổ − số cân thực tế"
          description="Số nhập và số giao lấy từ các phiếu đã xác nhận, gồm cả giao nội bộ. Chỉ nhập số kg cân được. Để trống dòng chưa kiểm kê, hoặc nhập 0 nếu đã cân và kho không còn hàng."
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
            { title: "Hao hụt (kg)", align: "center", render: (_, row) => {
              if (row.actualKg == null) return "—";
              const rounded = Math.round((row.quantityBase - row.actualKg) * 1000) / 1000;
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
