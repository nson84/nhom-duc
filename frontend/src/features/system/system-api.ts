import axios from "axios";

export async function waitUntilReady(signal: AbortSignal): Promise<void> {
  const started = Date.now();
  let lastError = "Không kết nối được máy chủ.";
  while (Date.now() - started < 60_000) {
    if (signal.aborted) {
      throw new DOMException("Aborted", "AbortError");
    }
    try {
      const response = await axios.get<{ success: boolean; data?: { status?: string } }>(
        `${import.meta.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080"}/api/v1/system/ready`,
        { timeout: 2500, signal },
      );
      if (response.data.success && response.data.data?.status === "UP") {
        return;
      }
      lastError = "Máy chủ đã mở nhưng cơ sở dữ liệu chưa sẵn sàng.";
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
