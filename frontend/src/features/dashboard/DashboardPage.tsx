import { Alert, Card, Col, DatePicker, Row, Segmented, Select, Statistic, Modal, Space, Tag, Typography, message } from "antd";
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
import { CheckOutlined, DeleteOutlined, UnlockOutlined, PlayCircleOutlined, SaveOutlined } from "@ant-design/icons";
import { useWorkingYear, workingYearStore } from "./working-year";
import { closeDistribution, closeMonth, deleteYearPeriod, getMonthStatus, openMonth, reopenMonth, reopenYear, getCharts, getSummary, getYearPeriod, listYearPeriods, saveYearPeriod, saveYearStart, type DashboardSummary, type MonthStatus, type YearPeriod } from "./dashboard-api";
import { formatPeriodLabel, visiblePeriod } from "./working-period";
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

export function DashboardPage({ annualOnly = false }: { annualOnly?: boolean } = {}) {
  const shop = useShop();
  const workingYear = useWorkingYear(shop);
  const canWrite = useCanWrite();
  const periodicFactory = shop === "MANUFACTURING";
  const colors = useThemeColors();
  const viewYear = workingYear.year;
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [summaryMode, setSummaryMode] = useState<"month" | "period">("month");
  const [startDate, setStartDate] = useState<Dayjs | null>(null);
  const [endDate, setEndDate] = useState<Dayjs | null>(null);
  const [period, setPeriod] = useState<YearPeriod | null>(null);
  const [periods, setPeriods] = useState<YearPeriod[]>([]);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [points, setPoints] = useState<{ day: string; revenue: number; cash: number; profit: number }[]>([]);
  const [dayMode, setDayMode] = useState<"active" | "all">("active");
  const [error, setError] = useState<string | null>(null);
  const [backupWarning, setBackupWarning] = useState<string | null>(null);
  const [yearEndRefresh, setYearEndRefresh] = useState(0);
  const [savingPeriod, setSavingPeriod] = useState(false);
  const [monthStatus, setMonthStatus] = useState<MonthStatus>("NOT_OPEN");


  useEffect(() => {
    let active = true;
    listYearPeriods().then((items) => {
      if (!active) return;
      setPeriods(items);
      const latest = items[items.length - 1];
      const selected = workingYearStore.get(shop);
      if (latest && !items.some(item => item.year === selected.year) &&
          !(latest.closed && selected.year === latest.year + 1)) {
        workingYearStore.set(shop, { year: latest.year, locked: selected.locked });
      }
    }).catch(reason => { if (active) message.error(toApiError(reason).message); });
    return () => { active = false; };
  }, [shop, yearEndRefresh]);

  const latestPeriod = periods[periods.length - 1];
  const nextKey = latestPeriod && latestPeriod.year < 2100 ? latestPeriod.year + 1 : undefined;
  const periodIndex = periods.findIndex(item => item.year === viewYear);
  const defaultPeriodName = `Kỳ ${periodIndex === -1 ? periods.length + 1 : periodIndex + 1}`;

  useEffect(() => {
    let active = true;
    setRange(null);
    setPeriod(null);
    setStartDate(null);
    setEndDate(null);
    setError(null);
    setSummary(null);
    setPoints([]);
    getYearPeriod(viewYear).then(async (value) => {
      if (!active) return;
      setPeriod(value);
      if (!annualOnly && value.started && (!workingYear.month || !visiblePeriod(value, workingYear.month))) {
        workingYearStore.setMonth(shop, dayjs(value.periodFrom).format("YYYY-MM"));
      }
      setError(null);
      setStartDate(value.started || (latestPeriod?.closed && viewYear === nextKey) ? dayjs(value.periodFrom) : null);
      setEndDate(value.plannedEnd ? dayjs(value.plannedEnd) : null);
      const shown = visiblePeriod(value, workingYear.month);
      setRange(shown?.started ? [dayjs(shown.periodFrom), dayjs(shown.periodTo)] : null);
      const month = workingYear.month;
      if (!annualOnly && value.started && !value.closed && month && shown &&
          month !== value.periodFrom.slice(0, 7) && await getMonthStatus(month) === "NOT_OPEN") {
        const previous = dayjs(`${month}-01`).subtract(1, "month").format("YYYY-MM");
        if (await getMonthStatus(previous) !== "CLOSED" && active && workingYearStore.get(shop) === workingYear) {
          workingYearStore.setMonth(shop, value.periodFrom.slice(0, 7));
        }
      }
    }).catch((reason) => { if (active) setError(toApiError(reason).message); });
    return () => { active = false; };
  }, [viewYear, yearEndRefresh, shop, workingYear.month, annualOnly, latestPeriod?.closed, nextKey, defaultPeriodName]);

  useEffect(() => {
    if (annualOnly || !workingYear.month) { setMonthStatus("NOT_OPEN"); return; }
    let active = true;
    getMonthStatus(workingYear.month).then((value) => { if (active) setMonthStatus(value); })
      .catch(() => { if (active) setMonthStatus("NOT_OPEN"); });
    return () => { active = false; };
  }, [workingYear.month, shop, yearEndRefresh, annualOnly]);

  const monthFrom = range?.[0].format("YYYY-MM-DD");
  const monthTo = range?.[1].format("YYYY-MM-DD");
  const sameSummaryRange = period?.started && monthFrom === period.periodFrom && monthTo === period.periodTo;
  const summaryFrom = summaryMode === "period" && period?.started ? period.periodFrom : monthFrom;
  const summaryTo = summaryMode === "period" && period?.started ? period.periodTo : monthTo;
  const summaryLabel = summaryMode === "period" ? "từ đầu kỳ"
    : workingYear.month ? `tháng ${dayjs(`${workingYear.month}-01`).format("MM/YYYY")}` : "theo tháng";

  useEffect(() => {
    if (annualOnly || !summaryFrom || !summaryTo) return;
    let active = true;
    setSummary(null);
    setPoints([]);
    Promise.all([getSummary(summaryFrom, summaryTo), getCharts(summaryFrom, summaryTo), getBackupStatus().catch(() => null)])
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
  }, [summaryFrom, summaryTo, shop, annualOnly]);

  const activePoints = useMemo(
    () => points.filter((p) => p.revenue > 0 || p.cash > 0),
    [points, periodicFactory],
  );

  const displayedBarData = dayMode === "active" && activePoints.length > 0 ? activePoints : points;
  const savedPeriodName = period && /^Năm \d{4}$/i.test(period.periodName.trim()) ? defaultPeriodName : period?.periodName;
  const startChanged = !!startDate && (!period?.started || period.periodFrom !== startDate.format("YYYY-MM-DD"));
  const endChanged = !!endDate && (!period?.endSaved || period.plannedEnd !== endDate.format("YYYY-MM-DD"));

  async function persistStart() {
    if (!startDate) return;
    if (startDate.isAfter(dayjs(), "day")) {
      setError("Ngày bắt đầu không được sau hôm nay. Hãy chọn ngày bắt đầu thực tế của kỳ này.");
      return;
    }
    setSavingPeriod(true);
    try {
      const saved = await saveYearStart(viewYear, startDate.format("YYYY-MM-DD"), period?.started ? period.periodName : defaultPeriodName);
      setPeriod(saved);
      const month = !period?.started || period.periodFrom !== saved.periodFrom
        ? saved.periodFrom.slice(0, 7) : workingYear.month;
      if (month !== workingYear.month) workingYearStore.setMonth(shop, month);
      const shown = visiblePeriod(saved, month);
      setRange(shown ? [dayjs(shown.periodFrom), dayjs(shown.periodTo)] : null);
      setError(null);
      message.success("Đã lưu ngày bắt đầu");
      setYearEndRefresh(value => value + 1);
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
      setYearEndRefresh(value => value + 1);
    } catch (reason) {
      setError(toApiError(reason).message);
    } finally {
      setSavingPeriod(false);
    }
  }

  function confirmDateChange(kind: "start" | "end") {
    const previous = kind === "start" ? period?.started && period.periodFrom : period?.plannedEnd;
    const next = kind === "start" ? startDate : endDate;
    if (!next) return;
    const save = kind === "start" ? persistStart : persistEnd;
    if (!previous || dayjs(previous).isSame(next, "day")) {
      void save();
      return;
    }
    Modal.confirm({
      title: kind === "start" ? "Đổi ngày bắt đầu kỳ?" : "Đổi ngày kết thúc dự kiến?",
      content: `${dayjs(previous).format("DD/MM/YYYY")} → ${next.format("DD/MM/YYYY")}`,
      okText: "Xác nhận thay đổi",
      cancelText: "Quay lại",
      autoFocusButton: "cancel",
      onOk: save,
    });
  }

  async function selectWorkingMonth(value: Dayjs | null) {
    if (!value || !period?.started) return;
    const month = value.format("YYYY-MM");
    if (month === workingYear.month) return;
    try {
      if (month !== period.periodFrom.slice(0, 7) && await getMonthStatus(month) === "NOT_OPEN") {
        const previous = value.subtract(1, "month").format("YYYY-MM");
        if (await getMonthStatus(previous) !== "CLOSED") {
          setError(`Hãy hoàn tất và khóa tháng ${value.subtract(1, "month").format("MM/YYYY")} trước khi chuyển sang tháng ${value.format("MM/YYYY")}.`);
          return;
        }
      }
      if (workingYearStore.get(shop) !== workingYear) return;
      setError(null);
      workingYearStore.setMonth(shop, month);
    } catch (error) {
      setError(toApiError(error).message);
    }
  }


  return (
    <div className="dashboard-page">
      <div className="page-toolbar">
        <h2>{annualOnly ? "Chốt kỳ" : "Tổng quan"}</h2>
        {annualOnly ? <Link to="/">Đổi kỳ ở Tổng quan</Link> : <><Typography.Text>Chọn kỳ làm việc</Typography.Text>
        <Select aria-label="Chọn kỳ làm việc" style={{ width: 380, maxWidth: "100%" }}
          value={viewYear} options={[
            ...periods.map((item, index) => ({ value: item.year, label: formatPeriodLabel(item, index) })),
            ...(periodIndex === -1 ? [{ value: viewYear, label: `${defaultPeriodName} · Chưa lưu ngày bắt đầu` }] : []),
          ]} onChange={(year) => workingYearStore.set(shop, { year, locked: false })} /></>}
        {!annualOnly && <><DatePicker picker="month" aria-label="Chọn tháng làm việc" placeholder="Chọn tháng" format="MM/YYYY" allowClear={false}
          value={workingYear.month ? dayjs(`${workingYear.month}-01`) : null}
          disabled={!period?.started}
          disabledDate={(date) => !!period?.started && (date.endOf("month").isBefore(dayjs(period.periodFrom), "day") || date.startOf("month").isAfter(dayjs(period.periodTo), "day"))}
          onChange={(value) => void selectWorkingMonth(value)} />
        {workingYear.month && period?.started && <Tag color={period.closed || monthStatus === "CLOSED" ? "green" : monthStatus === "OPEN" ? "blue" : "default"}>
          {period.closed ? "Kỳ đã chốt · Chỉ xem" : monthStatus === "CLOSED" ? "Tháng đã khóa" : monthStatus === "OPEN" ? "Tháng đang mở" : "Tháng chưa mở"}</Tag>}
        {workingYear.month && canWrite && period?.started && !period.closed && monthStatus === "NOT_OPEN" && <AppActionButton tone="create" icon={<PlayCircleOutlined />}
          onClick={async () => { try { await openMonth(viewYear, workingYear.month!); setMonthStatus("OPEN"); message.success("Đã mở tháng"); }
            catch (caught) { setError(toApiError(caught).message); } }}>Mở tháng</AppActionButton>}
        {workingYear.month && canWrite && period?.started && !period.closed && monthStatus === "OPEN" && <AppActionButton tone="confirm" icon={<CheckOutlined />}
          onClick={() => Modal.confirm({
            title: `Khóa tháng ${dayjs(`${workingYear.month}-01`).format("MM/YYYY")}?`,
            content: <><p>{savedPeriodName}: {range ? `${range[0].format("DD/MM/YYYY")} – ${range[1].format("DD/MM/YYYY")}` : "chưa có khoảng ngày"}.</p><p>Đối chiếu chứng từ trước khi khóa để mở tháng tiếp theo. Kiểm kê chốt sổ tháng là tùy chọn. Xưởng cần chốt kỳ công trước.</p></>,
            okText: "Khóa tháng", cancelText: "Quay lại",
            autoFocusButton: "cancel",
            onOk: async () => { try { await closeMonth(viewYear, workingYear.month!); setMonthStatus("CLOSED"); message.success("Đã khóa tháng"); }
              catch (caught) { setError(toApiError(caught).message); throw caught; } },
          })}>Khóa tháng để sang tháng mới</AppActionButton>}
        {workingYear.month && canWrite && period?.started && !period.closed && monthStatus === "CLOSED" &&
          <AppActionButton tone="edit" icon={<UnlockOutlined />} onClick={() => {
            const month = workingYear.month!;
            Modal.confirm({
              title: `Mở lại tháng ${dayjs(`${month}-01`).format("MM/YYYY")}?`,
              content: "Tháng này sẽ cho phép ghi và sửa chứng từ. Sau khi sửa xong, bạn có thể khóa lại.",
              okText: "Mở lại tháng", cancelText: "Quay lại", autoFocusButton: "cancel",
              onOk: async () => {
                try {
                  await reopenMonth(viewYear, month);
                  setMonthStatus("OPEN");
                  message.success("Đã mở lại tháng");
                } catch (caught) {
                  message.error(toApiError(caught).message);
                  throw caught;
                }
              },
            });
          }}>Mở lại tháng</AppActionButton>}</>}
      </div>
      {period && <Card size="small" style={{ marginBottom: 16 }}>
        <div className="year-period-actions">
          <div>
            <Typography.Text strong>{period.started ? savedPeriodName : defaultPeriodName}</Typography.Text>{" "}
            <Tag color={period.closed ? "green" : period.started ? "blue" : "default"}>{period.closed ? "Đã chốt" : period.started ? "Đang làm việc" : "Chưa bắt đầu"}</Tag>
            <div><Typography.Text type="secondary">{period.started ? `${dayjs(period.periodFrom).format("DD/MM/YYYY")} – ${period.plannedEnd ? dayjs(period.plannedEnd).format("DD/MM/YYYY") + (period.closed ? "" : " (dự kiến)") : "chưa đặt ngày dự kiến"}` : "Chọn và lưu ngày bắt đầu để tính số liệu"}</Typography.Text></div>
          </div>
          {canWrite && <Space wrap>
            {annualOnly && period.started && !period.closed && <AppActionButton tone="delete" icon={<DeleteOutlined />} onClick={() => {
              const dialog = Modal.confirm({
                title: `Xóa ${period.periodName}?`,
                content: `Ngày bắt đầu đã lưu: ${dayjs(period.periodFrom).format("DD/MM/YYYY")}. Chỉ xóa được kỳ chưa sử dụng.`,
                okText: "Xóa kỳ", okButtonProps: { danger: true }, cancelText: "Giữ kỳ", autoFocusButton: "cancel",
                onOk: async () => {
                  try {
                    await deleteYearPeriod(viewYear, period.periodFrom);
                    workingYearStore.setMonth(shop, undefined);
                    setYearEndRefresh(value => value + 1);
                    message.success("Đã xóa kỳ. Bạn có thể chọn lại năm.");
                  } catch (error) {
                    dialog.update({ content: <Alert type="error" showIcon message={toApiError(error).message} /> });
                    throw error;
                  }
                },
              });
            }}>Xóa kỳ</AppActionButton>}
            {annualOnly && !periodicFactory && period.closed && <AppActionButton tone="edit" icon={<UnlockOutlined />} onClick={() => {
              Modal.confirm({
                title: "Mở lại kiểm kê Phân phối?",
                content: "Kỳ sẽ trở về trạng thái chưa chốt. Hãy chọn kỳ này, vào Tồn kho để kiểm kê lại và chốt lại sau khi sửa.",
                okText: "Mở lại kiểm kê",
                cancelText: "Hủy",
                autoFocusButton: "cancel",
                onOk: async () => {
                  try {
                    await reopenYear(viewYear);
                    workingYearStore.set(shop, { year: viewYear, locked: true });
                    workingYearStore.setMonth(shop, undefined);
                    setYearEndRefresh(value => value + 1);
                    message.success("Đã mở lại. Vào Tồn kho để kiểm kê lại.");
                  } catch (error) {
                    message.error(toApiError(error).message);
                  }
                },
              });
            }}>Mở lại kiểm kê</AppActionButton>}
            {!annualOnly && period.closed && latestPeriod?.year === viewYear && nextKey != null && <AppActionButton tone="create" icon={<PlayCircleOutlined />}
              onClick={() => workingYearStore.set(shop, { year: nextKey, locked: false })}>Mở kỳ tiếp theo</AppActionButton>}
            {!period.closed && <>
              <DatePicker aria-label="Ngày bắt đầu kỳ" placeholder="Ngày bắt đầu" allowClear={false} value={startDate} format="DD/MM/YYYY" maxDate={dayjs()} onChange={(value) => { if (value) setStartDate(value); setError(null); }} />
              <AppActionButton tone="edit" icon={<SaveOutlined />} disabled={!startChanged} loading={savingPeriod}
                onClick={() => confirmDateChange("start")}>Lưu ngày bắt đầu</AppActionButton>
              {period.started && <><DatePicker aria-label="Ngày kết thúc dự kiến" placeholder="Ngày kết thúc dự kiến" allowClear={false} value={endDate} format="DD/MM/YYYY" disabled={startChanged} onChange={value => { if (value) setEndDate(value); }} />
              <AppActionButton tone="edit" icon={<SaveOutlined />} disabled={!period.started || startChanged || !endChanged} loading={savingPeriod}
                onClick={() => confirmDateChange("end")}>Lưu ngày dự kiến</AppActionButton></>}</>}
            {!annualOnly ? <Link to="/chot-nam">Mở Chốt kỳ</Link> : periodicFactory ? <Link to="/quyet-toan-xuong#kiem-ke-cuoi-nam">
              <AppActionButton tone="confirm" icon={<CheckOutlined />}>Mở kiểm kê Xưởng</AppActionButton>
            </Link> : <AppActionButton tone="confirm" icon={<CheckOutlined />} disabled={period.closed || !period.endSaved || startChanged || endChanged || dayjs(period.plannedEnd).isAfter(dayjs(), "day")} onClick={() => { const dialog = Modal.confirm({
          title: "Bạn đã kiểm kê tồn kho chưa?",
          content: `${savedPeriodName}: ${dayjs(period.periodFrom).format("DD/MM/YYYY")} – ${dayjs(period.plannedEnd).format("DD/MM/YYYY")}. Hãy kiểm tra số cân thực tế trước khi chốt kỳ.`,
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
      {annualOnly && period?.closed && <Link to="/so-cuoi-nam">Mở Sổ cuối năm</Link>}
      {annualOnly && !period?.closed && <Alert type="info" showIcon
        message="Kiểm kê theo ngày kết thúc kỳ"
        description={periodicFactory
          ? "Lưu ngày kết thúc, rồi mở Quyết toán Xưởng để kiểm kê và chốt. Chốt sổ tháng là tùy chọn."
          : <>Lưu ngày kết thúc, nhập số cân tại <Link to="/ton-kho">Tồn kho → Kiểm kê / Hao hụt</Link>, rồi quay lại bấm Chốt năm. App sẽ kiểm tra số cân và biến động kho trước khi xác nhận. Chốt sổ tháng là tùy chọn.</>} />}
      {!annualOnly && <>
      {backupWarning ? (
        <Alert
          type="warning"
          showIcon
          message="Cảnh báo sao lưu dữ liệu gần nhất thất bại"
          description={backupWarning}
          style={{ marginBottom: 16 }}
        />
      ) : null}
      <Space wrap style={{ marginBottom: 16 }}>
        <Segmented aria-label="Khoảng số liệu tổng quan" value={summaryMode} onChange={setSummaryMode}
          options={[{ label: "Theo tháng", value: "month" }, { label: "Từ đầu kỳ", value: "period" }]} />
        {summaryFrom && summaryTo && <Typography.Text type="secondary">
          {dayjs(summaryFrom).format("DD/MM/YYYY")} – {dayjs(summaryTo).format("DD/MM/YYYY")}
        </Typography.Text>}
        {sameSummaryRange && <Typography.Text type="secondary">Cả kỳ nằm trong tháng đang xem</Typography.Text>}
      </Space>
      <div className="dashboard-stat-grid">
        <Card><Statistic title={`Tổng tiền hàng nhập ${summaryLabel}`} value={formatMoney(summary?.purchaseAmount ?? 0)} /></Card>
        <Card><Statistic title={`Tổng nhôm nhập ${summaryLabel} (gồm nội bộ)`} value={summary ? `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(Number(summary.purchaseKg ?? 0) / 1000)} tấn` : "—"} /></Card>
        <Card><Statistic title={`Tổng tiền thu dự kiến ${summaryLabel}`} value={formatMoney(summary?.revenue ?? 0)} /></Card>
        <Card><Statistic title={`Đã thu đến hiện tại từ phiếu bán lập ${summaryLabel}`} value={formatMoney(summary?.cashCollected ?? 0)} /></Card>
        <Card><Statistic title={`Còn phải thu từ phiếu bán lập ${summaryLabel}`} value={formatMoney(summary?.customerDebt ?? 0)} /></Card>
        <Card><Statistic title={`Còn nợ nhà cung cấp từ phiếu nhập lập ${summaryLabel}`} value={formatMoney(summary?.supplierDebt ?? 0)} /></Card>
        <Card><Statistic title={shop === "DISTRIBUTION" ? `Tổng đã giao ${summaryLabel} (gồm chuyển Xưởng)` : `Tổng khối lượng hàng đã giao ${summaryLabel}`} value={`${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(Number(summary?.deliveredKg ?? 0) / 1000)} tấn`} />
          {period?.started && summaryFrom && summaryTo && <DeliveredByCategory from={summaryFrom} to={summaryTo} shopKey={shop} unit="tấn" />}
        </Card>
      </div>

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
      </>}
    </div>
  );
}
