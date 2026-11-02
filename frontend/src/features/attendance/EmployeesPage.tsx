import { Button, Card, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, message } from "antd";
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
        <Input.Search className="app-search" placeholder="Tìm mã, họ tên hoặc SĐT" allowClear onSearch={setQuery} style={{ width: 280 }} />
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
      <Table<Employee>
        rowKey="id"
        loading={loading}
        dataSource={employees}
        pagination={{ pageSize: 20 }}
        columns={[
          { title: "Mã NV", dataIndex: "employeeCode", width: 120 },
          { title: "Họ và tên", dataIndex: "fullName" },
          { title: "Điện thoại", dataIndex: "phone" },
          {
            title: "Ngày vào làm",
            dataIndex: "hireDate",
            render: (v: string | null) => (v ? dayjs(v).format("DD/MM/YYYY") : "—"),
          },
          {
            title: "Mức công ngày hiện tại",
            align: "right",
            render: (_, e) => (e.currentPayRate ? formatMoney(e.currentPayRate.dailyRate) : "Chưa thiết lập"),
          },
          {
            title: "Tăng ca ngày thường / giờ",
            align: "right",
            render: (_, e) => (e.currentPayRate ? formatMoney(e.currentPayRate.overtimeWeekdayRate) : "—"),
          },
          {
            title: "Trạng thái",
            render: (_, e) =>
              e.active ? <Tag color="green">Đang làm việc</Tag> : <Tag color="default">Ngừng làm việc</Tag>,
          },
          {
            title: "Thao tác",
            render: (_, e) => (
              <Space>
                <a onClick={() => setEditing(e)}>Sửa</a>
                <a onClick={() => setRatesFor(e)}>Mức công</a>
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
  employeeCode: string;
  fullName: string;
  phone?: string;
  address?: string;
  hireDate?: Dayjs | null;
  terminationDate?: Dayjs | null;
  active: boolean;
  notes?: string;
  initialDailyRate?: number;
  initialOvertimeWeekdayRate?: number;
  initialOvertimeRestdayRate?: number;
  initialOvertimeHolidayRate?: number;
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
      title={employee ? `Sửa hồ sơ nhân viên ${employee.employeeCode}` : "Thêm nhân viên mới"}
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
                employeeCode: employee.employeeCode,
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
                initialOvertimeRestdayRate: 70000,
                initialOvertimeHolidayRate: 90000,
              }
        }
        onFinish={async (values) => {
          setSaving(true);
          try {
            await saveEmployee(employee?.id, {
              employeeCode: values.employeeCode,
              fullName: values.fullName,
              phone: values.phone,
              address: values.address,
              hireDate: values.hireDate ? values.hireDate.format("YYYY-MM-DD") : undefined,
              terminationDate: values.terminationDate ? values.terminationDate.format("YYYY-MM-DD") : undefined,
              active: values.active,
              notes: values.notes,
              initialDailyRate: employee ? undefined : values.initialDailyRate,
              initialOvertimeWeekdayRate: employee ? undefined : values.initialOvertimeWeekdayRate,
              initialOvertimeRestdayRate: employee ? undefined : values.initialOvertimeRestdayRate,
              initialOvertimeHolidayRate: employee ? undefined : values.initialOvertimeHolidayRate,
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
        <Space align="start" style={{ display: "flex" }}>
          <Form.Item name="employeeCode" label="Mã nhân viên" rules={[{ required: true, message: "Nhập mã nhân viên" }]}>
            <Input style={{ width: 180 }} placeholder="NV001" />
          </Form.Item>
          <Form.Item
            name="fullName"
            label="Họ và tên"
            rules={requiredNameRules("Nhập họ và tên", 150)}
            style={{ flex: 1 }}
          >
            <Input style={{ width: 420 }} maxLength={150} />
          </Form.Item>
        </Space>
        <Space align="start" style={{ display: "flex" }}>
          <Form.Item name="phone" label="Số điện thoại">
            <Input style={{ width: 200 }} />
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
            <Form.Item name="initialOvertimeWeekdayRate" label="Tăng ca thường / giờ">
              <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="initialOvertimeRestdayRate" label="Tăng ca CN / giờ">
              <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="initialOvertimeHolidayRate" label="Tăng ca lễ / giờ">
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
  const [form] = Form.useForm<{
    effectiveFrom: Dayjs;
    dailyRate: number;
    standardHoursPerDay: number;
    overtimeWeekdayRate: number;
    overtimeRestdayRate: number;
    overtimeHolidayRate: number;
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
    <Modal open title={`Lịch sử mức công — ${employee.employeeCode} (${employee.fullName})`} footer={null} onCancel={onClose} width={780}>
      <p style={{ marginTop: 0 }}>
        Mức công mới áp dụng từ ngày hiệu lực được chọn. Kỳ công đã chốt giữ nguyên mức công cũ, không tự thay đổi.
      </p>
      <Table<PayRate>
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={rates}
        columns={[
          { title: "Từ ngày", dataIndex: "effectiveFrom", render: (v: string) => dayjs(v).format("DD/MM/YYYY") },
          {
            title: "Đến ngày",
            dataIndex: "effectiveTo",
            render: (v: string | null) => (v ? dayjs(v).format("DD/MM/YYYY") : <Tag color="green">Đang áp dụng</Tag>),
          },
          { title: "Công / ngày", dataIndex: "dailyRate", align: "right", render: (v: number) => formatMoney(v) },
          { title: "Giờ chuẩn", dataIndex: "standardHoursPerDay", align: "right" },
          { title: "TC thường / giờ", dataIndex: "overtimeWeekdayRate", align: "right", render: (v: number) => formatMoney(v) },
          { title: "TC nghỉ / giờ", dataIndex: "overtimeRestdayRate", align: "right", render: (v: number) => formatMoney(v) },
          { title: "TC lễ / giờ", dataIndex: "overtimeHolidayRate", align: "right", render: (v: number) => formatMoney(v) },
        ]}
      />
      <Form
        form={form}
        layout="vertical"
        style={{ marginTop: 16 }}
        initialValues={{
          effectiveFrom: dayjs(),
          dailyRate: employee.currentPayRate?.dailyRate ?? 350000,
          standardHoursPerDay: employee.currentPayRate?.standardHoursPerDay ?? 8,
          overtimeWeekdayRate: employee.currentPayRate?.overtimeWeekdayRate ?? 50000,
          overtimeRestdayRate: employee.currentPayRate?.overtimeRestdayRate ?? 70000,
          overtimeHolidayRate: employee.currentPayRate?.overtimeHolidayRate ?? 90000,
        }}
        onFinish={async (values) => {
          try {
            await addPayRate(employee.id, {
              effectiveFrom: values.effectiveFrom.format("YYYY-MM-DD"),
              dailyRate: values.dailyRate,
              standardHoursPerDay: values.standardHoursPerDay,
              overtimeWeekdayRate: values.overtimeWeekdayRate,
              overtimeRestdayRate: values.overtimeRestdayRate,
              overtimeHolidayRate: values.overtimeHolidayRate,
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
          <Form.Item name="dailyRate" label="Mức công / ngày" rules={[{ required: true }]}>
            <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="standardHoursPerDay" label="Giờ chuẩn" rules={[{ required: true }]}>
            <InputNumber min={1} max={24} style={{ width: 100 }} />
          </Form.Item>
          <Form.Item name="overtimeWeekdayRate" label="TC thường / giờ">
            <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="overtimeRestdayRate" label="TC ngày nghỉ / giờ">
            <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="overtimeHolidayRate" label="TC lễ / giờ">
            <InputNumber {...vndInputNumberProps} style={{ width: 180 }} />
          </Form.Item>
        </Space>
        <Button type="primary" htmlType="submit">Thiết lập mức công mới</Button>
      </Form>
    </Modal>
  );
}
