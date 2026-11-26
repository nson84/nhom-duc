import { printDate, printEscape as e, printMoney as money, printNumber as number, printPage } from "../../components/print-document";
import type { CustomerDocument, CustomerSummary } from "./customers-api";
import type { StockDocument } from "../stock/stock-api";

export function buildCustomerHistoryHtml(customer: CustomerSummary, rows: CustomerDocument[], documents: StockDocument[], range: [string, string] | null, accountQr = "") {
  const dateOnly = (value: string) => printDate(value.slice(0, 10));
  const tonnes = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 6 });
  const categories = new Map<string, number>();
  const methods: Record<string, string> = { CASH: "Tiền mặt", TRANSFER: "Chuyển khoản", OTHER: "Khác" };
  const byId = new Map(documents.map((document) => [document.id, document]));
  const sorted = [...rows].sort((a, b) => b.documentDate.localeCompare(a.documentDate) || b.id - a.id);
  const totals = sorted.reduce((result, row) => {
    const document = byId.get(row.id);
    for (const item of document?.items ?? []) {
      const kg = Number(item.quantityBase || 0);
      const category = item.categoryName || "Chưa rõ loại";
      result.kg += kg;
      categories.set(category, (categories.get(category) ?? 0) + kg);
    }
    if (!row.internalTransfer) {
      result.amount += Number(row.totalAmount || 0);
      result.paid += Number(row.paidAmount || 0);
      result.debt += Number(row.remainingAmount || 0);
    }
    return result;
  }, { kg: 0, amount: 0, paid: 0, debt: 0 });
  const body = `<h1>Lịch sử mua hàng — ${e(customer.name)}</h1>
    <p>${e(customer.shopName)} · Điện thoại: ${e(customer.phone || "—")}</p>
    <p>Ngày lập phiếu: ${range ? `${printDate(range[0])} – ${printDate(range[1])}` : "Tất cả thời gian"}. Sắp xếp mới nhất trước.</p>
    <p class="muted">Tiền đã trả tính mọi lần thanh toán của các phiếu trong bộ lọc, kể cả trả sau khoảng ngày lập phiếu. Chuyển nội bộ chỉ cộng khối lượng.</p>
    ${sorted.map((row) => {
      const document = byId.get(row.id);
      if (!document) return "";
      const internal = row.internalTransfer;
      const payments = [...(document.payments ?? [])].sort((a, b) => a.paymentDate.localeCompare(b.paymentDate));
      return `<section class="invoice"><h2>${e(row.documentCode)} · ${dateOnly(row.documentDate)}${internal ? " · Nội bộ" : ""}</h2>
        <table><thead><tr><th>Tên sản phẩm</th><th>Loại nhôm</th><th>Số lượng</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead><tbody>
        ${document.items.map((item) => `<tr><td>${e(item.productName)}</td><td>${e(item.categoryName || "—")}</td><td>${number(item.quantityDocument)} ${e(item.unitName)}</td><td>${money(item.unitPrice)}</td><td>${money(item.lineTotal)}</td></tr>`).join("")}</tbody></table>
        ${internal ? `<p class="muted">Giá chuyển nội bộ: ${money(row.totalAmount)}. Chỉ cộng khối lượng.</p>` : `
          <p><strong>Đã trả: ${money(row.paidAmount)}</strong></p>
          ${payments.length ? `<ul class="invoice-payments">${payments.map((payment) => {
            const method = payment.paymentMethod;
            const label = method ? methods[method] ?? method : "";
            const tone = label === "Tiền mặt" ? "cash" : label === "Chuyển khoản" ? "transfer" : "other";
            return `<li><span>${dateOnly(payment.paymentDate)}</span><span class="payment-amount">${money(payment.amount)}</span><span>${label ? `<span class="payment-method payment-method-${tone}">${e(label)}</span>` : ""}</span></li>`;
          }).join("")}</ul>` : ""}
          <p><strong>Còn nợ: ${money(row.remainingAmount)}</strong></p>`}
      </section>`;
    }).join("") || "<p>Không có hóa đơn trong bộ lọc.</p>"}
    <h2>Tổng kết các hóa đơn đã lọc</h2><table><thead><tr><th>Tổng khối lượng nhôm</th><th>Tổng tiền các phiếu</th><th>Tổng tiền đã trả</th><th>Tổng còn nợ</th></tr></thead><tbody><tr class="total"><td>${tonnes.format(totals.kg / 1000)} tấn${[...categories].map(([name, kg]) => `<br>${e(name)}: ${tonnes.format(kg / 1000)} tấn`).join("")}</td><td>${money(totals.amount)}</td><td>${money(totals.paid)}</td><td>${money(totals.debt)}</td></tr></tbody></table>`;
  return printPage(`Lịch sử mua hàng ${customer.name}`, body + accountQr);
}
