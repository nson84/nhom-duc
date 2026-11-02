import { Button, Card, Form, Input, InputNumber, Space } from "antd";
import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Link } from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../../services/error-handler";
import { getSettings, updateSettings, type ShopSettings } from "./settings-api";

const schema = z.object({
  shopName: z
    .string()
    .trim()
    .min(1, "Nhập tên cửa hàng")
    .max(200)
    .refine((value) => !/^[+-]?\d/u.test(value), {
      message: "Tên cửa hàng không được bắt đầu bằng số",
    }),
  shopAddress: z.string().max(300),
  shopPhone: z.string().max(30),
  backupTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, "Giờ sao lưu theo dạng HH:mm"),
  backupRetentionDays: z.number().int().min(1).max(3650),
  backupDirectory: z.string().max(1000),
});

export function SettingsPage() {
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { control, handleSubmit, reset, formState } = useForm<ShopSettings>({
    resolver: zodResolver(schema),
    defaultValues: {
      shopName: "",
      shopAddress: "",
      shopPhone: "",
      backupTime: "22:00",
      backupRetentionDays: 30,
      backupDirectory: "",
    },
  });

  useEffect(() => {
    getSettings().then(reset).catch((caught: unknown) => {
      setError(caught instanceof ApiError ? caught.message : "Không tải được cấu hình");
    });
  }, [reset]);

  const onSubmit = handleSubmit(async (values) => {
    setError(null);
    setMessage(null);
    try {
      const saved = await updateSettings(values);
      reset(saved);
      setMessage("Đã lưu cấu hình");
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Không lưu được cấu hình");
    }
  });

  return (
    <Card
      title="Cài đặt"
      extra={
        <Space>
          <Link to="/noi-dung-gioi-thieu">Sửa nội dung trang giới thiệu</Link>
        </Space>
      }
    >
      <Form layout="vertical" onFinish={onSubmit} style={{ maxWidth: 720 }}>
        <Controller name="shopName" control={control} render={({ field, fieldState }) => (
          <Form.Item label="Tên cửa hàng" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
            <Input {...field} />
          </Form.Item>
        )} />
        <Controller name="shopAddress" control={control} render={({ field, fieldState }) => (
          <Form.Item label="Địa chỉ" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
            <Input {...field} />
          </Form.Item>
        )} />
        <Controller name="shopPhone" control={control} render={({ field, fieldState }) => (
          <Form.Item label="Điện thoại" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
            <Input {...field} />
          </Form.Item>
        )} />
        <Controller name="backupTime" control={control} render={({ field, fieldState }) => (
          <Form.Item label="Giờ sao lưu hằng ngày" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
            <Input {...field} placeholder="22:00" />
          </Form.Item>
        )} />
        <Controller name="backupRetentionDays" control={control} render={({ field, fieldState }) => (
          <Form.Item label="Số ngày giữ bản sao lưu" validateStatus={fieldState.error ? "error" : ""} help={fieldState.error?.message}>
            <InputNumber {...field} min={1} max={3650} style={{ width: "100%" }} />
          </Form.Item>
        )} />
        <Controller name="backupDirectory" control={control} render={({ field, fieldState }) => (
          <Form.Item
            label="Thư mục sao lưu"
            extra="Nên chọn ổ khác ổ cài ứng dụng, USB hoặc ổ ngoài."
            validateStatus={fieldState.error ? "error" : ""}
            help={fieldState.error?.message}
          >
            <Input {...field} placeholder="D:\SaoLuuNhom" />
          </Form.Item>
        )} />
        {message ? <p>{message}</p> : null}
        {error ? <p style={{ color: "#a33b32" }}>{error}</p> : null}
        <Button type="primary" htmlType="submit" loading={formState.isSubmitting}>Lưu</Button>
      </Form>
    </Card>
  );
}
