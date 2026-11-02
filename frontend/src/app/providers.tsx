import { App as AntApp, ConfigProvider } from "antd";
import viVN from "antd/locale/vi_VN";
import { BrowserRouter } from "react-router-dom";
import { ReadyGate } from "../components/startup/ReadyGate";
import { AppRouter } from "./router";

/** Primary Ant Design = xanh biển tối (nút Tạo / Thêm). Các vai trò khác dùng AppActionButton + tone. */
export function Providers() {
  return (
    <ConfigProvider
      locale={viVN}
      theme={{
        token: {
          colorPrimary: "#2f6f8f",
          colorPrimaryHover: "#3a86ab",
          colorPrimaryActive: "#245a73",
          colorLink: "#2f6f8f",
          colorError: "#A52A2A",
          borderRadius: 6,
          fontFamily: '"Segoe UI", sans-serif',
          colorBgLayout: "#f3f5f7",
        },
        components: {
          Button: {
            primaryColor: "#ffffff",
            borderRadius: 999,
          },
        },
      }}
    >
      <AntApp>
        <ReadyGate>
          <BrowserRouter>
            <AppRouter />
          </BrowserRouter>
        </ReadyGate>
      </AntApp>
    </ConfigProvider>
  );
}
