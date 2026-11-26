import { printDate, printEscape as e, printMoney, printPage } from "../../components/print-document";
import { bankName, buildVietQrPayload, vietQrDataUrl } from "../../utils/vietqr";
import type { BankAccount } from "../settings/bank-accounts-api";
import type { BatchCollectionItem } from "../stock/stock-api";
import { hasBankAccount } from "./debt-print";

export async function receivingAccountQr(bank: BankAccount | undefined) {
  if (!hasBankAccount(bank)) return "";
  const image = await vietQrDataUrl(buildVietQrPayload({ bankBin: bank.bankBin, accountNo: bank.accountNo, amount: 0, note: "" }))
    .catch(() => "");
  return `<section class="invoice"><h3>Tài khoản nhận của cửa hàng</h3>
    <p>${e(bankName(bank.bankBin))} · ${e(bank.accountNo)} · ${e(bank.accountName)}</p>
    ${image ? `<img src="${image}" width="110" height="110" alt="VietQR tài khoản nhận" />` : ""}
  </section>`;
}

export async function buildCollectionReceipt(customer: string, store: string, receipt: {
  date: string; method: string; collector: string; items: BatchCollectionItem[];
}, bank?: BankAccount) {
  const amount = receipt.items.reduce((sum, item) => sum + Number(item.amount), 0);
  const qr = await receivingAccountQr(bank);
  return printPage("Biên nhận thu nợ", `<h1>Biên nhận thu nợ · Đã thu</h1>
    <p>${e(store)}</p><p>Khách hàng: <strong>${e(customer)}</strong></p>
    <p>Ngày thu: ${printDate(receipt.date.slice(0, 10))} · ${e(receipt.method)} · Người thu: ${e(receipt.collector)}</p>
    <table><thead><tr><th>Số phiếu</th><th>Ngày lập phiếu</th><th>Số tiền đã thu</th></tr></thead><tbody>
      ${receipt.items.map(item => `<tr><td>${e(item.documentCode)}</td><td>${printDate(item.documentDate.slice(0, 10))}</td><td>${printMoney(item.amount)}</td></tr>`).join("")}
      <tr class="total"><td colspan="2">Tổng đã thu</td><td>${printMoney(amount)}</td></tr>
    </tbody></table>
    ${qr ? "<p>QR chỉ cung cấp thông tin tài khoản. Khoản tiền trên đã được ghi nhận.</p>" : ""}
    ${qr}`);
}
