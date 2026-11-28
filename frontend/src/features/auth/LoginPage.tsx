import { Form, Input, Select } from "antd";
import { ArrowRightOutlined, EyeInvisibleOutlined, EyeOutlined, LoadingOutlined, LockOutlined } from "@ant-design/icons";
import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../../services/error-handler";
import { shopStore } from "../shop/shop-store";
import { getFirstRunStatus, login } from "./auth-api";
import { authStore } from "./auth-store";
import { getLastLogin, passwordForAccount, rememberLogin } from "./login-memory";
import { offerBrowserPassword, savedPassword, savePassword } from "./saved-password";

const ACCOUNT_OPTIONS = [
  { value: "admin", label: "admin — sao lưu và cài đặt" },
  { value: "phanphoi", label: "phanphoi — phân phối" },
  { value: "xuong", label: "xuong — xưởng" },
] as const;

const schema = z.object({
  username: z.enum(["admin", "phanphoi", "xuong"], { message: "Chọn tài khoản" }),
  password: z.string().min(1, "Nhập mật khẩu"),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const [pendingAccounts, setPendingAccounts] = useState<Array<FormValues["username"]>>([]);
  const { control, handleSubmit, formState, getValues, setValue, watch } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: getLastLogin(),
  });

  useEffect(() => {
    let active = true;
    getFirstRunStatus()
      .then((status) => {
        if (!active) return;
        setPendingAccounts(status.pendingAccounts);
        if (status.pendingAccounts.length === 0) return;
        const selected = getValues("username");
        if (status.pendingAccounts.length === ACCOUNT_OPTIONS.length || status.pendingAccounts.includes(selected)) {
          navigate("/doi-mat-khau", { replace: true });
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [getValues, navigate]);

  useEffect(() => {
    const account = getValues("username");
    savedPassword(account).then((password) => {
      if (password && getValues("username") === account && !getValues("password")) {
        setValue("password", password);
      }
    }).catch(() => undefined);
  }, [getValues, setValue]);

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      const result = await login(values.username, values.password);
      rememberLogin(values.username, values.password);
      void offerBrowserPassword(values.username, values.password).catch(() => undefined);
      await savePassword(values.username, values.password).catch(() => undefined);
      authStore.set({
        token: result.token,
        username: result.username,
        mustChangePassword: result.mustChangePassword,
        role: result.role,
        shopCode: result.shopCode,
      });
      shopStore.syncFromSession();
      navigate(result.mustChangePassword ? "/doi-mat-khau" : "/", { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Không đăng nhập được");
    }
  });

  return (
    <div className="login-screen">
      <div className="login-backdrop" aria-hidden />
      <main className="login-panel">
        <div className="login-stack">
          <div className="login-badge" aria-hidden>
            <span className="login-badge-title">Nhôm Đúc</span>
            <span className="login-badge-sub">CỬA HÀNG</span>
          </div>
          <div className="login-card">
            <img className="login-card-photo" src="/login-hero.jpg" alt="" />
            <div className="login-card-body">
              <header className="login-heading">
                <span className="login-eyebrow">KHÔNG GIAN QUẢN LÝ</span>
                <h1>Chào mừng trở lại.</h1>
                <p>Chọn tài khoản để bắt đầu ngày làm việc.</p>
              </header>
              <Form layout="vertical" onFinish={onSubmit} className="login-form" aria-busy={formState.isSubmitting}>
                <input type="text" name="username" autoComplete="username" value={watch("username")} readOnly tabIndex={-1} aria-hidden style={{ position: "absolute", opacity: 0, width: 1, height: 1, pointerEvents: "none" }} />
                <Controller
                  name="username"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Form.Item
                      label="Tài khoản"
                      validateStatus={fieldState.error ? "error" : ""}
                      help={fieldState.error?.message}
                      className="login-field-block"
                    >
                      <Select
                        {...field}
                        disabled={formState.isSubmitting}
                        options={[...ACCOUNT_OPTIONS]}
                        size="large"
                        variant="outlined"
                        className="login-underline-control"
                        popupMatchSelectWidth
                        aria-label="Tài khoản"
                        onChange={async (value: FormValues["username"]) => {
                          field.onChange(value);
                          if (pendingAccounts.includes(value)) {
                            navigate("/doi-mat-khau", { replace: true });
                            return;
                          }
                          setValue("password", passwordForAccount(value), {
                            shouldDirty: true,
                            shouldValidate: true,
                          });
                          setError(null);
                          const password = await savedPassword(value).catch(() => null);
                          if (password && getValues("username") === value && !getValues("password")) {
                            setValue("password", password, { shouldDirty: true, shouldValidate: true });
                          }
                        }}
                      />
                    </Form.Item>
                  )}
                />
                <Controller
                  name="password"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Form.Item
                      label="Mật khẩu"
                      validateStatus={fieldState.error ? "error" : ""}
                      help={fieldState.error?.message}
                      className="login-field-block"
                    >
                      <Input.Password
                        {...field}
                        disabled={formState.isSubmitting}
                        autoFocus
                        autoComplete="current-password"
                        size="large"
                        variant="outlined"
                        className="login-underline-control"
                        aria-label="Mật khẩu"
                        placeholder="Mật khẩu"
                        iconRender={(visible) => (visible ? <EyeOutlined /> : <EyeInvisibleOutlined />)}
                      />
                    </Form.Item>
                  )}
                />
                {error ? <p className="login-error" role="alert">{error}</p> : null}
                <div className="login-submit-wrap">
                  <button type="submit" className="login-submit" disabled={formState.isSubmitting}>
                    {formState.isSubmitting ? "Đang đăng nhập..." : "ĐĂNG NHẬP"}
                    {formState.isSubmitting ? <LoadingOutlined spin aria-hidden /> : <ArrowRightOutlined aria-hidden />}
                  </button>
                </div>
              </Form>
              <p className="login-footnote"><LockOutlined aria-hidden /> Quản lý cửa hàng Nhôm Đúc</p>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
