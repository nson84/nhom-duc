import axios from "axios";

export const runningBackend = { version: "Đang kiểm tra", revision: "unversioned", schemaVersion: "—" };

export async function waitUntilReady(signal: AbortSignal): Promise<void> {
  const started = Date.now();
  let lastError = "Không kết nối được máy chủ.";
  while (Date.now() - started < 60_000) {
    if (signal.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
    try {
      const response = await axios.get<{ success: boolean; data?: { status?: string; database?: string; application?: string; version?: string; revision?: string; schemaVersion?: string } }>(
        `${import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080"}/api/v1/system/ready`,
        { timeout: 2500, signal },
      );
      if (response.data.success && response.data.data?.status === "UP"
          && response.data.data.database === "UP" && response.data.data.application === "aluminum-manager") {
        runningBackend.version = response.data.data.version ?? "Không xác định";
        runningBackend.revision = response.data.data.revision ?? "unversioned";
        runningBackend.schemaVersion = response.data.data.schemaVersion ?? "—";
        return;
      }
      lastError = response.data.data?.application === "aluminum-manager"
        ? "Máy chủ đã mở nhưng cơ sở dữ liệu chưa sẵn sàng."
        : "Cổng API đang được dịch vụ khác sử dụng hoặc backend không đúng phiên bản.";
    } catch (error) {
      if (signal.aborted) {
        throw error;
      }
      lastError = "Không kết nối được backend hoặc MySQL chưa chạy.";
    }
    await sleep(1000, signal);
  }
  throw new Error(lastError);
}

function sleep(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timer = window.setTimeout(resolve, ms);
    signal.addEventListener("abort", () => {
      window.clearTimeout(timer);
      reject(new DOMException("Aborted", "AbortError"));
    }, { once: true });
  });
}
