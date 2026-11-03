import {
  AccountBookOutlined,
  AppstoreOutlined,
  BarChartOutlined,
  CalendarOutlined,
  CloudUploadOutlined,
  ContactsOutlined,
  DashboardOutlined,
  DollarOutlined,
  ImportOutlined,
  SettingOutlined,
  ShoppingCartOutlined,
  TeamOutlined,
  DatabaseOutlined,
} from "@ant-design/icons";
import { LogoutOutlined } from "@ant-design/icons";
import { Layout, Menu, Segmented, Tag } from "antd";
import { useEffect, useMemo, useState } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { SHOP_LABELS, shopStore, useShop, type ShopCode } from "../../features/shop/shop-store";
import { AppActionButton } from "../AppButtons";
import { logout } from "../../features/auth/auth-api";
import { authStore, useSession } from "../../features/auth/auth-store";
import { getSettings } from "../../features/settings/settings-api";

const { Header, Sider, Content } = Layout;

const ALL_ITEMS = [
  { key: "/", icon: <DashboardOutlined />, label: "Tổng quan" },
  { key: "/khach-hang", icon: <ContactsOutlined />, label: "Khách để lại thông tin" },
  { key: "/san-pham", icon: <AppstoreOutlined />, label: "Sản phẩm" },
  { key: "/nhap-kho", icon: <ImportOutlined />, label: "Nhập kho" },
  { key: "/ban-hang", icon: <ShoppingCartOutlined />, label: "Bán hàng" },
  { key: "/ton-kho", icon: <DatabaseOutlined />, label: "Tồn kho" },
  { key: "/nhan-vien", icon: <TeamOutlined />, label: "Nhân viên" },
  { key: "/cham-cong", icon: <CalendarOutlined />, label: "Chấm công" },
  { key: "/tien-cong", icon: <DollarOutlined />, label: "Tiền công" },
  { key: "/thu-chi", icon: <AccountBookOutlined />, label: "Thu chi" },
  { key: "/bao-cao", icon: <BarChartOutlined />, label: "Báo cáo" },
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

export function AppShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const session = useSession();
  const shop = useShop();
  const role = session?.role ?? "ADMIN";
  const [shopName, setShopName] = useState("Quản lý hàng hóa nhôm");

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

  const menuItems = useMemo(() => {
    return ALL_ITEMS.filter((item) => {
      if (PAYROLL_PATHS.has(item.key) && role !== "MANUFACTURING") {
        return false;
      }
      if (ADMIN_ONLY_PATHS.has(item.key) && role !== "ADMIN") {
        return false;
      }
      if (item.key === "/khach-hang" && role === "DISTRIBUTION") {
        return false;
      }
      return true;
    });
  }, [role]);

  const selected =
    location.pathname === "/noi-dung-gioi-thieu"
      ? "/khach-hang"
      : menuItems.some((item) => item.key === location.pathname)
        ? location.pathname
        : "/";

  return (
    <Layout style={{ minHeight: "100vh", width: "100%" }}>
      <Sider width={232} theme="dark" style={{ background: "#1e2933", minHeight: "100vh" }}>
        <div className="brand-block">{shopName}</div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selected]}
          items={menuItems}
          style={{ background: "#1e2933" }}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout style={{ minWidth: 0, flex: 1 }}>
        <Header style={{ background: "#fff", padding: "0 20px", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
          {role === "ADMIN" ? (
            <Segmented
              value={shop}
              options={(Object.keys(SHOP_LABELS) as ShopCode[]).map((code) => ({
                value: code,
                label: SHOP_LABELS[code],
              }))}
              onChange={(value) => {
                shopStore.set(value as ShopCode);
              }}
            />
          ) : (
            <Tag color="blue">{SHOP_LABELS[shop]}</Tag>
          )}
          <span style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <Tag>{ROLE_LABEL[role] ?? role}</Tag>
            <span>{session?.username}</span>
            <AppActionButton
              tone="cancel"
              icon={<LogoutOutlined />}
              onClick={() => {
                void logout()
                  .catch(() => undefined)
                  .finally(() => {
                    authStore.clear();
                    navigate("/dang-nhap", { replace: true });
                  });
              }}
            >
              Đăng xuất
            </AppActionButton>
          </span>
        </Header>
        <Content className="app-content">
          <Outlet key={`${role}-${shop}`} />
        </Content>
      </Layout>
    </Layout>
  );
}
