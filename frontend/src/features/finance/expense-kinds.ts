export const expenseKinds = [
  { value: "ELECTRICITY", label: "Tiền điện", category: "OPERATING_EXPENSE" },
  { value: "DAILY_MEALS", label: "Tiền ăn mỗi ngày", category: "OPERATING_EXPENSE" },
  { value: "BREAK_SNACKS", label: "Tiền nước + bánh kẹo giữa giờ", category: "OPERATING_EXPENSE" },
  { value: "MACHINE_SUPPLIES", label: "Tiền vật tư máy móc", category: "OPERATING_EXPENSE" },
  { value: "REPAIR_MAINTENANCE", label: "Tiền sửa chữa hoặc thay thế bảo dưỡng", category: "MAINTENANCE" },
] as const;

export type ExpenseKind = (typeof expenseKinds)[number]["value"];

export function expenseLabelFromDescription(description: string | null | undefined): string | null {
  return expenseKinds.find(({ label }) => description === label || description?.startsWith(`${label}: `))?.label ?? null;
}
