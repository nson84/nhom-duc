import { Alert, Card, DatePicker, InputNumber, Modal, Space, Statistic, Tag, Typography, message } from "antd";
import { PagedTable } from "../../components/PagedTable";
import { LiveSearch } from "../../components/LiveSearch";
import dayjs, { type Dayjs } from "dayjs";
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
import { useWorkingYear } from "../dashboard/working-year";
import { OpeningInventory } from "./OpeningInventory";
import { getSummary, getYearPeriod, type DashboardSummary, type YearPeriod } from "../dashboard/dashboard-api";
import { YearEndBalanceCard } from "../dashboard/YearEndBalanceCard";
import { visiblePeriod } from "../dashboard/working-period";
import { DeliveredByCategory } from "./DeliveredByCategory";

const qtyFormat = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });

function formatCountKg(value: number | string | undefined): string {
  if (value === undefined || value === null || value === "") return "";
  const [whole, fraction] = String(value).replace(/,/g, "").split(".");
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return fraction === undefined ? grouped : `${grouped}.${fraction}`;
}

function roundKg(value: number): number {
  return Math.max(0, Math.round(value * 1000) / 1000);
}

export function StockPage() {
  const shop = useShop();
  const workingYear = useWorkingYear(shop);
  const canWrite = useCanWrite();
  const asTon = shop === "DISTRIBUTION";
  const [rows, setRows] = useState<StockBalance[]>([]);
  const [allBalances, setAllBalances] = useState<StockBalance[]>([]);
  const [stockPeriod, setStockPeriod] = useState<YearPeriod | null>(null);
  const [stockFlow, setStockFlow] = useState<DashboardSummary | null>(null);
  const [monthRange, setMonthRange] = useState<[string, string] | null>(null);
  const factoryYear = workingYear.year;
  const [factoryReport, setFactoryReport] = useState<YearReport | null>(null);
  const [factoryCount, setFactoryCount] = useState<YearCount | null>(null);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<StockBalance | null>(null);
  const [movements, setMovements] = useState<Movement[]>([]);
  const [countOpen, setCountOpen] = useState(false);
  const [countSaving, setCountSaving] = useState(false);
  const [countPeriod, setCountPeriod] = useState<YearPeriod | null>(null);
  const [countDate, setCountDate] = useState<Dayjs | null>(null);
  const [countBalances, setCountBalances] = useState<StockBalance[]>([]);
  const [countedKg, setCountedKg] = useState<Record<string, number | null>>({});
  const [scrapKg, setScrapKg] = useState<Record<string, number | null>>({});
  const [clampTick, setClampTick] = useState(0);


  useEffect(() => {
    let active = true;
    setStockPeriod(null);
    setStockFlow(null);
    setMonthRange(null);
    getYearPeriod(workingYear.year).then(async (period) => {
      if (!active) return;
      setStockPeriod(period);
      const shown = visiblePeriod(period, workingYear.month);
      if (!shown?.started) return;
      setMonthRange([shown.periodFrom, shown.periodTo]);
      if (asTon) {
        const flow = await getSummary(shown.periodFrom, shown.periodTo);
        if (active) setStockFlow(flow);
      }
    }).catch(error => { if (active) message.error(toApiError(error).message); });
    return () => { active = false; };
  }, [shop, workingYear.year, workingYear.month, asTon]);

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
    setFactoryReport(null);
    setFactoryCount(null);
    if (asTon) return;
    let active = true;
    Promise.all([getYearReport(factoryYear), getYearCount(factoryYear)]).then(([report, count]) => {
      if (active) { setFactoryReport(report); setFactoryCount(count); }
    }).catch((error) => { if (active) message.error(toApiError(error).message); });
    return () => { active = false; };
  }, [asTon, factoryYear]);

  useEffect(() => {
    if (!selected) return;
    listMovements(selected.productId)
      .then(setMovements)
      .catch((error) => message.error(toApiError(error).message));
  }, [selected]);


  const countRows = useMemo(() => countBalances.map((row) => {
    const key = `${row.productId}-${row.categoryId}`;
    const actualKg = countedKg[key] ?? null;
    const preCountKg = roundKg(Number(row.quantityBase || 0) + Number(row.latestCountScrapKg || 0) + Number(row.latestCountLossKg || 0));
    return { ...row, key, actualKg, scrapKg: scrapKg[key] ?? 0, preCountKg };
  }), [countBalances, countedKg, scrapKg]);

  async function saveCount() {
    for (const row of countRows) {
      if (row.actualKg == null) continue;
      const maxActual = roundKg(row.preCountKg - row.scrapKg);
      if (row.actualKg > maxActual) {
        message.error(`${row.categoryName} (${row.productName}): còn trong kho tối đa là ${formatCountKg(maxActual)} kg (tồn sổ − vụn nhôm).`);
        return;
      }
    }
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
    if (!countDate) {
      message.warning("Chọn ngày kiểm kê.");
      return;
    }
    setCountSaving(true);
    try {
      const now = dayjs();
      const countTimestamp = countDate.isSame(now, "day") ? now : countDate.endOf("day");
      await saveInventoryCount(items, countTimestamp.format("YYYY-MM-DDTHH:mm:ss"));
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
    scrap: acc.scrap + Number(row.latestCountScrapKg || 0),
    loss: acc.loss + Number(row.latestCountLossKg || 0),
  }), { quantity: 0, scrap: 0, loss: 0 });
  const showLiveStock = stockPeriod?.started && !stockPeriod.closed;

  return (
    <Card className="stock-page-card" title="Tồn kho" extra={<Space>
      {asTon && canWrite && showLiveStock && <AppActionButton tone="secondary" icon={<AuditOutlined />} onClick={() => {
        void Promise.all([
          listBalances({ onlyInStock: false }),
          getYearPeriod(workingYear.year).catch(() => null),
        ]).then(([allRows, period]) => {
          const today = dayjs();
          const defaultDate = period?.plannedEnd && !dayjs(period.plannedEnd).isAfter(today, "day")
            ? dayjs(period.plannedEnd)
            : monthRange
              ? (today.isAfter(dayjs(monthRange[1]), "day") ? dayjs(monthRange[1]) : today)
              : (period?.started && today.isAfter(dayjs(period.periodTo), "day") ? dayjs(period.periodTo) : today);
          setCountPeriod(period);
          setCountDate(defaultDate);
          setCountBalances(allRows.sort((a, b) => a.categoryName.localeCompare(b.categoryName, "vi")));
          setCountedKg({});
          setScrapKg({});
          setCountOpen(true);
        }).catch((error) => message.error(toApiError(error).message));
      }}>Kiểm kê / Hao hụt</AppActionButton>}
    </Space>}>
      <OpeningInventory year={asTon ? workingYear.year : factoryYear} month={asTon || factoryYear === workingYear.year ? workingYear.month : undefined} />
      {asTon ? <div className="stock-stat-grid">
        <Card size="small"><Statistic title="Tiền hàng nhập trong khoảng xem" value={stockFlow ? formatMoney(stockFlow.purchaseAmount) : "—"} /></Card>
        <Card size="small"><Statistic title="Đã nhập trong khoảng xem (kg)" value={stockFlow ? qtyFormat.format(Number(stockFlow.purchaseKg)) : "—"} /></Card>
        <Card size="small"><Statistic title="Đã giao trong khoảng xem (kg)" value={stockFlow ? qtyFormat.format(Number(stockFlow.deliveredKg)) : "—"} />
          {monthRange && <DeliveredByCategory from={monthRange[0]} to={monthRange[1]} shopKey={shop} />}
        </Card>
        {showLiveStock && <>
          <Card size="small"><Statistic title="Tồn sổ hiện tại (kg)" value={qtyFormat.format(total.quantity)} /></Card>
          <Card size="small"><Statistic title="Vụn nhôm kiểm kê gần nhất (kg)" value={qtyFormat.format(total.scrap)} /></Card>
          <Card size="small"><Statistic title="Hao hụt thực kiểm gần nhất (kg)" value={qtyFormat.format(total.loss)} /></Card>
        </>}
      </div> : <>
        <Alert style={{ marginBottom: 12 }} type="info" showIcon message="Xưởng không trừ kho khi giao thành phẩm; tồn và hao hụt được quyết toán theo kiểm kê cuối kỳ." description={<Link to="/quyet-toan-xuong">Mở quyết toán Xưởng để kiểm kê từng loại nhôm và chốt kỳ</Link>} />
        {factoryCount && <Typography.Paragraph strong>
          Kỳ {dayjs(factoryCount.periodFrom).format("DD/MM/YYYY")} – {dayjs(factoryCount.periodTo).format("DD/MM/YYYY")}
        </Typography.Paragraph>}
        <div className="stock-stat-grid">
          <Card size="small"><Statistic title="Tổng tiền hàng nhập trong kỳ (gồm nội bộ)" value={factoryReport ? formatMoney(factoryReport.purchaseValue) : "—"} /></Card>
          <Card size="small"><Statistic title="Tổng nhôm nhập kho trong kỳ (kg)" value={factoryReport ? qtyFormat.format(factoryReport.purchasedKg) : "—"} /></Card>
          <Card size="small"><Statistic title="Đã giao khách trong kỳ (kg)" value={factoryReport ? qtyFormat.format(factoryReport.finishedSoldKg) : "—"} />
            {factoryCount && <DeliveredByCategory from={factoryCount.periodFrom} to={factoryCount.periodTo} shopKey={`${shop}-${factoryYear}`} />}
          </Card>
          <Card size="small"><Statistic title="Tổng vụn nhôm trong kỳ (kg)" value={factoryCount?.status === "MISSING" || !factoryCount || !factoryReport ? "Chưa kiểm kê" : qtyFormat.format(Number(factoryReport.scrapSoldKg) + Number(factoryCount.scrapKg))} /></Card>
          <Card size="small"><Statistic title="Hao hụt ước tính trong kỳ (kg)" value={factoryReport?.estimatedLossKg == null ? "Chưa kiểm kê" : factoryReport.estimatedLossKg < 0 ? "Cần đối chiếu" : qtyFormat.format(factoryReport.estimatedLossKg)} />
            {factoryReport?.estimatedLossKg != null && factoryReport.estimatedLossKg < 0 &&
              <Typography.Text type="secondary">Tồn kiểm kê vượt lượng có thể còn. Kiểm tra tồn đầu kỳ và số cân cuối kỳ.</Typography.Text>}
          </Card>
          <Card size="small"><Statistic title="Giá trị hàng tồn cuối kỳ" value={factoryReport?.closingValue == null ? "Chưa kiểm kê" : formatMoney(factoryReport.closingValue)} />
            <Typography.Text type="secondary">Nguyên liệu được định giá theo phiếu nhập gần nhất của từng loại.</Typography.Text>
          </Card>
        </div>
      </>}
      {stockPeriod?.closed && <YearEndBalanceCard key={factoryYear} year={factoryYear} />}
      {showLiveStock && <>
      {asTon && <Alert type="info" showIcon style={{ marginBottom: 12 }} message="Tồn sổ và kiểm kê gần nhất phản ánh kho hiện tại. Muốn chốt kỳ phải cân đủ các loại đã phát sinh." />}
      <Space style={{ marginBottom: 16 }} wrap>
        <LiveSearch value={query} onChange={setQuery} placeholder="Tìm mã hoặc tên" style={{ width: 360 }} suggestions={rows.flatMap((r) => [r.productCode, r.productName, r.categoryName])} />
      </Space>
      {!asTon && <Alert type="info" showIcon style={{ marginBottom: 12 }} message="Tồn sổ hiện tại theo từng loại nhôm; hàng giao khách ở Xưởng không tự trừ tồn. Số tồn thực tế được xác định khi kiểm kê cuối kỳ." />}
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
      </>}
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
          description="Còn trong kho không được lớn hơn (tồn sổ − vụn nhôm) và vụn không được lớn hơn phần thiếu. Để trống khối lượng nguyên liệu nếu loại chưa kiểm kê; nhập 0 nếu đã cân và không còn hàng. Thống kê giữ lần kiểm kê mới nhất theo từng loại."
        />
        <Space style={{ marginBottom: 12 }} wrap>
          <Typography.Text strong>Ngày kiểm kê:</Typography.Text>
          <DatePicker
            aria-label="Ngày kiểm kê"
            allowClear={false}
            format="DD/MM/YYYY"
            value={countDate}
            maxDate={dayjs()}
            disabledDate={(date) => date.isAfter(dayjs(), "day")
              || (!!countPeriod?.started && (date.isBefore(dayjs(countPeriod.periodFrom), "day") || date.isAfter(dayjs(countPeriod.periodTo), "day")))}
            onChange={(value) => value && setCountDate(value)}
          />
          <Typography.Text type="secondary">
            {countPeriod?.plannedEnd
              ? `Mặc định lấy theo ngày kết thúc dự kiến (${dayjs(countPeriod.plannedEnd).format("DD/MM/YYYY")}). Kho không được có phát sinh sau ngày kiểm kê.`
              : "Chọn đúng ngày chốt kiểm kê; kho không được có phát sinh sau ngày này."}
          </Typography.Text>
        </Space>
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
            { title: "Còn trong kho (kg)", align: "center", render: (_, row) => {
              const maxActual = roundKg(row.preCountKg - row.scrapKg);
              return (
                <InputNumber
                  key={`actual-${row.key}-${clampTick}`}
                  min={0}
                  max={maxActual}
                  step={1}
                  value={row.actualKg}
                  style={{ width: 150 }}
                  placeholder={`≤ ${formatCountKg(maxActual)}`}
                  formatter={formatCountKg}
                  parser={(v) => {
                    const raw = (v ?? "").replace(/,/g, "");
                    if (!raw) return "" as unknown as number;
                    const parsed = Number(raw);
                    if (!Number.isFinite(parsed)) return 0;
                    return parsed > maxActual ? maxActual : parsed;
                  }}
                  onInput={(text) => {
                    const raw = (text ?? "").replace(/,/g, "");
                    const parsed = Number(raw);
                    if (raw !== "" && Number.isFinite(parsed) && parsed > maxActual) {
                      setCountedKg((current) => ({ ...current, [row.key]: maxActual }));
                      setClampTick((t) => t + 1);
                    }
                  }}
                  onChange={(value) => {
                    const next = value == null ? null : Math.min(Math.max(0, Number(value)), maxActual);
                    setCountedKg((current) => ({ ...current, [row.key]: next }));
                  }} />
              );
            } },
            { title: "Vụn nhôm trong chênh lệch (kg)", align: "center", render: (_, row) => {
              const maxScrap = row.actualKg == null ? roundKg(row.preCountKg) : roundKg(row.preCountKg - row.actualKg);
              return (
                <InputNumber
                  key={`scrap-${row.key}-${clampTick}`}
                  min={0}
                  max={maxScrap}
                  step={1}
                  value={row.scrapKg}
                  style={{ width: 145 }}
                  formatter={formatCountKg}
                  parser={(v) => {
                    const raw = (v ?? "").replace(/,/g, "");
                    if (!raw) return "" as unknown as number;
                    const parsed = Number(raw);
                    if (!Number.isFinite(parsed)) return 0;
                    return parsed > maxScrap ? maxScrap : parsed;
                  }}
                  onInput={(text) => {
                    const raw = (text ?? "").replace(/,/g, "");
                    const parsed = Number(raw);
                    if (raw !== "" && Number.isFinite(parsed) && parsed > maxScrap) {
                      setScrapKg((current) => ({ ...current, [row.key]: maxScrap }));
                      setClampTick((t) => t + 1);
                    }
                  }}
                  onChange={(value) => {
                    const next = value == null ? null : Math.min(Math.max(0, Number(value)), maxScrap);
                    setScrapKg((current) => ({ ...current, [row.key]: next }));
                  }} />
              );
            } },
            { title: "Hao hụt thuần (kg)", align: "center", render: (_, row) => {
              if (row.actualKg == null) return "—";
              const rounded = Math.round((row.preCountKg - row.actualKg - row.scrapKg) * 1000) / 1000;
              return <span style={{ color: rounded !== 0 ? "var(--color-danger)" : undefined }}>
                {qtyFormat.format(rounded)}
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
          dataSource={monthRange ? movements.filter(row => {
            const day = dayjs(row.movementAt).format("YYYY-MM-DD");
            return day >= monthRange[0] && day <= monthRange[1];
          }) : []}
          columns={[
            { title: "Thời gian", dataIndex: "movementAt", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm") },
            { title: "Phiếu", align: "center", render: (_, movement) => <Space>{movement.documentCode}{movement.internal && <Tag color="blue">Nội bộ</Tag>}</Space> },
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
