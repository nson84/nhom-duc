import { SaveOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Checkbox, DatePicker, Form, Input, InputNumber, Modal, Radio, Select, Space, Statistic, Switch, Table, Tag, message } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { requiredNameRules } from "../../utils/name-rules";
import { vndAmountRules, vndInputNumberProps, vndUnitPriceRules } from "../../utils/vnd-input";
import {
  getMonthlyBoard,
  listEmployees,
  listHolidays,
  saveAttendanceBonus,
  saveAttendanceDay,
  saveAttendancePolicy,
  saveDayKind,
  saveHoliday,
  saveOvertime,
  saveRollCall,
  unlockRollCall,
  type AttendanceDay,
  type AttendancePolicy,
  type EmployeeMonthlyRow,
  type Employee,
  type Holiday,
  type HolidayKind,
  type MonthlyAttendanceBoard,
  type OvertimeRecord,
} from "./attendance-api";

const statusMeta: Record<AttendanceDay["status"], { short: string; label: string; color: string }> = {
  UNMARKED: { short: "—", label: "Chưa chấm", color: "default" },
  PRESENT: { short: "Đủ", label: "Đủ công", color: "green" },
  HALF_DAY: { short: "1/2", label: "Nửa công", color: "gold" },
  ABSENT: { short: "V", label: "Vắng", color: "red" },
  PAID_LEAVE: { short: "P", label: "Nghỉ phép hưởng lương", color: "blue" },
  UNPAID_LEAVE: { short: "KP", label: "Nghỉ không lương", color: "default" },
  HOLIDAY: { short: "Lễ", label: "Nghỉ lễ", color: "purple" },
};

const dayKinds: Record<HolidayKind, { mark: string; label: string; color: string }> = {
  SHOP_CLOSED: { mark: "CH", label: "Nghỉ cửa hàng", color: "#b45309" },
  NATIONAL_HOLIDAY: { mark: "QG", label: "Nghỉ lễ quốc gia", color: "#1d4ed8" },
  CATHOLIC_HOLIDAY: { mark: "CG", label: "Nghỉ lễ công giáo", color: "#6d28d9" },
};

const headerButtonStyle = {
  border: 0,
  background: "transparent",
  padding: 0,
  cursor: "pointer",
  color: "inherit",
  font: "inherit",
} as const;

export function AttendancePage() {
  const [period, setPeriod] = useState<Dayjs>(() => dayjs());
  const [board, setBoard] = useState<MonthlyAttendanceBoard | null>(null);
  const [loading, setLoading] = useState(false);
  const [selectedCell, setSelectedCell] = useState<{ emp: EmployeeMonthlyRow; day: AttendanceDay } | null>(null);
  const [rollDate, setRollDate] = useState<string | null>(null);
  const [kindDate, setKindDate] = useState<string | null>(null);
  const [showPolicy, setShowPolicy] = useState(false);
  const [showHolidays, setShowHolidays] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setBoard(await getMonthlyBoard(period.year(), period.month() + 1));
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [period]);

  useEffect(() => {
    void load();
  }, [load]);

  const dayNumbers = board ? Array.from({ length: board.daysInMonth }, (_, i) => i + 1) : [];

  const openRoll = (day: number) => {
    if (!board) return;
    const date = period.date(day);
    const iso = date.format("YYYY-MM-DD");
    if (board.periodClosed) {
      message.warning("Kỳ công tháng này đã chốt");
      return;
    }
    const now = dayjs();
    if (!now.isSame(date, "day") || now.isBefore(date.startOf("day").add(1, "minute"))) {
      message.info("Phiếu điểm danh chỉ mở từ 00:01 đến 23:59 trong ngày chấm công");
      return;
    }
    if ((board.lockedDates ?? []).includes(iso)) {
      Modal.confirm({
        title: "Mở lại phiếu điểm danh",
        content: `Điểm danh ngày ${date.format("DD/MM/YYYY")} đã khóa. Mở lại để sửa?`,
        okText: "Mở lại",
        cancelText: "Để nguyên",
        onOk: async () => {
          try {
            await unlockRollCall(iso);
            setRollDate(iso);
          } catch (error) {
            message.error(toApiError(error).message);
            throw error;
          }
        },
      });
      return;
    }
    setRollDate(iso);
  };

  return (
    <Card
      title={`Bảng chấm công tháng ${period.format("MM/YYYY")}`}
      extra={
        <Space wrap>
          <DatePicker
            picker="month"
            allowClear={false}
            format="MM/YYYY"
            value={period}
            onChange={(val) => val && setPeriod(val)}
          />
          <Button onClick={() => setShowHolidays(true)}>Lịch nghỉ lễ</Button>
          <Button onClick={() => setShowPolicy(true)}>Quy tắc chấm công</Button>
          <Button
            type="primary"
            onClick={() => {
              const today = dayjs();
              if (period.year() !== today.year() || period.month() !== today.month()) {
                message.info("Hãy chọn tháng hiện tại để điểm danh hôm nay");
                return;
              }
              openRoll(today.date());
            }}
          >
            Điểm danh hôm nay
          </Button>
        </Space>
      }
    >
      {board?.periodClosed ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Kỳ công tháng này đã chốt. Muốn chỉnh sửa chấm công hoặc tăng ca, hãy mở lại kỳ công ở mục Tiền công."
        />
      ) : null}
      <p style={{ marginTop: 0, color: "var(--color-text-secondary)" }}>
        Bấm số ngày để chấm công từ 00:01 đến 23:59 trong ngày. Mỗi buổi tính 0,5 công, tăng ca tính riêng theo giờ. Lưu xong sẽ khóa phiếu.
      </p>
      <Table<EmployeeMonthlyRow>
        rowKey="employeeId"
        loading={loading}
        dataSource={board?.employees ?? []}
        size="small"
        pagination={false}
        scroll={{ x: "max-content" }}
        columns={[
          { title: "Họ và tên", dataIndex: "fullName", align: "center" as const, fixed: "left", width: 160 },
          { title: "Tổng công", dataIndex: "totalCreditedDays", align: "center" as const, width: 90 },
          { title: "Giờ làm", dataIndex: "totalWorkedHours", align: "center" as const, width: 80 },
          {
            title: "Tăng ca đã duyệt",
            align: "center" as const,
            width: 140,
            render: (_, r) =>
              r.approvedOvertimeHours > 0 ? `${r.approvedOvertimeHours}h (${formatMoney(r.approvedOvertimePay)})` : "0h",
          },
          ...dayNumbers.map((d) => ({
            title: (
              <DayColumnTitle
                day={d}
                holiday={(board?.holidays ?? []).find((h) => dayjs(h.holidayDate).date() === d && dayjs(h.holidayDate).month() === period.month())}
                locked={(board?.lockedDates ?? []).includes(period.date(d).format("YYYY-MM-DD"))}
                onRoll={() => openRoll(d)}
                onKind={() => {
                  if (board?.periodClosed) {
                    message.warning("Kỳ công tháng này đã chốt");
                    return;
                  }
                  setKindDate(period.date(d).format("YYYY-MM-DD"));
                }}
              />
            ),
            width: 64,
            align: "center" as const,
            render: (_: unknown, row: EmployeeMonthlyRow) => {
              const cell = row.days[d - 1];
              if (!cell) return null;
              const meta = statusMeta[cell.status] ?? statusMeta.UNMARKED;
              const hasOt = cell.overtimes.length > 0;
              const sessionLabel = cell.morningPresent && cell.afternoonPresent
                ? "S+C" : cell.morningPresent ? "S" : cell.afternoonPresent ? "C" : meta.short;
              return (
                <div
                  style={{ cursor: "pointer" }}
                  onClick={() => setSelectedCell({ emp: row, day: cell })}
                  title={`${cell.workDate}: ${meta.label}${cell.morningPresent || cell.afternoonPresent ? ` (${sessionLabel})` : ""}${cell.workedHours > 0 ? ` · ${cell.workedHours}h` : ""}`}
                >
                  <Tag color={meta.color} style={{ margin: 0 }}>
                    {sessionLabel}
                    {hasOt ? "+" : ""}
                  </Tag>
                </div>
              );
            },
          })),
        ]}
      />
      {selectedCell && board ? (
        <DayAttendanceModal
          employee={selectedCell.emp}
          initialDay={selectedCell.day}
          policy={board.policy}
          periodClosed={board.periodClosed}
          rollLocked={(board.lockedDates ?? []).includes(selectedCell.day.workDate)}
          onClose={() => setSelectedCell(null)}
          onChanged={() => void load()}
        />
      ) : null}
      {rollDate && board ? (
        <RollCallModal
          board={board}
          workDate={rollDate}
          onClose={() => setRollDate(null)}
          onSaved={() => {
            setRollDate(null);
            void load();
          }}
        />
      ) : null}
      {kindDate && board ? (
        <DayKindModal
          workDate={kindDate}
          holiday={(board.holidays ?? []).find((h) => h.holidayDate === kindDate) ?? null}
          onClose={() => setKindDate(null)}
          onSaved={() => {
            setKindDate(null);
            void load();
          }}
        />
      ) : null}
      {showPolicy && board ? (
        <PolicyModal
          policy={board.policy}
          onClose={() => setShowPolicy(false)}
          onSaved={() => {
            setShowPolicy(false);
            void load();
          }}
        />
      ) : null}
      {showHolidays ? (
        <HolidaysModal
          year={period.year()}
          onClose={() => setShowHolidays(false)}
          onChanged={() => void load()}
        />
      ) : null}
    </Card>
  );
}

function DayAttendanceModal({
  employee,
  initialDay,
  policy,
  periodClosed,
  rollLocked,
  onClose,
  onChanged,
}: {
  employee: EmployeeMonthlyRow;
  initialDay: AttendanceDay;
  policy: AttendancePolicy;
  periodClosed: boolean;
  rollLocked: boolean;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [day, setDay] = useState<AttendanceDay>(initialDay);
  const [saving, setSaving] = useState(false);
  const [editingOt, setEditingOt] = useState<OvertimeRecord | null>(null);
  const [form] = Form.useForm<{
    status: AttendanceDay["status"];
    morningPresent: boolean;
    afternoonPresent: boolean;
    clockInTime?: string;
    clockOutTime?: string;
    notes?: string;
  }>();
  const [bonusForm] = Form.useForm<{ amount: number; notes?: string }>();
  const [otForm] = Form.useForm<{
    overtimeHours: number;
    approved: boolean;
    notes?: string;
  }>();

  const locked = periodClosed || rollLocked || day.locked;
  const payLocked = periodClosed || day.locked;
  const selectedStatus = Form.useWatch("status", form);

  return (
    <Modal
      open
      title={`Chấm công ${employee.fullName} — Ngày ${dayjs(day.workDate).format("DD/MM/YYYY")}`}
      footer={null}
      onCancel={onClose}
      width={720}
    >
      <Form
        form={form}
        layout="vertical"
        disabled={locked}
        initialValues={{
          status: day.status === "UNMARKED" || day.status === "HALF_DAY" ? "PRESENT" : day.status,
          morningPresent: day.status === "UNMARKED" ? true : day.morningPresent,
          afternoonPresent: day.status === "UNMARKED" ? true : day.afternoonPresent,
          clockInTime: day.clockIn ? dayjs(day.clockIn).format("HH:mm") : undefined,
          clockOutTime: day.clockOut ? dayjs(day.clockOut).format("HH:mm") : undefined,
          notes: day.notes ?? undefined,
        }}
        onFinish={async (values) => {
          if (values.status === "PRESENT" && !values.morningPresent && !values.afternoonPresent) {
            message.warning("Chọn buổi sáng, buổi chiều hoặc đổi trạng thái thành Vắng");
            return;
          }
          if (values.status === "PRESENT" && Boolean(values.clockInTime) !== Boolean(values.clockOutTime)) {
            message.warning("Nếu ghi giờ làm, hãy nhập cả giờ vào và giờ ra");
            return;
          }
          setSaving(true);
          try {
            const hasClock =
              (values.status === "PRESENT" || values.status === "HALF_DAY") &&
              values.clockInTime &&
              values.clockOutTime;
            const saved = await saveAttendanceDay(employee.employeeId, day.workDate, {
              status: values.status,
              morningPresent: values.status === "PRESENT" && values.morningPresent,
              afternoonPresent: values.status === "PRESENT" && values.afternoonPresent,
              clockIn: hasClock ? `${day.workDate}T${values.clockInTime}:00` : null,
              clockOut: hasClock ? `${day.workDate}T${values.clockOutTime}:00` : null,
              notes: values.notes,
            });
            setDay(saved);
            form.setFieldsValue({ morningPresent: saved.morningPresent, afternoonPresent: saved.afternoonPresent });
            onChanged();
            message.success("Đã lưu chấm công ngày");
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Space align="start" wrap size={[16, 32]}>
          <Form.Item name="status" label="Trạng thái công" rules={[{ required: true }]}>
            <Select
              style={{ width: 220 }}
              options={Object.entries(statusMeta)
                .filter(([value]) => value !== "UNMARKED" && value !== "HALF_DAY")
                .map(([value, m]) => ({ value, label: m.label }))}
            />
          </Form.Item>
          <Form.Item name="clockInTime" label="Giờ vào (nếu cần)">
            <Input placeholder={policy.standardStartTime.slice(0, 5)} style={{ width: 120 }} />
          </Form.Item>
          <Form.Item name="clockOutTime" label="Giờ ra (nếu cần)">
            <Input placeholder={policy.standardEndTime.slice(0, 5)} style={{ width: 120 }} />
          </Form.Item>
        </Space>
        {selectedStatus === "PRESENT" ? (
          <Space wrap>
            <Form.Item name="morningPresent" valuePropName="checked" label="Buổi sáng">
              <Checkbox>Có đi làm</Checkbox>
            </Form.Item>
            <Form.Item name="afternoonPresent" valuePropName="checked" label="Buổi chiều">
              <Checkbox>Có đi làm</Checkbox>
            </Form.Item>
          </Space>
        ) : null}
        <p style={{ marginTop: 0, color: "var(--color-text-secondary)" }}>
          Mỗi buổi tính 0,5 công. Tăng ca và thưởng được tính riêng.
        </p>
        <Form.Item name="notes" label="Ghi chú chấm công">
          <Input />
        </Form.Item>
        <Space>
          <Button type="primary" htmlType="submit" loading={saving} disabled={locked}>
            Lưu chấm công
          </Button>
          <span>
            Công quy đổi: <b>{day.creditedDays}</b> · Giờ làm: <b>{day.workedHours}h</b> · Muộn: {day.lateMinutes}p · Sớm:{" "}
            {day.earlyLeaveMinutes}p
          </span>
        </Space>
      </Form>

      <div style={{ marginTop: 24, borderTop: "1px solid var(--color-border)", paddingTop: 16 }}>
        <h4 style={{ marginTop: 0 }}>Tăng ca và thưởng của {employee.fullName}</h4>
        {!day.id ? (
          <p style={{ color: "var(--color-text-secondary)" }}>Lưu chấm công ngày trước khi thêm bản ghi tăng ca.</p>
        ) : (
          <>
            <Table<OvertimeRecord>
              rowKey="id"
              size="small"
              pagination={false}
              dataSource={day.overtimes}
              columns={[
                { title: "Số giờ", dataIndex: "overtimeHours", align: "right" },
                { title: "Đơn giá / giờ", dataIndex: "hourlyRateSnapshot", align: "right", render: (v: number) => formatMoney(v) },
                { title: "Thành tiền", dataIndex: "amount", align: "right", render: (v: number) => formatMoney(v) },
                {
                  title: "Xác nhận",
                  render: (_, ot) => (ot.approved ? <Tag color="green">Đã duyệt</Tag> : <Tag color="orange">Chưa duyệt</Tag>),
                },
                { title: "Ghi chú", dataIndex: "notes" },
                {
                  title: "",
                  render: (_, ot) =>
                    payLocked ? null : (
                      <a
                        onClick={() => {
                          setEditingOt(ot);
                          otForm.setFieldsValue({
                            overtimeHours: ot.overtimeHours,
                            approved: ot.approved,
                            notes: ot.notes ?? undefined,
                          });
                        }}
                      >
                        Sửa / Duyệt
                      </a>
                    ),
                },
              ]}
            />
            {!payLocked ? (
              <Form
                form={otForm}
                layout="vertical"
                style={{ marginTop: 12 }}
                initialValues={{ overtimeHours: 2, approved: true }}
                onFinish={async (values) => {
                  try {
                    const updated = await saveOvertime(day.id!, editingOt?.id, values);
                    setDay(updated);
                    setEditingOt(null);
                    otForm.resetFields();
                    onChanged();
                    message.success("Đã lưu tăng ca");
                  } catch (error) {
                    message.error(toApiError(error).message);
                  }
                }}
              >
                <Space align="start" wrap size={[16, 32]}>
                  <Form.Item name="overtimeHours" label="Số giờ" rules={[{ required: true }]}>
                    <InputNumber min={0.25} max={24} step={0.5} style={{ width: 110 }} />
                  </Form.Item>
                  <Form.Item name="notes" label="Ghi chú">
                    <Input style={{ width: 200 }} />
                  </Form.Item>
                  <Form.Item name="approved" label="Duyệt tính lương" valuePropName="checked">
                    <Checkbox>Đã duyệt</Checkbox>
                  </Form.Item>
                </Space>
                <Space>
                  <Button type="primary" htmlType="submit">
                    {editingOt ? "Lưu cập nhật tăng ca" : "Thêm tăng ca"}
                  </Button>
                  {editingOt ? (
                    <Button
                      onClick={() => {
                        setEditingOt(null);
                        otForm.resetFields();
                      }}
                    >
                      Hủy sửa
                    </Button>
                  ) : null}
                </Space>
              </Form>
            ) : null}
          </>
        )}
        {day.id ? (
          <Form
            form={bonusForm}
            layout="vertical"
            disabled={payLocked}
            initialValues={{ amount: day.bonusAmount ?? 0, notes: day.bonusNotes ?? "" }}
            style={{ marginTop: 18, borderTop: "1px solid var(--color-border)", paddingTop: 16 }}
            onFinish={async (values) => {
              try {
                const updated = await saveAttendanceBonus(day.id!, values);
                setDay(updated);
                onChanged();
                message.success("Đã lưu thưởng cho nhân viên");
              } catch (error) {
                message.error(toApiError(error).message);
              }
            }}
          >
            <Space align="start" wrap>
              <Form.Item name="amount" label="Thưởng trong ngày" rules={vndAmountRules}>
                <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
              </Form.Item>
              <Form.Item name="notes" label="Lý do thưởng">
                <Input style={{ width: 260 }} maxLength={500} />
              </Form.Item>
              <Button type="primary" htmlType="submit" disabled={payLocked} style={{ marginTop: 30 }}>
                Lưu thưởng
              </Button>
            </Space>
          </Form>
        ) : null}
      </div>
    </Modal>
  );
}

function DayColumnTitle({
  day,
  holiday,
  locked,
  onRoll,
  onKind,
}: {
  day: number;
  holiday?: Holiday;
  locked: boolean;
  onRoll: () => void;
  onKind: () => void;
}) {
  const kind = holiday ? dayKinds[holiday.holidayType] : undefined;
  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2, lineHeight: 1.15 }}>
      <button type="button" onClick={onRoll} style={{ ...headerButtonStyle, fontWeight: 650, color: locked ? "#1d4ed8" : "inherit" }}>
        {String(day).padStart(2, "0")}
      </button>
      <button
        type="button"
        onClick={onKind}
        title={kind ? `${kind.label} · ${formatMoney(holiday?.dayPay ?? 0)}` : "Đặt loại ngày"}
        style={{ ...headerButtonStyle, color: kind?.color ?? "var(--color-text-secondary)", fontSize: 11, fontWeight: 650 }}
      >
        {kind ? kind.mark : "·"}
      </button>
    </div>
  );
}

function RollCallModal({
  board,
  workDate,
  onClose,
  onSaved,
}: {
  board: MonthlyAttendanceBoard;
  workDate: string;
  onClose: () => void;
  onSaved: () => void;
}) {
  const date = dayjs(workDate);
  const people = board.employees.filter((employee) => employee.active);
  const [query, setQuery] = useState("");
  const [saving, setSaving] = useState(false);
  const [marks, setMarks] = useState(() => Object.fromEntries(people.map((employee) => {
    const cell = employee.days[date.date() - 1];
    const hours = cell?.overtimes
      .filter((ot) => ot.notes === "Tăng ca từ phiếu điểm danh")
      .reduce((sum, ot) => sum + ot.overtimeHours, 0) ?? 0;
    return [employee.employeeId, {
      morning: cell?.morningPresent ?? false,
      afternoon: cell?.afternoonPresent ?? false,
      overtime: hours > 0,
      hours,
    }];
  })));
  const shown = people.filter((employee) =>
    `${employee.fullName} ${employee.employeeCode}`.toLocaleLowerCase("vi").includes(query.trim().toLocaleLowerCase("vi")),
  );
  const totalDays = Object.values(marks).reduce((sum, mark) => sum + (Number(mark.morning) + Number(mark.afternoon)) / 2, 0);
  const totalHours = Object.values(marks).reduce((sum, mark) => sum + mark.hours, 0);
  const fullDays = Object.values(marks).filter((mark) => mark.morning && mark.afternoon).length;

  function updateMark(id: number, change: Partial<(typeof marks)[number]>) {
    setMarks((current) => ({ ...current, [id]: { ...current[id], ...change } }));
  }

  async function save() {
    if (Object.values(marks).some((mark) => mark.overtime && mark.hours <= 0)) {
      message.warning("Nhập số giờ cho từng nhân viên đã tích tăng ca.");
      return;
    }
    setSaving(true);
    try {
      await saveRollCall({
        workDate,
        items: people.map(({ employeeId }) => ({
          employeeId,
          morningPresent: marks[employeeId].morning,
          afternoonPresent: marks[employeeId].afternoon,
          overtimeHours: marks[employeeId].overtime ? marks[employeeId].hours : 0,
        })),
      });
      message.success("Đã lưu và khóa phiếu điểm danh");
      onSaved();
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal open title="Chấm công theo ngày" width={880}
      okText="Lưu chấm công" cancelText="Đóng" confirmLoading={saving}
      okButtonProps={{ disabled: !people.length, icon: <SaveOutlined /> }}
      onCancel={onClose} onOk={save}>
      <p>Ca sáng 0,5 công · Ca chiều 0,5 công · Tăng ca tính riêng theo giờ</p>
      <Space wrap style={{ marginBottom: 16 }}>
        <Input aria-label="Ngày chấm công" value={date.format("DD/MM/YYYY")} readOnly />
        <Input aria-label="Tìm nhân viên" allowClear placeholder="Tên hoặc mã nhân viên"
          value={query} onChange={(event) => setQuery(event.target.value)} />
      </Space>
      <div className="roll-call-summary">
        <Card size="small"><Statistic title="Đủ công" value={fullDays} /></Card>
        <Card size="small"><Statistic title="Tổng công" value={totalDays} /></Card>
        <Card size="small"><Statistic title="Giờ tăng ca" value={Number(totalHours.toFixed(3))} /></Card>
      </div>
      <Space wrap style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
        <strong>Danh sách nhân viên</strong>
        <Button onClick={() => setMarks((current) => Object.fromEntries(Object.entries(current).map(
          ([id, mark]) => [id, { ...mark, morning: true, afternoon: true }],
        )))}>Đủ công tất cả</Button>
      </Space>
      <Table<EmployeeMonthlyRow> rowKey="employeeId" size="small" pagination={false} dataSource={shown}
        scroll={{ x: 640, y: 420 }}
        columns={[
          { title: "Nhân viên", render: (_, employee) => <strong>{employee.fullName}</strong> },
          ...(["morning", "afternoon"] as const).map((shift) => ({
            title: shift === "morning" ? "Sáng" : "Chiều",
            align: "center" as const,
            render: (_: unknown, employee: EmployeeMonthlyRow) => <Checkbox
              aria-label={`${employee.fullName} — ${shift === "morning" ? "Sáng" : "Chiều"}`}
              checked={marks[employee.employeeId][shift]}
              onChange={(event) => updateMark(employee.employeeId, { [shift]: event.target.checked })} />,
          })),
          { title: "Tăng ca", align: "center", render: (_, employee) => <Checkbox
            aria-label={`Tăng ca — ${employee.fullName}`} checked={marks[employee.employeeId].overtime}
            onChange={(event) => updateMark(employee.employeeId, event.target.checked ? { overtime: true } : { overtime: false, hours: 0 })} /> },
          { title: "Số giờ tăng ca", align: "center", render: (_, employee) => <InputNumber
            aria-label={`Số giờ tăng ca — ${employee.fullName}`} min={0} max={24} step={0.5} precision={3}
            disabled={!marks[employee.employeeId].overtime} value={marks[employee.employeeId].hours}
            onChange={(hours) => updateMark(employee.employeeId, { hours: hours ?? 0 })} /> },
          { title: "Công", align: "center", render: (_, employee) =>
            (Number(marks[employee.employeeId].morning) + Number(marks[employee.employeeId].afternoon)) / 2 },
        ]} />
      <Space wrap size={24} style={{ marginTop: 16 }}>
        <strong>Tổng công ngày: {totalDays} công</strong>
        <strong>Tổng tăng ca: {Number(totalHours.toFixed(3))} giờ</strong>
        <span>{shown.length}/{people.length} nhân viên</span>
      </Space>
    </Modal>
  );
}

function DayKindModal({
  workDate,
  holiday,
  onClose,
  onSaved,
}: {
  workDate: string;
  holiday: Holiday | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<{ holidayType: HolidayKind | ""; dayPay: number }>();
  const [saving, setSaving] = useState(false);
  const kind = Form.useWatch("holidayType", form);

  return (
    <Modal
      open
      title={`Loại ngày ${dayjs(workDate).format("DD/MM/YYYY")}`}
      okText="Lưu"
      cancelText="Đóng"
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ holidayType: holiday?.holidayType ?? "", dayPay: holiday?.dayPay ?? 0 }}
        onFinish={async (values) => {
          setSaving(true);
          try {
            await saveDayKind({
              holidayDate: workDate,
              holidayType: values.holidayType || null,
              dayPay: values.holidayType ? values.dayPay : 0,
            });
            message.success(values.holidayType ? "Đã lưu loại ngày" : "Đã bỏ loại ngày");
            onSaved();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item name="holidayType" label="Loại ngày">
          <Radio.Group>
            <Space direction="vertical">
              <Radio value="">Ngày thường</Radio>
              <Radio value="SHOP_CLOSED">Nghỉ cửa hàng</Radio>
              <Radio value="NATIONAL_HOLIDAY">Nghỉ lễ quốc gia</Radio>
              <Radio value="CATHOLIC_HOLIDAY">Nghỉ lễ công giáo</Radio>
            </Space>
          </Radio.Group>
        </Form.Item>
        {kind ? (
          <Form.Item name="dayPay" label="Lương ngày này" rules={vndUnitPriceRules}>
            <InputNumber {...vndInputNumberProps} style={{ width: "100%" }} />
          </Form.Item>
        ) : null}
      </Form>
    </Modal>
  );
}

function PolicyModal({
  policy,
  onClose,
  onSaved,
}: {
  policy: AttendancePolicy;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  return (
    <Modal
      open
      title="Quy tắc chấm công và ca làm việc"
      okText="Lưu cấu hình"
      cancelText="Hủy"
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
      width={640}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          effectiveFrom: dayjs(policy.effectiveFrom),
          standardStartTime: policy.standardStartTime.slice(0, 5),
          standardEndTime: policy.standardEndTime.slice(0, 5),
          breakMinutes: policy.breakMinutes,
          lateGraceMinutes: policy.lateGraceMinutes,
          earlyLeaveGraceMinutes: policy.earlyLeaveGraceMinutes,
          roundingMinutes: policy.roundingMinutes,
          halfDayThresholdHours: policy.halfDayThresholdHours,
          fullDayCreditedDays: policy.fullDayCreditedDays,
          halfDayCreditedDays: policy.halfDayCreditedDays,
          overnightShiftAllowed: policy.overnightShiftAllowed,
          notes: policy.notes ?? "",
        }}
        onFinish={async (values) => {
          setSaving(true);
          try {
            await saveAttendancePolicy({
              effectiveFrom: values.effectiveFrom.format("YYYY-MM-DD"),
              standardStartTime: `${values.standardStartTime}:00`,
              standardEndTime: `${values.standardEndTime}:00`,
              breakMinutes: values.breakMinutes,
              lateGraceMinutes: values.lateGraceMinutes,
              earlyLeaveGraceMinutes: values.earlyLeaveGraceMinutes,
              roundingMinutes: values.roundingMinutes,
              halfDayThresholdHours: values.halfDayThresholdHours,
              fullDayCreditedDays: values.fullDayCreditedDays,
              halfDayCreditedDays: values.halfDayCreditedDays,
              overnightShiftAllowed: values.overnightShiftAllowed,
              notes: values.notes,
            });
            message.success("Đã lưu cấu hình chấm công");
            onSaved();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Space wrap>
          <Form.Item name="effectiveFrom" label="Áp dụng từ ngày" rules={[{ required: true }]}>
            <DatePicker format="DD/MM/YYYY" />
          </Form.Item>
          <Form.Item name="standardStartTime" label="Giờ bắt đầu ca (HH:mm)" rules={[{ required: true }]}>
            <Input style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="standardEndTime" label="Giờ kết thúc ca (HH:mm)" rules={[{ required: true }]}>
            <Input style={{ width: 130 }} />
          </Form.Item>
        </Space>
        <Space wrap>
          <Form.Item name="breakMinutes" label="Nghỉ giữa ca (phút)">
            <InputNumber min={0} />
          </Form.Item>
          <Form.Item name="lateGraceMinutes" label="Cho phép trễ (phút)">
            <InputNumber min={0} />
          </Form.Item>
          <Form.Item name="earlyLeaveGraceMinutes" label="Cho phép về sớm (phút)">
            <InputNumber min={0} />
          </Form.Item>
          <Form.Item name="roundingMinutes" label="Làm tròn (phút)">
            <InputNumber min={1} />
          </Form.Item>
        </Space>
        <Space wrap>
          <Form.Item name="fullDayCreditedDays" label="Hệ số công đủ ngày">
            <InputNumber min={0} step={0.5} />
          </Form.Item>
          <Form.Item name="halfDayCreditedDays" label="Hệ số công nửa ngày">
            <InputNumber min={0} step={0.25} />
          </Form.Item>
          <Form.Item name="halfDayThresholdHours" label="Ngưỡng giờ nửa ngày">
            <InputNumber min={0} step={0.5} />
          </Form.Item>
        </Space>
        <Form.Item name="overnightShiftAllowed" label="Cho phép ca làm qua đêm" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="notes" label="Ghi chú">
          <Input />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function HolidaysModal({
  year,
  onClose,
  onChanged,
}: {
  year: number;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [employees, setEmployees] = useState<Employee[] | null>(null);
  const [editing, setEditing] = useState<Holiday | null>(null);
  const [form] = Form.useForm<{
    holidayDate: Dayjs;
    name: string;
    holidayType: Holiday["holidayType"];
    paid: boolean;
    bonusAmount: number;
    receivedEmployeeIds: number[];
    notes?: string;
  }>();
  const holidayDate = Form.useWatch("holidayDate", form)?.format("YYYY-MM-DD");
  const bonusAmount = Form.useWatch("bonusAmount", form) ?? 0;
  const eligible = holidayDate ? (employees ?? []).filter((employee) =>
    (employee.active || employee.terminationDate != null) &&
    (!employee.hireDate || employee.hireDate <= holidayDate) &&
    (!employee.terminationDate || employee.terminationDate >= holidayDate),
  ) : [];

  const load = useCallback(async () => {
    try {
      setHolidays(await listHolidays(year));
    } catch (error) {
      message.error(toApiError(error).message);
    }
  }, [year]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    void listEmployees({}).then(setEmployees).catch((error) => message.error(toApiError(error).message));
  }, []);

  return (
    <Modal open title={`Lịch nghỉ lễ năm ${year}`} footer={null} onCancel={onClose} width={680}>
      <Table<Holiday>
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={holidays}
        columns={[
          { title: "Ngày", dataIndex: "holidayDate", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY") },
          { title: "Tên ngày nghỉ", dataIndex: "name", align: "center" },
          {
            title: "Loại",
            dataIndex: "holidayType",
            align: "center",
            render: (v: HolidayKind) => dayKinds[v]?.label ?? v,
          },
          {
            title: "Thưởng mỗi người",
            dataIndex: "bonusAmount",
            align: "center",
            render: (v: number) => formatMoney(v ?? 0),
          },
          {
            title: "",
            render: (_, h) => (
              <a
                onClick={() => {
                  setEditing(h);
                  form.setFieldsValue({
                    holidayDate: dayjs(h.holidayDate),
                    name: h.name,
                    holidayType: h.holidayType,
                    paid: h.paid,
                    bonusAmount: h.bonusAmount ?? 0,
                    receivedEmployeeIds: h.receivedEmployeeIds ?? [],
                    notes: h.notes ?? undefined,
                  });
                }}
              >
                Sửa
              </a>
            ),
          },
        ]}
      />
      <Form
        form={form}
        layout="vertical"
        style={{ marginTop: 16 }}
        initialValues={{ holidayType: "NATIONAL_HOLIDAY", paid: false, bonusAmount: 0, receivedEmployeeIds: [] }}
        onFinish={async (values) => {
          try {
            await saveHoliday(editing?.id, {
              holidayDate: values.holidayDate.format("YYYY-MM-DD"),
              name: values.name,
              holidayType: values.holidayType,
              paid: values.bonusAmount > 0 ? false : editing?.paid ?? false,
              dayPay: values.bonusAmount > 0 ? 0 : editing?.dayPay ?? 0,
              bonusAmount: values.bonusAmount,
              receivedEmployeeIds: (values.receivedEmployeeIds ?? []).filter((id) => eligible.some((employee) => employee.id === id)),
              notes: values.notes,
            });
            setEditing(null);
            form.resetFields();
            form.setFieldValue("holidayDate", values.holidayDate);
            await load();
            onChanged();
            message.success("Đã lưu ngày nghỉ lễ");
          } catch (error) {
            message.error(toApiError(error).message);
          }
        }}
      >
        <Space align="start" wrap size={[16, 32]}>
          <Form.Item name="holidayDate" label="Ngày" rules={[{ required: true }]}>
            <DatePicker format={["DD/MM/YYYY", "D/M/YYYY"]} defaultPickerValue={dayjs().year(year)}
              disabledDate={(date) => date.year() !== year} />
          </Form.Item>
          <Form.Item name="name" label="Tên ngày lễ" rules={requiredNameRules("Nhập tên", 150)}>
            <Input style={{ width: 200 }} maxLength={150} />
          </Form.Item>
          <Form.Item name="holidayType" label="Loại">
            <Select
              style={{ width: 180 }}
              options={[
                { value: "SHOP_CLOSED", label: "Nghỉ cửa hàng" },
                { value: "NATIONAL_HOLIDAY", label: "Nghỉ lễ quốc gia" },
                { value: "CATHOLIC_HOLIDAY", label: "Nghỉ lễ công giáo" },
              ]}
            />
          </Form.Item>
          <Form.Item name="bonusAmount" label="Tiền thưởng mỗi nhân viên" rules={vndUnitPriceRules}>
            <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
          </Form.Item>
        </Space>
        {editing && editing.dayPay > 0 && bonusAmount > 0 && (
          <Alert type="info" showIcon message="Lưu tiền thưởng sẽ thay mức lương ngày cũ của ngày lễ này." />
        )}
        {bonusAmount > 0 && holidayDate && (
          <Form.Item name="receivedEmployeeIds" label="Nhân viên đã nhận thưởng"
            extra="Tích người đã nhận trực tiếp. Những người chưa tích sẽ nhận cùng tiền công cuối tháng.">
            <Checkbox.Group options={eligible.map((employee) => ({
              label: `${employee.employeeCode} · ${employee.fullName}`, value: employee.id,
            }))} />
          </Form.Item>
        )}
        <Space>
          <Button type="primary" htmlType="submit" disabled={employees == null}>
            {editing ? "Lưu cập nhật" : "Thêm ngày lễ"}
          </Button>
          {editing ? (
            <Button
              onClick={() => {
                setEditing(null);
                form.resetFields();
              }}
            >
              Hủy sửa
            </Button>
          ) : null}
        </Space>
      </Form>
    </Modal>
  );
}
