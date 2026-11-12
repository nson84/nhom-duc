import { createBrowserRouter, createRoutesFromElements, Navigate, Outlet, Route, useLocation } from "react-router-dom";
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
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { FinancePage } from "../features/finance/FinancePage";
import { ReportsPage } from "../features/finance/ReportsPage";
import { ProductsPage } from "../features/products/ProductsPage";
import { BankAccountsPage } from "../features/settings/BankAccountsPage";
import { SettingsPage } from "../features/settings/SettingsPage";
import { CustomerDetailPage } from "../features/customers/CustomerDetailPage";
import { CustomersPage } from "../features/customers/CustomersPage";
import { DocumentsPage } from "../features/stock/DocumentsPage";
import { FactoryYearPage } from "../features/factory/FactoryYearPage";
import { CombinedYearPage } from "../features/admin/CombinedYearPage";
import { StockPage } from "../features/stock/StockPage";
import { AnnualBookPage } from "../features/annual-book/AnnualBookPage";

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

function HomePage() {
  return useSession()?.role === "ADMIN" ? <Navigate to="/tong-hop-hai-ben" replace /> : <DashboardPage />;
}

export const appRouter = createBrowserRouter(createRoutesFromElements(
    <>
      <Route path="/gioi-thieu" element={<LandingPage />} />
      <Route path="/dang-nhap" element={<LoginRoute />} />
      <Route element={<Guard />}>
        <Route path="/doi-mat-khau" element={<ChangePasswordPage />} />
        <Route element={<AppShell />}>
          <Route index element={<HomePage />} />
          <Route path="khach-mua" element={<CustomersPage />} />
          <Route path="khach-mua/:id" element={<CustomerDetailPage />} />
          <Route path="noi-dung-gioi-thieu" element={<AdminOnly><LandingContentPage /></AdminOnly>} />
          <Route path="san-pham" element={<ProductsPage />} />
          <Route path="nhap-kho" element={<DocumentsPage key="PURCHASE" kind="PURCHASE" />} />
          <Route path="ban-hang" element={<DocumentsPage key="SALE" kind="SALE" />} />
          <Route path="ton-kho" element={<StockPage />} />
          <Route path="quyet-toan-xuong" element={<FactoryYearPage />} />
          <Route path="tong-hop-hai-ben" element={<AdminOnly><CombinedYearPage /></AdminOnly>} />
          <Route path="nhan-vien" element={<ManufacturingOnly><EmployeesPage /></ManufacturingOnly>} />
          <Route path="cham-cong" element={<ManufacturingOnly><AttendancePage /></ManufacturingOnly>} />
          <Route path="tien-cong" element={<ManufacturingOnly><PayrollPage /></ManufacturingOnly>} />
          <Route path="thu-chi" element={<FinancePage />} />
          <Route path="bao-cao" element={<ReportsPage />} />
          <Route path="so-cuoi-nam" element={<AnnualBookPage />} />
          <Route path="sao-luu" element={<AdminOnly><BackupsPage /></AdminOnly>} />
          <Route path="tai-khoan-qr" element={<BankAccountsPage />} />
          <Route path="cai-dat" element={<AdminOnly><SettingsPage /></AdminOnly>} />
        </Route>
      </Route>
    </>,
));
