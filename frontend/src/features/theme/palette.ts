/**
 * Design tokens màu — nguồn duy nhất cho Light (Blue Gray) và Dark (Deep Navy).
 * Tên token là ngữ nghĩa (semantic); component chỉ dùng var(--color-*) hoặc useThemeColors().
 *
 * Tương phản (WCAG AA) đã tính trên bề mặt tương ứng:
 *  - Chữ chính / chữ phụ trên nền, thẻ: >= 4.5:1
 *  - Chữ trên nút (…Contrast) trên nền nút: >= 4.5:1
 */
export type AppThemeMode = "light" | "dark";

export type ThemePalette = {
  bg: string;
  sidebar: string;
  sidebarText: string;
  sidebarTextMuted: string;
  sidebarSelectedBg: string;
  surface: string;
  surfaceMuted: string;
  surfaceElevated: string;
  text: string;
  textSecondary: string;
  textPlaceholder: string;
  textDisabled: string;
  border: string;
  borderStrong: string;
  primary: string;
  primaryHover: string;
  primaryActive: string;
  primarySoft: string;
  primaryContrast: string;
  tableHover: string;
  tableHeader: string;
  focusRing: string;
  success: string;
  successSoft: string;
  warning: string;
  warningSoft: string;
  danger: string;
  dangerSoft: string;
  info: string;
  shadowCard: string;
  shadowOverlay: string;
  scrollbarThumb: string;
  scrollbarThumbHover: string;
  overlay: string;
  chart1: string;
  chart2: string;
  chart3: string;
  chart4: string;
  chartGrid: string;
  /** Nút hành động theo vai trò: nền / viền / chữ. */
  btn: Record<
    "create" | "edit" | "cancel" | "delete" | "confirm" | "pay" | "reverse" | "print" | "sync" | "secondary",
    { bg: string; border: string; fg: string }
  >;
  btnHoverBrightness: string;
};

export const LIGHT: ThemePalette = {
  bg: "#F1F5F9",
  sidebar: "#FFFFFF",
  sidebarText: "#0F172A",
  sidebarTextMuted: "#475569",
  sidebarSelectedBg: "#DBEAFE",
  surface: "#FFFFFF",
  surfaceMuted: "#F8FAFC",
  surfaceElevated: "#FFFFFF",
  text: "#0F172A",
  textSecondary: "#475569",
  textPlaceholder: "#64748B",
  textDisabled: "#94A3B8",
  border: "#CBD5E1",
  borderStrong: "#7C8CA1",
  primary: "#2563EB",
  primaryHover: "#1D4ED8",
  primaryActive: "#1E40AF",
  primarySoft: "#DBEAFE",
  primaryContrast: "#FFFFFF",
  tableHover: "#F1F5F9",
  tableHeader: "#F8FAFC",
  focusRing: "rgba(37, 99, 235, 0.35)",
  success: "#15803D",
  successSoft: "#DCFCE7",
  warning: "#B45309",
  warningSoft: "#FEF3C7",
  danger: "#B91C1C",
  dangerSoft: "#FEE2E2",
  info: "#1D4ED8",
  shadowCard: "0 1px 3px rgba(15, 23, 42, 0.08), 0 1px 2px rgba(15, 23, 42, 0.06)",
  shadowOverlay: "0 12px 32px rgba(15, 23, 42, 0.18)",
  scrollbarThumb: "#CBD5E1",
  scrollbarThumbHover: "#94A3B8",
  overlay: "rgba(15, 23, 42, 0.45)",
  chart1: "#2563EB",
  chart2: "#B45309",
  chart3: "#15803D",
  chart4: "#B91C1C",
  chartGrid: "#E2E8F0",
  btn: {
    create: { bg: "#2563EB", border: "#1D4ED8", fg: "#FFFFFF" },
    print: { bg: "#2563EB", border: "#1D4ED8", fg: "#FFFFFF" },
    edit: { bg: "#FEE2E2", border: "#FCA5A5", fg: "#7F1D1D" },
    cancel: { bg: "#C2410C", border: "#9A3412", fg: "#FFFFFF" },
    delete: { bg: "#B91C1C", border: "#991B1B", fg: "#FFFFFF" },
    confirm: { bg: "#15803D", border: "#166534", fg: "#FFFFFF" },
    pay: { bg: "#0F766E", border: "#115E59", fg: "#FFFFFF" },
    reverse: { bg: "#B45309", border: "#92400E", fg: "#FFFFFF" },
    sync: { bg: "#047857", border: "#065F46", fg: "#FFFFFF" },
    secondary: { bg: "#475569", border: "#334155", fg: "#FFFFFF" },
  },
  btnHoverBrightness: "0.9",
};

export const DARK: ThemePalette = {
  bg: "#0F172A",
  sidebar: "#111C30",
  sidebarText: "#F8FAFC",
  sidebarTextMuted: "#CBD5E1",
  sidebarSelectedBg: "#1E3A5F",
  surface: "#1E293B",
  surfaceMuted: "#172033",
  surfaceElevated: "#243247",
  text: "#F8FAFC",
  textSecondary: "#CBD5E1",
  textPlaceholder: "#94A3B8",
  textDisabled: "#94A3B8",
  border: "#475569",
  borderStrong: "#64748B",
  primary: "#60A5FA",
  primaryHover: "#93C5FD",
  primaryActive: "#3B82F6",
  primarySoft: "#1E3A5F",
  primaryContrast: "#0F172A",
  tableHover: "#334155",
  tableHeader: "#243247",
  focusRing: "rgba(96, 165, 250, 0.45)",
  success: "#4ADE80",
  successSoft: "#14532D",
  warning: "#FBBF24",
  warningSoft: "#78350F",
  danger: "#F87171",
  dangerSoft: "#7F1D1D",
  info: "#93C5FD",
  shadowCard: "0 1px 2px rgba(0, 0, 0, 0.45), 0 4px 12px rgba(0, 0, 0, 0.28)",
  shadowOverlay: "0 16px 40px rgba(0, 0, 0, 0.6)",
  scrollbarThumb: "#475569",
  scrollbarThumbHover: "#64748B",
  overlay: "rgba(2, 6, 23, 0.7)",
  chart1: "#60A5FA",
  chart2: "#FBBF24",
  chart3: "#4ADE80",
  chart4: "#F87171",
  chartGrid: "#334155",
  btn: {
    create: { bg: "#60A5FA", border: "#93C5FD", fg: "#0F172A" },
    print: { bg: "#60A5FA", border: "#93C5FD", fg: "#0F172A" },
    edit: { bg: "#FCA5A5", border: "#FECACA", fg: "#450A0A" },
    cancel: { bg: "#FB923C", border: "#FDBA74", fg: "#1C0A00" },
    delete: { bg: "#F87171", border: "#FCA5A5", fg: "#1F0505" },
    confirm: { bg: "#4ADE80", border: "#86EFAC", fg: "#052E16" },
    pay: { bg: "#2DD4BF", border: "#5EEAD4", fg: "#042F2E" },
    reverse: { bg: "#FBBF24", border: "#FCD34D", fg: "#1C1300" },
    sync: { bg: "#34D399", border: "#6EE7B7", fg: "#022C22" },
    secondary: { bg: "#CBD5E1", border: "#E2E8F0", fg: "#0F172A" },
  },
  btnHoverBrightness: "1.1",
};

export function paletteFor(mode: AppThemeMode): ThemePalette {
  return mode === "dark" ? DARK : LIGHT;
}

/** camelCase -> --color-kebab-case */
function cssName(key: string): string {
  return `--color-${key.replace(/([A-Z0-9])/g, "-$1").toLowerCase()}`;
}

/** Ghi toàn bộ token vào :root dưới dạng CSS variables. */
export function applyPaletteToDom(mode: AppThemeMode) {
  const palette = paletteFor(mode);
  const root = document.documentElement;
  for (const [key, value] of Object.entries(palette)) {
    if (key === "btn") {
      for (const [tone, c] of Object.entries(value as ThemePalette["btn"])) {
        root.style.setProperty(`--btn-${tone}-bg`, c.bg);
        root.style.setProperty(`--btn-${tone}-border`, c.border);
        root.style.setProperty(`--btn-${tone}-fg`, c.fg);
      }
    } else if (key === "btnHoverBrightness") {
      root.style.setProperty("--btn-hover-brightness", value as string);
    } else {
      root.style.setProperty(cssName(key), value as string);
    }
  }
  root.setAttribute("data-theme", mode);
  root.style.colorScheme = mode;
}
