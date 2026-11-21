import { Alert, Card, Col, DatePicker, Input, Row, Statistic, Modal, Space, Tag, Typography, message } from "antd";
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
import { Link } from "react-router-dom";
import { useShop } from "../shop/shop-store";
import { formatMoney } from "../../utils/money";
import { getBackupStatus } from "../backup/backup-api";
import type { ThemePalette } from "../theme/palette";
import { useThemeColors } from "../theme/theme-store";
import { useCanWrite } from "../auth/auth-store";
import { AppActionButton } from "../../components/AppButtons";
import { CheckOutlined, LockOutlined, UnlockOutlined, PlayCircleOutlined, SaveOutlined } from "@ant-design/icons";
import { useWorkingYear, workingYearStore } from "./working-year";
import { closeDistribution, closeMonth, getMonthStatus, openMonth, getCharts, getSummary, getYearPeriod, saveYearPeriod, saveYearStart, type DashboardSummary, type MonthStatus, type YearPeriod } from "./dashboard-api";
import { visiblePeriod } from "./working-period";
import { DeliveredByCategory } from "../stock/DeliveredByCategory";

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
  const workingYear = useWorkingYear(shop);
  const canWrite = useCanWrite();
  const periodicFactory = shop === "MANUFACTURING";
  const colors = useThemeColors();
  const viewYear = workingYear.year;
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [startDate, setStartDate] = useState<Dayjs | null>(null);
  const [endDate, setEndDate] = useState<Dayjs | null>(null);
  const [periodName, setPeriodName] = useState("");
  const [period, setPeriod] = useState<YearPeriod | null>(null);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [points, setPoints] = useState<{ day: string; revenue: number; cash: number; profit: number }[]>([]);
  const [dayMode, setDayMode] = useState<"active" | "all">("active");
  const [error, setError] = useState<string | null>(null);
  const [backupWarning, setBackupWarning] = useState<string | null>(null);
  const [yearEndRefresh, setYearEndRefresh] = useState(0);
  const [savingPeriod, setSavingPeriod] = useState(false);
  const [monthStatus, setMonthStatus] = useState<MonthStatus>("NOT_OPEN");


  function unlockYear() {
    Modal.confirm({
      title: `Mở khóa năm ${workingYear.year}?`,
      content: "Bạn muốn mở khóa để chuyển sang năm khác không? Phiếu và số liệu sẽ đổi theo năm mới được chọn.",
      okText: "Mở khóa",
      cancelText: "Giữ năm hiện tại",
      onOk: () => workingYearStore.set(shop, { year: workingYear.year, locked: false }),
    });
  }

  useEffect(() => {
    let active = true;
    setRange(null);
    setPeriod(null);
    setStartDate(null);
    setEndDate(null);
    setPeriodName("");
    setError(null);
    setSummary(null);
    setPoints([]);
    getYearPeriod(viewYear).then((value) => {
      if (!active) return;
      setPeriod(value);
      if (value.started && !workingYear.month) workingYearStore.setMonth(shop, dayjs(value.periodFrom).format("YYYY-MM"));
      setError(null);
      setPeriodName(value.periodName);
      setStartDate(value.started ? dayjs(value.periodFrom) : null);
      setEndDate(value.plannedEnd ? dayjs(value.plannedEnd) : null);
      const shown = visiblePeriod(value, workingYear.month);
      setRange(shown?.started ? [dayjs(shown.periodFrom), dayjs(shown.periodTo)] : null);
    }).catch((reason) => { if (active) setError(toApiError(reason).message); });
    return () => { active = false; };
  }, [viewYear, yearEndRefresh, shop, workingYear.month]);

  useEffect(() => {
    if (!workingYear.month) { setMonthStatus("NOT_OPEN"); return; }
    let active = true;
    getMonthStatus(workingYear.month).then((value) => { if (active) setMonthStatus(value); })
      .catch(() => { if (active) setMonthStatus("NOT_OPEN"); });
    return () => { active = false; };
  }, [workingYear.month, shop, yearEndRefresh]);

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
  const startChanged = !!startDate && (!period?.started || period.periodFrom !== startDate.format("YYYY-MM-DD") || period.periodName !== periodName.trim());
  const endChanged = !!endDate && (!period?.endSaved || period.plannedEnd !== endDate.format("YYYY-MM-DD"));

  async function persistStart() {
    if (!startDate || !periodName.trim()) return;
    if (startDate.isAfter(dayjs(), "day")) {
      setError("Ngày bắt đầu không được sau hôm nay. Hãy chọn ngày bắt đầu thực tế của kỳ này.");
      return;
    }
    setSavingPeriod(true);
    try {
      const saved = await saveYearStart(viewYear, startDate.format("YYYY-MM-DD"), periodName.trim());
      setPeriod(saved);
      setPeriodName(saved.periodName);
      const shown = visiblePeriod(saved, workingYear.month);
      setRange(shown ? [dayjs(shown.periodFrom), dayjs(shown.periodTo)] : null);
      setError(null);
      message.success("Đã lưu ngày bắt đầu");
    } catch (reason) {
      setError(toApiError(reason).message);
    } finally {
      setSavingPeriod(false);
    }
  }

  async function persistEnd() {
    if (!period?.started || !endDate) return;
    setSavingPeriod(true);
    try {
      const saved = await saveYearPeriod(viewYear, period.periodFrom, endDate.format("YYYY-MM-DD"));
      setPeriod(saved);
      const shown = visiblePeriod(saved, workingYear.month);
      setRange(shown ? [dayjs(shown.periodFrom), dayjs(shown.periodTo)] : null);
      setError(null);
      message.success("Đã lưu ngày kết thúc dự kiến");
    } catch (reason) {
      setError(toApiError(reason).message);
    } finally {
      setSavingPeriod(false);
    }
  }



  return (
    <div className="dashboard-page">
      <div className="page-toolbar">
        <h2>Tổng quan</h2>
        <Typography.Text>Chọn năm làm việc</Typography.Text>
        <DatePicker picker="year" aria-label="Chọn năm làm việc" allowClear={false} disabled={workingYear.locked}
          value={dayjs(`${viewYear}-01-01`)} onChange={(value) => {
            if (value) workingYearStore.set(shop, { year: value.year(), locked: false });
          }} />
        <DatePicker picker="month" aria-label="Chọn tháng làm việc" placeholder="Chọn tháng" format="MM/YYYY" allowClear={false}
          value={workingYear.month ? dayjs(`${workingYear.month}-01`) : null}
          disabled={!period?.started}
          disabledDate={(date) => !!period?.started && (date.endOf("month").isBefore(dayjs(period.periodFrom), "day") || date.startOf("month").isAfter(dayjs(period.periodTo), "day"))}
          onChange={(value) => workingYearStore.setMonth(shop, value?.format("YYYY-MM"))} />
        {workingYear.month && <Tag color={monthStatus === "CLOSED" ? "green" : monthStatus === "OPEN" ? "blue" : "default"}>
          {monthStatus === "CLOSED" ? "Tháng đã khóa" : monthStatus === "OPEN" ? "Tháng đang mở" : "Tháng chưa mở"}</Tag>}
        {workingYear.month && canWrite && !period?.closed && monthStatus === "NOT_OPEN" && <AppActionButton tone="create" icon={<PlayCircleOutlined />}
          onClick={async () => { try { await openMonth(viewYear, workingYear.month!); setMonthStatus("OPEN"); message.success("Đã mở tháng"); }
            catch (caught) { setError(toApiError(caught).message); } }}>Mở tháng</AppActionButton>}
        {workingYear.month && canWrite && !period?.closed && monthStatus === "OPEN" && <AppActionButton tone="confirm" icon={<CheckOutlined />}
          onClick={() => Modal.confirm({
            title: `Khóa tháng ${dayjs(`${workingYear.month}-01`).format("MM/YYYY")}?`,
            content: <><p>Kỳ {period?.periodName}: {range ? `${range[0].format("DD/MM/YYYY")} – ${range[1].format("DD/MM/YYYY")}` : "chưa có khoảng ngày"}.</p><p>Hãy đối chiếu phiếu nhập, phiếu bán, thu chi và công nợ trước khi khóa. Sau khi khóa, phiếu và chấm công thuộc tháng này không sửa được. Xưởng cần chốt kỳ công trước.</p></>,
            okText: "Khóa tháng", cancelText: "Quay lại",
            autoFocusButton: "cancel",
            onOk: async () => { try { await closeMonth(viewYear, workingYear.month!); setMonthStatus("CLOSED"); message.success("Đã khóa tháng"); }
              catch (caught) { setError(toApiError(caught).message); throw caught; } },
          })}>Khóa tháng</AppActionButton>}
        <AppActionButton tone="edit" icon={workingYear.locked ? <UnlockOutlined /> : <LockOutlined />}
          onClick={() => workingYear.locked ? unlockYear() : workingYearStore.set(shop, { year: viewYear, locked: true })}>
          {workingYear.locked ? "Mở khóa năm" : "Khóa năm"}
        </AppActionButton>
      </div>
      {period && <Card size="small" style={{ marginBottom: 16 }}>
        <div className="year-period-actions">
          <div>
            <Typography.Text strong>{period.periodName}</Typography.Text>{" "}
            <Tag color={period.closed ? "green" : period.started ? "blue" : "default"}>{period.closed ? "Đã chốt" : period.started ? "Đang làm việc" : "Chưa bắt đầu"}</Tag>
            <div><Typography.Text type="secondary">{period.started ? `${dayjs(period.periodFrom).format("DD/MM/YYYY")} – ${period.plannedEnd ? dayjs(period.plannedEnd).format("DD/MM/YYYY") + (period.closed ? "" : " (dự kiến)") : "chưa đặt ngày dự kiến"}` : "Chọn và lưu ngày bắt đầu để tính số liệu"}</Typography.Text></div>
            <div><Typography.Text type="secondary">Chỉ cần lưu ngày bắt đầu để làm việc. Khi biết ngày kết thúc, hãy lưu mốc đó rồi chốt năm.</Typography.Text></div>
          </div>
          {canWrite && <Space wrap>
            {period.closed && <AppActionButton tone="create" icon={<PlayCircleOutlined />}
              title={`Xem kỳ ${viewYear + 1}; lưu ngày bắt đầu để tính số liệu`}
              onClick={() => workingYear.locked ? unlockYear() : workingYearStore.set(shop, { year: viewYear + 1, locked: true })}>Sang năm {viewYear + 1}</AppActionButton>}
            {!period.closed && <><Input aria-label="Tên kỳ làm việc" placeholder="Tên kỳ, ví dụ: Kỳ 2026–2027" value={periodName} maxLength={100} style={{ width: 220 }} onChange={(event) => setPeriodName(event.target.value)} />
              <DatePicker aria-label="Ngày bắt đầu kỳ" placeholder="Ngày bắt đầu" value={startDate} format="DD/MM/YYYY" maxDate={dayjs()} onChange={(value) => { setStartDate(value); setError(null); }} />
              <AppActionButton tone="edit" icon={<SaveOutlined />} disabled={!startChanged || !periodName.trim()} loading={savingPeriod}
                onClick={() => void persistStart()}>Lưu ngày bắt đầu</AppActionButton>
              <DatePicker aria-label="Ngày kết thúc dự kiến" placeholder="Ngày dự kiến" value={endDate} format="DD/MM/YYYY" disabled={!period.started || startChanged} onChange={setEndDate} />
              <AppActionButton tone="edit" icon={<SaveOutlined />} disabled={!period.started || startChanged || !endChanged} loading={savingPeriod}
                onClick={() => void persistEnd()}>Lưu ngày dự kiến</AppActionButton></>}
            {periodicFactory ? <Link to={`/quyet-toan-xuong?year=${viewYear}#kiem-ke-cuoi-nam`}>
              <AppActionButton tone="confirm" icon={<CheckOutlined />} disabled={period.closed || !period.endSaved || startChanged || endChanged}>Chốt năm</AppActionButton>
            </Link> : <AppActionButton tone="confirm" icon={<CheckOutlined />} disabled={period.closed || !period.endSaved || startChanged || endChanged} onClick={() => { const dialog = Modal.confirm({
          title: "Bạn đã kiểm kê tồn kho chưa?",
          content: `Kỳ ${viewYear}: ${dayjs(period.periodFrom).format("DD/MM/YYYY")} – ${dayjs(period.plannedEnd).format("DD/MM/YYYY")}. Hãy kiểm tra số cân thực tế của từng loại nhôm trước khi xác nhận chốt năm.`,
          okText: "Đã kiểm kê, chốt năm",
          autoFocusButton: "cancel",
          cancelText: "Quay lại",
          onOk: async () => {
            try {
              const closing = await closeDistribution(period.periodFrom, period.plannedEnd!, viewYear);
              setSummary((current) => current ? { ...current, closing } : current);
              setYearEndRefresh((value) => value + 1);
              setError(null);
            } catch (error) {
              const detail = toApiError(error).message;
              setError(detail);
              dialog.update({
                content: <Alert type="error" showIcon message="Chưa thể chốt năm"
                  description={<Space direction="vertical"><span>{detail}</span>
                    <Link to="/ton-kho" onClick={() => dialog.destroy()}>Mở Tồn kho để kiểm kê</Link>
                  </Space>} />,
              });
              throw error;
            }
          },
        }); }}>Chốt năm</AppActionButton>}
          </Space>}
        </div>
      </Card>}
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
      {!periodicFactory && !period?.closed && <Alert type="info" showIcon style={{ marginBottom: 12 }}
        message="Để chốt kỳ Phân phối, cần cân thực tế toàn bộ các loại đã phát sinh kho."
        description={<Link to="/ton-kho">Mở Tồn kho → Kiểm kê / Hao hụt trước khi bấm Chốt năm</Link>} />}
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}><Card><Statistic title="Tổng tiền hàng nhập" value={formatMoney(summary?.purchaseAmount ?? 0)} /></Card></Col>
        <Col xs={24} md={8}><Card><Statistic title="Tổng tiền thu dự kiến" value={formatMoney(summary?.revenue ?? 0)} /></Card></Col>
        <Col xs={24} md={8}><Card><Statistic title="Đã thu từ phiếu bán trong kỳ" value={formatMoney(summary?.cashCollected ?? 0)} /></Card></Col>
        <Col xs={24} md={8}><Card><Statistic title="Còn phải thu từ phiếu bán trong kỳ" value={formatMoney(summary?.customerDebt ?? 0)} /></Card></Col>
        <Col xs={24} md={8}><Card><Statistic title="Doanh nghiệp còn nợ nhà cung cấp" value={formatMoney(summary?.supplierDebt ?? 0)} /></Card></Col>
        <Col xs={24} md={8}><Card><Statistic title={shop === "DISTRIBUTION" ? "Tổng đã giao (gồm chuyển Xưởng)" : "Tổng khối lượng hàng đã giao"} value={`${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(Number(summary?.deliveredKg ?? 0) / 1000)} tấn`} />
          {period?.started && range && <DeliveredByCategory from={range[0].format("YYYY-MM-DD")} to={range[1].format("YYYY-MM-DD")} shopKey={shop} unit="tấn" />}
        </Card></Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col span={24}>
          <Card
            title={periodicFactory ? "Biểu đồ cột: Thành phẩm và tiền đã thu" : "Biểu đồ cột: Doanh thu và tiền đã thu từ phiếu"}
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
              {periodicFactory ? "Biểu đồ này chỉ gồm phiếu bán thành phẩm; tiền phế liệu và lãi năm ở Quyết toán xưởng." : "Mỗi ngày hiển thị phiếu bán lập ngày đó và tổng khách đã trả cho chính các phiếu ấy, kể cả trả sau ngày bán. Sổ Thu chi vẫn ghi theo ngày nhận tiền thực tế."}
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
