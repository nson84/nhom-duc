import { Card, Checkbox, Input, Modal, Space, Table, Tag, message } from "antd";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { costPerKgToTon, kgToTon } from "../products/product-stock-unit";
import { useShop } from "../shop/shop-store";
import { listBalances, listMovements, type Movement, type StockBalance } from "./stock-api";

const qtyFormat = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });

export function StockPage() {
  const shop = useShop();
  const asTon = shop === "DISTRIBUTION";
  const [rows, setRows] = useState<StockBalance[]>([]);
  const [query, setQuery] = useState("");
  const [onlyInStock, setOnlyInStock] = useState(false);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<StockBalance | null>(null);
  const [movements, setMovements] = useState<Movement[]>([]);

  useEffect(() => {
    setLoading(true);
    listBalances({ q: query || undefined, onlyInStock })
      .then(setRows)
      .catch((error) => message.error(toApiError(error).message))
      .finally(() => setLoading(false));
  }, [query, onlyInStock]);

  useEffect(() => {
    if (!selected) return;
    listMovements(selected.productId)
      .then(setMovements)
      .catch((error) => message.error(toApiError(error).message));
  }, [selected]);

  const total = rows.reduce((sum, row) => sum + row.stockValue, 0);

  return (
    <Card title="Tồn kho" extra={<span>Giá trị tồn: <b>{formatMoney(total)}</b></span>}>
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          className="app-search"
          placeholder="Tìm mã hoặc tên"
          allowClear
          onSearch={setQuery}
          style={{ width: 280 }}
        />
        <Checkbox checked={onlyInStock} onChange={(e) => setOnlyInStock(e.target.checked)}>Chỉ hàng còn tồn</Checkbox>
      </Space>
      <Table<StockBalance>
        rowKey="productId"
        loading={loading}
        dataSource={rows}
        pagination={{ pageSize: 20 }}
        onRow={(record) => ({
          onClick: () => setSelected(record),
          className: "app-row-clickable",
        })}
        columns={[
          { title: "Mã", dataIndex: "productCode", align: "center" },
          { title: "Tên", dataIndex: "productName", align: "center" },
          { title: "Loại", dataIndex: "categoryName", align: "center" },
          {
            title: asTon ? "Tồn (tấn)" : "Tồn (kg)",
            align: "center",
            render: (_, r) => qtyFormat.format(asTon ? kgToTon(r.quantityBase) : r.quantityBase),
          },
          {
            title: asTon ? "Giá vốn BQ / tấn" : "Giá vốn BQ / kg",
            dataIndex: "averageCostPerBase",
            align: "center",
            render: (v: number) => formatMoney(asTon ? costPerKgToTon(v) : v),
          },
          { title: "Giá trị tồn", dataIndex: "stockValue", align: "center", render: (v: number) => formatMoney(v) },
        ]}
      />
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
        <Table<Movement>
          rowKey="id"
          size="small"
          dataSource={movements}
          pagination={{ pageSize: 10 }}
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
