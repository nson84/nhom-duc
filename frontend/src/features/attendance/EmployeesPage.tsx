import { DollarOutlined, EditOutlined, SaveOutlined } from "@ant-design/icons";
import { Button, Card, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Switch, Tag, message } from "antd";
import { AppActionButton } from "../../components/AppButtons";
import { PagedTable, ResponsiveTable as Table } from "../../components/PagedTable";
import { LiveSearch } from "../../components/LiveSearch";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { requiredNameRules } from "../../utils/name-rules";
import { vndInputNumberProps } from "../../utils/vnd-input";
import {
  addPayRate,
  listEmployees,
  listPayRates,
  deletePayRate,
  saveEmployee,
  type Employee,
  type PayRate,
} from "./attendance-api";

export function EmployeesPage() {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [query, setQuery] = useState("");
  const [activeFilter, setActiveFilter] = useState<boolean | undefined>();
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<Employee | "new" | null>(null);
  const [ratesFor, setRatesFor] = useState<Employee | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setEmployees(await listEmployees({ q: query || undefined, active: activeFilter }));
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [query, activeFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Card
      title="Nhân viên"
      extra={<Button type="primary" onClick={() => setEditing("new")}>Thêm nhân viên</Button>}
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <LiveSearch value={query} onChange={setQuery} placeholder="Tìm họ tên hoặc SĐT" style={{ width: 360 }} suggestions={employees.flatMap((e) => [e.fullName, e.phone ?? ""])} />
        <Select
          placeholder="Trạng thái làm việc"
          allowClear
          style={{ width: 200 }}
          value={activeFilter}
          onChange={setActiveFilter}
          options={[
            { value: true, label: "Đang làm việc" },
            { value: false, label: "Đã ngừng làm việc" },
          ]}
        />
      </Space>
      <PagedTable<Employee>
        rowKey="id"
        loading={loading}
        dataSource={employees}
        columns={[
          { title: "Họ và tên", dataIndex: "fullName", align: "center" },
          { title: "Điện thoại", dataIndex: "phone", align: "center" },
          {
            title: "Mức công ngày hiện tại",
            align: "center",
            render: (_, e) => (e.currentPayRate ? formatMoney(e.currentPayRate.dailyRate) : "Chưa thiết lập"),
          },
          {
            title: "Tiền tăng ca mỗi giờ",
            align: "center",
            render: (_, e) => (e.currentPayRate ? formatMoney(e.currentPayRate.overtimeWeekdayRate) : "—"),
          },
          { title: "Tiền tăng ca mỗi buổi", align: "center", render: (_, e) => e.currentPayRate ? formatMoney(e.currentPayRate.overtimeSessionRate) : "—" },
          {
            title: "Trạng thái",
            align: "center",
            render: (_, e) =>
              e.active ? <Tag color="green">Đang làm việc</Tag> : <Tag color="default">Ngừng làm việc</Tag>,
          },
          {
            title: "Thao tác",
            align: "center",
            render: (_, e) => (
              <Space size={8} wrap>
                <AppActionButton tone="edit" icon={<EditOutlined />} onClick={() => setEditing(e)}>
                  Sửa
                </AppActionButton>
                <AppActionButton tone="pay" icon={<DollarOutlined />} onClick={() => setRatesFor(e)}>
                  Mức công
                </AppActionButton>
              </Space>
            ),
          },
        ]}
      />
      {editing ? (
        <EmployeeModal
          employee={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void load();
          }}
        />
      ) : null}
      {ratesFor ? (
        <PayRatesModal
          employee={ratesFor}
          onClose={() => setRatesFor(null)}
          onChanged={() => void load()}
        />
      ) : null}
    </Card>
  );
}

type EmployeeFormValues = {
  fullName: string;
  phone: string;
  address?: string;
  hireDate?: Dayjs | null;
  terminationDate?: Dayjs | null;
  active: boolean;
  notes?: string;
  initialDailyRate?: number;
  initialOvertimeWeekdayRate?: number;
  initialOvertimeSessionRate?: number;
};

function EmployeeModal({
  employee,
  onClose,
  onSaved,
}: {
  employee: Employee | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<EmployeeFormValues>();
  const [saving, setSaving] = useState(false);

  return (
    <Modal
      open
      title={employee ? `Sửa hồ sơ — ${employee.fullName}` : "Thêm nhân viên mới"}
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
      width={680}
    >
      <Form<EmployeeFormValues>
        form={form}
        layout="vertical"
        initialValues={
          employee
            ? {
                fullName: employee.fullName,
                phone: employee.phone ?? undefined,
                address: employee.address ?? undefined,
                hireDate: employee.hireDate ? dayjs(employee.hireDate) : null,
                terminationDate: employee.terminationDate ? dayjs(employee.terminationDate) : null,
                active: employee.active,
                notes: employee.notes ?? undefined,
              }
            : {
                active: true,
                hireDate: dayjs(),
                initialDailyRate: 350000,
                initialOvertimeWeekdayRate: 50000,
                initialOvertimeSessionRate: 0,
              }
        }
        onFinish={async (values) => {
          setSaving(true);
          try {
            await saveEmployee(employee?.id, {
              fullName: values.fullName,
              phone: values.phone,
              address: values.address,
              hireDate: values.hireDate ? values.hireDate.format("YYYY-MM-DD") : undefined,
              terminationDate: values.terminationDate ? values.terminationDate.format("YYYY-MM-DD") : undefined,
              active: values.active,
              notes: values.notes,
              initialDailyRate: employee ? undefined : values.initialDailyRate,
              initialOvertimeWeekdayRate: employee ? undefined : values.initialOvertimeWeekdayRate,
              initialOvertimeSessionRate: employee ? undefined : values.initialOvertimeSessionRate,
            });
            message.success("Đã lưu hồ sơ nhân viên");
            onSaved();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item
          name="fullName"
          label="Họ và tên"
          rules={requiredNameRules("Nhập họ và tên", 150)}
        >
          <Input maxLength={150} />
        </Form.Item>
        <Space align="start" style={{ display: "flex" }} size={[16, 32]} wrap>
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
            <Input style={{ width: 200 }} placeholder="0912345678" inputMode="tel" />
          </Form.Item>
          <Form.Item name="hireDate" label="Ngày vào làm">
            <DatePicker format="DD/MM/YYYY" style={{ width: 190 }} />
          </Form.Item>
          <Form.Item name="terminationDate" label="Ngày nghỉ việc (nếu có)">
            <DatePicker format="DD/MM/YYYY" style={{ width: 190 }} />
          </Form.Item>
        </Space>
        <Form.Item name="address" label="Địa chỉ">
          <Input />
        </Form.Item>
        {!employee ? (
          <Space align="start" wrap size={[16, 32]}>
            <Form.Item name="initialDailyRate" label="Mức công / ngày">
              <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="initialOvertimeWeekdayRate" label="Tiền tăng ca mỗi giờ">
              <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="initialOvertimeSessionRate" label="Tiền tăng ca mỗi buổi">
              <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
            </Form.Item>
          </Space>
        ) : null}
        <Form.Item name="notes" label="Ghi chú">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item
          name="active"
          label="Trạng thái làm việc (Tắt để ngừng chấm công nhân viên này, không xóa lịch sử)"
          valuePropName="checked"
        >
          <Switch checkedChildren="Đang làm việc" unCheckedChildren="Ngừng làm việc" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function PayRatesModal({
  employee,
  onClose,
  onChanged,
}: {
  employee: Employee;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [rates, setRates] = useState<PayRate[]>([]);
  const today = dayjs().format("YYYY-MM-DD");
  const timeline: PayRate[] = [];
  let nextStart: string | undefined;
  for (const rate of rates) {
    if (nextStart === rate.effectiveFrom) continue;
    const next = timeline.at(-1);
    const boundary = nextStart ? dayjs(nextStart).subtract(1, "day").format("YYYY-MM-DD") : null;
    const end = boundary && (!rate.effectiveTo || rate.effectiveTo > boundary) ? boundary : rate.effectiveTo;
    if (next && next.effectiveFrom <= today && end === boundary
      && rate.dailyRate === next.dailyRate && rate.standardHoursPerDay === next.standardHoursPerDay
      && rate.overtimeWeekdayRate === next.overtimeWeekdayRate
      && rate.overtimeRestdayRate === next.overtimeRestdayRate
      && rate.overtimeHolidayRate === next.overtimeHolidayRate
      && rate.overtimeSessionRate === next.overtimeSessionRate) {
      timeline[timeline.length - 1] = { ...next, effectiveFrom: rate.effectiveFrom };
    } else {
      timeline.push({ ...rate, effectiveTo: end });
    }
    nextStart = rate.effectiveFrom;
  }
  const currentRateId = timeline.find(rate =>
    rate.effectiveFrom <= today && (!rate.effectiveTo || rate.effectiveTo >= today)
  )?.id;
  const [form] = Form.useForm<{
    effectiveFrom: Dayjs;
    dailyRate: number;
    overtimeWeekdayRate: number;
    overtimeSessionRate: number;
  }>();

  const load = useCallback(async () => {
    try {
      setRates(await listPayRates(employee.id));
    } catch (error) {
      message.error(toApiError(error).message);
    }
  }, [employee.id]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Modal open title={`Lịch sử mức công — ${employee.fullName}`} footer={null} onCancel={onClose} width={1000}>
      <p style={{ marginTop: 0 }}>
        Mức công mới áp dụng từ ngày hiệu lực được chọn. Kỳ công đã chốt giữ nguyên mức công cũ, không tự thay đổi.
      </p>
      <Table<PayRate>
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={timeline}
        columns={[
          { title: "Từ ngày", dataIndex: "effectiveFrom", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY") },
          {
            title: "Đến ngày",
            dataIndex: "effectiveTo",
            align: "center",
            render: (v: string | null, rate: PayRate) => rate.id === currentRateId
              ? <Tag color="green">Đang áp dụng</Tag>
              : v ? dayjs(v).format("DD/MM/YYYY")
              : rate.effectiveFrom > today ? <Tag color="blue">Chưa áp dụng</Tag>
              : <Tag>Đã thay thế</Tag>,
          },
          { title: "Tiền công mỗi ngày", dataIndex: "dailyRate", align: "center", render: (v: number) => formatMoney(v) },
          { title: "Tiền tăng ca mỗi giờ", dataIndex: "overtimeWeekdayRate", align: "center", render: (v: number) => formatMoney(v) },
          { title: "Tiền tăng ca mỗi buổi", dataIndex: "overtimeSessionRate", align: "center", render: (v: number) => formatMoney(v) },
          {
            title: "Thao tác",
            align: "center",
            render: (_, rate) => dayjs(rate.effectiveFrom).isAfter(dayjs(), "day") ? (
              <Button danger onClick={() => Modal.confirm({
                title: "Xóa mức công đã đặt trước?",
                content: `Ngày dự kiến áp dụng: ${dayjs(rate.effectiveFrom).format("DD/MM/YYYY")}`,
                okText: "Xóa",
                cancelText: "Quay lại",
                okButtonProps: { danger: true },
                onOk: async () => {
                  try {
                    await deletePayRate(employee.id, rate.id);
                  } catch (error) {
                    message.error(toApiError(error).message);
                    return;
                  }
                  message.success("Đã xóa mức công");
                  await load();
                  onChanged();
                },
              })}>Xóa</Button>
            ) : null,
          },
        ]}
      />
      <Form
        form={form}
        layout="vertical"
        style={{ marginTop: 16 }}
        initialValues={{
          effectiveFrom: dayjs(),
          dailyRate: employee.currentPayRate?.dailyRate ?? 350000,
          overtimeWeekdayRate: employee.currentPayRate?.overtimeWeekdayRate ?? 50000,
          overtimeSessionRate: employee.currentPayRate?.overtimeSessionRate ?? 0,
        }}
        onFinish={async (values) => {
          try {
            await addPayRate(employee.id, {
              effectiveFrom: values.effectiveFrom.format("YYYY-MM-DD"),
              dailyRate: values.dailyRate,
              standardHoursPerDay: employee.currentPayRate?.standardHoursPerDay ?? 8,
              overtimeWeekdayRate: values.overtimeWeekdayRate,
              overtimeSessionRate: values.overtimeSessionRate,
            });
            message.success("Đã thêm mức công mới");
            await load();
            onChanged();
          } catch (error) {
            message.error(toApiError(error).message);
          }
        }}
      >
        <Space align="start" wrap size={[16, 32]}>
          <Form.Item name="effectiveFrom" label="Áp dụng từ ngày" rules={[{ required: true }]}>
            <DatePicker format="DD/MM/YYYY" style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="dailyRate" label="Tiền công mỗi ngày" rules={[{ required: true }]}>
            <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="overtimeWeekdayRate" label="Tiền tăng ca mỗi giờ">
            <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="overtimeSessionRate" label="Tiền tăng ca mỗi buổi">
            <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
          </Form.Item>
        </Space>
        <div>
          <AppActionButton tone="create" icon={<SaveOutlined />} htmlType="submit">
            Lưu tiền công
          </AppActionButton>
        </div>
      </Form>
    </Modal>
  );
}
