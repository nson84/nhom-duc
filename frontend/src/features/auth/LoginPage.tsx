import { Button, Form, Input } from "antd";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../../services/error-handler";
import { login } from "./auth-api";
import { authStore } from "./auth-store";

const schema = z.object({
  username: z.string().trim().min(1, "Nhập tên đăng nhập"),
  password: z.string().min(1, "Nhập mật khẩu"),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const { control, handleSubmit, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { username: "", password: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      const result = await login(values.username, values.password);
      authStore.set({
        token: result.token,
        username: result.username,
        mustChangePassword: result.mustChangePassword,
      });
      navigate(result.mustChangePassword ? "/doi-mat-khau" : "/", { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Không đăng nhập được");
    }
  });

  return (
    <div className="login-screen">
      <aside className="login-aside">
        <div>
          <div className="login-mark" />
          <h1>Quản lý hàng hóa nhôm</h1>
          <p>Nhập kho, bán hàng, tồn kho, chấm công và sổ quỹ trên một máy.</p>
        </div>
        <p>Lần đầu đăng nhập, hệ thống yêu cầu đổi mật khẩu.</p>
      </aside>
      <main className="login-panel">
        <div className="login-card">
          <h2 style={{ marginTop: 0 }}>Đăng nhập</h2>
          <Form layout="vertical" onFinish={onSubmit}>
            <Controller
              name="username"
              control={control}
              render={({ field, fieldState }) => (
                <Form.Item
                  label="Tên đăng nhập"
                  validateStatus={fieldState.error ? "error" : ""}
                  help={fieldState.error?.message}
                >
                  <Input {...field} autoFocus autoComplete="username" size="large" />
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
                >
                  <Input.Password {...field} autoComplete="current-password" size="large" />
                </Form.Item>
              )}
            />
            {error ? <p style={{ color: "#a33b32" }}>{error}</p> : null}
            <Button type="primary" htmlType="submit" size="large" block loading={formState.isSubmitting}>
              Vào cửa hàng
            </Button>
          </Form>
        </div>
      </main>
    </div>
  );
}
