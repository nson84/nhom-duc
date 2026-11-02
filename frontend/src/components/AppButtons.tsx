import { Button, type ButtonProps } from "antd";
import type { CSSProperties, ReactNode } from "react";

/**
 * Màu nút theo vai trò — không dùng một màu cho mọi nút.
 * Mỗi nút: nền đặc + border + chữ trắng + icon (@ant-design/icons).
 */
export const APP_BUTTON_COLORS = {
  /** Tạo / Thêm / Lưu chính / Primary / In phiếu */
  create: { bg: "#2f6f8f", border: "#245a73" },
  /** Sửa / Chỉnh sửa — Goldenrod4 */
  edit: { bg: "#8B6914", border: "#6B5010" },
  /** Quay lại / hủy nhẹ — cam */
  cancel: { bg: "#C2410C", border: "#9A3412" },
  /** Xóa / Hủy nháp / nguy hiểm — đỏ đậm */
  delete: { bg: "#7F1D1D", border: "#641616" },
  /** Xác nhận phiếu — xanh lá đậm */
  confirm: { bg: "#166534", border: "#14532D" },
  /** Thu / Chi tiền */
  pay: { bg: "#0F766E", border: "#0D5F59" },
  /** Đảo phiếu — cam đất */
  reverse: { bg: "#EA580C", border: "#C2410C" },
  /** In phiếu — cùng xanh tạo */
  print: { bg: "#2f6f8f", border: "#245a73" },
  /** Phụ (cấu hình, lịch sử, …) */
  secondary: { bg: "#6B7280", border: "#4B5563" },
} as const;

export type AppButtonTone = keyof typeof APP_BUTTON_COLORS;

export function appButtonStyle(tone: AppButtonTone): CSSProperties {
  const c = APP_BUTTON_COLORS[tone];
  return {
    backgroundColor: c.bg,
    borderColor: c.border,
    color: "#fff",
    borderRadius: 999,
  };
}

/** @deprecated Dùng appButtonStyle("edit") — giữ để tương thích code cũ. */
export const appActionButtonStyle: CSSProperties = appButtonStyle("edit");

export type AppButtonProps = Omit<ButtonProps, "icon" | "type" | "danger" | "ghost"> & {
  /** Bắt buộc — import từ @ant-design/icons. */
  icon: ReactNode;
  /** Vai trò màu: create | edit | cancel | delete | confirm | pay | reverse | print | secondary */
  tone?: AppButtonTone;
};

export function AppActionButton({
  style,
  size = "small",
  className,
  icon,
  tone = "edit",
  children,
  ...rest
}: AppButtonProps) {
  return (
    <Button
      size={size}
      icon={icon}
      className={className ? `app-action-button app-btn-${tone} ${className}` : `app-action-button app-btn-${tone}`}
      style={{ ...appButtonStyle(tone), ...style }}
      {...rest}
    >
      {children}
    </Button>
  );
}

export function AppDeleteButton({
  children = "Xóa",
  icon,
  tone = "delete",
  ...rest
}: AppButtonProps) {
  return (
    <AppActionButton icon={icon} tone={tone} {...rest}>
      {children}
    </AppActionButton>
  );
}
