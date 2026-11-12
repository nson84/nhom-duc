import { App as AntApp, ConfigProvider, theme as antTheme, type ThemeConfig } from "antd";
import viVN from "antd/locale/vi_VN";
import { RouterProvider } from "react-router-dom";
import { ReadyGate } from "../components/startup/ReadyGate";
import { paletteFor, type AppThemeMode } from "../features/theme/palette";
import { useThemeMode } from "../features/theme/theme-store";
import { appRouter } from "./router";

/** Ánh xạ design tokens → Ant Design (một nguồn: features/theme/palette.ts). */
function buildAntTheme(mode: AppThemeMode): ThemeConfig {
  const p = paletteFor(mode);
  return {
    algorithm: mode === "dark" ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
    token: {
      colorPrimary: p.primary,
      colorPrimaryHover: p.primaryHover,
      colorPrimaryActive: p.primaryActive,
      colorPrimaryBg: p.primarySoft,
      colorLink: p.primary,
      colorLinkHover: p.primaryHover,
      colorInfo: p.info,
      colorSuccess: p.success,
      colorWarning: p.warning,
      colorError: p.danger,
      colorBgBase: p.surface,
      colorBgLayout: p.bg,
      colorBgContainer: p.surface,
      colorBgElevated: p.surfaceElevated,
      colorText: p.text,
      colorTextSecondary: p.textSecondary,
      colorTextTertiary: p.textSecondary,
      colorTextQuaternary: p.textPlaceholder,
      colorTextDescription: p.textSecondary,
      colorTextPlaceholder: p.textPlaceholder,
      colorTextDisabled: p.textDisabled,
      colorBorder: p.borderStrong,
      colorBorderSecondary: p.border,
      colorSplit: p.border,
      colorFillAlter: p.surfaceMuted,
      controlItemBgHover: p.tableHover,
      controlItemBgActive: p.primarySoft,
      controlOutline: p.focusRing,
      borderRadius: 8,
      borderRadiusLG: 12,
      fontFamily: '"Segoe UI", sans-serif',
      motionDurationMid: "0.18s",
    },
    components: {
      Button: {
        primaryColor: p.primaryContrast,
        colorPrimary: p.primary,
        colorPrimaryHover: p.primaryHover,
        colorPrimaryActive: p.primaryActive,
        borderRadius: 999,
        defaultColor: p.text,
        defaultBg: p.surface,
        defaultBorderColor: p.borderStrong,
        defaultHoverColor: p.primary,
        defaultHoverBorderColor: p.primary,
      },
      Layout: {
        bodyBg: p.bg,
        headerBg: p.surface,
        siderBg: p.sidebar,
        headerColor: p.text,
      },
      Card: {
        borderRadiusLG: 12,
        colorBgContainer: p.surface,
        headerFontSize: 16,
      },
      Table: {
        headerBg: p.tableHeader,
        headerColor: p.text,
        rowHoverBg: p.tableHover,
        borderColor: p.border,
        colorBgContainer: p.surface,
        footerBg: p.tableHeader,
      },
      Menu: {
        itemBg: p.sidebar,
        subMenuItemBg: p.sidebar,
        itemColor: p.sidebarTextMuted,
        itemHoverColor: p.sidebarText,
        itemHoverBg: p.tableHover,
        itemSelectedColor: p.primary,
        itemSelectedBg: p.sidebarSelectedBg,
        darkItemBg: p.sidebar,
        darkSubMenuItemBg: p.sidebar,
        darkItemColor: p.sidebarTextMuted,
        darkItemHoverColor: p.sidebarText,
        darkItemHoverBg: p.tableHover,
        darkItemSelectedColor: p.primary,
        darkItemSelectedBg: p.sidebarSelectedBg,
      },
      Modal: {
        contentBg: p.surfaceElevated,
        headerBg: p.surfaceElevated,
        titleColor: p.text,
        borderRadiusLG: 12,
      },
      Input: {
        colorBgContainer: p.surface,
        activeBorderColor: p.primary,
        hoverBorderColor: p.primary,
        activeShadow: `0 0 0 2px ${p.focusRing}`,
      },
      Select: {
        optionSelectedBg: p.primarySoft,
        optionSelectedColor: p.text,
        optionActiveBg: p.tableHover,
      },
      Tooltip: {
        colorBgSpotlight: mode === "dark" ? "#F8FAFC" : "#0F172A",
        colorTextLightSolid: mode === "dark" ? "#0F172A" : "#F8FAFC",
      },
      Tag: {
        defaultBg: p.surfaceMuted,
        defaultColor: p.text,
      },
      Segmented: {
        itemSelectedBg: p.primary,
        itemSelectedColor: p.primaryContrast,
        trackBg: p.surfaceMuted,
        itemColor: p.textSecondary,
      },
      Statistic: {
        titleFontSize: 14,
      },
    },
  };
}

export function Providers() {
  const mode = useThemeMode();
  return (
    <ConfigProvider locale={viVN} theme={buildAntTheme(mode)}>
      <AntApp>
        <ReadyGate>
          <RouterProvider router={appRouter} />
        </ReadyGate>
      </AntApp>
    </ConfigProvider>
  );
}
