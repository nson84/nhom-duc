import { Alert, Card, Col, Descriptions, Form, Input, Modal, Row, Select, Space, Statistic, Table, Tag, message } from "antd";
import { PhoneOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toApiError } from "../../services/error-handler";
import {
  getLeadSummary,
  leadStatusLabels,
  listLeads,
  requestTypeLabels,
  updateLead,
  type Lead,
  type LeadSummary,
} from "./landing-api";

/**
 * Danh sách khách để lại thông tin từ landing page.
 *
 * Đây là màn hình nội bộ: chỉ Admin đăng nhập mới xem được. Thông tin khách không bao giờ
 * hiển thị ra landing page hay trả về cho người không đăng nhập.
 */
export function LeadsPage() {
  const [rows, setRows] = useState<Lead[]>([]);
  const [summary, setSummary] = useState<LeadSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [status, setStatus] = useState<string | undefined>();
  const [query, setQuery] = useState("");
  const [editing, setEditing] = useState<Lead | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, sum] = await Promise.all([
        listLeads({ status, q: query || undefined }),
        getLeadSummary(),
      ]);
      setRows(list);
      setSummary(sum);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [status, query]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Card
      title="Khách để lại thông tin"
      extra={
        <Space>
          <Link to="/noi-dung-gioi-thieu">Sửa nội dung trang</Link>
          <a onClick={() => window.open("/gioi-thieu", "_blank")}>Mở trang giới thiệu</a>
        </Space>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Form trên landing page đổ về đây"
        description="Khách điền form ở trang giới thiệu công khai. Đánh dấu Đã gọi sau khi liên hệ để không gọi trùng."
      />
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="Khách mới chưa gọi" value={summary?.newCount ?? 0} valueStyle={{ color: "#d48806" }} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="Hôm nay" value={summary?.todayCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="Đã gọi" value={summary?.contactedCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic title="Đã xử lý" value={summary?.closedCount ?? 0} />
          </Card>
        </Col>
      </Row>

      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search className="app-search" placeholder="Tìm tên, số điện thoại, email" allowClear onSearch={setQuery} style={{ width: 280 }} />
        <Select
          allowClear
          placeholder="Trạng thái"
          style={{ width: 170 }}
          value={status}
          onChange={setStatus}
          options={Object.entries(leadStatusLabels).map(([value, item]) => ({ value, label: item.text }))}
        />
      </Space>

      <Table<Lead>
        rowKey="id"
        loading={loading}
        dataSource={rows}
        pagination={{ pageSize: 20 }}
        columns={[
          {
            title: "Thời điểm",
            dataIndex: "createdAt",
            align: "center",
            width: 150,
            render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm"),
          },
          { title: "Họ tên", dataIndex: "fullName", align: "center" },
          {
            title: "Điện thoại",
            dataIndex: "phone",
            align: "center",
            render: (v: string) => (
              <a href={`tel:${v}`}>
                <PhoneOutlined /> {v}
              </a>
            ),
          },
          {
            title: "Cần gì",
            dataIndex: "requestType",
            align: "center",
            render: (v: string) => requestTypeLabels[v] ?? v,
          },
          {
            title: "Nội dung",
            dataIndex: "message",
            align: "center",
            ellipsis: true,
            render: (v: string | null) => v || "—",
          },
          {
            title: "Trạng thái",
            dataIndex: "status",
            align: "center",
            render: (v: Lead["status"]) => (
              <Tag color={leadStatusLabels[v].color}>{leadStatusLabels[v].text}</Tag>
            ),
          },
          {
            title: "Thao tác",
            align: "center",
            width: 110,
            render: (_, row) => <a onClick={() => setEditing(row)}>Xử lý</a>,
          },
        ]}
      />

      {editing ? (
        <EditLeadModal
          lead={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void load();
          }}
        />
      ) : null}
    </Card>
  );
}

function EditLeadModal({
  lead,
  onClose,
  onSaved,
}: {
  lead: Lead;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<{ status: string; internalNote?: string }>();
  const [saving, setSaving] = useState(false);
  const [callMessage, setCallMessage] = useState<string | null>(null);

  return (
    <Modal
      open
      title={`Khách: ${lead.fullName}`}
      okText="Lưu"
      cancelText="Đóng"
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <Descriptions bordered size="small" column={1} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Điện thoại">
          <a href={`tel:${lead.phone}`}>
            <PhoneOutlined /> {lead.phone}
          </a>
        </Descriptions.Item>
        <Descriptions.Item label="Email">{lead.email || "—"}</Descriptions.Item>
        <Descriptions.Item label="Cần gì">{requestTypeLabels[lead.requestType] ?? lead.requestType}</Descriptions.Item>
        <Descriptions.Item label="Nội dung">{lead.message || "—"}</Descriptions.Item>
        <Descriptions.Item label="Nhận lúc">
          {dayjs(lead.createdAt).format("DD/MM/YYYY HH:mm")}
        </Descriptions.Item>
        {lead.contactedAt ? (
          <Descriptions.Item label="Đã gọi">
            {dayjs(lead.contactedAt).format("DD/MM/YYYY HH:mm")}
            {lead.contactedBy ? ` — ${lead.contactedBy}` : ""}
          </Descriptions.Item>
        ) : null}
      </Descriptions>

      <Form
        form={form}
        layout="vertical"
        initialValues={{ status: lead.status, internalNote: lead.internalNote ?? "" }}
        onFinish={async (values) => {
          setSaving(true);
          setCallMessage(null);
          try {
            await updateLead(lead.id, values);
            message.success("Đã cập nhật");
            onSaved();
          } catch (error) {
            setCallMessage(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item name="status" label="Trạng thái" rules={[{ required: true }]}>
          <Select
            options={Object.entries(leadStatusLabels).map(([value, item]) => ({ value, label: item.text }))}
          />
        </Form.Item>
        <Form.Item
          name="internalNote"
          label="Ghi chú nội bộ"
          extra="Chỉ hiện trong app, khách không thấy."
        >
          <Input.TextArea rows={3} maxLength={1000} showCount placeholder="Ví dụ: khách hẹn gọi lại chiều mai" />
        </Form.Item>
      </Form>
      {callMessage ? <Alert type="error" showIcon message={callMessage} /> : null}
    </Modal>
  );
}
