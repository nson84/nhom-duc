import { DeleteOutlined, EditOutlined, PlusOutlined, StarFilled, StarOutlined, UploadOutlined } from "@ant-design/icons";
import { Alert, Card, Checkbox, Form, Image, Input, Modal, Select, Space, Upload, message } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import { AppActionButton } from "../../components/AppButtons";
import { PagedTable } from "../../components/PagedTable";
import { toApiError } from "../../services/error-handler";
import { bankName, VIETQR_BANKS } from "../../utils/vietqr";
import {
  createBankAccount,
  deleteBankAccount,
  listBankAccounts,
  setDefaultBankAccount,
  updateBankAccount,
  type BankAccount,
} from "./bank-accounts-api";

type FormValues = {
  label: string;
  bankBin: string;
  accountNo: string;
  accountName?: string;
  imagePath?: string | null;
  isDefault?: boolean;
};

export function BankAccountsPage() {
  const [rows, setRows] = useState<BankAccount[]>([]);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<BankAccount | "new" | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listBankAccounts());
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
      className="app-fill-card"
      title="Tài khoản QR nhận tiền"
      extra={
        <AppActionButton tone="create" icon={<PlusOutlined />} onClick={() => setEditing("new")}>
          Thêm tài khoản QR
        </AppActionButton>
      }
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Ảnh QR dùng để bạn nhận ra đúng tài khoản trong danh sách này."
        description="Bản in danh sách nợ chỉ có một mã QR, tạo từ số tài khoản đã khai báo và gắn sẵn số tiền cùng nội dung chuyển khoản. Ảnh thẻ không được in ra, để khách không quét nhầm mã không có số nợ."
      />
      <PagedTable<BankAccount>
        rowKey="id"
        loading={loading}
        dataSource={rows}
        columns={[
          {
            title: "Ảnh QR",
            dataIndex: "imagePath",
            width: 88,
            align: "center",
            render: (src: string | null) =>
              src ? (
                <Image src={src} width={56} height={56} style={{ objectFit: "cover", borderRadius: 6 }} preview={{ mask: "Xem" }} />
              ) : (
                <span style={{ color: "var(--color-text-secondary)" }}>—</span>
              ),
          },
          { title: "Tên gợi nhớ", dataIndex: "label", align: "center" },
          {
            title: "Ngân hàng",
            dataIndex: "bankBin",
            align: "center",
            render: (bin: string) => bankName(bin) || bin,
          },
          { title: "Số tài khoản", dataIndex: "accountNo", align: "center" },
          { title: "Chủ tài khoản", dataIndex: "accountName", align: "center", render: (v: string) => v || "—" },
          {
            title: "Mặc định",
            dataIndex: "isDefault",
            width: 100,
            align: "center",
            render: (v: boolean) => (v ? <StarFilled style={{ color: "var(--color-warning)" }} /> : null),
          },
          {
            title: "Thao tác",
            align: "center",
            render: (_, row) => (
              <Space wrap>
                {!row.isDefault ? (
                  <AppActionButton
                    tone="secondary"
                    icon={<StarOutlined />}
                    onClick={() => {
                      void setDefaultBankAccount(row.id)
                        .then(() => {
                          message.success("Đã đặt làm mặc định");
                          void load();
                        })
                        .catch((error) => message.error(toApiError(error).message));
                    }}
                  >
                    Đặt mặc định
                  </AppActionButton>
                ) : null}
                <AppActionButton tone="edit" icon={<EditOutlined />} onClick={() => setEditing(row)}>
                  Sửa
                </AppActionButton>
                <AppActionButton
                  tone="delete"
                  icon={<DeleteOutlined />}
                  onClick={() => {
                    Modal.confirm({
                      title: "Xóa tài khoản QR này?",
                      content: `${row.label} — ${row.accountNo}`,
                      okText: "Xóa",
                      cancelText: "Quay lại",
                      onOk: () =>
                        deleteBankAccount(row.id)
                          .then(() => {
                            message.success("Đã xóa");
                            void load();
                          })
                          .catch((error) => message.error(toApiError(error).message)),
                    });
                  }}
                >
                  Xóa
                </AppActionButton>
              </Space>
            ),
          },
        ]}
      />
      {editing ? (
        <BankAccountModal
          account={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void load();
          }}
        />
      ) : null}
    </Card>
  );
}

function BankAccountModal({
  account,
  onClose,
  onSaved,
}: {
  account: BankAccount | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);
  const imageValue = Form.useWatch("imagePath", form);
  const imageBeforeUploadRef = useRef<string | null | undefined>(undefined);
  const uploadAcceptedRef = useRef(false);

  return (
    <Modal
      open
      title={account ? "Sửa tài khoản QR" : "Thêm tài khoản QR"}
      onCancel={onClose}
      okText="Lưu"
      cancelText="Đóng"
      confirmLoading={saving}
      width={560}
      onOk={() => form.submit()}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={
          account
            ? {
                label: account.label,
                bankBin: account.bankBin,
                accountNo: account.accountNo,
                accountName: account.accountName,
                imagePath: account.imagePath,
                isDefault: account.isDefault,
              }
            : { isDefault: false }
        }
        onFinish={async (values) => {
          setSaving(true);
          try {
            const payload = {
              label: values.label,
              bankBin: values.bankBin,
              accountNo: values.accountNo,
              accountName: values.accountName,
              imagePath: values.imagePath ?? null,
              isDefault: values.isDefault,
            };
            if (account) {
              await updateBankAccount(account.id, payload);
            } else {
              await createBankAccount(payload);
            }
            message.success("Đã lưu tài khoản QR");
            onSaved();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item name="label" label="Tên gợi nhớ" rules={[{ required: true, message: "Nhập tên gợi nhớ" }]}>
          <Input placeholder="VD: MB Hùng / Vietcombank cửa hàng" maxLength={100} />
        </Form.Item>
        <Form.Item name="bankBin" label="Ngân hàng" rules={[{ required: true, message: "Chọn ngân hàng" }]}>
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="Chọn ngân hàng"
            options={VIETQR_BANKS.map((b) => ({ value: b.bin, label: b.name }))}
          />
        </Form.Item>
        <Form.Item
          name="accountNo"
          label="Số tài khoản"
          rules={[
            { required: true, message: "Nhập số tài khoản" },
            { pattern: /^[0-9A-Za-z]{4,19}$/, message: "Số tài khoản chỉ gồm chữ và số, 4 đến 19 ký tự" },
          ]}
        >
          <Input inputMode="numeric" maxLength={19} placeholder="8881974116688" />
        </Form.Item>
        <Form.Item name="accountName" label="Tên chủ tài khoản">
          <Input maxLength={100} placeholder="NGUYEN XUAN HAI" />
        </Form.Item>
        <Form.Item
          label="Ảnh thẻ QR ngân hàng"
          extra="Ảnh này chỉ hiện trong danh sách tài khoản để bạn nhận ra đúng thẻ. Bản in cho khách chỉ có một mã QR gắn số tiền nợ."
        >
          <Space align="start" wrap>
            {imageValue ? (
              <Image
                src={imageValue}
                alt="Ảnh QR ngân hàng"
                width={120}
                style={{ borderRadius: 8, border: "1px solid var(--color-border)", objectFit: "contain" }}
                preview={{ mask: "Phóng to" }}
              />
            ) : null}
            <Upload
              accept="image/*"
              showUploadList={false}
              beforeUpload={(file) => {
                imageBeforeUploadRef.current = form.getFieldValue("imagePath");
                uploadAcceptedRef.current = false;
                if (file.size > 1_500_000) {
                  message.error("Ảnh quá lớn (tối đa khoảng 1,5 MB)");
                  return Upload.LIST_IGNORE;
                }
                return true;
              }}
              customRequest={({ file, onSuccess, onError }) => {
                const raw = file as File;
                const reader = new FileReader();
                reader.onload = () => {
                  uploadAcceptedRef.current = true;
                  form.setFieldValue("imagePath", String(reader.result));
                  onSuccess?.(reader.result);
                };
                reader.onerror = () => {
                  uploadAcceptedRef.current = false;
                  form.setFieldValue("imagePath", imageBeforeUploadRef.current);
                  onError?.(new Error("Không đọc được ảnh"));
                };
                reader.readAsDataURL(raw);
              }}
            >
              <AppActionButton tone="secondary" icon={<UploadOutlined />}>
                {imageValue ? "Đổi ảnh" : "Tải ảnh QR"}
              </AppActionButton>
            </Upload>
            {imageValue ? (
              <AppActionButton
                tone="cancel"
                icon={<DeleteOutlined />}
                onClick={() => form.setFieldValue("imagePath", null)}
              >
                Gỡ ảnh
              </AppActionButton>
            ) : null}
          </Space>
        </Form.Item>
        <Form.Item name="imagePath" hidden>
          <Input />
        </Form.Item>
        <Form.Item name="isDefault" valuePropName="checked">
          <Checkbox>Đặt làm tài khoản mặc định khi in danh sách nợ</Checkbox>
        </Form.Item>
      </Form>
    </Modal>
  );
}
