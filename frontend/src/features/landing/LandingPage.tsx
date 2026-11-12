import { Button, Card, Col, Row, Space, Typography } from "antd";
import {
  CheckCircleFilled,
  EnvironmentOutlined,
  PhoneOutlined,
  SafetyCertificateFilled,
  ToolFilled,
  TrophyFilled,
} from "@ant-design/icons";
import { useEffect, useState, type ReactNode } from "react";
import { api } from "../../services/api-client";
import { type LandingContent } from "./landing-api";

const { Title, Paragraph, Text } = Typography;

type Envelope<T> = { data: T };

type ShopInfo = {
  shopName: string;
  shopAddress: string;
  shopPhone: string;
  categories: string[];
  content: LandingContent;
};


const strengthIcons: ReactNode[] = [
  <ToolFilled key="tool" />,
  <SafetyCertificateFilled key="safe" />,
  <TrophyFilled key="trophy" />,
  <CheckCircleFilled key="check" />,
];

/**
 * Trang giới thiệu công khai cho khách.
 *
 * Đây là trang duy nhất không cần đăng nhập. Toàn bộ số liệu kinh doanh, giá nhập, tồn kho và cấu
 * hình cửa hàng không được hiển thị ở đây. Chữ từng phần lấy từ API (Admin sửa được).
 */
export function LandingPage() {
  const [info, setInfo] = useState<ShopInfo | null>(null);

  useEffect(() => {
    api
      .get<Envelope<ShopInfo>>("/api/v1/public/shop-info")
      .then((res) => setInfo(res.data.data))
      .catch(() => undefined);
  }, []);

  const shopName = info?.shopName || "Cửa hàng nhôm";
  const phone = info?.shopPhone || "";
  const content = info?.content;

  return (
    <div className="landing">
      <header className="landing-header">
        <div className="landing-wrap landing-header-inner">
          <strong>{shopName}</strong>
          <Space wrap>
            {phone ? (
              <Button href={`tel:${phone}`} icon={<PhoneOutlined />}>
                {phone}
              </Button>
            ) : null}
            <Button type="primary" href="#lien-he">
              {content?.ctaHeader || "Nhận báo giá"}
            </Button>
          </Space>
        </div>
      </header>

      <section className="landing-hero">
        <div className="landing-wrap">
          <Title level={1}>{content?.heroTitle || "Nhôm cửa, nhôm hộp — báo giá trong 15 phút"}</Title>
          <Paragraph className="landing-lead">
            {content?.heroSubtitle ||
              "Cửa hàng cung cấp nhôm loại 1, loại 2, phụ kiện và khuôn cửa cho thợ và công trình. Để lại số điện thoại, chúng tôi gọi lại xác nhận khối lượng và chốt giá ngay."}
          </Paragraph>
          <Space wrap size="middle">
            <Button type="primary" size="large" href="#lien-he">
              {content?.ctaPrimary || "Để lại số điện thoại"}
            </Button>
            {phone ? (
              <Button size="large" href={`tel:${phone}`} icon={<PhoneOutlined />}>
                Gọi {phone}
              </Button>
            ) : null}
          </Space>
        </div>
      </section>

      <section className="landing-wrap landing-strengths">
        <Row gutter={[16, 16]}>
          {(content?.strengths || []).map((item, index) => (
            <Col xs={24} sm={12} lg={6} key={`${item.title}-${index}`}>
              <Card className="landing-card" bordered={false}>
                <div className="landing-icon">{strengthIcons[index] ?? strengthIcons[0]}</div>
                <Title level={4}>{item.title}</Title>
                <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                  {item.text}
                </Paragraph>
              </Card>
            </Col>
          ))}
        </Row>
      </section>

      {info && info.categories.length > 0 ? (
        <section className="landing-wrap" style={{ paddingBottom: 8 }}>
          <Title level={3}>{content?.categoriesTitle || "Mặt hàng đang cung cấp"}</Title>
          <Space wrap>
            {info.categories.map((c) => (
              <span className="landing-chip" key={c}>
                {c}
              </span>
            ))}
          </Space>
        </section>
      ) : null}

      <section className="landing-contact" id="lien-he">
        <div className="landing-wrap">
          <Row gutter={[32, 24]} align="middle">
            <Col xs={24} lg={10}>
              <Title level={2}>{content?.contactTitle || "Nhận báo giá"}</Title>
              <Paragraph>
                {content?.contactDescription ||
                  "Điền thông tin bên cạnh. Cửa hàng sẽ gọi lại trong giờ làm việc để xác nhận khối lượng, kích thước và báo giá."}
              </Paragraph>
              {info?.shopAddress ? (
                <Paragraph>
                  <EnvironmentOutlined /> {info.shopAddress}
                </Paragraph>
              ) : null}
              {phone ? (
                <Paragraph>
                  <PhoneOutlined /> <Text strong>{phone}</Text>
                </Paragraph>
              ) : null}
              <Text type="secondary">
                {content?.privacyNote ||
                  "Thông tin của bạn chỉ dùng để liên hệ tư vấn, không chia sẻ cho bên thứ ba."}
              </Text>
            </Col>
            <Col xs={24} lg={14}>
              <Card>Liên hệ trực tiếp qua thông tin cửa hàng để được tư vấn.</Card>
            </Col>
          </Row>
        </div>
      </section>

      <footer className="landing-footer">
        <div className="landing-wrap">
          <Text type="secondary">
            {shopName}
            {info?.shopAddress ? ` — ${info.shopAddress}` : ""}
            {phone ? ` — ${phone}` : ""}
          </Text>
        </div>
      </footer>
    </div>
  );
}
