import { Alert, Card, Col, DatePicker, Empty, Row, Segmented, Statistic } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useEffect, useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { ApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { getBackupStatus } from "../backup/backup-api";
import { getCharts, getSummary, type DashboardSummary } from "./dashboard-api";

const { RangePicker } = DatePicker;

const compactAxis = (value: number) => {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1).replace(/\.0$/, "")}tr`;
  if (value >= 1_000) return `${Math.round(value / 1_000)}k`;
  return String(value);
};

export function DashboardPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().startOf("month"), dayjs()]);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [points, setPoints] = useState<{ day: string; revenue: number; cash: number; profit: number }[]>([]);
  const [dayMode, setDayMode] = useState<"active" | "all">("active");
  const [error, setError] = useState<string | null>(null);
  const [backupWarning, setBackupWarning] = useState<string | null>(null);

  useEffect(() => {
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
    () => points.filter((p) => p.revenue > 0 || p.cash > 0 || p.profit !== 0),
    [points],
  );

  const displayedBarData = dayMode === "active" && activePoints.length > 0 ? activePoints : points;

  const pieData = useMemo(() => {
    const cogs = Math.max(0, Number(summary?.cogs ?? 0));
    const profit = Math.max(0, Number(summary?.grossProfit ?? 0));
    const expenses = Math.max(0, Number(summary?.operatingExpenses ?? 0));
    const uncollected = Math.max(0, Number(summary?.revenue ?? 0) - Number(summary?.cashCollected ?? 0));
    return [
      { name: "Giá vốn hàng bán", value: cogs, color: "#5b8c9a" },
      { name: "Lãi gộp", value: profit, color: "#389e0d" },
      { name: "Chi phí hoạt động", value: expenses, color: "#d46b08" },
      { name: "Chưa thu tiền", value: uncollected, color: "#cf1322" },
    ].filter((item) => item.value > 0);
  }, [summary]);

  return (
    <div style={{ overflowX: "hidden" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>Tổng quan</h2>
        <RangePicker
          value={range}
          allowClear={false}
          format="DD/MM/YYYY"
          onChange={(values) => {
            if (values?.[0] && values[1]) {
              setRange([values[0], values[1]]);
            }
          }}
        />
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
      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card><Statistic title="Doanh thu" value={formatMoney(summary?.revenue ?? 0)} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="Tiền thực thu" value={formatMoney(summary?.cashCollected ?? 0)} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="Lãi gộp" value={formatMoney(summary?.grossProfit ?? 0)} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="Chi phí hoạt động" value={formatMoney(summary?.operatingExpenses ?? 0)} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="Giá trị tồn kho" value={formatMoney(summary?.inventoryValue ?? 0)} /></Card>
        </Col>
        <Col xs={24} md={8}>
          <Card><Statistic title="Tiền công tháng đang xem" value={formatMoney(summary?.payroll ?? 0)} /></Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={15}>
          <Card
            title="Biểu đồ cột: Doanh thu, Thực thu & Lãi gộp"
            extra={
              <Segmented
                size="small"
                value={dayMode}
                onChange={(val) => setDayMode(val as "active" | "all")}
                options={[
                  { label: "Ngày có giao dịch", value: "active" },
                  { label: "Tất cả ngày", value: "all" },
                ]}
              />
            }
          >
            <p style={{ marginTop: 0, color: "#5c6b73" }}>
              Doanh thu là giá trị hàng đã bán. Tiền thực thu là tiền đã nhận. Tiền nhập hàng chưa bán nằm trong tồn kho.
            </p>
            <div style={{ height: 320 }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={displayedBarData} margin={{ top: 8, right: 24, left: 8, bottom: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e6ebef" vertical={false} />
                  <XAxis dataKey="day" />
                  <YAxis width={68} tickFormatter={compactAxis} />
                  <Tooltip
                    isAnimationActive={false}
                    cursor={{ fill: "rgba(47, 111, 143, 0.08)" }}
                    formatter={(value) => formatMoney(Number(value ?? 0))}
                  />
                  <Legend />
                  <Bar
                    dataKey="revenue"
                    name="Doanh thu"
                    fill="#2f6f8f"
                    radius={[4, 4, 0, 0]}
                    maxBarSize={44}
                    isAnimationActive={false}
                  />
                  <Bar
                    dataKey="cash"
                    name="Tiền thực thu"
                    fill="#c47b2b"
                    radius={[4, 4, 0, 0]}
                    maxBarSize={44}
                    isAnimationActive={false}
                  />
                  <Bar
                    dataKey="profit"
                    name="Lãi gộp"
                    fill="#389e0d"
                    radius={[4, 4, 0, 0]}
                    maxBarSize={44}
                    isAnimationActive={false}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Card>
        </Col>

        <Col xs={24} lg={9}>
          <Card title="Biểu đồ tròn: Cơ cấu tài chính trong kỳ">
            <p style={{ marginTop: 0, color: "#5c6b73" }}>
              Tỷ trọng Giá vốn, Lãi gộp, Chi phí hoạt động và khoản Chưa thu trong kỳ đang chọn.
            </p>
            <div style={{ height: 320, display: "flex", alignItems: "center", justifyContent: "center" }}>
              {pieData.length === 0 ? (
                <Empty description="Chưa có số liệu trong kỳ" />
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={pieData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="46%"
                      innerRadius={58}
                      outerRadius={96}
                      paddingAngle={3}
                      isAnimationActive={false}
                    >
                      {pieData.map((entry) => (
                        <Cell key={entry.name} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip
                      isAnimationActive={false}
                      formatter={(value) => formatMoney(Number(value ?? 0))}
                    />
                    <Legend verticalAlign="bottom" />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </div>
          </Card>
        </Col>
      </Row>
    </div>
  );
}
