import { Form, Input, Select } from "antd";
import { EyeInvisibleOutlined, EyeOutlined } from "@ant-design/icons";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../../services/error-handler";
import { shopStore } from "../shop/shop-store";
import { login } from "./auth-api";
import { authStore } from "./auth-store";

const ACCOUNT_OPTIONS = [
  { value: "admin", label: "admin — xem báo cáo" },
  { value: "phanphoi", label: "phanphoi — phân phối" },
  { value: "xuong", label: "xuong — xưởng" },
] as const;

/** Mật khẩu mặc định bootstrap (AdminBootstrap) — autofill khi chọn tài khoản. */
const DEFAULT_PASSWORDS: Record<(typeof ACCOUNT_OPTIONS)[number]["value"], string> = {
  admin: "123456",
  phanphoi: "123456",
  xuong: "123456",
};

const schema = z.object({
  username: z.enum(["admin", "phanphoi", "xuong"], { message: "Chọn tài khoản" }),
  password: z.string().min(1, "Nhập mật khẩu"),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const { control, handleSubmit, formState, setValue } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: "admin", password: DEFAULT_PASSWORDS.admin },
  });

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      const result = await login(values.username, values.password);
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
              <Form layout="vertical" onFinish={onSubmit} className="login-form">
                <Controller
                  name="username"
                  control={control}
                  render={({ field, fieldState }) => (
                    <Form.Item
                      validateStatus={fieldState.error ? "error" : ""}
                      help={fieldState.error?.message}
                      className="login-field-block"
                    >
                      <Select
                        {...field}
                        options={[...ACCOUNT_OPTIONS]}
                        size="large"
                        variant="outlined"
                        className="login-underline-control"
                        popupMatchSelectWidth
                        aria-label="Tài khoản"
                        onChange={(value: FormValues["username"]) => {
                          field.onChange(value);
                          setValue("password", DEFAULT_PASSWORDS[value], {
                            shouldDirty: true,
                            shouldValidate: true,
                          });
                          setError(null);
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
                      validateStatus={fieldState.error ? "error" : ""}
                      help={fieldState.error?.message}
                      className="login-field-block"
                    >
                      <Input.Password
                        {...field}
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
                {error ? <p className="login-error">{error}</p> : null}
                <div className="login-submit-wrap">
                  <button type="submit" className="login-submit" disabled={formState.isSubmitting}>
                    {formState.isSubmitting ? "Đang đăng nhập..." : "ĐĂNG NHẬP"}
                  </button>
                </div>
              </Form>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
