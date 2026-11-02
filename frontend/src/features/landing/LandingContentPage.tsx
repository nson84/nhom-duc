import { Alert, Button, Card, Col, Divider, Form, Input, Row, Space, message } from "antd";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toApiError } from "../../services/error-handler";
import {
  getLandingContent,
  updateLandingContent,
  type LandingContent,
} from "./landing-api";

/**
 * Sửa chữ từng phần trên trang giới thiệu công khai.
 *
 * Tên/SĐT/địa chỉ cửa hàng vẫn lấy từ Cài đặt; trang này chỉ sửa tiêu đề, mô tả và 4 điểm nổi bật.
 */
export function LandingContentPage() {
  const [form] = Form.useForm<LandingContent>();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setLoading(true);
    getLandingContent()
      .then((content) => form.setFieldsValue(content))
      .catch((error) => message.error(toApiError(error).message))
      .finally(() => setLoading(false));
  }, [form]);

  const onFinish = async (values: LandingContent) => {
    setSaving(true);
    try {
      const saved = await updateLandingContent(values);
      form.setFieldsValue(saved);
      message.success("Đã lưu nội dung trang giới thiệu");
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      title="Nội dung trang giới thiệu"
      loading={loading}
      extra={
        <Space>
          <Link to="/khach-hang">Khách để lại thông tin</Link>
          <a onClick={() => window.open("/gioi-thieu", "_blank")}>Xem trang</a>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Sửa chữ từng phần trên trang /gioi-thieu"
        description="Tên cửa hàng, địa chỉ và số điện thoại lấy từ Cài đặt. Phần dưới đây chỉ là nội dung chữ của từng khối trên trang."
      />
      <Form form={form} layout="vertical" onFinish={onFinish} disabled={loading}>
        <Divider orientation="left">Phần đầu (hero)</Divider>
        <Form.Item
          name="heroTitle"
          label="Tiêu đề lớn"
          rules={[{ required: true, message: "Nhập tiêu đề" }, { max: 200 }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="heroSubtitle"
          label="Mô tả dưới tiêu đề"
          rules={[{ required: true, message: "Nhập mô tả" }, { max: 1000 }]}
        >
          <Input.TextArea rows={3} showCount maxLength={1000} />
        </Form.Item>
        <Row gutter={[16, 32]}>
          <Col xs={24} md={12}>
            <Form.Item
              name="ctaHeader"
              label="Nút trên thanh đầu trang"
              rules={[{ required: true }, { max: 80 }]}
            >
              <Input />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="ctaPrimary"
              label="Nút chính trong phần đầu"
              rules={[{ required: true }, { max: 80 }]}
            >
              <Input />
            </Form.Item>
          </Col>
        </Row>

        <Divider orientation="left">Bốn điểm nổi bật</Divider>
        <Form.List name="strengths">
          {(fields) => (
            <Row gutter={[16, 32]}>
              {fields.map((field, index) => (
                <Col xs={24} md={12} key={field.key}>
                  <Card size="small" title={`Điểm ${index + 1}`} style={{ marginBottom: 8 }}>
                    <Form.Item
                      {...field}
                      name={[field.name, "title"]}
                      label="Tiêu đề"
                      rules={[{ required: true, message: "Nhập tiêu đề" }, { max: 120 }]}
                    >
                      <Input />
                    </Form.Item>
                    <Form.Item
                      {...field}
                      name={[field.name, "text"]}
                      label="Mô tả"
                      rules={[{ required: true, message: "Nhập mô tả" }, { max: 500 }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Input.TextArea rows={2} showCount maxLength={500} />
                    </Form.Item>
                  </Card>
                </Col>
              ))}
            </Row>
          )}
        </Form.List>

        <Divider orientation="left">Danh mục mặt hàng</Divider>
        <Form.Item
          name="categoriesTitle"
          label="Tiêu đề khối mặt hàng"
          rules={[{ required: true }, { max: 120 }]}
        >
          <Input />
        </Form.Item>

        <Divider orientation="left">Khối liên hệ / form</Divider>
        <Form.Item
          name="contactTitle"
          label="Tiêu đề khối liên hệ"
          rules={[{ required: true }, { max: 120 }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          name="contactDescription"
          label="Mô tả khối liên hệ"
          rules={[{ required: true }, { max: 1000 }]}
        >
          <Input.TextArea rows={3} showCount maxLength={1000} />
        </Form.Item>
        <Form.Item
          name="privacyNote"
          label="Ghi chú bảo mật (dưới mô tả)"
          rules={[{ required: true }, { max: 300 }]}
        >
          <Input.TextArea rows={2} showCount maxLength={300} />
        </Form.Item>
        <Row gutter={[16, 32]}>
          <Col xs={24} md={8}>
            <Form.Item
              name="formSubmitLabel"
              label="Nút gửi form"
              rules={[{ required: true }, { max: 80 }]}
            >
              <Input />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item
              name="formSuccessTitle"
              label="Tiêu đề sau khi gửi"
              rules={[{ required: true }, { max: 120 }]}
            >
              <Input />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item
              name="formAgainLabel"
              label="Nút gửi thêm"
              rules={[{ required: true }, { max: 80 }]}
            >
              <Input />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item
          name="formSuccessDescription"
          label="Mô tả sau khi gửi thành công"
          rules={[{ required: true }, { max: 500 }]}
        >
          <Input.TextArea rows={2} showCount maxLength={500} />
        </Form.Item>

        <Button type="primary" htmlType="submit" loading={saving}>
          Lưu nội dung
        </Button>
      </Form>
    </Card>
  );
}
