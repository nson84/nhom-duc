import { Button, type ButtonProps } from "antd";
import type { CSSProperties, ReactNode } from "react";

/**
 * Màu nút theo vai trò, lấy từ design tokens (features/theme/palette.ts):
 * nền / viền / chữ đều qua CSS variables nên tự đổi theo Light / Dark và đạt AA.
 */
const TONES = ["create", "edit", "cancel", "delete", "confirm", "pay", "reverse", "print", "sync", "secondary"] as const;

export const APP_BUTTON_COLORS = Object.fromEntries(
  TONES.map((tone) => [
    tone,
    { bg: `var(--btn-${tone}-bg)`, border: `var(--btn-${tone}-border)`, color: `var(--btn-${tone}-fg)` },
  ]),
) as Record<(typeof TONES)[number], { bg: string; border: string; color: string }>;
export type AppButtonTone = keyof typeof APP_BUTTON_COLORS;

export function appButtonStyle(tone: AppButtonTone): CSSProperties {
  const c = APP_BUTTON_COLORS[tone];
  return {
    backgroundColor: c.bg,
    borderColor: c.border,
    color: c.color,
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
