import {
  AccountBookOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  BellOutlined,
  CalendarOutlined,
  CloudUploadOutlined,
  DashboardOutlined,
  DollarOutlined,
  ImportOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
  MoonOutlined,
  QrcodeOutlined,
  SettingOutlined,
  SunOutlined,
  ShoppingCartOutlined,
  TeamOutlined,
  DatabaseOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { Avatar, Badge, Button, Drawer, Dropdown, Layout, Menu, Space, Tag, Tooltip, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { SHOP_LABELS, useShop } from "../../features/shop/shop-store";
import { logout } from "../../features/auth/auth-api";
import { authStore, useSession } from "../../features/auth/auth-store";
import { getSettings } from "../../features/settings/settings-api";
import { listCategories } from "../../features/products/products-api";
import { themeStore, useThemeMode } from "../../features/theme/theme-store";
import { dismissPriceReviewAlert, listPriceReviewAlerts, markPriceReviewAlertSeen, removeStalePriceReviewAlerts, PRICE_REVIEW_CHANGED } from "../../features/products/price-review-alerts";

const { Header, Sider, Content } = Layout;

const ALL_ITEMS = [
  { key: "/", icon: <DashboardOutlined />, label: "Tổng quan" },
  { key: "/khach-mua", icon: <UserOutlined />, label: "Khách mua" },
  { key: "/san-pham", icon: <AppstoreOutlined />, label: "Sản phẩm" },
  { key: "/nhap-kho", icon: <ImportOutlined />, label: "Nhập kho" },
  { key: "/ban-hang", icon: <ShoppingCartOutlined />, label: "Bán hàng" },
  { key: "/ton-kho", icon: <DatabaseOutlined />, label: "Tồn kho" },
  { key: "/quyet-toan-xuong", icon: <BarChartOutlined />, label: "Quyết toán xưởng" },
  { key: "/tong-hop-hai-ben", icon: <BarChartOutlined />, label: "Tổng hợp hai bên" },
  { key: "/nhan-vien", icon: <TeamOutlined />, label: "Nhân viên" },
  { key: "/cham-cong", icon: <CalendarOutlined />, label: "Chấm công" },
  { key: "/tien-cong", icon: <DollarOutlined />, label: "Tiền công" },
  { key: "/thu-chi", icon: <AccountBookOutlined />, label: "Thu chi" },
  { key: "/bao-cao", icon: <BarChartOutlined />, label: "Báo cáo" },
  { key: "/so-cuoi-nam", icon: <DatabaseOutlined />, label: "Sổ cuối năm" },
  { key: "/sao-luu", icon: <CloudUploadOutlined />, label: "Sao lưu" },
  { key: "/cai-dat", icon: <SettingOutlined />, label: "Cài đặt" },
];

const PAYROLL_PATHS = new Set(["/nhan-vien", "/cham-cong", "/tien-cong"]);
const ADMIN_ONLY_PATHS = new Set(["/sao-luu", "/cai-dat"]);

const ROLE_LABEL: Record<string, string> = {
  ADMIN: "Admin thống kê",
  DISTRIBUTION: "Phân phối",
  MANUFACTURING: "Xưởng",
};

function useMaxWidth(maxPx: number) {
  const query = `(max-width: ${maxPx}px)`;
  const [matches, setMatches] = useState(() =>
    typeof window !== "undefined" ? window.matchMedia(query).matches : false,
  );

  useEffect(() => {
    const media = window.matchMedia(query);
    const sync = () => setMatches(media.matches);
    sync();
    media.addEventListener("change", sync);
    return () => media.removeEventListener("change", sync);
  }, [query]);

  return matches;
}

export function AppShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const session = useSession();
  const shop = useShop();
  const themeMode = useThemeMode();
  const role = session?.role ?? "ADMIN";
  const [shopName, setShopName] = useState("Quản lý hàng hóa nhôm");
  const dark = themeMode === "dark";
  const isDrawerNav = useMaxWidth(991);
  const [navOpen, setNavOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const notificationShop = role === "ADMIN" ? null : role;
  const [storedAlerts, setStoredAlerts] = useState(() => notificationShop ? listPriceReviewAlerts(notificationShop) : []);
  const priceAlerts = storedAlerts.filter((alert) => alert.shop === notificationShop);

  useEffect(() => {
    if (!notificationShop) return;
    const refresh = () => setStoredAlerts(listPriceReviewAlerts(notificationShop));
    refresh();
    void listCategories().then((categories) => removeStalePriceReviewAlerts(notificationShop, categories)).catch(() => undefined);
    window.addEventListener(PRICE_REVIEW_CHANGED, refresh);
    window.addEventListener("storage", refresh);
    return () => {
      window.removeEventListener(PRICE_REVIEW_CHANGED, refresh);
      window.removeEventListener("storage", refresh);
    };
  }, [notificationShop]);

  useEffect(() => {
    let active = true;
    getSettings()
      .then((settings) => {
        if (active && settings.shopName) {
          setShopName(settings.shopName);
        }
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    setNavOpen(false);
  }, [location.pathname, isDrawerNav]);

  const menuItems = useMemo(() => {
    return ALL_ITEMS.filter((item) => {
      if (role === "ADMIN") return item.key === "/tong-hop-hai-ben" || item.key === "/so-cuoi-nam" || ADMIN_ONLY_PATHS.has(item.key);
      if (PAYROLL_PATHS.has(item.key) && role !== "MANUFACTURING") {
        return false;
      }
      if (ADMIN_ONLY_PATHS.has(item.key)) {
        return false;
      }
      if (item.key === "/tong-hop-hai-ben") return false;
      if (item.key === "/quyet-toan-xuong" && shop === "DISTRIBUTION") {
        return false;
      }
      if (item.key === "/ton-kho" && shop === "MANUFACTURING") {
        return false;
      }
      return true;
    });
  }, [role, shop]);

  const selected =
    location.pathname.startsWith("/khach-mua")
        ? "/khach-mua"
        : menuItems.some((item) => item.key === location.pathname)
          ? location.pathname
          : "/";

  const menu = (
    <Menu
      className="app-nav-menu"
      theme={dark ? "dark" : "light"}
      mode="inline"
      selectedKeys={[selected]}
      items={menuItems}
      onClick={({ key }) => navigate(key)}
    />
  );

  const navToggleLabel = isDrawerNav ? "Mở menu" : collapsed ? "Mở rộng menu" : "Thu gọn menu";

  return (
    <Layout className="app-shell">
      {isDrawerNav ? (
        <Drawer
          className="app-nav-drawer"
          placement="left"
          open={navOpen}
          onClose={() => setNavOpen(false)}
          width={280}
          title={shopName}
          destroyOnHidden
        >
          {menu}
        </Drawer>
      ) : (
        <Sider
          width={232}
          collapsedWidth={72}
          collapsed={collapsed}
          trigger={null}
          collapsible
          theme={dark ? "dark" : "light"}
          className={`app-sider${collapsed ? " is-collapsed" : ""}`}
        >
          <div className="brand-block" title={shopName}>
            <span className="brand-full">{shopName}</span>
            <span className="brand-mark" aria-hidden>
              {(shopName.trim()[0] ?? "N").toUpperCase()}
            </span>
          </div>
          {menu}
        </Sider>
      )}
      <Layout className="app-main">
        <Header className="app-header">
          <div className="app-header-bar">
            <Button
              type="text"
              className="theme-toggle nav-toggle"
              shape="circle"
              aria-label={navToggleLabel}
              icon={isDrawerNav ? <MenuOutlined /> : collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => {
                if (isDrawerNav) {
                  setNavOpen(true);
                  return;
                }
                setCollapsed((value) => !value);
              }}
            />
            <div className="app-header-shop">
              <Tag color="blue">{role === "ADMIN" ? "Tổng hợp hai bên" : SHOP_LABELS[shop]}</Tag>
            </div>
            <div className="app-header-end">
            {notificationShop && <Dropdown
              trigger={["click"]}
              menu={{ items: priceAlerts.length ? priceAlerts.flatMap((alert) => [
                {
                  key: `open:${alert.id}`,
                  className: "price-alert-item",
                  label: (
                    <div className="price-alert-content">
                      <span className="price-alert-avatar"><BellOutlined /></span>
                      <span className="price-alert-copy">
                        <strong>{alert.categoryName}</strong>: kiểm tra giá bán / kg, / tạ, / tấn
                        <small>{new Date(alert.createdAt).toLocaleString("vi-VN")}</small>
                      </span>
                      {!alert.seenAt ? <span className="price-alert-unread" aria-label="Chưa xem" /> : null}
                    </div>
                  ),
                  onClick: () => {
                    markPriceReviewAlertSeen(alert.id, alert.createdAt);
                    navigate(`/san-pham?loai=${alert.categoryId}`);
                  },
                },
                {
                  key: `done:${alert.id}`,
                  label: `Đã cập nhật giá bán ${alert.categoryName}`,
                  onClick: () => dismissPriceReviewAlert(alert.id),
                },
              ]) : [{ key: "empty", disabled: true, label: "Không có nhắc giá bán" }] }}
              overlayClassName="price-alert-dropdown"
            >
              <Badge count={priceAlerts.filter((alert) => !alert.seenAt).length} size="small">
                <Button type="text" className="theme-toggle" shape="circle" aria-label={`Thông báo giá bán · ${SHOP_LABELS[notificationShop]}`} icon={<BellOutlined />} />
              </Badge>
            </Dropdown>}
            <Tooltip title={dark ? "Chế độ sáng" : "Chế độ tối"}>
              <Button
                type="text"
                className="theme-toggle"
                shape="circle"
                aria-label={dark ? "Bật chế độ sáng" : "Bật chế độ tối"}
                icon={dark ? <SunOutlined /> : <MoonOutlined />}
                onClick={() => themeStore.toggle()}
              />
            </Tooltip>
            <Dropdown
              trigger={["click"]}
              menu={{
                items: [
                  {
                    key: "profile",
                    disabled: true,
                    label: (
                      <div style={{ minWidth: 160, lineHeight: 1.4, cursor: "default" }}>
                        <div style={{ fontWeight: 600 }}>{session?.username}</div>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                          {ROLE_LABEL[role] ?? role}
                        </Typography.Text>
                      </div>
                    ),
                  },
                  { type: "divider" },
                  {
                    key: "bank-qr",
                    icon: <QrcodeOutlined />,
                    label: "Tài khoản QR nhận tiền",
                    onClick: () => navigate("/tai-khoan-qr"),
                  },
                  ...(role === "ADMIN"
                    ? [
                        {
                          key: "settings",
                          icon: <SettingOutlined />,
                          label: "Cài đặt cửa hàng",
                          onClick: () => navigate("/cai-dat"),
                        },
                      ]
                    : []),
                  {
                    key: "theme",
                    icon: dark ? <SunOutlined /> : <MoonOutlined />,
                    label: dark ? "Chế độ sáng" : "Chế độ tối",
                    onClick: () => themeStore.toggle(),
                  },
                  {
                    key: "logout",
                    icon: <LogoutOutlined />,
                    danger: true,
                    label: "Đăng xuất",
                    onClick: () => {
                      void logout()
                        .catch(() => undefined)
                        .finally(() => {
                          authStore.clear();
                          navigate("/dang-nhap", { replace: true });
                        });
                    },
                  },
                ],
              }}
            >
              <Space className="app-user-trigger" size={10}>
                <Tag className="app-header-role">{ROLE_LABEL[role] ?? role}</Tag>
                <Avatar className="app-avatar" style={{ verticalAlign: "middle" }}>
                  {(session?.username ?? "?").slice(0, 1).toUpperCase()}
                </Avatar>
              </Space>
            </Dropdown>
            </div>
          </div>
        </Header>
        <Content className="app-content">
          <Outlet key={`${role}-${shop}`} />
        </Content>
      </Layout>
    </Layout>
  );
}
