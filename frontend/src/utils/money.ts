/** Hiển thị tiền: dấu phẩy ngăn nghìn (75,000,000 đ). */
export function formatMoney(value: number | string): string {
  const amount = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(amount)) {
    return "0 đ";
  }
  const rounded = Math.round(amount);
  const sign = rounded < 0 ? "-" : "";
  const digits = String(Math.abs(rounded)).replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return `${sign}${digits} đ`;
}
