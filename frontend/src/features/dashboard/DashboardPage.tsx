import { Alert, Button, Card, Col, DatePicker, Row, Statistic, Modal } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useEffect, useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ApiError, toApiError } from "../../services/error-handler";
import { useShop } from "../shop/shop-store";
import { Link } from "react-router-dom";
import { formatMoney } from "../../utils/money";
import { getBackupStatus } from "../backup/backup-api";
import type { ThemePalette } from "../theme/palette";
import { useThemeColors } from "../theme/theme-store";
import { useCanWrite } from "../auth/auth-store";
import { closeDistribution, getCharts, getSummary, getYearPeriod, type DashboardSummary, type YearPeriod } from "./dashboard-api";
import { YearEndBalanceCard } from "./YearEndBalanceCard";
import { getYearReport, type YearReport } from "../factory/factory-api";

const { RangePicker } = DatePicker;

const compactAxis = (value: number) => {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1).replace(/\.0$/, "")}tr`;
  if (value >= 1_000) return `${Math.round(value / 1_000)}k`;
  return String(value);
};

function tooltipStyle(colors: ThemePalette) {
  return {
    background: colors.surfaceElevated,
    border: `1px solid ${colors.border}`,
    borderRadius: 8,
    color: colors.text,
  };
}

export function DashboardPage() {
  const shop = useShop();
  const canWrite = useCanWrite();
  const periodicFactory = shop === "MANUFACTURING";
  const colors = useThemeColors();
  const [viewYear, setViewYear] = useState(dayjs().year());
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [closeRange, setCloseRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [period, setPeriod] = useState<YearPeriod | null>(null);
  const [factoryReport, setFactoryReport] = useState<YearReport | null>(null);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [points, setPoints] = useState<{ day: string; revenue: number; cash: number; profit: number }[]>([]);
  const [dayMode, setDayMode] = useState<"active" | "all">("active");
  const [error, setError] = useState<string | null>(null);
  const [backupWarning, setBackupWarning] = useState<string | null>(null);
  const [yearEndRefresh, setYearEndRefresh] = useState(0);

  useEffect(() => {
    let active = true;
    setRange(null);
    setPeriod(null);
    setSummary(null);
    getYearPeriod(viewYear).then((value) => {
      if (!active) return;
      setPeriod(value);
      const dates: [Dayjs, Dayjs] = [dayjs(value.periodFrom), dayjs(value.periodTo)];
      setRange(dates);
      setCloseRange(dates);
    }).catch((reason) => { if (active) setError(toApiError(reason).message); });
    return () => { active = false; };
  }, [viewYear, yearEndRefresh, shop]);

  useEffect(() => {
    if (!periodicFactory) {
      setFactoryReport(null);
      return;
    }
    let active = true;
    setFactoryReport(null);
    getYearReport(viewYear).then((value) => { if (active) setFactoryReport(value); })
      .catch(() => { if (active) setFactoryReport(null); });
    return () => { active = false; };
  }, [periodicFactory, viewYear]);

  useEffect(() => {
    if (!range) return;
    let active = true;
    const from = range[0].format("YYYY-MM-DD");
    const to = range[1].format("YYYY-MM-DD");
    Promise.all([getSummary(from, to), getCharts(from, to), getBackupStatus().catch(() => null)])
      .then(([nextSummary, chart, backupStatus]) => {
        if (!active) {
          return;
        }
        setSummary(nextSummary);
        setBackupWarning(backupStatus?.lastBackupFailed ? backupStatus.warningMessage : null);
        setPoints(
          chart.map((point) => ({
            day: dayjs(point.day).format("DD/MM"),
            revenue: Number(point.revenue),
            cash: Number(point.cashCollected),
            profit: Number(point.grossProfit),
          })),
        );
        setError(null);
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }
        setError(caught instanceof ApiError ? caught.message : "Không tải được tổng quan");
      });
    return () => {
      active = false;
    };
  }, [range]);

  const activePoints = useMemo(
    () => points.filter((p) => p.revenue > 0 || p.cash > 0),
    [points, periodicFactory],
  );

  const displayedBarData = dayMode === "active" && activePoints.length > 0 ? activePoints : points;



  return (
    <div className="dashboard-page">
      <div className="page-toolbar">
        <h2>Tổng quan</h2>
        <DatePicker picker="year" aria-label="Năm xem tổng quan" allowClear={false}
          value={dayjs(`${viewYear}-01-01`)} onChange={(value) => value && setViewYear(value.year())} />
        {period && <span>Kỳ {viewYear}: {dayjs(period.periodFrom).format("DD/MM/YYYY")} – {dayjs(period.periodTo).format("DD/MM/YYYY")}{period.closed ? " · Đã chốt" : " · Chưa chốt"}</span>}
        {!periodicFactory && canWrite && !period?.closed && <RangePicker aria-label="Ngày bắt đầu và chốt kỳ" value={closeRange}
          allowClear={false} format="DD/MM/YYYY"
          onChange={(values) => { if (values?.[0] && values[1]) setCloseRange([values[0], values[1]]); }} />}
        {!periodicFactory && canWrite && !period?.closed && <Button disabled={!summary || !closeRange} onClick={() => Modal.confirm({
          title: `Chốt năm ${viewYear} từ ${closeRange?.[0].format("DD/MM/YYYY")} đến ${closeRange?.[1].format("DD/MM/YYYY")}?`,
          content: "Ngày chốt do cửa hàng chọn. Kỳ sau bắt đầu ngay hôm sau. Tồn kho và công nợ được chụp tại cuối ngày chốt.",
          okText: "Chốt năm",
          cancelText: "Quay lại",
          onOk: async () => {
            try {
              if (!closeRange) return;
              const closing = await closeDistribution(closeRange[0].format("YYYY-MM-DD"), closeRange[1].format("YYYY-MM-DD"), viewYear);
              setSummary((current) => current ? { ...current, closing } : current);
              setYearEndRefresh((value) => value + 1);
              setError(null);
            } catch (error) { setError(toApiError(error).message); throw error; }
          },
        })}>Chốt năm</Button>}
      </div>
      {error ? <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} /> : null}
      {backupWarning ? (
        <Alert
          type="warning"
          showIcon
          message="Cảnh báo sao lưu dữ liệu gần nhất thất bại"
          description={backupWarning}
          style={{ marginBottom: 16 }}
        />
      ) : null}
      {periodicFactory && <Alert type="info" showIcon style={{ marginBottom: 16 }} message="Lãi và hao hụt của xưởng được tính theo kiểm kê cuối kỳ" description={<Link to="/quyet-toan-xuong">Mở quyết toán xưởng</Link>} />}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card><Statistic title={periodicFactory ? "Doanh thu thành phẩm" : "Doanh thu"} value={formatMoney(summary?.revenue ?? 0)} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title={periodicFactory ? "Đã thu từ thành phẩm" : "Tiền thực thu"} value={formatMoney(summary?.cashCollected ?? 0)} /></Card>
        </Col>

        <Col xs={24} md={8}>
          <Card><Statistic title="Chi phí hoạt động" value={formatMoney(summary?.operatingExpenses ?? 0)} /></Card>
        </Col>

        {!periodicFactory && summary && <>
          <Col xs={24} md={8}><Card><Statistic title="Tổng tiền đã chi" value={formatMoney(summary.cashExpenses ?? 0)} /></Card></Col>
          <Col xs={24} md={8}><Card><Statistic title="Khách còn nợ" value={formatMoney(summary.customerDebt ?? 0)} /></Card></Col>
          <Col xs={24} md={8}><Card><Statistic title="Cửa hàng còn nợ nhà cung cấp" value={formatMoney(summary.supplierDebt ?? 0)} /></Card></Col>
          {summary.closing && <>
            <Col xs={24} md={8}><Card><Statistic title="Lãi kỳ đã chốt" value={formatMoney(summary.closing.profit)} /></Card></Col>
            <Col xs={24} md={8}><Card><Statistic title={`Tồn cuối kỳ · ${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 6 }).format(summary.closing.quantityKg / 1000)} tấn`} value={formatMoney(summary.closing.inventoryValue)} /></Card></Col>
          </>}
        </>}
        {periodicFactory && <Col xs={24} md={8}>
          <Card><Statistic title="Tiền công tháng đang xem" value={formatMoney(summary?.payroll ?? 0)} /></Card>
        </Col>}
        {periodicFactory && factoryReport?.estimatedProfit != null && <Col xs={24} md={8}>
          <Card><Statistic title={factoryReport.countStatus === "CLOSED" ? "Lãi năm đã chốt" : "Lãi năm ước tính · chưa chốt"}
            value={formatMoney(factoryReport.estimatedProfit)} /></Card>
        </Col>}
      </Row>

      <YearEndBalanceCard year={viewYear} refreshKey={yearEndRefresh} />

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={15}>
          <Card
            title={periodicFactory ? "Biểu đồ cột: Thành phẩm và tiền đã thu" : "Biểu đồ cột: Doanh thu và tiền đã thu"}
            extra={
              <div className="chart-day-toggle" role="group" aria-label="Cách hiển thị ngày trên biểu đồ">
                <button
                  type="button"
                  className={dayMode === "active" ? "is-active" : undefined}
                  aria-pressed={dayMode === "active"}
                  onClick={() => setDayMode("active")}
                >
                  Ngày có giao dịch
                </button>
                <button
                  type="button"
                  className={dayMode === "all" ? "is-active" : undefined}
                  aria-pressed={dayMode === "all"}
                  onClick={() => setDayMode("all")}
                >
                  Tất cả ngày
                </button>
              </div>
            }
          >
            <p style={{ marginTop: 0, color: "var(--color-text-secondary)" }}>
              {periodicFactory ? "Biểu đồ này chỉ gồm phiếu bán thành phẩm; tiền phế liệu và lãi năm ở Quyết toán xưởng." : "Doanh thu là giá trị hàng đã bán. Tiền thực thu là tiền đã nhận. Tiền nhập hàng chưa bán nằm trong tồn kho."}
            </p>
            <div style={{ height: 320 }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={displayedBarData} margin={{ top: 8, right: 24, left: 8, bottom: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={colors.chartGrid} vertical={false} />
                  <XAxis dataKey="day" tick={{ fill: colors.textSecondary }} stroke={colors.border} />
                  <YAxis width={68} tickFormatter={compactAxis} tick={{ fill: colors.textSecondary }} stroke={colors.border} />
                  <Tooltip
                    isAnimationActive={false}
                    cursor={{ fill: colors.tableHover }}
                    contentStyle={tooltipStyle(colors)}
                    labelStyle={{ color: colors.text }}
                    itemStyle={{ color: colors.text }}
                    formatter={(value) => formatMoney(Number(value ?? 0))}
                  />
                  <Legend wrapperStyle={{ color: colors.text }} />
                  <Bar
                    dataKey="revenue"
                    name="Doanh thu"
                    fill={colors.chart1}
                    radius={[4, 4, 0, 0]}
                    maxBarSize={44}
                    isAnimationActive={false}
                  />
                  <Bar
                    dataKey="cash"
                    name="Tiền thực thu"
                    fill={colors.chart2}
                    radius={[4, 4, 0, 0]}
                    maxBarSize={44}
                    isAnimationActive={false}
                  />

                </BarChart>
              </ResponsiveContainer>
            </div>
          </Card>
        </Col>


      </Row>
    </div>
  );
}
