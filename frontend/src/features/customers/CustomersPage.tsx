import { BellOutlined, PlusOutlined, UserOutlined } from "@ant-design/icons";
import { Alert, Card, Col, DatePicker, Form, Input, Modal, Row, Space, Statistic, Switch, Tag, message } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AppActionButton } from "../../components/AppButtons";
import { PagedTable } from "../../components/PagedTable";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { requiredNameRules } from "../../utils/name-rules";
import { useCanWrite, useSession } from "../auth/auth-store";
import { SHOP_LABELS, useShop } from "../shop/shop-store";
import { useWorkingYear } from "../dashboard/working-year";
import { getYearPeriod } from "../dashboard/dashboard-api";
import { visiblePeriod } from "../dashboard/working-period";
import { CustomerSuggestSearch } from "./CustomerSuggestSearch";
import { DeliveredByCategory } from "../stock/DeliveredByCategory";
import {
  listCustomers,
  listDueReminders,
  saveCustomer,
  type Customer,
  type CustomerReminder,
} from "./customers-api";

const { RangePicker } = DatePicker;

export function CustomersPage() {
  const shop = useShop();
  const workingYear = useWorkingYear(shop);
  const canWrite = useCanWrite();
  const session = useSession();
  const isAdmin = session?.role === "ADMIN";
  const navigate = useNavigate();
  const [rows, setRows] = useState<Customer[]>([]);
  const [due, setDue] = useState<CustomerReminder[]>([]);
  const [query, setQuery] = useState("");
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [periodReady, setPeriodReady] = useState(false);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);

  const scope = isAdmin ? ("all" as const) : undefined;

  useEffect(() => {
    let active = true;
    setPeriodReady(false);
    getYearPeriod(workingYear.year).then((value) => {
      if (!active) return;
      const shown = visiblePeriod(value, workingYear.month);
      setRange(shown?.started ? [dayjs(shown.periodFrom), dayjs(shown.periodTo)] : null);
      setPeriodReady(true);
    }).catch(() => { if (active) setPeriodReady(true); });
    return () => { active = false; };
  }, [shop, workingYear.year, workingYear.month]);

  const load = useCallback(async () => {
    if (!periodReady || !range) { setRows([]); setDue([]); return; }
    setLoading(true);
    try {
      const [customers, reminders] = await Promise.all([
        listCustomers({
          q: query || undefined,
          from: range?.[0]?.format("YYYY-MM-DD"),
          to: range?.[1]?.format("YYYY-MM-DD"),
          scope,
        }),
        listDueReminders(scope),
      ]);
      setRows(customers);
      setDue(reminders);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [query, range, scope, periodReady]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Card
      title="Khách mua"
      extra={
        canWrite ? (
          <AppActionButton tone="create" icon={<PlusOutlined />} onClick={() => setCreating(true)}>
            Khách hàng
          </AppActionButton>
        ) : null
      }
    >
      {due.length > 0 ? (
        <Alert
          type="warning"
          showIcon
          icon={<BellOutlined />}
          style={{ marginBottom: 16 }}
          message={`Có ${due.length} lịch nhắc nợ đến hạn`}
          description={
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              {due.slice(0, 5).map((r) => (
                <li key={r.id}>
                  <Link to={`/khach-mua/${r.customerId}`}>{r.customerName}</Link>
                  {isAdmin ? ` (${SHOP_LABELS[r.shopCode as keyof typeof SHOP_LABELS] ?? r.shopName})` : ""}
                  {" — "}
                  {r.remindOn}
                  {r.documentCode ? ` · ${r.documentCode}` : ""}
                  {r.note ? ` · ${r.note}` : ""}
                </li>
              ))}
            </ul>
          }
        />
      ) : null}

      <Space style={{ marginBottom: 16 }} wrap>
        <CustomerSuggestSearch
          value={query}
          scope={scope}
          style={{ width: 320 }}
          onSearch={setQuery}
        />
        <RangePicker
          disabled={!!workingYear.month}
          format="DD/MM/YYYY"
          value={range}
          onChange={(value) => setRange(value as [Dayjs, Dayjs] | null)}
          placeholder={["Từ ngày bán", "Đến ngày"]}
          allowClear
        />
      </Space>

      <Row className="customer-summary-row" gutter={[12, 12]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} xl={6}><Card size="small"><Statistic title="Khối lượng đã giao" value={rows.reduce((sum, row) => sum + (row.deliveredKg ?? 0), 0)} precision={3} suffix="kg" />
          <DeliveredByCategory from={range?.[0]?.format("YYYY-MM-DD")} to={range?.[1]?.format("YYYY-MM-DD")}
            customerIds={rows.map((row) => row.id)} scope={scope} shopKey={scope} />
        </Card></Col>
        <Col xs={24} sm={12} xl={6}><Card size="small"><Statistic title="Tiền hàng phải thu" value={formatMoney(rows.reduce((sum, row) => sum + row.totalSales, 0))} /></Card></Col>
        <Col xs={24} sm={12} xl={6}><Card size="small"><Statistic title={<span title="Tổng đã thu đến nay của các hóa đơn đang lọc">Đã thu đến nay</span>} value={formatMoney(rows.reduce((sum, row) => sum + (row.receivedAmount ?? 0), 0))} /></Card></Col>
        <Col xs={24} sm={12} xl={6}><Card size="small"><Statistic title="Khách còn nợ" value={formatMoney(rows.reduce((sum, row) => sum + row.outstandingDebt, 0))} /></Card></Col>
      </Row>
      <PagedTable<Customer>
        rowKey="id"
        loading={loading}
        dataSource={rows}
        onRow={(record) => ({
          onClick: () => navigate(`/khach-mua/${record.id}`),
          className: "app-row-clickable",
        })}
        columns={[
          ...(isAdmin
            ? [
                {
                  title: "Cửa hàng",
                  dataIndex: "shopCode" as const,
                  align: "center" as const,
                  render: (code: string) => SHOP_LABELS[code as keyof typeof SHOP_LABELS] ?? code,
                },
              ]
            : []),
          { title: "Tên khách", dataIndex: "name", align: "center", render: (name: string, row) => <>{name} {row.internalTransfer && <Tag color="blue">Nội bộ</Tag>}</> },
          { title: "SĐT", dataIndex: "phone", align: "center", render: (v: string | null) => v || "—" },
          {
            title: "Tiền hàng phải thu",
            dataIndex: "totalSales",
            align: "center",
            render: (v: number) => formatMoney(v),
          },
          { title: "Đã thu từ hóa đơn (đến nay)", dataIndex: "receivedAmount", align: "center", render: (v: number) => formatMoney(v) },
          { title: "Đã giao (kg)", dataIndex: "deliveredKg", align: "center", render: (v: number) => new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(v) },
          {
            title: "Còn nợ",
            dataIndex: "outstandingDebt",
            align: "center",
            render: (v: number, r) =>
              v > 0 ? (
                <Tag color="red">
                  {formatMoney(v)} ({r.openInvoiceCount})
                </Tag>
              ) : (
                <Tag color="green">0</Tag>
              ),
          },
          {
            title: "Trạng thái",
            dataIndex: "active",
            align: "center",
            render: (v: boolean) => (v ? <Tag color="green">Đang dùng</Tag> : <Tag>Ngừng</Tag>),
          },
        ]}
      />

      {creating ? (
        <CustomerFormModal
          onClose={() => setCreating(false)}
          onSaved={() => {
            setCreating(false);
            void load();
          }}
        />
      ) : null}
    </Card>
  );
}

function CustomerFormModal({
  initialName,
  customer,
  onClose,
  onSaved,
}: {
  initialName?: string;
  customer?: Pick<Customer, "id" | "name" | "phone" | "note" | "active">;
  onClose: () => void;
  onSaved: (customer: Customer) => void;
}) {
  const [form] = Form.useForm<{ name: string; phone?: string; note?: string; active: boolean }>();
  const [saving, setSaving] = useState(false);

  return (
    <Modal
      title={
        <Space>
          <UserOutlined />
          {customer ? "Sửa thông tin khách" : "Thêm khách hàng"}
        </Space>
      }
      open
      onCancel={onClose}
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={saving}
      onOk={() => form.submit()}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          name: customer?.name ?? initialName ?? "",
          phone: customer?.phone ?? "",
          note: customer?.note ?? "",
          active: customer?.active ?? true,
        }}
        onFinish={async (values) => {
          setSaving(true);
          try {
            const saved = await saveCustomer(customer?.id, {
              name: values.name.trim(),
              phone: values.phone?.trim() || undefined,
              note: values.note?.trim() || undefined,
              active: values.active,
            });
            message.success(customer ? "Đã cập nhật thông tin khách" : "Đã thêm khách hàng");
            onSaved(saved);
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item name="name" label="Tên khách" rules={requiredNameRules("Nhập tên khách", 200)}>
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="phone" label="Số điện thoại">
          <Input maxLength={30} />
        </Form.Item>
        <Form.Item name="note" label="Ghi chú">
          <Input.TextArea maxLength={500} rows={2} />
        </Form.Item>
        <Form.Item name="active" label="Đang dùng" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  );
}

export { CustomerFormModal };
