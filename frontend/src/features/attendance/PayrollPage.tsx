import { Alert, Button, Card, Col, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Row, Space, Statistic, message } from "antd";
import { PrinterOutlined } from "@ant-design/icons";
import { PrintDocument } from "../../components/print-document";
import { buildPayrollMonthHtml } from "./payroll-print";
import { ResponsiveTable as Table } from "../../components/PagedTable";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { vndAmountRules, vndInputNumberProps } from "../../utils/vnd-input";
import { useShop } from "../shop/shop-store";
import { useWorkingYear } from "../dashboard/working-year";
import { getMonthStatus, getYearPeriod, type MonthStatus, type YearPeriod } from "../dashboard/dashboard-api";
import {
  adjustPayrollDetail,
  addPayrollAdvance,
  calculatePayroll,
  closePayrollPeriod,
  createOrGetPayrollPeriod,
  getMonthlyBoard,
  payPayrollPeriod,
  reopenPayrollPeriod,
  type PayrollDetail,
  type PayrollPeriod,
} from "./attendance-api";

export function PayrollPage() {
  const workingYear = useWorkingYear(useShop());
  const [monthPicker, setMonthPicker] = useState<Dayjs>(() => dayjs(`${workingYear.year}-01-01`));
  const [resolvedWorkingYear, setResolvedWorkingYear] = useState<number | null>(null);
  const [workingPeriod, setWorkingPeriod] = useState<YearPeriod | null>(null);
  const [monthStatus, setMonthStatus] = useState<MonthStatus>("NOT_OPEN");
  const [period, setPeriod] = useState<PayrollPeriod | null>(null);
  const [loading, setLoading] = useState(false);
  const [editingDetail, setEditingDetail] = useState<PayrollDetail | null>(null);
  const [statement, setStatement] = useState<PayrollDetail | null>(null);
  const [showReopen, setShowReopen] = useState(false);
  const [showPay, setShowPay] = useState(false);
  const [advancing, setAdvancing] = useState<PayrollDetail | null>(null);
  const [printHtml, setPrintHtml] = useState<string | null>(null);
  const [printing, setPrinting] = useState(false);

  useEffect(() => {
    let active = true;
    setResolvedWorkingYear(null);
    setPeriod(null);
    getYearPeriod(workingYear.year)
      .then((value) => { if (active) { setWorkingPeriod(value); setMonthPicker(dayjs(workingYear.month ? `${workingYear.month}-01` : value.periodFrom)); setResolvedWorkingYear(value.started ? workingYear.year : null); } })
      .catch((error) => { if (active) { setWorkingPeriod(null); message.error(toApiError(error).message); } });
    return () => { active = false; };
  }, [workingYear.year, workingYear.month]);

  useEffect(() => {
    let active = true;
    getMonthStatus(monthPicker.format("YYYY-MM")).then((value) => { if (active) setMonthStatus(value); })
      .catch(() => { if (active) setMonthStatus("NOT_OPEN"); });
    return () => { active = false; };
  }, [monthPicker]);

  const load = useCallback(async () => {
    if (resolvedWorkingYear !== workingYear.year) return;
    setLoading(true);
    try {
      setPeriod(await createOrGetPayrollPeriod(monthPicker.year(), monthPicker.month() + 1));
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [monthPicker, resolvedWorkingYear, workingYear.year]);

  useEffect(() => {
    void load();
  }, [load]);

  const isClosed = period?.status === "CLOSED";
  const remainingPay = period ? Math.max(0, period.grandTotalPay - period.paidAmount) : 0;

  return (
    <Card
      title={`Tiền công tháng ${monthPicker.format("MM/YYYY")}`}
      extra={
        <Space wrap>
          {period ? <Button icon={<PrinterOutlined />} loading={printing} onClick={async () => {
            setPrinting(true);
            try {
              const board = await getMonthlyBoard(period.year, period.month);
              setPrintHtml(buildPayrollMonthHtml(period, board));
            } catch (error) {
              message.error(toApiError(error).message);
            } finally {
              setPrinting(false);
            }
          }}>In bảng công và lương</Button> : null}
          <DatePicker
            picker="month"
            allowClear={false}
            disabled={!!workingYear.month}
            disabledDate={(date) => !!workingPeriod?.started && (date.endOf("month").isBefore(dayjs(workingPeriod.periodFrom), "day") || date.startOf("month").isAfter(dayjs(workingPeriod.periodTo), "day"))}
            format="MM/YYYY"
            value={monthPicker}
            onChange={(val) => val && setMonthPicker(val)}
          />
          {period && !isClosed && monthStatus === "OPEN" ? (
            <>
              <Button
                loading={loading}
                onClick={async () => {
                  setLoading(true);
                  try {
                    setPeriod(await calculatePayroll(period.id));
                    message.success("Đã tính lại tiền công từ bảng chấm công");
                  } catch (error) {
                    message.error(toApiError(error).message);
                  } finally {
                    setLoading(false);
                  }
                }}
              >
                Tính lại tiền công
              </Button>
              <Button
                type="primary"
                loading={loading}
                onClick={() =>
                  Modal.confirm({
                    title: `Chốt kỳ công tháng ${monthPicker.format("MM/YYYY")}?`,
                    content:
                      "Sau khi chốt, bảng chấm công và kết quả lương tháng này sẽ khóa và không tự thay đổi khi cập nhật hồ sơ nhân viên.",
                    okText: "Chốt kỳ công",
                    cancelText: "Hủy",
                    onOk: async () => {
                      setPeriod(await closePayrollPeriod(period.id));
                      message.success("Đã chốt kỳ công");
                    },
                  })
                }
              >
                Chốt kỳ công
              </Button>
            </>
          ) : null}
          {period && isClosed ? (
            <>
              {remainingPay > 0 ? (
                <Button type="primary" onClick={() => setShowPay(true)}>
                  Ghi nhận chi lương
                </Button>
              ) : null}
              <Button danger onClick={() => setShowReopen(true)}>
                Mở lại kỳ công
              </Button>
            </>
          ) : null}
        </Space>
      }
    >
      {monthStatus !== "OPEN" && <Alert type="info" showIcon style={{ marginBottom: 12 }}
        message={monthStatus === "CLOSED" ? "Tháng đã khóa; chỉ xem tiền công" : "Hãy mở tháng ở Tổng quan trước khi lập tiền công"} />}
      {period ? (
        <>
          <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
            <Col xs={24} md={6}>
              <Card size="small">
                <Statistic
                  title="Trạng thái kỳ công"
                  value={isClosed ? "Đã chốt khóa" : "Đang mở"}
                  valueStyle={{ color: isClosed ? "var(--color-success)" : "var(--color-warning)" }}
                />
              </Card>
            </Col>
            <Col xs={24} md={6}>
              <Card size="small">
                <Statistic title="Tổng lương cơ bản" value={formatMoney(period.totalBasePay)} />
              </Card>
            </Col>
            <Col xs={24} md={6}>
              <Card size="small">
                <Statistic title="Tổng tăng ca đã duyệt" value={formatMoney(period.totalOvertimePay)} />
              </Card>
            </Col>
            <Col xs={24} md={6}>
              <Card size="small">
                <Statistic
                  title="Tổng thực lĩnh"
                  value={formatMoney(period.grandTotalPay)}
                  suffix={isClosed ? `(Đã chi: ${formatMoney(period.paidAmount)})` : undefined}
                />
              </Card>
            </Col>
          </Row>
          {period.reopenReason ? (
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={`Lịch sử mở lại kỳ công gần nhất: ${period.reopenReason} (${period.reopenedAt ? dayjs(period.reopenedAt).format("DD/MM/YYYY HH:mm") : ""})`}
            />
          ) : null}
          <Table<PayrollDetail>
            rowKey="id"
            loading={loading}
            dataSource={period.details}
            pagination={false}
            columns={[
              { title: "Họ và tên", dataIndex: "fullName", align: "center" },
              { title: "Ngày công", dataIndex: "creditedDays", align: "center" },
              { title: "Lương cơ bản", dataIndex: "basePay", align: "center", render: (v: number) => formatMoney(v) },
              { title: "Tăng ca đã duyệt", dataIndex: "overtimePay", align: "center", render: (v: number) => formatMoney(v) },
              { title: "Thưởng", dataIndex: "bonusPay", align: "center", render: (v: number) => formatMoney(v) },
              { title: "Phụ cấp", dataIndex: "allowances", align: "center", render: (v: number) => formatMoney(v) },
              { title: "Ứng tiền lương", dataIndex: "advanceAmount", align: "center", render: (v: number) => formatMoney(v ?? 0) },
              {
                title: "Thực lĩnh",
                dataIndex: "totalPay",
                align: "center",
                render: (v: number) => <b>{formatMoney(v)}</b>,
              },
              {
                title: "Thao tác", align: "center",
                render: (_, d) =>
                  isClosed ? (
                    <Button size="small" onClick={() => setStatement(d)}>Xem bảng kê</Button>
                  ) : (
                    <Space wrap><Button size="small" onClick={() => setEditingDetail(d)}>Phụ cấp và tiền ứng</Button>
                      <Button size="small" onClick={() => setAdvancing(d)}>Ghi ứng lương</Button></Space>
                  ),
              },
            ]}
          />
        </>
      ) : null}

      {statement && period && isClosed ? (
        <Modal open title={`Bảng kê tiền công tháng ${period.month}/${period.year} — ${statement.fullName}`}
          footer={null} onCancel={() => setStatement(null)} width={720}>
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="Số ngày công">{statement.creditedDays}</Descriptions.Item>
            <Descriptions.Item label="Lương cơ bản">{formatMoney(statement.basePay)}</Descriptions.Item>
            <Descriptions.Item label="Số giờ tăng ca đã duyệt">{statement.overtimeHours ?? "—"}</Descriptions.Item>
            <Descriptions.Item label="Tiền tăng ca">{formatMoney(statement.overtimePay)}</Descriptions.Item>
            <Descriptions.Item label="Phụ cấp">{formatMoney(statement.allowances)}</Descriptions.Item>
            <Descriptions.Item label="Thưởng khác">{formatMoney(statement.bonusPay - (statement.holidayBonusPay ?? 0))}</Descriptions.Item>
            <Descriptions.Item label="Thưởng lễ trả cùng lương">{formatMoney(statement.holidayBonusPay ?? 0)}</Descriptions.Item>
            <Descriptions.Item label="Thưởng lễ đã nhận trực tiếp">{statement.holidayBonusReceived == null ? "—" : formatMoney(statement.holidayBonusReceived)}</Descriptions.Item>
            <Descriptions.Item label="Tiền lương đã ứng">{formatMoney(statement.advanceAmount ?? 0)}</Descriptions.Item>
            {statement.deductions > 0 && <Descriptions.Item label="Khấu trừ đã ghi trước đây">{formatMoney(statement.deductions)}</Descriptions.Item>}
            <Descriptions.Item label="Tiền công cuối tháng sau khi trừ ứng"><strong>{formatMoney(statement.totalPay)}</strong></Descriptions.Item>
            <Descriptions.Item label="Tổng tiền sau khi trả đủ">
              {formatMoney(statement.totalPay + (statement.advanceAmount ?? 0) + (statement.holidayBonusReceived ?? 0))}
            </Descriptions.Item>
          </Descriptions>
        </Modal>
      ) : null}

      {advancing && period && <AdvanceModal detail={advancing} year={period.year} month={period.month}
        periodFrom={workingPeriod?.periodFrom}
        onClose={() => setAdvancing(null)} onSubmit={async values => {
          setPeriod(await addPayrollAdvance(period.id, advancing.id, values));
          setAdvancing(null);
          message.success("Đã ghi khoản ứng lương");
        }} />}
      {editingDetail && period ? (
        <AdjustModal
          detail={editingDetail}
          onClose={() => setEditingDetail(null)}
          onSubmit={async (values) => {
            try {
              setPeriod(await adjustPayrollDetail(period.id, editingDetail.id, values));
              setEditingDetail(null);
              message.success("Đã cập nhật phụ cấp và tiền ứng");
            } catch (error) {
              message.error(toApiError(error).message);
            }
          }}
        />
      ) : null}

      {showReopen && period ? (
        <ReopenModal
          onClose={() => setShowReopen(false)}
          onSubmit={async (reason) => {
            try {
              setPeriod(await reopenPayrollPeriod(period.id, reason));
              setShowReopen(false);
              message.success("Đã mở lại kỳ công và ghi nhật ký");
            } catch (error) {
              message.error(toApiError(error).message);
            }
          }}
        />
      ) : null}

      {showPay && period ? (
        <PayModal
          remaining={remainingPay}
          onClose={() => setShowPay(false)}
          onSubmit={async (values) => {
            try {
              setPeriod(await payPayrollPeriod(period.id, values));
              setShowPay(false);
              message.success("Đã ghi nhận phiếu chi lương");
            } catch (error) {
              message.error(toApiError(error).message);
            }
          }}
        />
      ) : null}
      {printHtml ? <PrintDocument title={`Bảng công và lương ${monthPicker.format("MM/YYYY")}`} html={printHtml} onClose={() => setPrintHtml(null)} /> : null}
    </Card>
  );
}

function AdvanceModal({detail, year, month, periodFrom, onClose, onSubmit}: {
  detail: PayrollDetail; year: number; month: number; onClose: () => void;
  periodFrom?: string;
  onSubmit: (values: { date: string; amount: number; note?: string; idempotencyKey: string }) => Promise<void>;
}) {
  const [form] = Form.useForm<{date: Dayjs; amount: number; note?: string}>();
  const [saving, setSaving] = useState(false);
  const [key] = useState(() => crypto.randomUUID());
  const monthStart = dayjs(`${year}-${String(month).padStart(2,"0")}-01`);
  const firstDate = periodFrom && dayjs(periodFrom).isAfter(monthStart) ? dayjs(periodFrom) : monthStart;
  return <Modal open title={`Ứng lương — ${detail.fullName}`} onCancel={onClose} okText="Ghi ứng" cancelText="Quay lại"
    confirmLoading={saving} onOk={() => form.submit()}>
    <Form form={form} layout="vertical" initialValues={{date: firstDate}}
      onFinish={async values => {
        setSaving(true);
        try { await onSubmit({...values, date: values.date.format("YYYY-MM-DD"), idempotencyKey: key}); }
        catch(error) { message.error(toApiError(error).message); }
        finally { setSaving(false); }
      }}>
      <Form.Item name="date" label="Ngày ứng" rules={[{required:true}]}>
        <DatePicker format="DD/MM/YYYY" disabledDate={date => date.year() !== year || date.month()+1 !== month || date.isBefore(firstDate, "day")} />
      </Form.Item>
      <Form.Item name="amount" label="Số tiền ứng" rules={vndAmountRules}>
        <InputNumber {...vndInputNumberProps} min={500} max={detail.totalPay} style={{width:"100%"}} />
      </Form.Item>
      <Form.Item name="note" label="Ghi chú"><Input.TextArea maxLength={500} /></Form.Item>
    </Form>
    <p>Các khoản đã ghi</p>
    {(detail.advances ?? []).map(entry => <p key={entry.id}>{dayjs(entry.date).format("DD/MM/YYYY")} · {formatMoney(entry.amount)} · {entry.note}</p>)}
    {detail.advanceAmount > (detail.advances ?? []).reduce((sum,entry)=>sum+entry.amount,0) &&
      <p>Phần ứng cũ chưa có ngày {formatMoney(detail.advanceAmount - (detail.advances ?? []).reduce((sum,entry)=>sum+entry.amount,0))}</p>}
  </Modal>;
}

function AdjustModal({
  detail,
  onClose,
  onSubmit,
}: {
  detail: PayrollDetail;
  onClose: () => void;
  onSubmit: (values: { allowances: number; advanceAmount: number }) => Promise<void>;
}) {
  const [form] = Form.useForm<{ allowances: number; advanceAmount: number }>();
  return (
    <Modal
      open
      title={`Phụ cấp và ứng tiền lương — ${detail.fullName}`}
      okText="Lưu"
      cancelText="Hủy"
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ allowances: detail.allowances, advanceAmount: detail.advanceAmount ?? 0 }}
        onFinish={onSubmit}
      >
        <Form.Item name="allowances" label="Phụ cấp" rules={vndAmountRules}>
          <InputNumber {...vndInputNumberProps} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item name="advanceAmount" label="Tiền lương đã ứng" rules={vndAmountRules}
          extra="Nhập tổng số tiền đã ứng trong tháng. Khoản này được trừ vào tiền công cuối tháng.">
          <InputNumber {...vndInputNumberProps} style={{ width: "100%" }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function ReopenModal({ onClose, onSubmit }: { onClose: () => void; onSubmit: (reason: string) => Promise<void> }) {
  const [form] = Form.useForm<{ reason: string }>();
  return (
    <Modal
      open
      title="Mở lại kỳ công đã chốt"
      okText="Mở lại kỳ công"
      okButtonProps={{ danger: true }}
      cancelText="Hủy"
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <p style={{ marginTop: 0 }}>
        Hệ thống sẽ lưu lại người thao tác, thời điểm và lý do mở lại kỳ công vào sổ nhật ký.
      </p>
      <Form form={form} layout="vertical" onFinish={({ reason }) => onSubmit(reason)}>
        <Form.Item name="reason" label="Lý do mở lại kỳ công" rules={[{ required: true, message: "Nhập lý do" }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function PayModal({
  remaining,
  onClose,
  onSubmit,
}: {
  remaining: number;
  onClose: () => void;
  onSubmit: (values: { amount: number; description?: string; idempotencyKey: string }) => Promise<void>;
}) {
  const [form] = Form.useForm<{ amount: number; description?: string }>();
  const key = useMemo(() => crypto.randomUUID(), []);
  return (
    <Modal open title="Ghi nhận chi trả tiền công" okText="Ghi nhận" cancelText="Hủy" onCancel={onClose} onOk={() => form.submit()}>
      <Form
        form={form}
        layout="vertical"
        initialValues={{ amount: remaining }}
        onFinish={(values) => onSubmit({ ...values, idempotencyKey: key })}
      >
        <Form.Item name="amount" label={`Số tiền chi (còn lại ${formatMoney(remaining)})`} rules={vndAmountRules}>
          <InputNumber {...vndInputNumberProps} min={1} max={remaining} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item name="description" label="Ghi chú phiếu chi">
          <Input placeholder="Chi lương nhân viên..." />
        </Form.Item>
      </Form>
    </Modal>
  );
}
