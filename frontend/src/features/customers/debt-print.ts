import dayjs from "dayjs";
import { formatMoney } from "../../utils/money";
import { formatQtyInput } from "../../utils/vnd-input";
import { vndToWords } from "../../utils/vnd-words";
import { bankName, buildVietQrPayload, toTransferNote, TRANSFER_NOTE_MAX, vietQrDataUrl } from "../../utils/vietqr";
import type { BankAccount } from "../settings/bank-accounts-api";
import type { ShopSettings } from "../settings/settings-api";
import type { DocumentItem, StockDocument } from "../stock/stock-api";
import type { CustomerDocument, CustomerSummary } from "./customers-api";

const PAYMENT_STATUS: Record<string, { text: string; tone: string }> = {
  UNPAID: { text: "Chưa thanh toán", tone: "unpaid" },
  PARTIAL: { text: "Thanh toán một phần", tone: "partial" },
  PAID: { text: "Đã thanh toán", tone: "paid" },
};

function paymentMethodLabel(raw: string | null | undefined): string {
  if (!raw) return "";
  const value = raw.trim();
  const upper = value.toUpperCase();
  if (upper === "CASH" || upper.includes("CASH") || value === "Tiền mặt") return "Tiền mặt";
  if (upper === "TRANSFER" || upper.includes("TRANSFER") || value === "Chuyển khoản") return "Chuyển khoản";
  if (upper === "OTHER" || value === "Khác") return "Khác";
  return value;
}

function lineQty(item: DocumentItem): string {
  const byArea = item.billingUnit === "m2" || item.unitName === "m²";
  if (byArea && item.widthM && item.heightM) {
    return `${formatQtyInput(item.quantityDocument)} m² (${formatQtyInput(item.widthM)}×${formatQtyInput(item.heightM)})`;
  }
  if (byArea) return `${formatQtyInput(item.quantityDocument)} m²`;
  return `${formatQtyInput(item.quantityDocument)} ${item.unitName || ""}`.trim();
}

function receiptDetail(doc: StockDocument): string {
  const payments = doc.payments ?? [];
  if (payments.length === 0) {
    return `<div class="invoice-sum"><span>Đã thu: <b>${formatMoney(doc.paidAmount)}</b></span></div>`;
  }
  const lines = payments
    .map((payment) => {
      const when = dayjs(payment.paymentDate).format("DD/MM/YYYY HH:mm");
      const method = paymentMethodLabel(payment.paymentMethod) || "—";
      return `<tr>
        <td class="when">${when}</td>
        <td class="money">${formatMoney(payment.amount)}</td>
        <td>${escapeHtml(method)}</td>
        <td>${escapeHtml(payment.collectedBy) || "—"}</td>
      </tr>`;
    })
    .join("");
  return `<table class="receipts">
    <colgroup>
      <col class="c-when" /><col class="c-money" /><col class="c-method" /><col class="c-who" />
    </colgroup>
    <thead>
      <tr>
        <th>Ngày giờ</th>
        <th>Số tiền</th>
        <th>Hình thức thanh toán</th>
        <th>Người thu</th>
      </tr>
    </thead>
    <tbody>${lines}</tbody>
    <tfoot>
      <tr><td colspan="4">Đã thu: <b>${formatMoney(doc.paidAmount)}</b></td></tr>
    </tfoot>
  </table>`;
}

function paymentBadge(doc: StockDocument): string {
  const status = PAYMENT_STATUS[doc.paymentStatus] ?? { text: doc.paymentStatus, tone: "unpaid" };
  const methods = [...new Set((doc.payments ?? []).map((p) => paymentMethodLabel(p.paymentMethod)).filter(Boolean))];
  const method = methods.length > 0 ? methods.join(", ") : paymentMethodLabel(doc.paymentMethod);
  const text = method ? `${status.text} · ${method}` : status.text;
  return `<span class="badge ${status.tone}">${escapeHtml(text)}</span>`;
}

function escapeHtml(value: string | null | undefined) {
  return (value ?? "").replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

/** Phiếu bán đã xác nhận còn nợ, cũ nhất trước. */
export function openDebtDocuments(docs: CustomerDocument[]): CustomerDocument[] {
  return docs
    .filter((d) => d.status === "CONFIRMED" && d.remainingAmount > 0)
    .sort((a, b) => dayjs(a.documentDate).valueOf() - dayjs(b.documentDate).valueOf());
}

export function hasBankAccount(account: BankAccount | null | undefined): account is BankAccount {
  return Boolean(account?.bankBin && account.accountNo);
}

/** "KHACH A tra 7500000 18h05 05-11-2026" — cắt bớt tên để vừa giới hạn ký tự của ngân hàng. */
export function debtTransferNote(customerName: string, amount: number, at: dayjs.Dayjs): string {
  const suffix = ` tra ${Math.round(amount)} ${at.format("HH[h]mm DD-MM-YYYY")}`;
  const name = toTransferNote(customerName).slice(0, Math.max(0, TRANSFER_NOTE_MAX - suffix.length)).trim();
  return toTransferNote(`${name}${suffix}`);
}

export async function buildDebtListHtml(params: {
  customer: CustomerSummary;
  docs: StockDocument[];
  shop: ShopSettings | null;
  bank: BankAccount | null;
  withQr: boolean;
}): Promise<string> {
  const { customer, docs, shop, bank, withQr } = params;
  const now = dayjs();
  const total = docs.reduce(
    (sum, d) => sum + Math.max(0, Number(d.totalAmount) - Number(d.paidAmount)),
    0,
  );

  let qrBlock = "";
  if (withQr && hasBankAccount(bank) && total > 0) {
    const note = debtTransferNote(customer.name, total, now);
    const image = await vietQrDataUrl(
      buildVietQrPayload({ bankBin: bank.bankBin, accountNo: bank.accountNo, amount: total, note }),
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
          <tr><td>Số tiền</td><td><b>${formatMoney(total)}</b></td></tr>
          <tr><td>Nội dung</td><td><b>${escapeHtml(note)}</b></td></tr>
        </table>
      </div>
    </div>`;
  }

  const invoices = docs
    .map((doc, index) => {
      const remaining = Math.max(0, Number(doc.totalAmount) - Number(doc.paidAmount));
      const lines = (doc.items ?? [])
        .map(
          (item) => `<tr>
            <td>${escapeHtml(item.productName)}</td>
            <td>${escapeHtml(item.categoryName) || "—"}</td>
            <td class="c">${escapeHtml(lineQty(item))}</td>
            <td class="r">${formatMoney(item.unitPrice)}</td>
            <td class="r">${formatMoney(item.lineTotal)}</td>
          </tr>`,
        )
        .join("");
      return `<section class="invoice">
        <div class="invoice-head">
          <div>
            <b>Hóa đơn ${index + 1} · ${dayjs(doc.documentDate).format("DD/MM/YYYY HH:mm")}</b>
            <div class="people">Người xuất: <b>${escapeHtml(doc.issuedBy) || "—"}</b> · Người nhận: <b>${escapeHtml(doc.receivedBy) || "—"}</b></div>
          </div>
          ${paymentBadge(doc)}
        </div>
        <table class="items">
          <thead>
            <tr>
              <th>Sản phẩm</th><th>Loại nhôm</th><th>Số lượng</th><th>Đơn giá</th><th>Thành tiền</th>
            </tr>
          </thead>
          <tbody>
            ${lines || `<tr><td colspan="5" class="c">Không có dòng hàng</td></tr>`}
          </tbody>
        </table>
        <div class="invoice-sum">
          <span>Thành tiền: <b>${formatMoney(doc.totalAmount)}</b></span>
          <span>Còn nợ: <b>${formatMoney(remaining)}</b></span>
        </div>
        ${receiptDetail(doc)}
      </section>`;
    })
    .join("");

  return `<!doctype html>
<html lang="vi">
<head>
  <meta charset="utf-8" />
  <title>Danh sách nợ - ${escapeHtml(customer.name)} - ${now.format("DD-MM-YYYY")}</title>
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
    table.items { width: 100%; border-collapse: collapse; margin-top: 8px; }
    .items th, .items td { border: 1px solid #222; padding: 5px 6px; vertical-align: middle; text-align: center; }
    .items th { background: #f2f2f2; }
    .invoice { margin-top: 14px; page-break-inside: avoid; }
    .invoice-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 6px; }
    .people { margin-top: 2px; }
    .badge {
      display: inline-block;
      border: 1.5px solid currentColor;
      border-radius: 999px;
      padding: 2px 10px;
      font-size: 12px;
      font-weight: 700;
      white-space: nowrap;
    }
    .badge.unpaid { color: #9f1239; }
    .badge.partial { color: #9a3412; }
    .badge.paid { color: #166534; }
    .invoice-sum { display: flex; justify-content: center; flex-wrap: wrap; gap: 8px 18px; margin-top: 4px; }
    table.receipts { width: 100%; border-collapse: collapse; margin-top: 6px; table-layout: fixed; }
    .receipts col.c-when { width: 28%; }
    .receipts col.c-money { width: 22%; }
    .receipts col.c-method { width: 28%; }
    .receipts col.c-who { width: 22%; }
    .receipts th, .receipts td { border: 1px solid #222; padding: 4px 6px; text-align: center; vertical-align: middle; }
    .receipts th { background: #f2f2f2; font-weight: 700; }
    .receipts .when, .receipts .money { white-space: nowrap; }
    .receipts tfoot td { border: none; padding-top: 4px; font-weight: 700; }
    .grand-debt { margin-top: 12px; text-align: center; font-size: 14px; font-weight: 700; border-top: 1.5px solid #222; padding-top: 6px; }
    .c { text-align: center; }
    .words { margin-top: 6px; font-style: italic; }
    .qr {
      display: flex; flex-wrap: wrap; gap: 14px; align-items: center;
      margin-top: 16px; padding: 10px; border: 1px dashed #555;
      page-break-inside: avoid;
    }
    .qr-card img { width: 120px; height: auto; border: 1px solid #ccc; border-radius: 6px; }
    .qr-scan { text-align: center; }
    .qr-scan img { width: 150px; height: 150px; }
    .qr-caption { font-size: 11px; color: #444; margin-top: 4px; max-width: 150px; }
    .qr-title { font-weight: 700; font-size: 14px; margin-bottom: 4px; }
    .qr-info td { padding: 2px 12px 2px 0; vertical-align: top; }
    .footer-note { margin-top: 18px; font-size: 11px; color: #444; border-top: 1px dashed #999; padding-top: 6px; }
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
    <h1 class="title">DANH SÁCH CÔNG NỢ KHÁCH HÀNG</h1>
    <div class="sub">Tính đến ${now.format("HH:mm [ngày] DD/MM/YYYY")}</div>
    <table class="meta">
      <tr><td>Khách hàng:</td><td><b>${escapeHtml(customer.name)}</b></td></tr>
      <tr><td>Số điện thoại:</td><td><b>${escapeHtml(customer.phone) || "—"}</b></td></tr>
      <tr><td>Số hóa đơn còn nợ:</td><td><b>${docs.length}</b></td></tr>
    </table>
    ${invoices || `<p class="c">Khách không còn nợ</p>`}
    <div class="grand-debt">Tổng còn nợ: ${formatMoney(total)}</div>
    <div class="words"><b>Bằng chữ:</b> ${escapeHtml(vndToWords(total))}.</div>
    ${qrBlock}
    <div class="footer-note">In lúc ${now.format("DD/MM/YYYY HH:mm")} · Hóa đơn sắp theo thời gian, cũ nhất trước.</div>
  </div>
</body>
</html>`;
}
