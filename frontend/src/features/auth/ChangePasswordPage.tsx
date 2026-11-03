import { Button, Card, Form, Input } from "antd";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../../services/error-handler";
import { changePassword } from "./auth-api";
import { authStore } from "./auth-store";

const schema = z
  .object({
    currentPassword: z.string().min(1, "Nhập mật khẩu hiện tại"),
    newPassword: z.string().min(6, "Mật khẩu mới phải từ 6 ký tự"),
    confirmPassword: z.string().min(1, "Nhập lại mật khẩu mới"),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    path: ["confirmPassword"],
    message: "Mật khẩu nhập lại chưa khớp",
  });

type FormValues = z.infer<typeof schema>;

export function ChangePasswordPage() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const { control, handleSubmit, formState } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    try {
      const result = await changePassword(values.currentPassword, values.newPassword);
      authStore.set({
        token: result.token,
        username: result.username,
        mustChangePassword: result.mustChangePassword,
        role: result.role,
        shopCode: result.shopCode,
      });
      navigate("/", { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Không đổi được mật khẩu");
    }
  });

  return (
    <div className="startup-screen">
      <Card className="startup-card" title="Đổi mật khẩu">
        <p>Tài khoản quản trị cần mật khẩu mới trước khi vào các nghiệp vụ.</p>
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
          {error ? <p style={{ color: "#a33b32" }}>{error}</p> : null}
          <Button type="primary" htmlType="submit" loading={formState.isSubmitting}>
            Lưu mật khẩu
          </Button>
        </Form>
      </Card>
    </div>
  );
}
