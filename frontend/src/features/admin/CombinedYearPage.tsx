import { Alert, Card, InputNumber, Space, Statistic, Table, Typography } from "antd";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { formatMoney } from "../../utils/money";
import { toApiError } from "../../services/error-handler";
import { getCombinedYear, type CombinedBranch, type CombinedYear } from "./combined-api";

const money = (value: number | null | undefined) => value == null ? "Chưa chốt" : formatMoney(value);

export function CombinedYearPage() {
  const [year, setYear] = useState(dayjs().year());
  const [data, setData] = useState<CombinedYear | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setLoading(true);
    getCombinedYear(year).then((next) => { if (active) { setData(next); setError(null); } })
      .catch((err) => { if (active) setError(toApiError(err).message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [year]);

  const branches: CombinedBranch[] = data ? [data.distribution, data.manufacturing] : [];
  return <div>
    <div className="page-toolbar"><h2>Tổng hợp phân phối và xưởng</h2>
      <Space>Năm <InputNumber min={2000} max={2100} precision={0} value={year}
        onChange={(value) => { if (value) setYear(value); }} /></Space></div>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    {data && data.estimatedProfit == null && <Alert type="info" showIcon
      message="Lãi quản trị đang chờ chốt năm của hai bên"
      description="Doanh thu và tiền đã thu vẫn xem được. Lãi gộp hiển thị khi Phân phối chốt đủ năm và Xưởng chốt kiểm kê năm."
      style={{ marginBottom: 16 }} />}
    {data && (data.distribution.periodFrom !== data.manufacturing.periodFrom || data.distribution.periodTo !== data.manufacturing.periodTo) &&
      <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="Hai bên đang chốt ở hai khoảng ngày khác nhau"
        description="Số tổng cộng gồm hai kỳ khác nhau; xem ngày của từng bên trước khi đối chiếu." />}
    <Card loading={loading}>
      <Space size="large" wrap>
        <Statistic title="Doanh thu bên ngoài + phế liệu" value={money(data?.revenue)} />
        <Statistic title="Tiền đã thu trong năm" value={money(data?.cashCollected)} />
        <Statistic title="Tổng tiền vào" value={money(data?.cashIn)} />
        <Statistic title="Tổng tiền ra" value={money(data?.cashOut)} />
        <Statistic title="Dòng tiền thuần" value={money(data?.netCash)} />
        <Statistic title="Công nợ khách cuối năm" value={money(data?.customerDebt)} />
        <Statistic title="Lãi quản trị gộp" value={money(data?.estimatedProfit)} />
      </Space>
    </Card>
    <Card title="Chi tiết từng bên" loading={loading} style={{ marginTop: 16 }}>
      <Table rowKey="shopId" pagination={false} dataSource={branches} scroll={{ x: 850 }} columns={[
        { title: "Bên", dataIndex: "name" },
        { title: "Kỳ chốt", render: (_, item: CombinedBranch) => `${dayjs(item.periodFrom).format("DD/MM/YYYY")} – ${dayjs(item.periodTo).format("DD/MM/YYYY")}` },
        { title: "Bán hàng", dataIndex: "salesRevenue", render: money },
        { title: "Bán phế liệu", dataIndex: "scrapRevenue", render: money },
        { title: "Đã thu", dataIndex: "cashCollected", render: money },
        { title: "Tiền vào", dataIndex: "cashIn", render: money },
        { title: "Tiền ra", dataIndex: "cashOut", render: money },
        { title: "Dòng tiền thuần", dataIndex: "netCash", render: money },
        { title: "Công nợ", dataIndex: "customerDebt", render: money },
        { title: "Mua ngoài", dataIndex: "externalPurchases", render: money },
        { title: "Giá vốn nhôm", dataIndex: "materialCost", render: money },
        { title: "Lương", dataIndex: "payrollCost", render: (v: number, row: CombinedBranch) =>
          row.name === "Phân phối" ? "Không áp dụng" : money(v) },
        { title: "Chi phí khác", dataIndex: "otherExpenses", render: money },
        { title: "Lãi quản trị", dataIndex: "estimatedProfit", render: money },
      ]} />
      <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
        Giao dịch chuyển nhôm giữa phân phối và xưởng không cộng vào doanh thu bán ra ngoài.
        Phân phối không có lương nhân viên; lương chỉ lấy từ xưởng. Lãi xưởng dùng giá trị kiểm kê cuối năm.
      </Typography.Paragraph>
    </Card>
  </div>;
}
