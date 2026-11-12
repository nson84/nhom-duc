import dayjs from "dayjs";
import { message } from "antd";
import { formatMoney } from "../../utils/money";
import { formatQtyInput } from "../../utils/vnd-input";
import { vndToWords } from "../../utils/vnd-words";
import { bankName, buildVietQrPayload, vietQrDataUrl } from "../../utils/vietqr";
import { debtTransferNote } from "../customers/debt-print";
import { getCustomer } from "../customers/customers-api";
import type { BankAccount } from "../settings/bank-accounts-api";
import { getSettings, type ShopSettings } from "../settings/settings-api";
import type { DocumentItem, StockDocument } from "./stock-api";

type Kind = "PURCHASE" | "SALE";

const FORM = {
  PURCHASE: {
    title: "PHIẾU NHẬP KHO",
    counterparty: "Nhà cung cấp",
    leftSign: "Người giao hàng",
    midSign: "Thủ kho",
    rightSign: "Người lập phiếu",
  },
  SALE: {
    title: "PHIẾU XUẤT KHO",
    counterparty: "Khách hàng",
    leftSign: "Người lập phiếu",
    midSign: "Thủ kho",
    rightSign: "Người nhận hàng",
  },
} as const;

const PAYMENT_STATUS: Record<string, string> = {
  UNPAID: "Chưa thanh toán",
  PARTIAL: "Thanh toán một phần",
  PAID: "Đã thanh toán",
};

function escapeHtml(value: string | null | undefined) {
  return (value ?? "").replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

function paymentMethodLabel(raw: string | null | undefined): string {
  if (!raw) return "—";
  const value = raw.trim();
  const upper = value.toUpperCase();
  if (upper === "CASH" || upper.includes("CASH") || value === "Tiền mặt") return "Tiền mặt";
  if (upper === "TRANSFER" || upper.includes("TRANSFER") || value === "Chuyển khoản") return "Chuyển khoản";
  return value;
}

function buildItemRows(doc: StockDocument): string {
  return doc.items
    .map((item, index) => {
      const isM2 = item.billingUnit === "m2" || item.unitName === "m²";
      const unitLabel = isM2
        ? item.widthM && item.heightM
          ? `m² (${formatQtyInput(item.widthM)}×${formatQtyInput(item.heightM)})`
          : "m²"
        : item.unitName;
      const qtyLabel = isM2
        ? `${formatQtyInput(item.quantityDocument)} m²`
        : formatQtyInput(item.quantityDocument);
      const note = isM2 ? `Trừ tồn ${formatQtyInput(item.quantityBase)} kg` : "";
      return `<tr>
        <td class="c">${index + 1}</td>
        <td>${escapeHtml(item.productName)}${note ? `<div class="muted">${escapeHtml(note)}</div>` : ""}</td>
        <td>${escapeHtml(item.categoryName)}</td>
        <td class="c">${escapeHtml(unitLabel)}</td>
        <td>${qtyLabel}</td>
        <td>${formatMoney(item.unitPrice)}</td>
        <td>${formatMoney(item.lineTotal)}</td>
      </tr>`;
    })
    .join("");
}

function buildHtml(params: {
  doc: StockDocument;
  kind: Kind;
  shopName: string;
  shopAddress: string;
  shopPhone: string;
  customerPhone: string | null;
}): string {
  const { doc, kind, shopName, shopAddress, shopPhone, customerPhone } = params;
  const form = FORM[kind];
  const when = dayjs(doc.documentDate);
  const remaining = Math.max(0, doc.totalAmount - doc.paidAmount);
  const rows = buildItemRows(doc);
  const whoLabel = kind === "SALE" ? "Người thu" : "Người chi";
  const receiptLines = (doc.payments ?? [])
    .map((payment) => {
      const paidAt = dayjs(payment.paymentDate).format("DD/MM/YYYY HH:mm");
      return `<tr>
        <td class="when">${paidAt}</td>
        <td class="money">${formatMoney(payment.amount)}</td>
        <td>${escapeHtml(payment.paymentMethod) || "—"}</td>
        <td>${escapeHtml(payment.collectedBy) || "—"}</td>
      </tr>`;
    })
    .join("");
  const receiptBlock = receiptLines
    ? `<table class="receipts">
        <colgroup>
          <col class="c-when" /><col class="c-money" /><col class="c-method" /><col class="c-who" />
        </colgroup>
        <thead>
          <tr>
            <th>Ngày giờ</th>
            <th>Số tiền</th>
            <th>Hình thức thanh toán</th>
            <th>${whoLabel}</th>
          </tr>
        </thead>
        <tbody>${receiptLines}</tbody>
      </table>`
    : "";

  return `<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8" />
  <title>${escapeHtml(doc.documentCode)}</title>
  <style>
    @page { size: A4; margin: 12mm 14mm; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      color: #111;
      font-family: "Times New Roman", Times, serif;
      font-size: 12.5px;
      line-height: 1.35;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
    .sheet { width: 100%; max-width: 190mm; margin: 0 auto; }
    .head {
      display: grid;
      grid-template-columns: 1.3fr 1fr;
      gap: 12px;
      align-items: start;
      border-bottom: 1.5px solid #222;
      padding-bottom: 8px;
    }
    .shop-name { font-size: 15px; font-weight: 700; text-transform: uppercase; }
    .shop-meta { margin-top: 2px; }
    .nation { text-align: center; }
    .nation-top { font-weight: 700; font-size: 12px; text-transform: uppercase; }
    .nation-sub { font-style: italic; font-size: 12px; }
    .nation-line { width: 96px; height: 1px; background: #222; margin: 4px auto 0; }
    .title-block { text-align: center; margin: 14px 0 10px; }
    .title { margin: 0; font-size: 20px; letter-spacing: 1px; font-weight: 700; }
    .doc-no { margin-top: 4px; font-size: 13px; }
    .meta {
      width: 100%;
      border-collapse: collapse;
      margin-bottom: 10px;
    }
    .meta td { padding: 2px 0; vertical-align: top; }
    .meta .lbl { width: 128px; white-space: nowrap; }
    .meta .val { font-weight: 600; }
    table.items {
      width: 100%;
      border-collapse: collapse;
      table-layout: fixed;
      margin-top: 4px;
    }
    .items th, .items td {
      border: 1px solid #222;
      padding: 5px 6px;
      vertical-align: top;
      word-wrap: break-word;
    }
    .items th {
      background: #f2f2f2;
      font-weight: 700;
      text-align: center;
    }
    .items col.c-stt { width: 34px; }
    .items col.c-cat { width: 92px; }
    .items col.c-unit { width: 78px; }
    .items col.c-qty { width: 70px; }
    .items col.c-price { width: 88px; }
    .items col.c-total { width: 96px; }
    .c { text-align: center; }
    .items th, .items td { text-align: center; vertical-align: middle; }
    .muted { color: #444; font-size: 11px; font-weight: 400; margin-top: 2px; }
    .totals {
      width: 100%;
      border-collapse: collapse;
      margin-top: 8px;
    }
    .totals td { padding: 3px 0; }
    .totals .lbl { width: 62%; }
    .totals td { text-align: center; }
    .totals .num { font-variant-numeric: tabular-nums; }
    .totals .grand { font-size: 14px; font-weight: 700; border-top: 1px solid #222; padding-top: 6px; }
    .words { margin-top: 6px; font-style: italic; }
    table.receipts { width: 100%; border-collapse: collapse; margin-top: 8px; table-layout: fixed; }
    .receipts col.c-when { width: 28%; }
    .receipts col.c-money { width: 22%; }
    .receipts col.c-method { width: 28%; }
    .receipts col.c-who { width: 22%; }
    .receipts th, .receipts td { border: 1px solid #222; padding: 4px 6px; text-align: center; vertical-align: middle; }
    .receipts th { background: #f2f2f2; }
    .receipts .when, .receipts .money { white-space: nowrap; }
    .notes { margin-top: 8px; }
    .sign {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 10px;
      margin-top: 28px;
      text-align: center;
      page-break-inside: avoid;
    }
    .sign b { display: block; }
    .sign .hint { font-style: italic; font-size: 11.5px; margin-top: 2px; }
    .sign .space { height: 56px; }
    .footer-note {
      margin-top: 18px;
      font-size: 11px;
      color: #444;
      border-top: 1px dashed #999;
      padding-top: 6px;
    }
    @media print {
      body { margin: 0; }
      .no-print { display: none !important; }
    }
    @media screen {
      body { background: #e8e8e8; padding: 16px; }
      .sheet {
        background: #fff;
        padding: 14mm 12mm;
        box-shadow: 0 2px 12px rgba(0,0,0,.18);
      }
      .toolbar {
        max-width: 190mm;
        margin: 0 auto 12px;
        display: flex;
        gap: 8px;
        justify-content: flex-end;
      }
      .toolbar button {
        border: 1px solid #245a73;
        background: #2f6f8f;
        color: #fff;
        border-radius: 4px;
        padding: 8px 14px;
        font: 13px/1.2 Arial, sans-serif;
        cursor: pointer;
      }
    }
  </style>
</head>
<body>
  <div class="toolbar no-print">
    <button type="button" onclick="window.print()">In / Lưu PDF</button>
    <button type="button" onclick="window.close()" style="background:#666;border-color:#555">Đóng</button>
  </div>
  <div class="sheet">
    <div class="head">
      <div>
        <div class="shop-name">${escapeHtml(shopName || "Cửa hàng nhôm")}</div>
        <div class="shop-meta">${escapeHtml(shopAddress || "—")}</div>
        <div class="shop-meta">Điện thoại: ${escapeHtml(shopPhone || "—")}</div>
      </div>
      <div class="nation">
        <div class="nation-top">Cộng hòa Xã hội Chủ nghĩa Việt Nam</div>
        <div class="nation-sub">Độc lập - Tự do - Hạnh phúc</div>
        <div class="nation-line"></div>
      </div>
    </div>

    <div class="title-block">
      <h1 class="title">${form.title}</h1>
      <div class="doc-no">
        Số: <b>${escapeHtml(doc.documentCode)}</b>
        &nbsp;&nbsp;Ngày ${when.format("DD")} tháng ${when.format("MM")} năm ${when.format("YYYY")}
      </div>
    </div>

    <table class="meta">
      <tr>
        <td class="lbl">${form.counterparty}:</td>
        <td class="val" colspan="3">${escapeHtml(doc.counterpartyName) || "—"}</td>
      </tr>
      ${
        kind === "SALE"
          ? `<tr>
        <td class="lbl">Số điện thoại:</td>
        <td class="val" colspan="3">${escapeHtml(customerPhone) || "—"}</td>
      </tr>`
          : ""
      }
      <tr>
        <td class="lbl">${kind === "SALE" ? "Người xuất:" : "Người giao:"}</td>
        <td class="val">${escapeHtml(doc.issuedBy) || "—"}</td>
        <td class="lbl" style="width:110px;padding-left:16px">${kind === "SALE" ? "Người nhận:" : "Người nhập:"}</td>
        <td class="val">${escapeHtml(doc.receivedBy) || "—"}</td>
      </tr>
      ${
        doc.reason
          ? `<tr><td class="lbl">Lý do:</td><td class="val" colspan="3">${escapeHtml(doc.reason)}</td></tr>`
          : ""
      }
      <tr>
        <td class="lbl">Thanh toán:</td>
        <td class="val">${escapeHtml(PAYMENT_STATUS[doc.paymentStatus] ?? doc.paymentStatus)}</td>
        <td class="lbl" style="width:110px;padding-left:16px">Hình thức:</td>
        <td class="val">${escapeHtml(paymentMethodLabel(doc.paymentMethod))}</td>
      </tr>
    </table>

    <table class="items">
      <colgroup>
        <col class="c-stt" /><col />
        <col class="c-cat" /><col class="c-unit" /><col class="c-qty" />
        <col class="c-price" /><col class="c-total" />
      </colgroup>
      <thead>
        <tr>
          <th>STT</th>
          <th>Tên hàng</th>
          <th>Loại nhôm</th>
          <th>Đơn vị</th>
          <th>Số lượng</th>
          <th>Đơn giá</th>
          <th>Thành tiền</th>
        </tr>
      </thead>
      <tbody>
        ${rows || `<tr><td colspan="7" class="c">Không có dòng hàng</td></tr>`}
      </tbody>
    </table>

    <table class="totals">
      <tr>
        <td class="lbl">Cộng tiền hàng</td>
        <td class="num">${formatMoney(doc.subtotal)}</td>
      </tr>
      ${
        doc.discountAmount
          ? `<tr><td class="lbl">Chiết khấu</td><td class="num">-${formatMoney(doc.discountAmount)}</td></tr>`
          : ""
      }
      <tr class="grand">
        <td class="lbl">Tổng cộng</td>
        <td class="num">${formatMoney(doc.totalAmount)}</td>
      </tr>
      <tr>
        <td class="lbl">Đã ${kind === "SALE" ? "thu" : "chi"}</td>
        <td class="num">${formatMoney(doc.paidAmount)}</td>
      </tr>
      <tr>
        <td class="lbl">Còn lại</td>
        <td class="num">${formatMoney(remaining)}</td>
      </tr>
    </table>

    ${receiptBlock}
    <div class="words"><b>Bằng chữ:</b> ${escapeHtml(vndToWords(doc.totalAmount))}.</div>
    ${
      doc.notes && !doc.internal
        ? `<div class="notes"><b>Ghi chú:</b> ${escapeHtml(doc.notes)}</div>`
        : ""
    }

    <div class="sign">
      <div>
        <b>${form.leftSign}</b>
        <div class="hint">(Ký, ghi rõ họ tên)</div>
        <div class="space"></div>
      </div>
      <div>
        <b>${form.midSign}</b>
        <div class="hint">(Ký, ghi rõ họ tên)</div>
        <div class="space"></div>
      </div>
      <div>
        <b>${form.rightSign}</b>
        <div class="hint">(Ký, ghi rõ họ tên)</div>
        <div class="space"></div>
      </div>
    </div>

    <div class="footer-note">
      In lúc ${dayjs().format("DD/MM/YYYY HH:mm")} · Chọn máy in “Microsoft Print to PDF” hoặc “Lưu thành PDF” để lưu bản PDF.
    </div>
  </div>
  <script>
    window.addEventListener("load", function () {
      setTimeout(function () { window.print(); }, 250);
    });
  </script>
</body>
</html>`;
}

function invoiceQty(item: DocumentItem): string {
  const byArea = item.billingUnit === "m2" || item.unitName === "m²";
  const qty = formatQtyInput(item.quantityDocument);
  if (byArea && item.widthM && item.heightM) {
    return `${qty} (${formatQtyInput(item.widthM)}×${formatQtyInput(item.heightM)})`;
  }
  return qty;
}

function invoiceUnit(item: DocumentItem): string {
  if (item.billingUnit === "m2" || item.unitName === "m²") return "m²";
  return item.unitName || "—";
}

/** Hóa đơn bán: xem trước rồi in PDF, cùng kiểu với danh sách nợ. */
export async function buildSaleInvoiceHtml(params: {
  doc: StockDocument;
  shop: ShopSettings | null;
  bank: BankAccount | null;
  customerName: string;
}): Promise<string> {
  const { doc, shop, bank, customerName } = params;
  const now = dayjs();
  const seller = doc.issuedBy?.trim() || "—";
  const buyer = customerName.trim() || doc.counterpartyName?.trim() || "—";
  const remaining = Math.max(0, Number(doc.totalAmount) - Number(doc.paidAmount));
  const payAmount = remaining > 0 ? remaining : Number(doc.totalAmount);
  const rows = (doc.items ?? [])
    .map(
      (item) => `<tr>
        <td>${escapeHtml(item.productName)}</td>
        <td>${escapeHtml(item.categoryName) || "—"}</td>
        <td>${escapeHtml(invoiceQty(item))}</td>
        <td>${escapeHtml(invoiceUnit(item))}</td>
        <td>${formatMoney(item.unitPrice)}</td>
        <td>${formatMoney(item.lineTotal)}</td>
      </tr>`,
    )
    .join("");

  let qrBlock = "";
  if (!doc.internal && bank?.bankBin && bank.accountNo && payAmount > 0) {
    const note = debtTransferNote(buyer === "—" ? "Khach" : buyer, payAmount, now);
    const image = await vietQrDataUrl(
      buildVietQrPayload({ bankBin: bank.bankBin, accountNo: bank.accountNo, amount: payAmount, note }),
    );
    qrBlock = `<div class="qr">
      <div class="qr-scan">
        <img src="${image}" alt="Mã QR chuyển khoản" />
      </div>
      <div class="qr-info">
        <div class="qr-title">Chuyển khoản VietQR</div>
        <table>
          <tr><td>Tài khoản</td><td><b>${escapeHtml(bank.label)}</b></td></tr>
          <tr><td>Ngân hàng</td><td><b>${escapeHtml(bankName(bank.bankBin))}</b></td></tr>
          <tr><td>Số tài khoản</td><td><b>${escapeHtml(bank.accountNo)}</b></td></tr>
          ${bank.accountName ? `<tr><td>Chủ tài khoản</td><td><b>${escapeHtml(bank.accountName)}</b></td></tr>` : ""}
          <tr><td>Số tiền</td><td><b>${formatMoney(payAmount)}</b></td></tr>
          <tr><td>Nội dung</td><td><b>${escapeHtml(note)}</b></td></tr>
        </table>
      </div>
    </div>`;
  }

  return `<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8" />
  <title>Hóa đơn bán hàng - ${escapeHtml(buyer)}</title>
  <style>
    @page { size: A4; margin: 12mm 14mm; }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      color: #111;
      font-family: "Times New Roman", Times, serif;
      font-size: 12.5px;
      line-height: 1.35;
      -webkit-print-color-adjust: exact;
      print-color-adjust: exact;
    }
    .sheet { width: 100%; max-width: 190mm; margin: 0 auto; }
    .head { border-bottom: 1.5px solid #222; padding-bottom: 8px; }
    .shop-name { font-size: 15px; font-weight: 700; text-transform: uppercase; }
    .title { text-align: center; margin: 14px 0 4px; font-size: 20px; letter-spacing: 1px; }
    .sub { text-align: center; margin-bottom: 12px; }
    .meta td { padding: 2px 12px 2px 0; }
    table.items { width: 100%; border-collapse: collapse; margin-top: 8px; table-layout: fixed; }
    .items th, .items td { border: 1px solid #222; padding: 5px 6px; vertical-align: middle; text-align: center; }
    .items th { background: #f2f2f2; }
    .items col.c-name { width: 22%; }
    .items col.c-cat { width: 16%; }
    .items col.c-qty { width: 16%; }
    .items col.c-unit { width: 12%; }
    .items col.c-price { width: 17%; }
    .items col.c-total { width: 17%; }
    .sum { margin-top: 8px; text-align: center; font-size: 14px; font-weight: 700; }
    .qr {
      display: flex; flex-wrap: wrap; gap: 14px; align-items: center;
      margin-top: 16px; padding: 10px; border: 1px dashed #555;
      page-break-inside: avoid;
    }
    .qr-scan img { width: 150px; height: 150px; }
    .qr-title { font-weight: 700; font-size: 14px; margin-bottom: 4px; }
    .qr-info td { padding: 2px 12px 2px 0; vertical-align: top; }
    @media screen {
      body { background: #e8e8e8; padding: 16px; }
      .sheet { background: #fff; padding: 14mm 12mm; box-shadow: 0 2px 12px rgba(0,0,0,.18); }
    }
    @media print { body { background: #fff; padding: 0; } .sheet { padding: 0; box-shadow: none; } }
  </style>
</head>
<body>
  <div class="sheet">
    <div class="head">
      <div class="shop-name">${escapeHtml(shop?.shopName || "Cửa hàng nhôm")}</div>
      <div>${escapeHtml(shop?.shopAddress || "")}</div>
      ${shop?.shopPhone ? `<div>Điện thoại: ${escapeHtml(shop.shopPhone)}</div>` : ""}
    </div>
    <h1 class="title">HÓA ĐƠN BÁN HÀNG</h1>
    <div class="sub">${dayjs(doc.documentDate).format("HH:mm [ngày] DD/MM/YYYY")}</div>
    <table class="meta">
      <tr><td>Người bán:</td><td><b>${escapeHtml(seller)}</b></td></tr>
      <tr><td>Khách hàng:</td><td><b>${escapeHtml(buyer)}</b></td></tr>
    </table>
    <table class="items">
      <colgroup>
        <col class="c-name" /><col class="c-cat" /><col class="c-qty" />
        <col class="c-unit" /><col class="c-price" /><col class="c-total" />
      </colgroup>
      <thead>
        <tr>
          <th>Sản phẩm</th><th>Loại nhôm</th><th>Số lượng</th><th>Đơn vị</th><th>Đơn giá</th><th>Thành tiền</th>
        </tr>
      </thead>
      <tbody>
        ${rows || `<tr><td colspan="6">Không có dòng hàng</td></tr>`}
      </tbody>
    </table>
    <div class="sum">Tổng cộng: ${formatMoney(doc.totalAmount)}</div>
    ${qrBlock}
  </div>
</body>
</html>`;
}

export async function printStockDocument(doc: StockDocument, kind: Kind) {
  const [shop, customer] = await Promise.all([
    getSettings().catch(() => null),
    doc.customerId
      ? getCustomer(doc.customerId, "all").catch(() => null)
      : Promise.resolve(null),
  ]);

  const html = buildHtml({
    doc,
    kind,
    shopName: shop?.shopName ?? "",
    shopAddress: shop?.shopAddress ?? "",
    shopPhone: shop?.shopPhone ?? "",
    customerPhone: customer?.phone ?? null,
  });

  const frame = window.open("", "_blank", "width=920,height=1100");
  if (!frame) {
    message.error("Trình duyệt chặn cửa sổ in. Cho phép cửa sổ bật lên rồi thử lại.");
    return;
  }
  frame.document.open();
  frame.document.write(html);
  frame.document.close();
}
