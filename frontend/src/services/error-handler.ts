import axios from "axios";

export class ApiError extends Error {
  code?: string;
  status?: number;
  details?: unknown;

  constructor(message: string, code?: string, status?: number, details?: unknown) {
    super(message);
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; code?: string; details?: unknown } | undefined;
    if (!error.response) {
      return new ApiError("Không kết nối được máy chủ. Kiểm tra backend và MySQL.", "NETWORK", 0);
    }
    return new ApiError(
      data?.message || "Yêu cầu không thực hiện được",
      data?.code,
      error.response.status,
      data?.details,
    );
  }
  return new ApiError("Có lỗi không xác định");
}
