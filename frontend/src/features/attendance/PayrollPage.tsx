import { Alert, Button, Card, Col, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Row, Space, Statistic, message } from "antd";
import { ResponsiveTable as Table } from "../../components/PagedTable";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { vndAmountRules, vndInputNumberProps } from "../../utils/vnd-input";
import {
  adjustPayrollDetail,
  calculatePayroll,
  closePayrollPeriod,
  createOrGetPayrollPeriod,
  payPayrollPeriod,
  reopenPayrollPeriod,
  type PayrollDetail,
  type PayrollPeriod,
} from "./attendance-api";

export function PayrollPage() {
  const [monthPicker, setMonthPicker] = useState<Dayjs>(() => dayjs());
  const [period, setPeriod] = useState<PayrollPeriod | null>(null);
  const [loading, setLoading] = useState(false);
  const [editingDetail, setEditingDetail] = useState<PayrollDetail | null>(null);
  const [statement, setStatement] = useState<PayrollDetail | null>(null);
  const [showReopen, setShowReopen] = useState(false);
  const [showPay, setShowPay] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setPeriod(await createOrGetPayrollPeriod(monthPicker.year(), monthPicker.month() + 1));
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [monthPicker]);

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
          <DatePicker
            picker="month"
            allowClear={false}
            format="MM/YYYY"
            value={monthPicker}
            onChange={(val) => val && setMonthPicker(val)}
          />
          {period && !isClosed ? (
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
                    <Button size="small" onClick={() => setEditingDetail(d)}>Phụ cấp và tiền ứng</Button>
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
    </Card>
  );
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
