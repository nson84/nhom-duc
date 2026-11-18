import { StopOutlined } from "@ant-design/icons";
import {
  Alert,
  AutoComplete,
  Button,
  Card,
  Col,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
  message,
} from "antd";
import { Link } from "react-router-dom";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AppActionButton } from "../../components/AppButtons";
import { PagedTable } from "../../components/PagedTable";
import { LiveSearch } from "../../components/LiveSearch";
import { useCanWrite, useSession } from "../auth/auth-store";
import { getMonthStatus, getYearPeriod, type MonthStatus, type YearPeriod } from "../dashboard/dashboard-api";
import { useWorkingYear, workingYearStore } from "../dashboard/working-year";
import { visiblePeriod } from "../dashboard/working-period";
import { SHOP_LABELS, useShop } from "../shop/shop-store";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { vndAmountRules, vndInputNumberProps } from "../../utils/vnd-input";
import { expenseKinds, expenseLabelFromDescription, type ExpenseKind } from "./expense-kinds";
import {
  createFinanceTransaction,
  getFinanceSummary,
  listFinanceTransactions,
  voidFinanceTransaction,
  type FinanceSummary,
  type FinanceTransaction,
} from "./finance-api";

const categoryLabels: Record<FinanceTransaction["category"], string> = {
  SALE_RECEIPT: "Thu tiền bán hàng",
  PURCHASE_PAYMENT: "Chi trả phiếu nhập kho",
  MAINTENANCE: "Bảo trì hệ thống",
  OPERATING_EXPENSE: "Chi phí hoạt động",
  PAYROLL: "Chi lương nhân viên",
  OTHER_INCOME: "Thu khác",
  OTHER_EXPENSE: "Chi khác",
};

export function FinancePage() {
  const canWrite = useCanWrite();
  const session = useSession();
  const shop = useShop();
  const workingYear = useWorkingYear(shop);
  const [scope, setScope] = useState<"shop" | "all">("shop");
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [dayjs().startOf("month"), dayjs()]);
  const year = workingYear.year;
  const [period, setPeriod] = useState<YearPeriod | null>(null);
  const [monthStatus, setMonthStatus] = useState<MonthStatus>("NOT_OPEN");
  useEffect(() => {
    if (!workingYear.month) { setMonthStatus("NOT_OPEN"); return; }
    let active = true;
    getMonthStatus(workingYear.month).then((value) => { if (active) setMonthStatus(value); })
      .catch(() => { if (active) setMonthStatus("NOT_OPEN"); });
    return () => { active = false; };
  }, [shop, workingYear.month]);
  const [typeFilter, setTypeFilter] = useState<string | undefined>();
  const [categoryFilter, setCategoryFilter] = useState<string | undefined>();
  const [query, setQuery] = useState("");
  const [summary, setSummary] = useState<FinanceSummary | null>(null);
  const [transactions, setTransactions] = useState<FinanceTransaction[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [viewing, setViewing] = useState<FinanceTransaction | null>(null);
  const [voiding, setVoiding] = useState<FinanceTransaction | null>(null);

  useEffect(() => {
    if (shop === "DISTRIBUTION" && scope !== "all" && categoryFilter === "PAYROLL") setCategoryFilter(undefined);
  }, [shop, scope, categoryFilter]);

  useEffect(() => {
    let active = true;
    setPeriod(null);
    setSummary(null);
    setTransactions([]);
    getYearPeriod(year).then((value) => {
      if (!active) return;
      const shown = visiblePeriod(value, workingYear.month);
      setPeriod(shown);
      if (shown) setRange([dayjs(shown.periodFrom), dayjs(shown.periodTo)]);
    }).catch((error) => { if (active) message.error(toApiError(error).message); });
    return () => { active = false; };
  }, [year, shop, workingYear.month]);

  const load = useCallback(async () => {
    if (!period?.started) {
      setSummary(null);
      setTransactions([]);
      return;
    }
    setLoading(true);
    try {
      const from = range[0].format("YYYY-MM-DD");
      const to = range[1].format("YYYY-MM-DD");
      const [s, t] = await Promise.all([
        getFinanceSummary(from, to, scope === "all" ? "all" : undefined),
        listFinanceTransactions({
          from,
          to,
          type: typeFilter,
          category: categoryFilter,
          q: query || undefined,
          scope: scope === "all" ? "all" : undefined,
        }),
      ]);
      setSummary(s);
      setTransactions(t);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [range, typeFilter, categoryFilter, query, scope, period?.started]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Card
      title="Sổ thu chi"
      extra={
        canWrite ? (
          <Button type="primary" disabled={!period?.started || period.closed || monthStatus !== "OPEN"} onClick={() => setCreating(true)}>Lập phiếu chi</Button>
        ) : null
      }
    >
      {period && !period.started && <Alert type="info" showIcon style={{ marginBottom: 16 }}
        message="Kỳ này chưa có ngày bắt đầu" description={<Link to="/">Vào Tổng quan để lưu ngày bắt đầu trước khi xem Thu chi theo năm</Link>} />}
      <p style={{ marginTop: 0, color: "var(--color-text-secondary)" }}>
        Sổ thu chi chỉ hiển thị khoản tiền theo ngày thực nhận hoặc thực chi trong tháng đang chọn. Phiếu bán và phiếu nhập gốc có thể thuộc tháng khác; tiền nhập hàng không tính vào chi phí hoạt động.
      </p>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={6}>
          <Card size="small">
            <Statistic title="Tổng tiền thực thu trong kỳ" value={formatMoney(summary?.totalIncome ?? 0)} />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card size="small">
            <Statistic
              title={shop === "DISTRIBUTION" && scope !== "all" ? "Chi phí hoạt động" : "Chi phí hoạt động & lương"}
              value={formatMoney(
                (summary?.operatingExpense ?? 0) + (summary?.payrollExpense ?? 0) + (summary?.otherExpense ?? 0),
              )}
            />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card size="small">
            <Statistic title="Tiền trả nhà cung cấp trong kỳ" value={formatMoney(summary?.purchasePaymentOutflow ?? 0)} />
          </Card>
        </Col>
        <Col xs={24} md={6}>
          <Card size="small">
            <Statistic
              title="Thu trừ chi trong kỳ (không phải lãi)"
              value={formatMoney(summary?.netCashflow ?? 0)}
              valueStyle={{ color: (summary?.netCashflow ?? 0) >= 0 ? "var(--color-success)" : "var(--color-danger)" }}
            />
          </Card>
        </Col>
        {scope === "all" ? (
          <Col xs={24} md={12}>
            <Card size="small">
              <Statistic
                title="Giữa hai cửa hàng (thu / chi nội bộ, không cộng vào số ngoài)"
                value={`${formatMoney(summary?.internalIncome ?? 0)} / ${formatMoney(summary?.internalExpense ?? 0)}`}
              />
            </Card>
          </Col>
        ) : null}
      </Row>

      <Space style={{ marginBottom: 16 }} wrap>
        {session?.role === "ADMIN" ? (
          <Select
            style={{ width: 220 }}
            value={scope}
            onChange={setScope}
            options={[
              { value: "shop", label: SHOP_LABELS[shop] },
              { value: "all", label: "Cả hai cửa hàng" },
            ]}
          />
        ) : null}
        <DatePicker picker="year" aria-label="Năm xem thu chi" allowClear={false} disabled={workingYear.locked}
          value={dayjs(`${year}-01-01`)} onChange={(value) => value && workingYearStore.set(shop, { year: value.year(), locked: false })} />
        {period && <span>{period.periodName}: {period.started ? `${dayjs(period.periodFrom).format("DD/MM/YYYY")} – ${period.closed ? dayjs(period.periodTo).format("DD/MM/YYYY") : "đang làm việc"}` : "chưa bắt đầu"}</span>}
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
          options={Object.entries(categoryLabels)
            .filter(([value]) => shop !== "DISTRIBUTION" || scope === "all" || value !== "PAYROLL")
            .map(([value, label]) => ({ value, label }))}
        />
        <LiveSearch value={query} onChange={setQuery} placeholder="Tìm số phiếu hoặc ghi chú" style={{ width: 360 }} suggestions={transactions.flatMap((t) => [t.transactionCode, t.description ?? ""])} />
      </Space>

      <PagedTable<FinanceTransaction>
        rowKey="id"
        loading={loading}
        dataSource={transactions}
        onRow={(record) => ({
          onClick: () => setViewing(record),
          className: "app-row-clickable",
        })}
        columns={[
          {
            title: "Số phiếu",
            dataIndex: "transactionCode",
            width: 180,
            align: "center",
            render: (code: string, tx) => (
              <div style={{ lineHeight: 1.35 }}>
                {tx.internal ? (
                  <div>
                    <Tag color="gold" style={{ marginInlineEnd: 0 }}>
                      Nội bộ
                    </Tag>
                  </div>
                ) : null}
                <div style={{ marginTop: tx.internal || tx.status !== "CONFIRMED" ? 4 : 0, fontWeight: 500 }}>{code}</div>
                {tx.status !== "CONFIRMED" ? (
                  <div style={{ marginTop: 4 }}>
                    <Tag style={{ marginInlineEnd: 0 }}>Đã hủy</Tag>
                  </div>
                ) : null}
              </div>
            ),
          },
          {
            title: "Ngày ghi thu/chi",
            dataIndex: "transactionDate",
            width: 150,
            align: "center",
            render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm"),
          },
          {
            title: "Loại",
            dataIndex: "transactionType",
            width: 88,
            align: "center",
            render: (v: string) => (v === "INCOME" ? <Tag color="green">Thu</Tag> : <Tag color="volcano">Chi</Tag>),
          },
          {
            title: "Nhóm",
            dataIndex: "category",
            align: "center",
            render: (v: FinanceTransaction["category"], tx: FinanceTransaction) =>
              tx.referenceType === "HOLIDAY_BONUS" ? "Chi thưởng lễ"
                : tx.referenceType === "MANUAL" ? expenseLabelFromDescription(tx.description) ?? categoryLabels[v] ?? v
                  : categoryLabels[v] ?? v,
          },
          {
            title: "Số tiền",
            dataIndex: "amount",
            width: 140,
            align: "center",
            render: (v: number) => <b>{formatMoney(v)}</b>,
          },
        ]}
      />

      {creating ? (
        <CreateTxModal
          shop={shop}
          workingPeriod={period}
          onClose={() => setCreating(false)}
          onSaved={() => {
            setCreating(false);
            void load();
          }}
        />
      ) : null}

      {viewing ? (
        <FinanceDetailModal
          tx={viewing}
          canWrite={canWrite}
          onClose={() => setViewing(null)}
          onVoid={() => {
            setVoiding(viewing);
            setViewing(null);
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

function FinanceDetailModal({
  tx,
  canWrite,
  onClose,
  onVoid,
}: {
  tx: FinanceTransaction;
  canWrite: boolean;
  onClose: () => void;
  onVoid: () => void;
}) {
  const holidayBonus = tx.referenceType === "HOLIDAY_BONUS";
  const canVoid = canWrite && tx.status === "CONFIRMED" && tx.referenceType !== "STOCK_DOCUMENT" && !holidayBonus;

  return (
    <Modal
      open
      title={tx.transactionCode}
      onCancel={onClose}
      footer={
        <Space>
          {canVoid ? (
            <AppActionButton tone="delete" icon={<StopOutlined />} onClick={onVoid}>
              Hủy phiếu
            </AppActionButton>
          ) : null}
          <Button onClick={onClose}>Đóng</Button>
        </Space>
      }
    >
      <Descriptions size="small" column={1} bordered>
        <Descriptions.Item label="Thời gian">{dayjs(tx.transactionDate).format("DD/MM/YYYY HH:mm")}</Descriptions.Item>
        <Descriptions.Item label="Loại">
          {tx.transactionType === "INCOME" ? <Tag color="green">Thu</Tag> : <Tag color="volcano">Chi</Tag>}
          {tx.internal ? (
            <Tag color="gold" style={{ marginLeft: 8 }}>
              Nội bộ
            </Tag>
          ) : null}
          {tx.status !== "CONFIRMED" ? <Tag style={{ marginLeft: 8 }}>Đã hủy</Tag> : null}
        </Descriptions.Item>
        <Descriptions.Item label="Nhóm">{holidayBonus ? "Chi thưởng lễ"
          : tx.referenceType === "MANUAL" ? expenseLabelFromDescription(tx.description) ?? categoryLabels[tx.category] ?? tx.category
            : categoryLabels[tx.category] ?? tx.category}</Descriptions.Item>
        <Descriptions.Item label="Số tiền">
          <b>{formatMoney(tx.amount)}</b>
        </Descriptions.Item>
        {tx.spentBy && <Descriptions.Item label="Người chi">{tx.spentBy}</Descriptions.Item>}
        <Descriptions.Item label="Ghi chú">{tx.description || "—"}</Descriptions.Item>
      </Descriptions>
      {holidayBonus ? (
        <p>Chỉnh trạng thái đã nhận trong Lịch nghỉ lễ để cập nhật khoản chi này.</p>
      ) : tx.referenceType === "STOCK_DOCUMENT" ? (
        <p style={{ marginTop: 12, marginBottom: 0, color: "var(--color-text-secondary)" }}>
          Phiếu thu/chi gắn hóa đơn bán hoặc nhập — không hủy riêng tại đây.
        </p>
      ) : canVoid ? (
        <p style={{ marginTop: 12, marginBottom: 0, color: "var(--color-text-secondary)" }}>
          Phiếu tự lập: có thể hủy nếu ghi nhầm (vẫn giữ lịch sử, không cộng vào báo cáo).
        </p>
      ) : null}
    </Modal>
  );
}

function CreateTxModal({
  shop,
  workingPeriod,
  onClose,
  onSaved,
}: {
  shop: "DISTRIBUTION" | "MANUFACTURING";
  workingPeriod: YearPeriod | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<{
    transactionDate: Dayjs;
    expenseKind: ExpenseKind;
    amount: number;
    description?: string;
    spentBy?: string;
  }>();
  const [saving, setSaving] = useState(false);
  const key = useMemo(() => crypto.randomUUID(), []);
  const periodStart = workingPeriod?.started ? dayjs(workingPeriod.periodFrom) : null;
  const periodEnd = workingPeriod?.started ? dayjs(workingPeriod.periodTo) : null;

  return (
    <Modal
      open
      title="Lập phiếu chi"
      okText="Lưu phiếu"
      cancelText="Hủy"
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <Form
        form={form}
        layout="vertical"
        onFinishFailed={({ errorFields }) => {
          const dateError = errorFields.find((field) => field.name[0] === "transactionDate");
          if (dateError) message.warning(dateError.errors[0]);
        }}
        initialValues={{
          transactionDate: periodStart && (dayjs().isBefore(periodStart) || periodEnd && dayjs().isAfter(periodEnd))
            ? periodStart : dayjs(),
          expenseKind: expenseKinds[0].value,
        }}
        onFinish={async (values) => {
          setSaving(true);
          try {
            const kind = expenseKinds.find((option) => option.value === values.expenseKind);
            if (!kind) throw new Error("Chọn khoản chi hợp lệ");
            await createFinanceTransaction({
              transactionDate: values.transactionDate.format("YYYY-MM-DDTHH:mm:ss"),
              transactionType: "EXPENSE",
              category: kind.category,
              amount: values.amount,
              description: values.description?.trim() ? `${kind.label}: ${values.description.trim()}` : kind.label,
              spentBy: values.spentBy,
              idempotencyKey: key,
            });
            message.success("Đã ghi nhận phiếu chi");
            onSaved();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item name="transactionDate" label="Thời gian" rules={[{ required: true }, {
          validator: (_, value: Dayjs | null) => {
            if (!value || !periodStart) return Promise.resolve();
            if (value.isBefore(periodStart, "day") || periodEnd && value.isAfter(periodEnd, "day"))
              return Promise.reject(new Error("Ngày phiếu chi phải nằm trong năm đang khóa"));
            return Promise.resolve();
          },
        }]}>
          <DatePicker showTime needConfirm={false} format="DD/MM/YYYY HH:mm" style={{ width: "100%" }}
            minDate={periodStart ?? undefined} maxDate={periodEnd ?? undefined} />
        </Form.Item>
        <Form.Item name="expenseKind" label="Khoản chi" rules={[{ required: true }]}>
          <Select options={expenseKinds.map(({ value, label }) => ({ value, label }))} />
        </Form.Item>
        <Form.Item name="amount" label="Số tiền" rules={vndAmountRules}>
          <InputNumber {...vndInputNumberProps} style={{ width: "100%" }} />
        </Form.Item>
        {shop === "DISTRIBUTION" && <Form.Item name="spentBy" label="Người chi" rules={[{ required: true, whitespace: true, message: "Nhập người chi" }]}>
          <AutoComplete options={[{ value: "Hải" }, { value: "Thuấn" }]} placeholder="Chọn Hải, Thuấn hoặc nhập tên khác">
            <Input maxLength={150} />
          </AutoComplete>
        </Form.Item>}
        <Form.Item name="description" label="Ghi chú">
          <Input.TextArea rows={3} maxLength={400} placeholder="Chi tiết khoản chi, người nhận hoặc lý do (nếu có)" />
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
