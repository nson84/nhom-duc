import { Alert, Button, Card, Descriptions, Form, Input, InputNumber, Modal, Space, Tag, message } from "antd";
import { PagedTable } from "../../components/PagedTable";
import dayjs from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { toApiError } from "../../services/error-handler";
import {
  createManualBackup,
  getBackupStatus,
  listBackups,
  restoreBackup,
  updateBackupSettings,
  type BackupLog,
  type BackupSettings,
  type BackupStatus,
} from "./backup-api";

function formatBytes(bytes: number | null): string {
  if (!bytes || bytes <= 0) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  return `${(kb / 1024).toFixed(2)} MB`;
}

export function BackupsPage() {
  const [status, setStatus] = useState<BackupStatus | null>(null);
  const [logs, setLogs] = useState<BackupLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [backingUp, setBackingUp] = useState(false);
  const [restoring, setRestoring] = useState<BackupLog | null>(null);
  const [editingSettings, setEditingSettings] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [s, l] = await Promise.all([getBackupStatus(), listBackups()]);
      setStatus(s);
      setLogs(l);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Card
      title="Sao lưu & Phục hồi dữ liệu"
      extra={
        <Space>
          <Button onClick={() => setEditingSettings(true)}>Cấu hình lịch sao lưu</Button>
          <Button
            type="primary"
            loading={backingUp}
            onClick={async () => {
              setBackingUp(true);
              try {
                await createManualBackup();
                message.success("Đã tạo bản sao lưu dữ liệu thành công");
                await load();
              } catch (error) {
                message.error(toApiError(error).message);
              } finally {
                setBackingUp(false);
              }
            }}
          >
            Sao lưu ngay
          </Button>
        </Space>
      }
    >
      {status?.lastBackupFailed ? (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Cảnh báo sao lưu"
          description={status.warningMessage}
        />
      ) : null}

      {status ? (
        <Descriptions bordered size="small" column={{ xs: 1, md: 2 }} style={{ marginBottom: 16 }}>
          <Descriptions.Item label="Giờ sao lưu tự động hằng ngày">{status.backupTime}</Descriptions.Item>
          <Descriptions.Item label="Thời gian lưu giữ">{status.retentionDays} ngày</Descriptions.Item>
          <Descriptions.Item label="Thư mục lưu trữ" span={2}>
            <code>{status.effectiveDirectory}</code>
          </Descriptions.Item>
          <Descriptions.Item label="Lần sao lưu gần nhất" span={2}>
            {status.latestBackup
              ? `${dayjs(status.latestBackup.startedAt).format("DD/MM/YYYY HH:mm:ss")} — ${status.latestBackup.status}`
              : "Chưa có bản sao lưu nào"}
          </Descriptions.Item>
        </Descriptions>
      ) : null}

      <p style={{ color: "var(--color-text-secondary)", marginTop: 0 }}>
        Mỗi bản sao lưu đều được kiểm tra kích thước và mã băm toàn vẹn SHA-256. Trước khi phục hồi, hệ thống tự động tạo một bản sao lưu trạng thái hiện tại để phòng ngừa rủi ro.
      </p>

      <PagedTable<BackupLog>
        rowKey="id"
        loading={loading}
        dataSource={logs}
        columns={[
          {
            title: "Thời điểm sao lưu",
            dataIndex: "startedAt",
            width: 170,
            render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm:ss"),
          },
          {
            title: "Trạng thái",
            dataIndex: "status",
            width: 120,
            render: (v: BackupLog["status"]) =>
              v === "SUCCESS" ? (
                <Tag color="green">Thành công</Tag>
              ) : v === "FAILED" ? (
                <Tag color="red">Thất bại</Tag>
              ) : (
                <Tag color="gold">Đang chạy</Tag>
              ),
          },
          {
            title: "Kích thước",
            dataIndex: "fileSizeBytes",
            align: "right",
            width: 110,
            render: (v: number | null) => formatBytes(v),
          },
          {
            title: "SHA-256",
            dataIndex: "checksumSha256",
            width: 180,
            render: (v: string | null) => (v ? <code>{v.slice(0, 16)}…</code> : "—"),
          },
          {
            title: "Đường dẫn / Thông báo",
            render: (_, log) => log.errorMessage || log.backupPath || "—",
          },
          {
            title: "Thao tác",
            width: 120,
            render: (_, log) =>
              log.status === "SUCCESS" ? (
                <a onClick={() => setRestoring(log)}>Phục hồi</a>
              ) : null,
          },
        ]}
      />

      {restoring ? (
        <RestoreModal
          backup={restoring}
          onClose={() => setRestoring(null)}
          onRestored={() => {
            setRestoring(null);
            void load();
          }}
        />
      ) : null}

      {editingSettings && status ? (
        <BackupSettingsModal
          initial={{
            backupTime: status.backupTime,
            backupRetentionDays: status.retentionDays,
            backupDirectory: status.effectiveDirectory,
          }}
          onClose={() => setEditingSettings(false)}
          onSaved={() => {
            setEditingSettings(false);
            void load();
          }}
        />
      ) : null}
    </Card>
  );
}

function RestoreModal({
  backup,
  onClose,
  onRestored,
}: {
  backup: BackupLog;
  onClose: () => void;
  onRestored: () => void;
}) {
  const [form] = Form.useForm<{ confirmText: string; reason: string }>();
  const [saving, setSaving] = useState(false);

  return (
    <Modal
      open
      title="Xác nhận phục hồi dữ liệu"
      okText="Thực hiện phục hồi"
      okButtonProps={{ danger: true }}
      cancelText="Hủy"
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 12 }}
        message="Phục hồi sẽ ghi đè dữ liệu nghiệp vụ hiện tại bằng dữ liệu tại thời điểm sao lưu."
        description={`Bản sao lưu ngày ${dayjs(backup.startedAt).format("DD/MM/YYYY HH:mm:ss")}. Hệ thống sẽ tự động sao lưu trạng thái hiện tại trước khi ghi đè.`}
      />
      <Form
        form={form}
        layout="vertical"
        onFinish={async (values) => {
          setSaving(true);
          try {
            await restoreBackup(backup.id, values);
            message.success("Đã phục hồi dữ liệu thành công");
            onRestored();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item
          name="confirmText"
          label="Nhập chính xác chữ KHOI PHUC để xác nhận"
          rules={[{ required: true, message: "Nhập KHOI PHUC" }]}
        >
          <Input placeholder="KHOI PHUC" />
        </Form.Item>
        <Form.Item name="reason" label="Lý do phục hồi" rules={[{ required: true, message: "Nhập lý do phục hồi" }]}>
          <Input.TextArea rows={2} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function BackupSettingsModal({
  initial,
  onClose,
  onSaved,
}: {
  initial: BackupSettings;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<BackupSettings>();
  const [saving, setSaving] = useState(false);

  return (
    <Modal
      open
      title="Cấu hình lịch sao lưu tự động"
      okText="Lưu cấu hình"
      cancelText="Hủy"
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={initial}
        onFinish={async (values) => {
          setSaving(true);
          try {
            await updateBackupSettings(values);
            message.success("Đã lưu cấu hình lịch sao lưu");
            onSaved();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item name="backupTime" label="Giờ sao lưu hằng ngày (HH:mm)" rules={[{ required: true }]}>
          <Input placeholder="22:00" />
        </Form.Item>
        <Form.Item name="backupRetentionDays" label="Số ngày giữ bản sao lưu" rules={[{ required: true }]}>
          <InputNumber min={1} max={3650} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item
          name="backupDirectory"
          label="Thư mục lưu bản sao lưu"
          extra="Nên đặt ở ổ đĩa khác ổ C:, USB hoặc ổ cứng ngoài."
        >
          <Input placeholder="D:\SaoLuuNhom" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
