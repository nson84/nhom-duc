import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { AppShell } from "../components/layout/AppShell";
import { AttendancePage } from "../features/attendance/AttendancePage";
import { EmployeesPage } from "../features/attendance/EmployeesPage";
import { PayrollPage } from "../features/attendance/PayrollPage";
import { ChangePasswordPage } from "../features/auth/ChangePasswordPage";
import { LoginPage } from "../features/auth/LoginPage";
import { useSession } from "../features/auth/auth-store";
import { BackupsPage } from "../features/backup/BackupsPage";
import { LandingContentPage } from "../features/landing/LandingContentPage";
import { LandingPage } from "../features/landing/LandingPage";
import { LeadsPage } from "../features/landing/LeadsPage";
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { FinancePage } from "../features/finance/FinancePage";
import { ReportsPage } from "../features/finance/ReportsPage";
import { ProductsPage } from "../features/products/ProductsPage";
import { SettingsPage } from "../features/settings/SettingsPage";
import { DocumentsPage } from "../features/stock/DocumentsPage";
import { StockPage } from "../features/stock/StockPage";

function LoginRoute() {
  const session = useSession();
  if (session?.mustChangePassword) {
    return <Navigate to="/doi-mat-khau" replace />;
  }
  if (session) {
    return <Navigate to="/" replace />;
  }
  return <LoginPage />;
}

function Guard() {
  const session = useSession();
  const location = useLocation();
  if (!session) {
    return <Navigate to="/dang-nhap" replace />;
  }
  if (session.mustChangePassword && location.pathname !== "/doi-mat-khau") {
    return <Navigate to="/doi-mat-khau" replace />;
  }
  if (!session.mustChangePassword && location.pathname === "/doi-mat-khau") {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}

function ManufacturingOnly({ children }: { children: ReactNode }) {
  const session = useSession();
  if (session?.role !== "MANUFACTURING") {
    return <Navigate to="/" replace />;
  }
  return children;
}

function AdminOnly({ children }: { children: ReactNode }) {
  const session = useSession();
  if (session?.role !== "ADMIN") {
    return <Navigate to="/" replace />;
  }
  return children;
}

export function AppRouter() {
  return (
    <Routes>
      <Route path="/gioi-thieu" element={<LandingPage />} />
      <Route path="/dang-nhap" element={<LoginRoute />} />
      <Route element={<Guard />}>
        <Route path="/doi-mat-khau" element={<ChangePasswordPage />} />
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="khach-hang" element={<LeadsPage />} />
          <Route path="noi-dung-gioi-thieu" element={<AdminOnly><LandingContentPage /></AdminOnly>} />
          <Route path="san-pham" element={<ProductsPage />} />
          <Route path="nhap-kho" element={<DocumentsPage kind="PURCHASE" />} />
          <Route path="ban-hang" element={<DocumentsPage kind="SALE" />} />
          <Route path="ton-kho" element={<StockPage />} />
          <Route path="nhan-vien" element={<ManufacturingOnly><EmployeesPage /></ManufacturingOnly>} />
          <Route path="cham-cong" element={<ManufacturingOnly><AttendancePage /></ManufacturingOnly>} />
          <Route path="tien-cong" element={<ManufacturingOnly><PayrollPage /></ManufacturingOnly>} />
          <Route path="thu-chi" element={<FinancePage />} />
          <Route path="bao-cao" element={<ReportsPage />} />
          <Route path="sao-luu" element={<AdminOnly><BackupsPage /></AdminOnly>} />
          <Route path="cai-dat" element={<AdminOnly><SettingsPage /></AdminOnly>} />
        </Route>
      </Route>
    </Routes>
  );
}
