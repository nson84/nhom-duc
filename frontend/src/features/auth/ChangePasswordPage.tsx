import { Button, Card, Checkbox, Form, Input, Select, Space, Spin } from "antd";
import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../../services/error-handler";
import { shopStore } from "../shop/shop-store";
import { changePassword, getFirstRunStatus, setupFirstRunPassword } from "./auth-api";
import { authStore, useSession } from "./auth-store";
import { forgetLoginPassword, rememberLogin } from "./login-memory";
import { savePassword } from "./saved-password";

type AccountName = "admin" | "phanphoi" | "xuong";

const ACCOUNT_LABELS: Record<AccountName, string> = {
  admin: "admin — sao lưu và cài đặt",
  phanphoi: "phanphoi — phân phối",
  xuong: "xuong — xưởng",
};

const authenticatedSchema = z
  .object({
    currentPassword: z.string().min(1, "Nhập mật khẩu hiện tại"),
    newPassword: z.string().min(6, "Mật khẩu mới phải từ 6 ký tự"),
    confirmPassword: z.string().min(1, "Nhập lại mật khẩu mới"),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "Mật khẩu nhập lại chưa khớp",
  });

const firstRunSchema = z
  .object({
    username: z.enum(["admin", "phanphoi", "xuong"], { message: "Chọn tài khoản" }),
    newPassword: z.string().min(6, "Mật khẩu mới phải từ 6 ký tự"),
    confirmPassword: z.string().min(1, "Nhập lại mật khẩu mới"),
    applyToAllPending: z.boolean(),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "Mật khẩu nhập lại chưa khớp",
  });

type AuthenticatedFormValues = z.infer<typeof authenticatedSchema>;
type FirstRunFormValues = z.infer<typeof firstRunSchema>;

export function ChangePasswordPage() {
  const session = useSession();
  if (session?.mustChangePassword) {
    return <AuthenticatedChangePassword />;
  }
  return <FirstRunPasswordSetup />;
}

function AuthenticatedChangePassword() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const { control, handleSubmit, formState } = useForm<AuthenticatedFormValues>({
    resolver: zodResolver(authenticatedSchema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      const result = await changePassword(values.currentPassword, values.newPassword);
      forgetLoginPassword();
      await savePassword(result.username, values.newPassword).catch(() => undefined);
      authStore.set({
        token: result.token,
        username: result.username,
        mustChangePassword: result.mustChangePassword,
        role: result.role,
        shopCode: result.shopCode,
      });
      shopStore.syncFromSession();
      navigate("/", { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Không đổi được mật khẩu");
    }
  });

  return (
    <div className="startup-screen">
      <Card className="startup-card" title="Đổi mật khẩu">
        <p>Xác nhận mật khẩu hiện tại hoặc nhập mật khẩu mới để vào ứng dụng.</p>
        <Form layout="vertical" onFinish={onSubmit}>
          <Controller
            name="currentPassword"
            control={control}
            render={({ field, fieldState }) => (
              <Form.Item label="Mật khẩu hiện tại" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
                <Input.Password {...field} autoComplete="current-password" />
              </Form.Item>
            )}
          />
          <Controller
            name="newPassword"
            control={control}
            render={({ field, fieldState }) => (
              <Form.Item label="Mật khẩu mới" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
                <Input.Password {...field} autoComplete="new-password" />
              </Form.Item>
            )}
          />
          <Controller
            name="confirmPassword"
            control={control}
            render={({ field, fieldState }) => (
              <Form.Item label="Nhập lại mật khẩu mới" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
                <Input.Password {...field} autoComplete="new-password" />
              </Form.Item>
            )}
          />
          {error ? <p style={{ color: "var(--color-danger)" }}>{error}</p> : null}
          <Button type="primary" htmlType="submit" loading={formState.isSubmitting}>
            Lưu mật khẩu
          </Button>
        </Form>
      </Card>
    </div>
  );
}

function FirstRunPasswordSetup() {
  const navigate = useNavigate();
  const [pendingAccounts, setPendingAccounts] = useState<AccountName[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { control, handleSubmit, formState, setValue } = useForm<FirstRunFormValues>({
    resolver: zodResolver(firstRunSchema),
    defaultValues: {
      username: "admin",
      newPassword: "",
      confirmPassword: "",
      applyToAllPending: true,
    },
  });

  useEffect(() => {
    let active = true;
    getFirstRunStatus()
      .then((status) => {
        if (!active) return;
        if (status.pendingAccounts.length === 0) {
          navigate("/dang-nhap", { replace: true });
          return;
        }
        setPendingAccounts(status.pendingAccounts);
        const preferred = status.pendingAccounts.includes("admin") ? "admin" : status.pendingAccounts[0];
        setValue("username", preferred);
        setValue("applyToAllPending", status.pendingAccounts.length > 1);
      })
      .catch(() => {
        if (active) navigate("/dang-nhap", { replace: true });
      });
    return () => {
      active = false;
    };
  }, [navigate, setValue]);

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      const applyAll = (pendingAccounts?.length ?? 0) > 1 && values.applyToAllPending;
      const result = await setupFirstRunPassword(values.username, values.newPassword, applyAll);
      const updatedAccounts = applyAll && pendingAccounts ? pendingAccounts : [values.username];
      rememberLogin(values.username, values.newPassword);
      await Promise.all(updatedAccounts.map((acc) => savePassword(acc, values.newPassword).catch(() => undefined)));
      authStore.set({
        token: result.token,
        username: result.username,
        mustChangePassword: result.mustChangePassword,
        role: result.role,
        shopCode: result.shopCode,
      });
      shopStore.syncFromSession();
      navigate("/", { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Không thiết lập được mật khẩu");
    }
  });

  if (pendingAccounts === null) {
    return (
      <div className="startup-screen">
        <Card className="startup-card">
          <Spin /> <span style={{ marginLeft: 8 }}>Đang kiểm tra tài khoản...</span>
        </Card>
      </div>
    );
  }

  return (
    <div className="startup-screen">
      <Card className="startup-card" title="Đặt mật khẩu mới">
        <p>Tài khoản vừa được khởi tạo. Đặt mật khẩu mới để vào thẳng ứng dụng, không cần nhập mật khẩu ngẫu nhiên.</p>
        <Form layout="vertical" onFinish={onSubmit}>
          <Controller
            name="username"
            control={control}
            render={({ field, fieldState }) => (
              <Form.Item label="Tài khoản" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
                <Select
                  {...field}
                  disabled={formState.isSubmitting}
                  options={pendingAccounts.map((account) => ({ value: account, label: ACCOUNT_LABELS[account] }))}
                />
              </Form.Item>
            )}
          />
          <Controller
            name="newPassword"
            control={control}
            render={({ field, fieldState }) => (
              <Form.Item label="Mật khẩu mới" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
                <Input.Password {...field} autoFocus autoComplete="new-password" placeholder="Ít nhất 6 ký tự" />
              </Form.Item>
            )}
          />
          <Controller
            name="confirmPassword"
            control={control}
            render={({ field, fieldState }) => (
              <Form.Item label="Nhập lại mật khẩu mới" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
                <Input.Password {...field} autoComplete="new-password" placeholder="Nhập lại mật khẩu mới" />
              </Form.Item>
            )}
          />
          {pendingAccounts.length > 1 ? (
            <Controller
              name="applyToAllPending"
              control={control}
              render={({ field }) => (
                <Form.Item>
                  <Checkbox checked={field.value} onChange={(e) => field.onChange(e.target.checked)}>
                    Áp dụng mật khẩu này cho tất cả tài khoản mới ({pendingAccounts.join(", ")})
                  </Checkbox>
                </Form.Item>
              )}
            />
          ) : null}
          {error ? <p style={{ color: "var(--color-danger)" }}>{error}</p> : null}
          <Space>
            <Button type="primary" htmlType="submit" loading={formState.isSubmitting}>
              Lưu mật khẩu & Vào ứng dụng
            </Button>
            {pendingAccounts.length < 3 ? (
              <Button onClick={() => navigate("/dang-nhap", { replace: true })} disabled={formState.isSubmitting}>
                Quay lại đăng nhập
              </Button>
            ) : null}
          </Space>
        </Form>
      </Card>
    </div>
  );
}
