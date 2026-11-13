import type { AnnualBook } from "./annual-book-api";
import { expenseLabelFromDescription } from "../finance/expense-kinds";

const money = (value: number | null | undefined) => value == null ? "—" : new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 }).format(value) + " đ";
const qty = (value: number | null | undefined) => value == null ? "—" : new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(value);
const date = (value: string) => new Date(value).toLocaleString("vi-VN", { hour12: false });
const day = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString("vi-VN");
const esc = (value: string | number | null | undefined) => String(value ?? "").replace(/[&<>"']/g, (char) => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
})[char] ?? char);
const sum = (values: number[]) => values.reduce((total, value) => total + Number(value), 0);
const row = (values: (string | number)[], cells = "td") => `<tr>${values.map((value) => `<${cells}>${value}</${cells}>`).join("")}</tr>`;
const table = (headers: string[], rows: string[][], empty = "Không có dữ liệu") => `<table><thead>${row(headers.map(esc), "th")}</thead><tbody>${rows.length ? rows.map((cells) => row(cells)).join("") : `<tr><td colspan="${headers.length}">${empty}</td></tr>`}</tbody></table>`;
const categoryNames: Record<string, string> = {
  SALE_RECEIPT: "Thu tiền bán hàng", OTHER_INCOME: "Thu khác / bán phế liệu",
  PURCHASE_PAYMENT: "Trả tiền nhập hàng", PAYROLL: "Chi công thợ",
  OPERATING_EXPENSE: "Chi phí hoạt động / ăn uống", MAINTENANCE: "Bảo trì",
  OTHER_EXPENSE: "Chi khác",
};

function saleBlock(book: AnnualBook, sale: AnnualBook["sales"][number]) {
  const items = book.saleItems.filter((item) => item.documentId === sale.id);
  const payments = book.payments.filter((payment) => payment.documentId === sale.id);
  const remaining = Math.max(0, sale.total - sale.paidAsOf);
  const paid = remaining <= 0;
  return `<div class="invoice ${paid ? "invoice-paid" : "invoice-unpaid"}">
    <div class="invoice-heading"><h4>${esc(sale.code)} · ${esc(date(sale.date))}</h4><span class="payment-status ${paid ? "status-paid" : "status-unpaid"}">${paid ? "✓ Đã thanh toán" : sale.paidAsOf > 0 ? "⚠ Thanh toán một phần" : "⚠ Chưa thanh toán"}</span></div>
    <p>Khách: ${esc(sale.customerName || "Chưa gắn khách")} · Người bán: ${esc(sale.seller || "—")} · Người nhận: ${esc(sale.receiver || "—")}</p>
    ${table(["Sản phẩm", "Loại nhôm", "Số lượng", "Đơn giá", "Thành tiền"], items.map((item) => [
      esc(item.product), esc(item.category), `${qty(item.quantity)} ${esc(item.unit)}`, money(item.unitPrice), money(item.amount),
    ]))}
    ${table(["Tiền hóa đơn", `Đã thu đến ${day(book.periodTo)}`, "Còn phải thu"], [[money(sale.total), money(sale.paidAsOf), money(remaining)]])}
    ${sale.notes ? `<p>Ghi chú: ${esc(sale.notes)}</p>` : ""}
    ${table(["Lần thu", "Số tiền", "Hình thức", "Người thu", "Ghi chú"], payments.map((payment) => [
      esc(date(payment.date)), money(payment.amount), esc(payment.method), esc(payment.collector || "—"), esc(payment.note || ""),
    ]), "Chưa ghi thu")}
  </div>`;
}

function transactions(book: AnnualBook, kind: "receipts" | "expenses") {
  const entries = book[kind];
  const groupOf = (entry: typeof entries[number]) => kind === "expenses"
    ? expenseLabelFromDescription(entry.description) ?? entry.category : entry.category;
  const groups = [...new Set(entries.map(groupOf))];
  return `<section class="chapter"><h2>${kind === "receipts" ? "Sổ thu" : "Sổ chi"} · ${esc(book.shopName)}</h2>
    <p>Ghi theo ngày thu/chi thực tế từ ${day(book.periodFrom)} đến ${day(book.periodTo)}. Tiền thu nợ cũ hoặc trả nợ cũ không tính lãi lần hai.</p>
    ${groups.map((category) => {
      const rows = entries.filter((entry) => groupOf(entry) === category);
      return `<h3>${esc(categoryNames[category] ?? category)} · ${money(sum(rows.map((entry) => entry.amount)))}</h3>`
        + table(["Ngày giờ", "Mã", "Đối tác", "Chứng từ", "Người chi", "Nội dung", "Số tiền"], rows.map((entry) => [
          esc(date(entry.date)), esc(entry.code), esc(entry.counterparty), esc(entry.reference),
          esc(entry.spentBy || ""), esc(entry.description || ""), money(entry.amount),
        ]));
    }).join("") || "<p>Không có giao dịch.</p>"}
    <p class="grand">Tổng ${kind === "receipts" ? "thu" : "chi"}: ${money(sum(entries.map((entry) => entry.amount)))}</p>
  </section>`;
}

function shopHtml(book: AnnualBook) {
  const debtors = book.customers.filter((customer) => customer.closingDebt > 0);
  return `<section class="shop-cover"><h1>${esc(book.shopName)}</h1><h2>Sổ tổng hợp cuối năm ${book.year}</h2>
    <p>Kỳ từ ${day(book.periodFrom)} đến hết ngày ${day(book.periodTo)}</p>
    <p>Trạng thái: <strong>${book.closed ? "Đã chốt năm" : "Chưa có bản chốt năm - số liệu tạm tính"}</strong></p>
    <div class="summary"><div>Khách đã gắn còn nợ <b>${money(sum(book.customers.map((c) => c.closingDebt)))}</b></div>
      <div>Phiếu chưa gắn khách còn nợ <b>${money(sum(book.sales.filter((s) => s.customerId == null).map((s) => Math.max(0, s.total - s.paidAsOf))))}</b></div>
      <div>Tồn nhôm <b>${qty(sum(book.stock.map((s) => s.quantityKg)))} kg</b></div>
      <div>Giá trị tồn đã chốt <b>${money(book.inventoryValue)}</b></div>
      <div>Còn nợ nhà cung cấp <b>${money(sum(book.purchases.map((p) => Math.max(0, p.total - p.paidAsOf))))}</b></div>
      <div>Lãi đã chốt <b>${money(book.profit)}</b></div></div>
    <h3>Nội dung</h3><ol><li>Tổng hợp khách mua</li><li>Chi tiết từng khách và phiếu bán</li>
      <li>Khách còn nợ cuối năm</li><li>Sổ thu</li><li>Sổ chi</li><li>Phiếu nhập và nợ nhà cung cấp</li><li>Công thợ theo tháng</li><li>Tồn kho cuối năm</li></ol>
  </section>
  <section class="chapter"><h2>Tổng hợp khách mua · ${esc(book.shopName)}</h2>
    ${table(["Khách hàng", "Điện thoại", "Nợ đầu năm", "Mua trong năm", "Đã trả trong năm", "Nợ cuối năm"],
      book.customers.map((c) => [esc(c.name), esc(c.phone || ""), money(c.openingDebt), money(c.sales), money(c.receipts), money(c.closingDebt)]))}
    <p class="grand">Tổng mua: ${money(sum(book.customers.map((c) => c.sales)))} · Tổng đã trả: ${money(sum(book.customers.map((c) => c.receipts)))} · Còn nợ: ${money(sum(book.customers.map((c) => c.closingDebt)))}</p>
  </section>
  <section class="chapter"><h2>Chi tiết khách mua · ${esc(book.shopName)}</h2>
    ${book.customers.map((customer) => {
      const sales = book.sales.filter((sale) => sale.customerId === customer.id);
      return `<article class="customer"><h3>${esc(customer.name)}</h3>
        <p>Điện thoại: ${esc(customer.phone || "—")} · Ghi chú: ${esc(customer.note || "—")}</p>
        <p>Nợ đầu năm: ${money(customer.openingDebt)} · Mua trong năm: ${money(customer.sales)} · Đã trả trong năm: ${money(customer.receipts)} · Nợ cuối năm: <b>${money(customer.closingDebt)}</b></p>
        ${sales.length ? sales.map((sale) => saleBlock(book, sale)).join("") : "<p>Không có phiếu bán còn liên quan trong năm.</p>"}
      </article>`;
    }).join("") || "<p>Không có khách hàng.</p>"}
    ${book.sales.filter((sale) => sale.customerId == null).length ? `<article class="customer"><h3>Phiếu bán chưa gắn khách</h3>${book.sales.filter((sale) => sale.customerId == null).map((sale) => saleBlock(book, sale)).join("")}</article>` : ""}
  </section>
  <section class="chapter"><h2>Khách còn nợ cuối năm · ${esc(book.shopName)}</h2>
    ${table(["Khách", "Điện thoại", "Phiếu", "Ngày bán", "Tiền phiếu", "Đã thu", "Còn nợ"], debtors.flatMap((c) =>
      book.sales.filter((sale) => sale.customerId === c.id && sale.total > sale.paidAsOf).map((sale) => [
        esc(c.name), esc(c.phone || ""), esc(sale.code), esc(date(sale.date)), money(sale.total), money(sale.paidAsOf), money(sale.total - sale.paidAsOf),
      ])))}
    <p class="grand">Tổng nợ khách đã gắn: ${money(sum(debtors.map((c) => c.closingDebt)))}</p>
    ${book.sales.filter((s) => s.customerId == null && s.total > s.paidAsOf).length
      ? `<h3>Phiếu còn nợ chưa gắn khách</h3>${table(["Tên ghi trên phiếu", "Phiếu", "Ngày bán", "Còn nợ"],
        book.sales.filter((s) => s.customerId == null && s.total > s.paidAsOf).map((s) => [
          esc(s.customerName || "—"), esc(s.code), esc(date(s.date)), money(s.total - s.paidAsOf),
        ]))}` : ""}
  </section>
  ${transactions(book, "receipts")}${transactions(book, "expenses")}
  <section class="chapter"><h2>Phiếu nhập và nợ nhà cung cấp · ${esc(book.shopName)}</h2>
    <p>Gồm phiếu nhập trong năm, phiếu cũ còn nợ cuối năm và phiếu cũ có trả tiền trong năm. Tiền trả thực tế cũng nằm trong Sổ chi.</p>
    ${book.purchases.map((purchase) => `<div class="invoice"><h3>${esc(purchase.code)} · ${esc(date(purchase.date))}</h3>
      <p>Nhà cung cấp: ${esc(purchase.supplier || "—")}${purchase.notes ? ` · Ghi chú: ${esc(purchase.notes)}` : ""}</p>
      ${table(["Sản phẩm", "Loại nhôm", "Số lượng", "Đơn giá", "Thành tiền"],
        book.purchaseItems.filter((item) => item.documentId === purchase.id).map((item) => [
          esc(item.product), esc(item.category), `${qty(item.quantity)} ${esc(item.unit)}`, money(item.unitPrice), money(item.amount),
        ]))}
      <p class="total">Tiền phiếu: ${money(purchase.total)} · Đã trả đến ${day(book.periodTo)}: ${money(purchase.paidAsOf)} · Còn nợ: ${money(Math.max(0, purchase.total - purchase.paidAsOf))}</p>
    </div>`).join("") || "<p>Không có phiếu nhập liên quan.</p>"}
    <p class="grand">Tổng nợ nhà cung cấp từ phiếu: ${money(sum(book.purchases.map((p) => Math.max(0, p.total - p.paidAsOf))))}</p>
  </section>
  <section class="chapter"><h2>Công thợ theo tháng · ${esc(book.shopName)}</h2>
    <p>Tiền công tính theo kỳ. Tiền đã chi thực tế nằm trong Sổ chi; không cộng hai bảng thành một khoản chi.</p>
    ${table(["Tháng", "Nhân viên", "Ngày công", "Lương cơ bản", "Tăng ca", "Thưởng", "Phụ cấp", "Khấu trừ", "Tổng công"],
      book.wages.map((w) => [`${w.month}/${w.year}`, esc(w.employee), qty(w.days), money(w.basePay), money(w.overtimePay),
        money(w.bonusPay), money(w.allowances), money(w.deductions), money(w.totalPay)]),
      "Sổ này không có tiền công nhân viên")}
  </section>
  <section class="chapter"><h2>Tồn kho cuối năm · ${esc(book.shopName)}</h2>
    <p>Khối lượng theo mốc ${day(book.periodTo)}. Xưởng lấy số kiểm kê kỳ; Phân phối lấy biến động kho đến cuối kỳ.</p>
    ${table(["Sản phẩm / nhóm", "Loại nhôm", "Số lượng (kg)", "Giá trị kiểm kê"], book.stock.map((s) => [
      esc(s.product), esc(s.category), qty(s.quantityKg), money(s.value),
    ]))}
    <p class="grand">Tổng khối lượng: ${qty(sum(book.stock.map((s) => s.quantityKg)))} kg · Giá trị tồn đã chốt: ${money(book.inventoryValue)}</p>
  </section>`;
}

export function buildAnnualBookHtml(books: AnnualBook[]) {
  return `<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>Sổ cuối năm ${books[0]?.year ?? ""}</title>
  <style>
    @page { size: A4; margin: 16mm 13mm 17mm; @bottom-center { content: counter(page); font-size: 9pt; color: #666; } }
    * { box-sizing: border-box; } body { font: 10pt Arial, sans-serif; color: #192533; line-height: 1.35; margin: 0; }
    h1 { font-size: 25pt; margin: 0 0 8mm; } h2 { font-size: 16pt; border-bottom: 1.5pt solid #183b63; padding-bottom: 3mm; margin: 0 0 6mm; }
    h3 { font-size: 12pt; margin: 7mm 0 3mm; } h4 { font-size: 10pt; margin: 4mm 0 2mm; }
    p { margin: 2mm 0; } table { width: 100%; border-collapse: collapse; margin: 3mm 0 5mm; font-size: 8.5pt; }
    th, td { border: 0.5pt solid #aab6c1; padding: 1.8mm 2mm; text-align: left; vertical-align: top; overflow-wrap: anywhere; }
    th { background: #e8eef4; } thead { display: table-header-group; } tr, .invoice { break-inside: avoid; }
    .shop-cover { break-before: page; min-height: 230mm; padding-top: 25mm; }
    .shop-cover:first-child { break-before: auto; } .chapter { break-before: page; }
    .customer { break-before: page; } .customer:first-of-type { break-before: auto; }
    .invoice { border: 1pt solid #c8d2dc; border-radius: 2mm; padding: 3mm; margin: 5mm 0; }
    .invoice-heading { display: flex; justify-content: space-between; align-items: center; gap: 4mm; }
    .invoice-heading h4 { margin: 0 0 2mm; }
    .invoice-paid { border-left: 3pt solid #198754; }
    .invoice-unpaid { border: 1.2pt solid #c83c3c; border-left: 3pt solid #c83c3c; background: #fff4f2; }
    .payment-status { font-weight: bold; padding: 1.5mm 2mm; border-radius: 1mm; white-space: nowrap; }
    .status-paid { color: #166534; background: #dcfce7; }
    .status-unpaid { color: #991b1b; background: #fee2e2; }
    .summary { display: grid; grid-template-columns: 1fr 1fr; gap: 5mm; margin: 15mm 0; }
    .summary div { border: 1px solid #9fb0c2; padding: 5mm; } .summary b { display: block; margin-top: 2mm; }
    .total, .grand { text-align: right; font-weight: bold; } .grand { border-top: 1px solid #8c9dae; padding-top: 3mm; }
    @media screen { body { background: #dce3e9; padding: 15mm; } .shop-cover, .chapter { background: white; padding: 15mm; margin: 0 auto 12mm; width: 210mm; max-width: 100%; box-shadow: 0 2mm 10mm #0002; } }
  </style></head><body>${books.map(shopHtml).join("")}</body></html>`;
}
