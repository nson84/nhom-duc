import { Button, Card, Col, DatePicker, Form, Input, InputNumber, Modal, Row, Select, Space, Statistic, Table, Tag, message } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { vndAmountRules, vndInputNumberProps } from "../../utils/vnd-input";
import {
  createFinanceTransaction,
  getFinanceSummary,
  listFinanceTransactions,
  voidFinanceTransaction,
  type FinanceSummary,
  type FinanceTransaction,
} from "./finance-api";

const { RangePicker } = DatePicker;

const categoryLabels: Record<FinanceTransaction["category"], string> = {
  SALE_RECEIPT: "Thu tiền bán hàng",
  PURCHASE_PAYMENT: "Chi trả phiếu nhập kho",
  OPERATING_EXPENSE: "Chi phí hoạt động",
  PAYROLL: "Chi lương nhân viên",
  OTHER_INCOME: "Thu khác",
  OTHER_EXPENSE: "Chi khác",
};

export function FinancePage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [dayjs().startOf("month"), dayjs()]);
  const [typeFilter, setTypeFilter] = useState<string | undefined>();
  const [categoryFilter, setCategoryFilter] = useState<string | undefined>();
  const [query, setQuery] = useState("");
  const [summary, setSummary] = useState<FinanceSummary | null>(null);
  const [transactions, setTransactions] = useState<FinanceTransaction[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState<"INCOME" | "EXPENSE" | null>(null);
  const [voiding, setVoiding] = useState<FinanceTransaction | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const from = range[0].format("YYYY-MM-DD");
      const to = range[1].format("YYYY-MM-DD");
      const [s, t] = await Promise.all([
        getFinanceSummary(from, to),
        listFinanceTransactions({
          from,
          to,
          type: typeFilter,
          category: categoryFilter,
          q: query || undefined,
        }),
      ]);
      setSummary(s);
      setTransactions(t);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [range, typeFilter, categoryFilter, query]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Card
      title="Sổ thu chi"
      extra={
        <Space>
          <Button onClick={() => setCreating("INCOME")}>Lập phiếu thu ngoài</Button>
          <Button type="primary" onClick={() => setCreating("EXPENSE")}>Lập phiếu chi</Button>
        </Space>
      }
    >
      <p style={{ marginTop: 0, color: "#5c6b73" }}>
        Khoản thu bán hàng và khoản chi phiếu nhập được tự động liên kết từ phiếu kho để chống ghi trùng. Chi trả nhập hàng được theo dõi riêng, không tính vào chi phí hoạt động trong kỳ.
      </p>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={6}>
          <Card size="small">
            <Statistic title="Tổng thực thu" value={formatMoney(summary?.totalIncome ?? 0)} />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card size="small">
            <Statistic
              title="Chi phí hoạt động & lương"
              value={formatMoney(
                (summary?.operatingExpense ?? 0) + (summary?.payrollExpense ?? 0) + (summary?.otherExpense ?? 0),
              )}
            />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card size="small">
            <Statistic title="Tiền chi nhập hàng" value={formatMoney(summary?.purchasePaymentOutflow ?? 0)} />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card size="small">
            <Statistic
              title="Chênh lệch dòng tiền"
              value={formatMoney(summary?.netCashflow ?? 0)}
              valueStyle={{ color: (summary?.netCashflow ?? 0) >= 0 ? "#389e0d" : "#cf1322" }}
            />
          </Card>
        </Col>
      </Row>

      <Space style={{ marginBottom: 16 }} wrap>
        <RangePicker
          value={range}
          allowClear={false}
          format="DD/MM/YYYY"
          onChange={(vals) => {
            if (vals?.[0] && vals[1]) setRange([vals[0], vals[1]]);
          }}
        />
        <Select
          allowClear
          placeholder="Loại phiếu"
          style={{ width: 150 }}
          value={typeFilter}
          onChange={setTypeFilter}
          options={[
            { value: "INCOME", label: "Phiếu thu" },
            { value: "EXPENSE", label: "Phiếu chi" },
          ]}
        />
        <Select
          allowClear
          placeholder="Nhóm giao dịch"
          style={{ width: 210 }}
          value={categoryFilter}
          onChange={setCategoryFilter}
          options={Object.entries(categoryLabels).map(([value, label]) => ({ value, label }))}
        />
        <Input.Search className="app-search" placeholder="Tìm số phiếu hoặc diễn giải" allowClear onSearch={setQuery} style={{ width: 280 }} />
      </Space>

      <Table<FinanceTransaction>
        rowKey="id"
        loading={loading}
        dataSource={transactions}
        pagination={{ pageSize: 20 }}
        columns={[
          { title: "Số phiếu", dataIndex: "transactionCode", width: 150 },
          {
            title: "Thời gian",
            dataIndex: "transactionDate",
            width: 150,
            render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm"),
          },
          {
            title: "Loại",
            dataIndex: "transactionType",
            width: 100,
            render: (v: string) => (v === "INCOME" ? <Tag color="green">Thu</Tag> : <Tag color="volcano">Chi</Tag>),
          },
          {
            title: "Nhóm",
            dataIndex: "category",
            render: (v: FinanceTransaction["category"]) => categoryLabels[v] ?? v,
          },
          { title: "Diễn giải", dataIndex: "description" },
          {
            title: "Số tiền",
            dataIndex: "amount",
            align: "right",
            render: (v: number) => <b>{formatMoney(v)}</b>,
          },
          {
            title: "Trạng thái",
            dataIndex: "status",
            render: (v: string) => (v === "CONFIRMED" ? <Tag color="blue">Hợp lệ</Tag> : <Tag>Đã hủy</Tag>),
          },
          {
            title: "Thao tác",
            render: (_, tx) =>
              tx.status === "CONFIRMED" && tx.referenceType !== "STOCK_DOCUMENT" ? (
                <a onClick={() => setVoiding(tx)}>Hủy phiếu</a>
              ) : null,
          },
        ]}
      />

      {creating ? (
        <CreateTxModal
          type={creating}
          onClose={() => setCreating(null)}
          onSaved={() => {
            setCreating(null);
            void load();
          }}
        />
      ) : null}

      {voiding ? (
        <VoidTxModal
          tx={voiding}
          onClose={() => setVoiding(null)}
          onSaved={() => {
            setVoiding(null);
            void load();
          }}
        />
      ) : null}
    </Card>
  );
}

function CreateTxModal({
  type,
  onClose,
  onSaved,
}: {
  type: "INCOME" | "EXPENSE";
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<{
    transactionDate: Dayjs;
    category: string;
    amount: number;
    description: string;
  }>();
  const [saving, setSaving] = useState(false);
  const key = useMemo(() => crypto.randomUUID(), []);

  const options =
    type === "INCOME"
      ? [{ value: "OTHER_INCOME", label: "Thu khác (ngoài bán hàng)" }]
      : [
          { value: "OPERATING_EXPENSE", label: "Chi phí hoạt động (điện, nước, vận chuyển, vật tư...)" },
          { value: "PAYROLL", label: "Chi lương nhân viên" },
          { value: "OTHER_EXPENSE", label: "Chi khác" },
        ];

  return (
    <Modal
      open
      title={type === "INCOME" ? "Lập phiếu thu ngoài" : "Lập phiếu chi"}
      okText="Lưu phiếu"
      cancelText="Hủy"
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          transactionDate: dayjs(),
          category: options[0].value,
        }}
        onFinish={async (values) => {
          setSaving(true);
          try {
            await createFinanceTransaction({
              transactionDate: values.transactionDate.format("YYYY-MM-DDTHH:mm:ss"),
              transactionType: type,
              category: values.category,
              amount: values.amount,
              description: values.description,
              idempotencyKey: key,
            });
            message.success("Đã ghi nhận phiếu thu/chi");
            onSaved();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item name="transactionDate" label="Thời gian" rules={[{ required: true }]}>
          <DatePicker showTime format="DD/MM/YYYY HH:mm" style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item name="category" label="Nhóm giao dịch" rules={[{ required: true }]}>
          <Select options={options} />
        </Form.Item>
        <Form.Item name="amount" label="Số tiền" rules={vndAmountRules}>
          <InputNumber {...vndInputNumberProps} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item name="description" label="Diễn giải" rules={[{ required: true, message: "Nhập diễn giải" }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function VoidTxModal({
  tx,
  onClose,
  onSaved,
}: {
  tx: FinanceTransaction;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<{ reason: string }>();
  const [saving, setSaving] = useState(false);

  return (
    <Modal
      open
      title={`Hủy phiếu ${tx.transactionCode}`}
      okText="Xác nhận hủy"
      okButtonProps={{ danger: true }}
      cancelText="Quay lại"
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <p style={{ marginTop: 0 }}>
        Phiếu thu/chi sẽ chuyển sang trạng thái Đã hủy, không xóa khỏi sổ lịch sử và không còn cộng vào báo cáo.
      </p>
      <Form
        form={form}
        layout="vertical"
        onFinish={async ({ reason }) => {
          setSaving(true);
          try {
            await voidFinanceTransaction(tx.id, reason);
            message.success("Đã hủy phiếu thu/chi");
            onSaved();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item name="reason" label="Lý do hủy phiếu" rules={[{ required: true, message: "Nhập lý do" }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
