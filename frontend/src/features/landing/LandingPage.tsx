import { Alert, Button, Card, Col, Form, Input, Row, Select, Space, Typography, message } from "antd";
import {
  CheckCircleFilled,
  EnvironmentOutlined,
  PhoneOutlined,
  SafetyCertificateFilled,
  ToolFilled,
  TrophyFilled,
} from "@ant-design/icons";
import { useEffect, useState, type ReactNode } from "react";
import { toApiError } from "../../services/error-handler";
import { api } from "../../services/api-client";
import { requiredNameRules } from "../../utils/name-rules";
import { requestTypeLabels, submitLead, type LandingContent } from "./landing-api";

const { Title, Paragraph, Text } = Typography;

type Envelope<T> = { data: T };

type ShopInfo = {
  shopName: string;
  shopAddress: string;
  shopPhone: string;
  categories: string[];
  content: LandingContent;
};

type FormValues = {
  fullName: string;
  phone: string;
  email?: string;
  requestType: string;
  message?: string;
  /** Bẫy spam: ẩn bằng CSS, người thật không thấy nên không điền. */
  website?: string;
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
  const [form] = Form.useForm<FormValues>();
  const [info, setInfo] = useState<ShopInfo | null>(null);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);

  useEffect(() => {
    api
      .get<Envelope<ShopInfo>>("/api/v1/public/shop-info")
      .then((res) => setInfo(res.data.data))
      .catch(() => undefined);
  }, []);

  const onFinish = async (values: FormValues) => {
    setSending(true);
    try {
      await submitLead({ ...values, sourcePage: "landing" });
      setSent(true);
      form.resetFields();
      message.success("Đã gửi thông tin. Chúng tôi sẽ gọi lại sớm.");
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setSending(false);
    }
  };

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
              <Card className="landing-form-card">
                {sent ? (
                  <Alert
                    type="success"
                    showIcon
                    message={content?.formSuccessTitle || "Đã nhận thông tin của bạn"}
                    description={
                      content?.formSuccessDescription ||
                      "Cửa hàng sẽ gọi lại sớm. Nếu cần gấp, hãy gọi trực tiếp số điện thoại bên cạnh."
                    }
                    action={
                      <Button size="small" onClick={() => setSent(false)}>
                        {content?.formAgainLabel || "Gửi thêm"}
                      </Button>
                    }
                  />
                ) : (
                  <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
                    <Row gutter={[16, 32]}>
                      <Col xs={24} sm={12}>
                        <Form.Item
                          name="fullName"
                          label="Họ và tên"
                          rules={requiredNameRules("Nhập họ tên", 150)}
                        >
                          <Input placeholder="Nguyễn Văn A" size="large" maxLength={150} />
                        </Form.Item>
                      </Col>
                      <Col xs={24} sm={12}>
                        <Form.Item
                          name="phone"
                          label="Số điện thoại"
                          rules={[
                            { required: true, message: "Nhập số điện thoại" },
                            {
                              pattern: /^(0|\+84)[0-9]{8,10}$/,
                              message: "Số chưa đúng, ví dụ 0912345678",
                            },
                          ]}
                        >
                          <Input placeholder="0912345678" size="large" inputMode="tel" />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Row gutter={[16, 32]}>
                      <Col xs={24} sm={12}>
                        <Form.Item
                          name="requestType"
                          label="Bạn cần gì?"
                          initialValue="BAO_GIA"
                          rules={[{ required: true }]}
                        >
                          <Select
                            size="large"
                            options={Object.entries(requestTypeLabels).map(([value, label]) => ({
                              value,
                              label,
                            }))}
                          />
                        </Form.Item>
                      </Col>
                      <Col xs={24} sm={12}>
                        <Form.Item
                          name="email"
                          label="Email (không bắt buộc)"
                          rules={[{ type: "email", message: "Email chưa đúng định dạng" }]}
                        >
                          <Input placeholder="email@example.com" size="large" inputMode="email" />
                        </Form.Item>
                      </Col>
                    </Row>
                    <Form.Item name="message" label="Nhu cầu cụ thể (không bắt buộc)">
                      <Input.TextArea
                        rows={3}
                        maxLength={1000}
                        showCount
                        placeholder="Ví dụ: cần 200 kg nhôm loại 1 dày 1,4 ly, giao trong tuần này"
                      />
                    </Form.Item>
                    <Form.Item name="website" className="landing-honeypot" aria-hidden="true">
                      <Input tabIndex={-1} autoComplete="off" />
                    </Form.Item>
                    <Button type="primary" htmlType="submit" size="large" block loading={sending}>
                      {content?.formSubmitLabel || "Gửi thông tin"}
                    </Button>
                  </Form>
                )}
              </Card>
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
