import type { AnnualBook } from "./annual-book-api";
import { expenseLabelFromDescription } from "../finance/expense-kinds";

const money = (value: number | null | undefined) => value == null ? "—" : new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 }).format(value) + " đ";
const qty = (value: number | null | undefined) => value == null ? "—" : new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(value);
const date = (value: string) => `${day(value.slice(0, 10))} ${value.slice(11, 16)}`;
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
  const remaining = Math.max(0, sale.total - sale.paidNow);
  const paid = remaining <= 0;
  return `<div class="invoice ${paid ? "invoice-paid" : "invoice-unpaid"}">
    <div class="invoice-heading"><h4>${esc(sale.code)} · ${esc(date(sale.date))}</h4><span class="payment-status ${paid ? "status-paid" : "status-unpaid"}">${paid ? "✓ Đã thanh toán" : sale.paidNow > 0 ? "⚠ Thanh toán một phần" : "⚠ Chưa thanh toán"}</span></div>
    <p>Khách: ${esc(sale.customerName || "Chưa gắn khách")} · Người bán: ${esc(sale.seller || "—")} · Người nhận: ${esc(sale.receiver || "—")}</p>
    ${table(["Sản phẩm", "Loại nhôm", "Số lượng", "Đơn giá", "Thành tiền"], items.map((item) => [
      esc(item.product), esc(item.category), `${qty(item.quantity)} ${esc(item.unit)}`, money(item.unitPrice), money(item.amount),
    ]))}
    ${table(["Khối lượng nhôm", "Tiền hóa đơn", "Đã thu đến lúc in", "Còn phải thu hiện tại"], [[`${qty(sum(items.map((item) => item.quantityKg)))} kg`, money(sale.total), money(sale.paidNow), money(remaining)]])}
    ${sale.paidNow !== sale.paidAsOf ? `<p class="period-note">Tại ngày chốt ${day(book.periodTo)}: đã thu ${money(sale.paidAsOf)}, còn nợ ${money(Math.max(0, sale.total - sale.paidAsOf))}. Các lần trả sau kỳ được ghi riêng bên dưới.</p>` : ""}
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
        + table(["Ngày giờ", "Mã", kind === "expenses" ? "Người chi" : "Người thu", "Nội dung", "Số tiền"], rows.map((entry) => [
          esc(date(entry.date)), esc(entry.code),
          esc(entry.spentBy || ""), esc(entry.description || ""), money(entry.amount),
        ]));
    }).join("") || "<p>Không có giao dịch.</p>"}
    <p class="grand">Tổng ${kind === "receipts" ? "thu" : "chi"}: ${money(sum(entries.map((entry) => entry.amount)))}</p>
  </section>`;
}

function shopHtml(book: AnnualBook) {
  const customerSales = book.sales.filter((sale) => sale.customerId != null);
  const currentDebt = (sale: AnnualBook["sales"][number]) => Math.max(0, sale.total - sale.paidNow);
  const debtors = customerSales.filter((sale) => currentDebt(sale) > 0);
  const externalPurchases = book.purchases.filter((purchase) => !purchase.internal);
  const internalPurchases = book.purchases.filter((purchase) => purchase.internal);
  const customerAnchor = (id: number) => `customer-${book.shopCode}-${id}`;
  return `<section class="shop-cover"><h1>${esc(book.shopName)}</h1><h2>Sổ tổng hợp ${esc(book.periodName)}</h2>
    <p>Kỳ từ ${day(book.periodFrom)} đến hết ngày ${day(book.periodTo)}</p>
    <p>Trạng thái: <strong>${book.closed ? "Đã chốt năm" : "Chưa có bản chốt năm - số liệu tạm tính"}</strong></p>
    <div class="summary"><div>Nợ khách tại ngày chốt <b>${money(sum(book.customers.map((c) => c.closingDebt)))}</b></div>
      <div>Khách còn nợ đến lúc in <b>${money(sum(debtors.map(currentDebt)))}</b></div>
      <div>Phiếu chưa gắn khách còn nợ đến lúc in <b>${money(sum(book.sales.filter((s) => s.customerId == null).map(currentDebt)))}</b></div>
      <div>Tồn nhôm <b>${qty(sum(book.stock.map((s) => s.quantityKg)))} kg</b></div>
      <div>Giá trị tồn đã chốt <b>${money(book.inventoryValue)}</b></div>
      <div>Còn nợ nhà cung cấp tại ngày chốt <b>${money(sum(externalPurchases.map((p) => Math.max(0, p.total - p.paidAsOf))))}</b></div>
      <div>Lãi đã chốt <b>${money(book.profit)}</b></div></div>
    <h3>Nội dung</h3><ol><li>Tổng hợp khách mua</li><li>Mỗi khách một mục chi tiết phiếu bán</li>
      <li>Khách còn nợ đến lúc in</li><li>Sổ thu</li><li>Sổ chi</li><li>Phiếu nhập và nợ nhà cung cấp</li>${book.shopCode === "MANUFACTURING" ? "<li>Nhập nhôm nội bộ từ Phân phối</li><li>Công thợ theo tháng</li>" : ""}<li>Tồn kho cuối năm</li></ol>
  </section>
  <section class="chapter"><h2>Tổng hợp khách mua · ${esc(book.shopName)}</h2>
    ${table(["Khách hàng", "Điện thoại", "Khối lượng đã lấy", "Tiền hàng trong sổ", "Đã trả đến lúc in", "Còn nợ hiện tại"],
      book.customers.map((c) => { const sales = customerSales.filter((sale) => sale.customerId === c.id); return [
        esc(c.name), esc(c.phone || ""), `${qty(sum(book.saleItems.filter((item) => sales.some((sale) => sale.id === item.documentId)).map((item) => item.quantityKg)))} kg`,
        money(sum(sales.map((sale) => sale.total))), money(sum(sales.map((sale) => sale.paidNow))), money(sum(sales.map(currentDebt))),
      ]; }))}
    <p class="grand">Tổng tiền các phiếu: ${money(sum(customerSales.map((sale) => sale.total)))} · Đã trả đến lúc in: ${money(sum(customerSales.map((sale) => sale.paidNow)))} · Còn nợ hiện tại: ${money(sum(debtors.map(currentDebt)))}</p>
    <p class="period-note">Nợ tại ngày chốt ${day(book.periodTo)}: ${money(sum(book.customers.map((c) => c.closingDebt)))}. Số hiện tại có thể khác do khách trả sau kỳ.</p>
  </section>
  ${book.customers.map((customer) => {
      const sales = book.sales.filter((sale) => sale.customerId === customer.id);
      const items = book.saleItems.filter((item) => sales.some((sale) => sale.id === item.documentId));
      return `<section class="chapter customer-page" id="${customerAnchor(customer.id)}"><h2>Khách hàng: ${esc(customer.name)}</h2>
        <p>Điện thoại: ${esc(customer.phone || "—")} · Ghi chú: ${esc(customer.note || "—")}</p>
        ${sales.length ? sales.map((sale) => saleBlock(book, sale)).join("") : "<p>Không có phiếu bán còn liên quan trong năm.</p>"}
        <h4>Tổng kết khách hàng ${esc(customer.name)}</h4>
        ${table(["Khối lượng nhôm", "Tổng tiền các phiếu", "Đã trả đến lúc in", "Còn nợ hiện tại"], [[
          `${qty(sum(items.map((item) => item.quantityKg)))} kg`, money(sum(sales.map((sale) => sale.total))),
          money(sum(sales.map((sale) => sale.paidNow))), money(sum(sales.map(currentDebt))),
        ]])}
      </section>`;
    }).join("")}
    ${book.sales.filter((sale) => sale.customerId == null).length ? `<section class="chapter customer-page"><h2>Phiếu bán chưa gắn khách</h2>${book.sales.filter((sale) => sale.customerId == null).map((sale) => saleBlock(book, sale)).join("")}</section>` : ""}
  <section class="chapter"><h2>Khách còn nợ đến lúc in · ${esc(book.shopName)}</h2>
    ${table(["Khách", "Điện thoại", "Phiếu", "Ngày bán", "Tiền phiếu", "Đã thu", "Còn nợ"], debtors.map((sale) => {
      const customer = book.customers.find((c) => c.id === sale.customerId);
      return [esc(customer?.name || sale.customerName), esc(customer?.phone || ""), esc(sale.code), esc(date(sale.date)), money(sale.total), money(sale.paidNow), money(currentDebt(sale))];
    }))}
    <p class="grand">Tổng nợ khách đã gắn hiện tại: ${money(sum(debtors.map(currentDebt)))}</p>
    ${book.sales.filter((s) => s.customerId == null && currentDebt(s) > 0).length
      ? `<h3>Phiếu còn nợ chưa gắn khách</h3>${table(["Tên ghi trên phiếu", "Phiếu", "Ngày bán", "Còn nợ"],
        book.sales.filter((s) => s.customerId == null && currentDebt(s) > 0).map((s) => [
          esc(s.customerName || "—"), esc(s.code), esc(date(s.date)), money(currentDebt(s)),
        ]))}` : ""}
  </section>
  ${transactions(book, "receipts")}${transactions(book, "expenses")}
  <section class="chapter"><h2>Phiếu nhập và nợ nhà cung cấp · ${esc(book.shopName)}</h2>
    <p>Gồm phiếu nhập trong năm, phiếu cũ còn nợ cuối năm và phiếu cũ có trả tiền trong năm. Tiền trả thực tế cũng nằm trong Sổ chi.</p>
    ${externalPurchases.map((purchase) => `<div class="invoice"><h3>${esc(purchase.code)} · ${esc(date(purchase.date))}</h3>
      <p>Nhà cung cấp: ${esc(purchase.supplier || "—")}${purchase.notes ? ` · Ghi chú: ${esc(purchase.notes)}` : ""}</p>
      ${table(["Sản phẩm", "Loại nhôm", "Số lượng", "Đơn giá", "Thành tiền"],
        book.purchaseItems.filter((item) => item.documentId === purchase.id).map((item) => [
          esc(item.product), esc(item.category), `${qty(item.quantity)} ${esc(item.unit)}`, money(item.unitPrice), money(item.amount),
        ]))}
      <p class="total">Tiền phiếu: ${money(purchase.total)} · Đã trả đến ${day(book.periodTo)}: ${money(purchase.paidAsOf)} · Còn nợ: ${money(Math.max(0, purchase.total - purchase.paidAsOf))}</p>
    </div>`).join("") || "<p>Không có phiếu nhập nhà cung cấp liên quan.</p>"}
    <p class="grand">Tổng nợ nhà cung cấp từ phiếu: ${money(sum(externalPurchases.map((p) => Math.max(0, p.total - p.paidAsOf))))}</p>
  </section>
  ${book.shopCode === "MANUFACTURING" ? `<section class="chapter"><h2>Nhập nhôm nội bộ từ Phân phối · ${esc(book.shopName)}</h2>
    <p>Giá trị phiếu nội bộ là giá chuyển/giá vốn quản trị; không phải nợ nhà cung cấp bên ngoài.</p>
    ${internalPurchases.map((purchase) => `<div class="invoice"><h3>${esc(purchase.code)} · ${esc(date(purchase.date))}</h3>
      ${table(["Sản phẩm", "Loại nhôm", "Số lượng", "Giá chuyển / đơn vị", "Thành tiền"],
        book.purchaseItems.filter((item) => item.documentId === purchase.id).map((item) => [
          esc(item.product), esc(item.category), `${qty(item.quantity)} ${esc(item.unit)}`, money(item.unitPrice), money(item.amount),
        ]))}
      <p class="total">Tổng khối lượng: ${qty(sum(book.purchaseItems.filter((item) => item.documentId === purchase.id).map((item) => item.quantityKg)))} kg · Giá trị chuyển: ${money(purchase.total)}</p>
    </div>`).join("") || "<p>Không có phiếu nhập nội bộ trong kỳ.</p>"}
    <p class="grand">Tổng nhôm nhập nội bộ: ${qty(sum(book.purchaseItems.filter((item) => internalPurchases.some((p) => p.id === item.documentId)).map((item) => item.quantityKg)))} kg · ${money(sum(internalPurchases.map((p) => p.total)))}</p>
  </section>
  <section class="chapter"><h2>Công thợ theo tháng · ${esc(book.shopName)}</h2>
    <p>Mỗi tháng ghi riêng từng nhân viên. Tiền đã chi thực tế nằm trong Sổ chi; không cộng hai bảng thành một khoản chi.</p>
    ${[...new Set(book.wages.map((w) => `${w.year}-${String(w.month).padStart(2, "0")}`))].map((month) => `<h3>Tháng ${esc(month.slice(5))}/${esc(month.slice(0, 4))}</h3>
      ${book.wages.filter((w) => `${w.year}-${String(w.month).padStart(2, "0")}` === month).map((w) => {
        const events = book.wageEvents.filter((event) => event.year === w.year && event.month === w.month && event.employeeId === w.employeeId);
        return `<div class="invoice"><h4>${esc(w.employee)}</h4>
          ${table(["Khoản", "Số tiền / số ngày", "Ghi chú"], [
            ["Ngày công", qty(w.days), ""], ["Lương cơ bản", money(w.basePay), ""], ["Tăng ca", money(w.overtimePay), "Đã duyệt"],
            ["Thưởng cộng vào lương", money(w.bonusPay), "Gồm thưởng công và thưởng lễ chưa nhận"],
            ["Phụ cấp", money(w.allowances), ""], ["Khấu trừ", money(w.deductions), ""],
            ["Đã ứng trong tháng", money(w.advanceAmount), w.advanceAmount > 0 ? "Dữ liệu hiện chỉ lưu tổng tháng, chưa lưu ngày ứng" : ""],
            ["Tiền công còn lĩnh", money(w.totalPay), "Đã trừ tiền ứng"],
          ])}
          ${events.length ? table(["Ngày", "Khoản thưởng", "Số tiền", "Trạng thái / ghi chú"], events.map((event) => [
            day(event.date), esc(event.kind), money(event.amount),
            `${event.kind.startsWith("Thưởng lễ") ? event.received ? "Đã nhận trực tiếp; không cộng vào lương" : "Chưa nhận; đã cộng vào lương" : "Đã cộng vào lương"}${event.note ? ` · ${esc(event.note)}` : ""}`,
          ])) : ""}
        </div>`;
      }).join("")}`).join("") || "<p>Chưa có kỳ công nhân viên trong khoảng này.</p>"}
  </section>` : ""}
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
    .customer-page { break-before: page; } .customer-page > h2 { margin-top: 0; }
    .customer-page > table:last-child { break-inside: avoid; }
    a { color: #183b63; text-decoration: none; }
    .invoice { border: 1pt solid #c8d2dc; border-radius: 2mm; padding: 3mm; margin: 5mm 0; }
    .invoice-heading { display: flex; justify-content: space-between; align-items: center; gap: 4mm; }
    .invoice-heading h4 { margin: 0 0 2mm; }
    .invoice-paid { border-left: 3pt solid #198754; }
    .invoice-unpaid { border: 1.2pt solid #c83c3c; border-left: 3pt solid #c83c3c; background: #fff4f2; }
    .payment-status { font-weight: bold; padding: 1.5mm 2mm; border-radius: 1mm; white-space: nowrap; }
    .status-paid { color: #166534; background: #dcfce7; }
    .status-unpaid { color: #991b1b; background: #fee2e2; }
    .period-note { color: #4b5563; font-size: 8.5pt; }
    .summary { display: grid; grid-template-columns: 1fr 1fr; gap: 5mm; margin: 15mm 0; }
    .summary div { border: 1px solid #9fb0c2; padding: 5mm; } .summary b { display: block; margin-top: 2mm; }
    .total, .grand { text-align: right; font-weight: bold; } .grand { border-top: 1px solid #8c9dae; padding-top: 3mm; }
    @media screen { body { background: #dce3e9; padding: 15mm; } .shop-cover, .chapter { background: white; padding: 15mm; margin: 0 auto 12mm; width: 210mm; max-width: 100%; box-shadow: 0 2mm 10mm #0002; } }
  </style></head><body>${books.map(shopHtml).join("")}</body></html>`;
}
