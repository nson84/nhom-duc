import { Card, Col, Row, Statistic, Typography } from "antd";
import { useEffect, useState } from "react";
import { formatMoney } from "../../utils/money";
import { getYearEndBalance, type YearEndBalance } from "./dashboard-api";

export function YearEndBalanceCard({ year, refreshKey = 0 }: { year: number; refreshKey?: number }) {
  const [balance, setBalance] = useState<YearEndBalance | null>(null);

  useEffect(() => {
    let active = true;
    getYearEndBalance(year).then((value) => { if (active) setBalance(value); })
      .catch(() => { if (active) setBalance(null); });
    return () => { active = false; };
  }, [year, refreshKey]);

  if (!balance) return <Card title={`Số dư chốt năm ${year}`} style={{ marginTop: 16 }}>
    <Typography.Text type="secondary">Chưa có bản chốt số dư kỳ này. Xưởng chốt tại Quyết toán xưởng; Phân phối chọn khoảng ngày trên Tổng quan rồi bấm Chốt năm.</Typography.Text>
  </Card>;
  return <Card title={`Số dư chốt năm ${year} → kỳ sau`} style={{ marginTop: 16 }}>
    <Typography.Paragraph>Kỳ: {new Date(`${balance.periodFrom}T00:00:00`).toLocaleDateString("vi-VN")} – {new Date(`${balance.periodTo}T00:00:00`).toLocaleDateString("vi-VN")}</Typography.Paragraph>
    <Row gutter={[16, 16]}>
      <Col xs={12} md={8}><Statistic title="Nhôm chuyển kỳ" value={`${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(balance.inventoryKg)} kg`} /></Col>
      <Col xs={12} md={8}><Statistic title="Giá trị nhôm chuyển kỳ" value={formatMoney(balance.inventoryValue)} /></Col>
      <Col xs={12} md={8}><Statistic title="Khách còn nợ" value={formatMoney(balance.customerDebt)} /></Col>
      <Col xs={12} md={8}><Statistic title="Còn nợ nhà cung cấp" value={formatMoney(balance.supplierDebt)} /></Col>
      <Col xs={12} md={8}><Statistic title="Lãi năm đã chốt" value={formatMoney(balance.profit)} /></Col>
    </Row>
    <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
      Công nợ và tồn kho tiếp tục theo dõi ở năm sau. Thu hoặc trả nợ cũ chỉ thay đổi dòng tiền và số nợ, không tính lãi lần hai.
    </Typography.Paragraph>
  </Card>;
}
