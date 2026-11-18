import { printDate, printEscape as e, printMoney as money, printNumber as number, printPage } from "../../components/print-document";
import type { CustomerDocument, CustomerSummary } from "./customers-api";
import type { StockDocument } from "../stock/stock-api";

export function buildCustomerHistoryHtml(customer: CustomerSummary, rows: CustomerDocument[], documents: StockDocument[], range: [string, string] | null) {
  const byId = new Map(documents.map((document) => [document.id, document]));
  const sorted = [...rows].sort((a, b) => b.documentDate.localeCompare(a.documentDate) || b.id - a.id);
  const totals = sorted.reduce((result, row) => {
    const document = byId.get(row.id);
    result.kg += (document?.items ?? []).reduce((kg, item) => kg + Number(item.quantityBase || 0), 0);
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
      return `<section class="invoice"><h2>${e(row.documentCode)} · ${printDate(row.documentDate)}${internal ? " · Nội bộ" : ""}</h2>
        <p>Người xuất: ${e(document.issuedBy || "—")} · Người nhận: ${e(document.receivedBy || "—")}</p>
        <table><thead><tr><th>Sản phẩm / loại nhôm</th><th>Số lượng</th><th>Khối lượng nhôm</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead><tbody>
        ${document.items.map((item) => `<tr><td>${e(item.productName)}${item.categoryName ? ` / ${e(item.categoryName)}` : ""}</td><td>${number(item.quantityDocument)} ${e(item.unitName)}</td><td>${number(item.quantityBase)} kg</td><td>${money(item.unitPrice)}</td><td>${money(item.lineTotal)}</td></tr>`).join("")}</tbody></table>
        ${internal ? `<p class="muted">Giá trị chuyển nội bộ: ${money(row.totalAmount)}; không tính vào tiền hàng và nợ khách.</p>` : ""}
        ${payments.length ? `<h3>Các lần khách đã trả</h3><table><thead><tr><th>Ngày giờ</th><th>Số tiền</th><th>Hình thức</th><th>Người thu</th><th>Ghi chú</th></tr></thead><tbody>${payments.map((payment) => `<tr><td>${printDate(payment.paymentDate)}</td><td>${money(payment.amount)}</td><td>${e(payment.paymentMethod || "—")}</td><td>${e(payment.collectedBy || "—")}</td><td>${e(payment.note || "")}</td></tr>`).join("")}</tbody></table>` : "<p>Chưa có lần thanh toán.</p>"}
        <table><thead><tr><th>Khối lượng nhôm</th><th>Tiền hàng</th><th>Đã trả</th><th>Còn nợ</th></tr></thead><tbody><tr class="total"><td>${number(document.items.reduce((kg, item) => kg + Number(item.quantityBase || 0), 0))} kg</td><td>${money(internal ? 0 : row.totalAmount)}</td><td>${money(internal ? 0 : row.paidAmount)}</td><td>${money(internal ? 0 : row.remainingAmount)}</td></tr></tbody></table>
      </section>`;
    }).join("") || "<p>Không có hóa đơn trong bộ lọc.</p>"}
    <h2>Tổng kết các hóa đơn đã lọc</h2><table><thead><tr><th>Khối lượng nhôm</th><th>Tổng tiền hàng</th><th>Khách đã trả</th><th>Khách còn nợ</th></tr></thead><tbody><tr class="total"><td>${number(totals.kg)} kg</td><td>${money(totals.amount)}</td><td>${money(totals.paid)}</td><td>${money(totals.debt)}</td></tr></tbody></table>`;
  return printPage(`Lịch sử mua hàng ${customer.name}`, body);
}
