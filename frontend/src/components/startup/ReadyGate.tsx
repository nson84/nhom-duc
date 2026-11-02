import { Button, Card, Spin } from "antd";
import { useEffect, useState, type ReactNode } from "react";
import { waitUntilReady } from "../../features/system/system-api";

export function ReadyGate({ children }: { children: ReactNode }) {
  const [attempt, setAttempt] = useState(0);
  const [status, setStatus] = useState<"waiting" | "ready" | "error">("waiting");
  const [message, setMessage] = useState("Đang chờ máy chủ và cơ sở dữ liệu...");

  useEffect(() => {
    const controller = new AbortController();
    setStatus("waiting");
    setMessage("Đang chờ máy chủ và cơ sở dữ liệu...");
    waitUntilReady(controller.signal)
      .then(() => {
        if (!controller.signal.aborted) {
          setStatus("ready");
        }
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setStatus("error");
        setMessage(error instanceof Error ? error.message : "Không kết nối được máy chủ.");
      });
    return () => controller.abort();
  }, [attempt]);

  if (status === "ready") {
    return children;
  }

  return (
    <div className="startup-screen">
      <Card className="startup-card" title="Quản lý hàng hóa nhôm">
        {status === "waiting" ? <Spin /> : null}
        <p>{message}</p>
        {status === "error" ? (
          <>
            <p>Kiểm tra MySQL đang chạy trên máy này, sau đó khởi động lại backend Spring Boot.</p>
            <Button type="primary" onClick={() => setAttempt((value) => value + 1)}>
              Thử lại
            </Button>
          </>
        ) : null}
      </Card>
    </div>
  );
}
