import type { Rule } from "antd/es/form";

/** Chuẩn hóa mọi kiểu giá trị ô tiền (số / chuỗi có dấu phẩy) → số nguyên VND. */
export function toVndInteger(value: unknown): number | null {
  if (value === undefined || value === null || value === "") return null;
  if (typeof value === "number") {
    return Number.isFinite(value) ? Math.round(value) : null;
  }
  const digits = String(value).replace(/[^\d]/g, "");
  if (!digits) return null;
  const n = Number(digits);
  return Number.isFinite(n) ? n : null;
}

/** Làm tròn về hàng nghìn (bội số 1.000 đ). */
export function roundVndToThousand(value: number): number {
  if (!Number.isFinite(value) || value <= 0) return 0;
  return Math.round(value / 1000) * 1000;
}

export function formatVndInput(value: number | string | undefined): string {
  const n = toVndInteger(value);
  if (n == null) return "";
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

export function parseVndInput(displayValue: string | undefined): number {
  return toVndInteger(displayValue) ?? 0;
}

function isEmptyFormNumber(val: unknown): boolean {
  return val === undefined || val === null || val === "";
}

/** Chỉ kiểm tra khi blur / gửi form — không báo lỗi khi đang gõ. */
export const documentFieldValidateTrigger = ["onBlur", "onSubmit"] as const;

export const positiveIntegerRules: Rule[] = [
  {
    validator: async (_, val) => {
      if (isEmptyFormNumber(val)) {
        throw new Error("Nhập số lượng");
      }
      const n = typeof val === "number" ? val : Number(val);
      if (!Number.isFinite(n) || n <= 0 || !Number.isInteger(n)) {
        throw new Error("Chỉ nhập số nguyên dương");
      }
    },
  },
];

/** Số lượng phiếu: thập phân được phép, phải > 0, không âm. */
export const positiveDecimalRules: Rule[] = [
  {
    validator: async (_, val) => {
      if (isEmptyFormNumber(val)) {
        throw new Error("Nhập số lượng");
      }
      const n = typeof val === "number" ? val : Number(val);
      if (!Number.isFinite(n) || n <= 0) {
        throw new Error("Số lượng phải lớn hơn 0, không nhập số âm");
      }
    },
  },
];

/** Số tiền chung: nguyên, không thập phân. */
export const vndAmountRules: Rule[] = [
  {
    validator: async (_, val) => {
      if (isEmptyFormNumber(val)) {
        throw new Error("Nhập số tiền");
      }
      const n = toVndInteger(val);
      if (n == null || n < 0) {
        throw new Error("Không nhập số thập phân, chỉ số nguyên");
      }
    },
  },
];

/** Đơn giá hợp lệ kết thúc ,000 hoặc ,500 (bội 500 đ). */
export const UNIT_PRICE_STEP_ERROR =
  "Đơn giá phải là bội 500 đ (VD: 75,000 hoặc 75,500)";

/** Thu/chi / trả nợ: cùng bước 500 đ (không nhận số lẻ kiểu 1,111). */
export const PAYMENT_AMOUNT_STEP_ERROR =
  "Số tiền phải là bội 500 đ (VD: 1,000 hoặc 1,500)";

/** true nếu số hợp lệ làm đơn giá (0 hoặc bội 500 — dạng …,000 / …,500). */
export function isValidUnitPriceVnd(n: number | null): boolean {
  if (n == null || n < 0) return false;
  if (n === 0) return true;
  return n % 500 === 0;
}

/** Alias rõ nghĩa cho số tiền thanh toán / trả nợ. */
export const isValidPaymentAmountVnd = isValidUnitPriceVnd;

/**
 * Khi blur ô đơn giá: nếu đang gõ dở (không phải bội 500) → trả về `fallback`
 * (số đúng trước đó hoặc 0). Không làm tròn / ghi đè trong lúc đang gõ.
 */
export function commitUnitPriceOnBlur(raw: unknown, fallback: number): {
  value: number;
  reverted: boolean;
  error: string | null;
} {
  if (isEmptyFormNumber(raw)) {
    return { value: fallback, reverted: false, error: null };
  }
  const n = toVndInteger(raw);
  if (n == null || n < 0) {
    return { value: fallback, reverted: true, error: "Không nhập số thập phân, chỉ số nguyên" };
  }
  if (!isValidUnitPriceVnd(n)) {
    return { value: fallback, reverted: true, error: UNIT_PRICE_STEP_ERROR };
  }
  return { value: n, reverted: false, error: null };
}

/** Đơn giá: nguyên + bội 500 (…000 hoặc …500). Chỉ chạy khi blur/submit. */
export const vndUnitPriceRules: Rule[] = [
  {
    validator: async (_, val) => {
      if (isEmptyFormNumber(val)) {
        throw new Error("Nhập số tiền");
      }
      const n = toVndInteger(val);
      if (n == null || n < 0) {
        throw new Error("Không nhập số thập phân, chỉ số nguyên");
      }
      if (!isValidUnitPriceVnd(n)) {
        throw new Error(UNIT_PRICE_STEP_ERROR);
      }
    },
  },
];

/** Số tiền thu / chi / trả nợ: nguyên + bội 500. */
export const vndPaymentAmountRules: Rule[] = [
  {
    validator: async (_, val) => {
      if (isEmptyFormNumber(val)) {
        throw new Error("Nhập số tiền");
      }
      const n = toVndInteger(val);
      if (n == null || n <= 0) {
        throw new Error("Số tiền phải lớn hơn 0");
      }
      if (!isValidPaymentAmountVnd(n)) {
        throw new Error(PAYMENT_AMOUNT_STEP_ERROR);
      }
    },
  },
];

/** InputNumber VND: không thập phân, không nút +/-, dấu phẩy ngàn, hậu tố đ. */
export const vndInputNumberProps = {
  min: 0 as const,
  precision: 0 as const,
  step: 500 as const,
  controls: false as const,
  formatter: formatVndInput,
  parser: parseVndInput,
  suffix: "đ" as const,
  inputMode: "numeric" as const,
};

/**
 * Số lượng: nhập 1.5 hoặc 1,5 đều chuẩn hóa thành 1,5 (dấu phẩy = thập phân).
 */
export function formatQtyInput(value: number | string | undefined): string {
  if (value === undefined || value === null || value === "") return "";
  const n = typeof value === "number" ? value : parseQtyInput(String(value));
  if (!Number.isFinite(n)) return "";
  const trimmed = n
    .toFixed(6)
    .replace(/(\.\d*?[1-9])0+$/u, "$1")
    .replace(/\.0+$/u, "")
    .replace(/\.$/u, "");
  return trimmed.replace(".", ",");
}

export function parseQtyInput(displayValue: string | undefined): number {
  let s = (displayValue ?? "").replace(/\s/g, "");
  if (!s) return 0;
  if (s.includes(",") && s.includes(".")) {
    const lastComma = s.lastIndexOf(",");
    const lastDot = s.lastIndexOf(".");
    if (lastComma > lastDot) {
      s = s.replace(/\./g, "").replace(",", ".");
    } else {
      s = s.replace(/,/g, "");
    }
  } else if (s.includes(",")) {
    s = s.replace(",", ".");
  }
  const n = Number(s);
  return Number.isFinite(n) ? n : 0;
}

export const qtyInputNumberProps = {
  min: 0.000001 as const,
  step: 0.1 as const,
  controls: false as const,
  formatter: formatQtyInput,
  parser: parseQtyInput,
  inputMode: "decimal" as const,
};
