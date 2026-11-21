import { Alert, Button, Card, Descriptions, Form, Input, InputNumber, Modal, Select, Space, Tag, Typography, message } from "antd";
import { PagedTable } from "../../components/PagedTable";
import dayjs from "dayjs";
import { useCallback, useEffect, useState } from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { authStore } from "../auth/auth-store";
import { toApiError } from "../../services/error-handler";
import {
  createManualBackup,
  getBackupStatus,
  listBackups,
  restoreBackup,
  updateBackupSettings,
  getCloudSettings,
  saveCloudSettings,
  testCloudConnection,
  getCloudStatus,
  listCloudObjects,
  syncCloud,
  downloadCloud,
  verifyCloud,
  verifyLocal,
  prepareCloud,
  prepareLocal,
  getRecoveryKey,
  importRecoveryKey,
  type CloudSettings,
  type CloudInput,
  type CloudStatus,
  type CloudObject,
  type BackupLog,
  type BackupSettings,
  type BackupStatus,
  type PreparedBackup,
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
  const [loading, setLoading] = useState(true);
  const [backingUp, setBackingUp] = useState(false);
  const [restoring, setRestoring] = useState<BackupLog | null>(null);
  const [editingSettings, setEditingSettings] = useState(false);
  const [cloud, setCloud] = useState<CloudSettings | null>(null);
  const [editingCloud, setEditingCloud] = useState(false);
  const [testingCloud, setTestingCloud] = useState(false);
  const [cloudStatus, setCloudStatus] = useState<CloudStatus | null>(null);
  const [cloudObjects, setCloudObjects] = useState<CloudObject[]>([]);
  const [syncingCloud, setSyncingCloud] = useState(false);
  const [recoveryKey, setRecoveryKey] = useState<string | null>(null);
  const [verifyingObject, setVerifyingObject] = useState<CloudObject | null>(null);
  const [prepareMode, setPrepareMode] = useState(false);
  const [enteredRecoveryKey, setEnteredRecoveryKey] = useState("");
  const [databaseUsername, setDatabaseUsername] = useState("");
  const [databasePassword, setDatabasePassword] = useState("");
  const [preparingLocal, setPreparingLocal] = useState<BackupLog | null>(null);
  const [importingRecoveryKey, setImportingRecoveryKey] = useState(false);
  const [checkingBackup, setCheckingBackup] = useState(false);
  const [prepared, setPrepared] = useState<PreparedBackup | null>(null);
  const [preparedMysql, setPreparedMysql] = useState<{ username: string; password: string } | null>(null);
  const [preparedRecoveryKey, setPreparedRecoveryKey] = useState("");
  const [activeDatabase, setActiveDatabase] = useState<string | null>(null);
  const [switchingDatabase, setSwitchingDatabase] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [s, l, c, cs] = await Promise.all([getBackupStatus(), listBackups(), getCloudSettings(), getCloudStatus()]);
      setStatus(s);
      setLogs(l);
      setCloud(c);
      setCloudStatus(cs);
      setCloudObjects(cs.configured ? await listCloudObjects().catch(() => []) : []);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    if (isTauri()) void invoke<string | null>("recovery_status").then(setActiveDatabase)
      .catch(error => message.error(String(error)));
  }, [load]);

  const showSwitched = (databaseName: string, firstRunCredentials: string | null) => {
    setActiveDatabase(databaseName === "aluminum_app" ? null : databaseName);
    setPrepared(null);
    setPreparedMysql(null);
    setPreparedRecoveryKey("");
    Modal.success({
      title: `Đã chuyển sang ${databaseName}`,
      width: 600,
      content: <>
        <p>Đăng nhập lại để xem dữ liệu vừa phục hồi. Database trước vẫn được giữ để quay lại.</p>
        {firstRunCredentials ? <><p>Thông tin đăng nhập lần đầu trên máy mới:</p>
          <Input.TextArea readOnly value={firstRunCredentials} autoSize={{ minRows: 4 }} />
          <p>Hãy cất thông tin này, đăng nhập và đổi mật khẩu ngay.</p></> :
          <p>Dùng thông tin đăng nhập đã lưu của database này. Nếu đây là lần đầu mở trên máy mới mà chưa thấy mật khẩu, xem thư mục <code>runtime/credentials</code> cạnh hồ sơ phục hồi.</p>}
      </>,
      onOk: () => { authStore.clear(); window.location.assign("/dang-nhap"); },
    });
  };

  return (
    <Card
      title="Sao lưu & Phục hồi dữ liệu"
      extra={
        <Space>
          <Button onClick={async () => {
            try { setRecoveryKey(await getRecoveryKey()); }
            catch (error) { message.error(toApiError(error).message); }
          }}>Khóa phục hồi</Button>
          <Button onClick={() => setImportingRecoveryKey(true)}>Nhập khóa máy cũ</Button>
          <Button onClick={() => setEditingCloud(true)}>Cấu hình Cloudflare R2</Button>
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
      {prepared && <Alert type="success" showIcon style={{ marginBottom: 16 }}
        message="Đã kiểm tra và dựng bản phục hồi riêng"
        description={<>
          <p>Database đang làm việc được giữ nguyên. Đã chạy {prepared.sqlStatements} câu lệnh và kiểm tra {prepared.imagesVerified} ảnh.</p>
          <Typography.Paragraph copyable code>{prepared.databaseName}</Typography.Paragraph>
          <p>Thư mục ảnh đã phục hồi:</p>
          <Typography.Paragraph copyable code style={{ overflowWrap: "anywhere" }}>{prepared.imageDirectory}</Typography.Paragraph>
          <p>Hồ sơ đã xác minh dùng để khởi động bản phục hồi:</p>
          <Typography.Paragraph copyable code style={{ overflowWrap: "anywhere" }}>{prepared.profilePath}</Typography.Paragraph>
          <p>Đây là database kiểm tra, chưa phải dữ liệu đang sử dụng. Sau khi đối chiếu, chọn nút dưới đây để ứng dụng chuyển dữ liệu và kiểm tra lại.</p>
          {isTauri() && preparedMysql ? <Button type="primary" loading={switchingDatabase}
            onClick={() => Modal.confirm({
              title: `Dùng dữ liệu ${prepared.databaseName}?`,
              content: "Ứng dụng sẽ sao lưu, dừng backend hiện tại rồi mở database đã dựng. Nếu không mở được, ứng dụng sẽ thử quay lại database cũ.",
              okText: "Dùng dữ liệu này", cancelText: "Hủy",
              onOk: async () => {
                setSwitchingDatabase(true);
                try {
                  const recoveryKey = preparedRecoveryKey || await getRecoveryKey();
                  const result = await invoke<{ databaseName: string; firstRunCredentials: string | null }>("activate_recovery", {
                    profilePath: prepared.profilePath,
                    databaseUsername: preparedMysql.username,
                    databasePassword: preparedMysql.password,
                    recoveryKey,
                  });
                  showSwitched(result.databaseName, result.firstRunCredentials);
                } catch (error) { message.error(String(error)); }
                finally { setSwitchingDatabase(false); }
              },
            })}>Dùng dữ liệu này</Button> :
            <p>Nút chuyển dữ liệu chỉ có trong ứng dụng desktop.</p>}
        </>} />}
      {activeDatabase && isTauri() ? <Alert type="info" showIcon style={{ marginBottom: 16 }}
        message={`Đang dùng database phục hồi ${activeDatabase}`}
        description={<Button loading={switchingDatabase} onClick={() => Modal.confirm({
          title: "Quay lại database trước?", content: "Ứng dụng sẽ sao lưu trạng thái hiện tại rồi chuyển về database trước.",
          okText: "Quay lại", cancelText: "Hủy", onOk: async () => {
            setSwitchingDatabase(true);
            try { const result = await invoke<{ databaseName: string; firstRunCredentials: string | null }>("rollback_recovery");
              showSwitched(result.databaseName, result.firstRunCredentials); }
            catch (error) { message.error(String(error)); }
            finally { setSwitchingDatabase(false); }
          },
        })}>Quay lại database trước</Button>} /> : null}
      <Card size="small" title="Nếu mất máy: lấy dữ liệu từ R2 về máy mới" style={{ marginBottom: 16 }}>
        <ol style={{ margin: 0, paddingInlineStart: 22 }}>
          <li>Cất khóa phục hồi bên ngoài máy. Khóa này khác mật khẩu đăng nhập và khóa truy cập R2.</li>
          <li>Trên máy mới, cài Java 17, MySQL 8 và ứng dụng; đăng nhập Admin mới. Tạo khóa truy cập R2 mới trong tài khoản Cloudflare của bạn, rồi nhập endpoint, bucket và khóa vào Cấu hình Cloudflare R2.</li>
          <li>Chọn bản trong danh sách R2, bấm Dựng DB từ R2. Nhập khóa phục hồi cũ và tài khoản MySQL có quyền tạo database. Chờ kết quả DB, ảnh và hồ sơ xác minh hiện trên trang này.</li>
          <li>Đối chiếu phiếu, công nợ, tồn kho và ảnh. Bấm Dùng dữ liệu này ngay trong ứng dụng desktop. Đăng nhập lại bằng tài khoản mới, lưu khóa phục hồi mới rồi cấu hình lại R2 và lịch sao lưu.</li>
        </ol>
        <Typography.Paragraph type="secondary" style={{ margin: "10px 0 0" }}>Có thể tải tệp .ndbak trực tiếp từ Cloudflare Dashboard → R2 → bucket → backups khi cần. Tệp vẫn mã hóa; chỉ tải về chưa khôi phục dữ liệu. Bản nháp chỉ lưu trong trình duyệt không nằm trong backup.</Typography.Paragraph>
      </Card>
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
          <Descriptions.Item label="Tần suất sao lưu">Mỗi {status.frequencyHours} giờ</Descriptions.Item>
          <Descriptions.Item label="Dự kiến lần tiếp theo">{dayjs(status.nextBackupAt).format("DD/MM/YYYY HH:mm")}</Descriptions.Item>
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

      <Alert type={cloud === null || cloud.configured ? "info" : "warning"} showIcon style={{ marginBottom: 16 }}
        message={cloud === null ? (loading ? "Đang kiểm tra cấu hình Cloudflare R2" : "Không tải được cấu hình Cloudflare R2")
          : cloud.configured ? `R2 đã lưu cấu hình: ${cloud.bucket}` : "Chưa cấu hình Cloudflare R2"}
        description={<Space wrap>
          <span>Bản sao được mã hóa và kiểm tra toàn vẹn trước khi tải lên R2.</span>
          {cloud?.configured ? <Button loading={testingCloud} onClick={async () => {
            setTestingCloud(true);
            try { await testCloudConnection(); message.success("Kết nối R2 thành công"); }
            catch (error) { message.error(toApiError(error).message); }
            finally { setTestingCloud(false); }
          }}>Kiểm tra kết nối</Button> : null}
        </Space>} />

      {cloudStatus ? <Descriptions bordered size="small" column={{ xs: 1, md: 2 }} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Bản chờ đồng bộ">{cloudStatus.pending}</Descriptions.Item>
        <Descriptions.Item label="Bản cloud gần nhất">{cloudStatus.latestKey || "Chưa có"}</Descriptions.Item>
        {cloudStatus.lastError ? <Descriptions.Item label="Đồng bộ" span={2}>{cloudStatus.lastError}</Descriptions.Item> : null}
      </Descriptions> : null}
      {cloud?.configured ? <Button loading={syncingCloud} style={{ marginBottom: 16 }} onClick={async () => {
        setSyncingCloud(true);
        try { const count = await syncCloud(); message.success(`Đã tải ${count} bản sao lưu lên R2`); await load(); }
        catch (error) { message.error(toApiError(error).message); }
        finally { setSyncingCloud(false); }
      }}>Đồng bộ R2 ngay</Button> : null}

      {cloudObjects.length ? <PagedTable<CloudObject> rowKey="key" dataSource={cloudObjects}
        columns={[
          { title: "Bản trên R2", dataIndex: "key" },
          { title: "Tải lên", dataIndex: "uploadedAt", render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm") },
          { title: "Kích thước", dataIndex: "size", render: (v: number) => formatBytes(v) },
          { title: "Thao tác", render: (_, item) => <Space wrap>
            <Button onClick={async () => {
              try { const path = await downloadCloud(item.key); message.success(`Đã tải về: ${path}`); }
              catch (error) { message.error(toApiError(error).message); }
            }}>Tải về máy</Button>
            <Button onClick={() => { setPrepareMode(false); setVerifyingObject(item); }}>Kiểm tra</Button>
            <Button onClick={() => { setPrepareMode(true); setVerifyingObject(item); }}>Dựng DB từ R2</Button>
          </Space> },
        ]} /> : null}

      <Alert type="warning" showIcon style={{ marginBottom: 16 }}
        message="Khôi phục trực tiếp đang tạm khóa"
        description="Nút khôi phục trực tiếp có thể ghi đè dữ liệu hiện tại, nên chưa được bật. Nếu mất máy, chọn bản trên R2 và dùng 'Dựng DB từ R2' để kiểm tra trên database riêng trước. Việc tạo bản backup vẫn hoạt động bình thường." />

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
            render: (_, log) => log.status === "SUCCESS" && log.backupPath?.endsWith(".ndbak")
              ? <Space><Button onClick={async () => {
                  try { await verifyLocal(log.id); message.success("Bản sao lưu hợp lệ"); }
                  catch (error) { message.error(toApiError(error).message); }
                }}>Kiểm tra</Button><Button onClick={() => setPreparingLocal(log)}>Dựng DB từ máy này</Button></Space> : <span>Tạm khóa</span>,
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
            frequencyHours: status.frequencyHours,
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
      {editingCloud ? <CloudSettingsModal initial={cloud} onClose={() => setEditingCloud(false)}
        onSaved={() => { setEditingCloud(false); void load(); }} /> : null}
      <Modal open={!!recoveryKey} title="Khóa phục hồi khi mất máy" footer={<Button onClick={() => setRecoveryKey(null)}>Đã lưu an toàn</Button>}
        onCancel={() => setRecoveryKey(null)}>
        <Alert type="warning" showIcon style={{ marginBottom: 12 }}
          message="Lưu khóa này ở nơi riêng ngoài máy tính. Mất cả máy và khóa sẽ không giải mã được bản sao trên R2." />
        <Input.TextArea value={recoveryKey || ""} readOnly autoSize={{ minRows: 2 }} />
      </Modal>
      <Modal open={!!verifyingObject} confirmLoading={checkingBackup} closable={!checkingBackup} maskClosable={!checkingBackup}
        cancelButtonProps={{disabled: checkingBackup}} title={prepareMode ? "Dựng database khôi phục thử" : "Kiểm tra bản sao từ R2"}
        okText={prepareMode ? "Dựng DB riêng" : "Tải và kiểm tra"}
        onCancel={() => { setVerifyingObject(null); setEnteredRecoveryKey(""); setDatabasePassword(""); }}
        onOk={async () => {
          if (!verifyingObject) return;
          setCheckingBackup(true);
          try {
            if (prepareMode) {
              const result = await prepareCloud(verifyingObject.key, enteredRecoveryKey,
                databaseUsername, databasePassword);
              setPrepared(result);
              setPreparedMysql({ username: databaseUsername, password: databasePassword });
              setPreparedRecoveryKey(enteredRecoveryKey);
              message.success(`Đã dựng ${result.databaseName}: ${result.sqlStatements} câu lệnh, ${result.imagesVerified} ảnh`);
            } else {
              await verifyCloud(verifyingObject.key, enteredRecoveryKey);
              message.success("Đã tải, giải mã và kiểm tra toàn vẹn");
            }
            setVerifyingObject(null); setEnteredRecoveryKey(""); setDatabasePassword("");
          } catch (error) { message.error(toApiError(error).message); }
          finally { setCheckingBackup(false); }
        }}>
        <p>Nhập khóa phục hồi đã cất ngoài máy. Trên máy đang dùng, có thể để trống. Database đang làm việc không bị thay thế.</p>
        <Input.Password value={enteredRecoveryKey} onChange={event => setEnteredRecoveryKey(event.target.value)}
          placeholder="Khóa phục hồi" autoComplete="off" />
        {prepareMode ? <MysqlRestoreFields username={databaseUsername} password={databasePassword}
          onUsername={setDatabaseUsername} onPassword={setDatabasePassword} /> : null}
      </Modal>
      <Modal open={!!preparingLocal} title="Dựng database khôi phục thử" okText="Dựng DB riêng"
        confirmLoading={checkingBackup} closable={!checkingBackup} maskClosable={!checkingBackup} cancelButtonProps={{ disabled: checkingBackup }}
        onCancel={() => { setPreparingLocal(null); setDatabasePassword(""); }}
        onOk={async () => {
          if (!preparingLocal) return;
          setCheckingBackup(true);
          try { const result = await prepareLocal(preparingLocal.id, databaseUsername, databasePassword);
            setPrepared(result);
            setPreparedMysql({ username: databaseUsername, password: databasePassword });
            setPreparedRecoveryKey("");
            message.success(`Đã dựng ${result.databaseName}: ${result.sqlStatements} câu lệnh, ${result.imagesVerified} ảnh`);
            setPreparingLocal(null); setDatabasePassword(""); }
          catch (error) { message.error(toApiError(error).message); }
          finally { setCheckingBackup(false); }
        }}>
        <p>Nhập tài khoản MySQL có quyền tạo database thử. Mật khẩu chỉ dùng cho thao tác này và không được lưu.</p>
        <MysqlRestoreFields username={databaseUsername} password={databasePassword}
          onUsername={setDatabaseUsername} onPassword={setDatabasePassword} />
      </Modal>
      <Modal open={importingRecoveryKey} title="Nhập khóa phục hồi từ máy cũ" okText="Lưu khóa"
        onCancel={() => { setImportingRecoveryKey(false); setEnteredRecoveryKey(""); }}
        onOk={async () => {
          try { await importRecoveryKey(enteredRecoveryKey); message.success("Đã lưu khóa phục hồi");
            setImportingRecoveryKey(false); setEnteredRecoveryKey(""); }
          catch (error) { message.error(toApiError(error).message); }
        }}>
        <Alert type="info" showIcon style={{ marginBottom: 12 }}
          message="Chỉ nhập trên máy mới. Khóa đang dùng trên máy hiện tại không bị ghi đè." />
        <Input.Password value={enteredRecoveryKey} onChange={event => setEnteredRecoveryKey(event.target.value)}
          placeholder="Khóa đã lưu ở nơi riêng" autoComplete="off" />
      </Modal>
    </Card>
  );
}

function MysqlRestoreFields({ username, password, onUsername, onPassword }: {
  username: string; password: string; onUsername: (value: string) => void; onPassword: (value: string) => void;
}) {
  return <Space direction="vertical" style={{ width: "100%", marginTop: 12 }}>
    <Input value={username} onChange={event => onUsername(event.target.value)}
      placeholder="Tài khoản MySQL có quyền CREATE DATABASE" autoComplete="off" />
    <Input.Password value={password} onChange={event => onPassword(event.target.value)}
      placeholder="Mật khẩu MySQL" autoComplete="new-password" />
  </Space>;
}

function CloudSettingsModal({ initial, onClose, onSaved }: {
  initial: CloudSettings | null; onClose: () => void; onSaved: () => void;
}) {
  const [form] = Form.useForm<CloudInput>();
  const [saving, setSaving] = useState(false);
  return <Modal open title="Kết nối Cloudflare R2" okText="Lưu cấu hình" cancelText="Hủy"
    confirmLoading={saving} onCancel={onClose} onOk={() => form.submit()}>
    <Alert type="warning" showIcon style={{ marginBottom: 12 }}
      message="Khóa trong ảnh đã gửi trước đây cần được tạo lại trên Cloudflare trước khi dùng cho dữ liệu thật." />
    <Form form={form} layout="vertical" initialValues={{
      endpoint: initial?.endpoint || "", bucket: initial?.bucket || "nhom-duc-backup",
      accessKeyId: initial?.accessKeyId || "", secretAccessKey: "",
    }} onFinish={async (values) => {
      setSaving(true);
      try { await saveCloudSettings(values); message.success("Đã lưu cấu hình R2"); onSaved(); }
      catch (error) { message.error(toApiError(error).message); }
      finally { setSaving(false); }
    }}>
      <Form.Item name="endpoint" label="S3 API endpoint" rules={[{ required: true }]}>
        <Input placeholder="https://<account-id>.r2.cloudflarestorage.com" />
      </Form.Item>
      <Form.Item name="bucket" label="Bucket" rules={[{ required: true }]}><Input /></Form.Item>
      <Form.Item name="accessKeyId" label="Access Key ID" rules={[{ required: true }]}><Input autoComplete="off" /></Form.Item>
      <Form.Item name="secretAccessKey" label="Secret Access Key"
        extra={initial?.configured ? "Để trống nếu giữ khóa đã lưu." : "Khóa chỉ hiển thị một lần trên Cloudflare."}>
        <Input.Password autoComplete="new-password" />
      </Form.Item>
    </Form>
  </Modal>;
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
        <Form.Item name="backupTime" hidden><Input /></Form.Item>
        <Form.Item name="frequencyHours" label="Tần suất sao lưu (giờ)" rules={[{ required: true }]}>
          <Select options={[1, 2, 4, 8, 24].map(value => ({ value, label: `Mỗi ${value} giờ` }))} />
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
