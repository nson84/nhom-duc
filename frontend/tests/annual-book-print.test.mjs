import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import ts from 'typescript';

function loadModule(path) {
  const source = readFileSync(path, 'utf8');
  const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const exports = {};
  vm.runInNewContext(js, { exports, require: name => loadModule(resolve(dirname(path), `${name}.ts`)) });
  return exports;
}
const { buildAnnualBookHtml } = loadModule(fileURLToPath(new URL('../src/features/annual-book/annual-book-print.ts', import.meta.url)));

function fixture(shopCode) {
  const sales = Array.from({ length: 40 }, (_, i) => ({
    id: i + 1, customerId: i < 20 ? 1 : 2, customerName: i < 20 ? 'Nguyễn Hải <script>' : 'Trần Thuấn',
    code: `TEST-${String(i + 1).padStart(3, '0')}`, date: `2025-02-${String(18 + i % 3).padStart(2, '0')}T10:00:00`,
    seller: 'Hải', receiver: 'Thuấn', total: 100000, paidAsOf: i % 2 ? 50000 : 100000,
    paidNow: i % 2 ? 50000 : 100000, openingPaid: 0, notes: 'Đối chiếu từng hóa đơn, không cộng tiền chuyển nội bộ vào công nợ.',
  }));
  return {
    year: 2025, periodName: 'Kỳ 2025', periodFrom: '2025-02-18', periodTo: '2025-02-28', shopCode,
    shopName: shopCode === 'DISTRIBUTION' ? 'Phân phối nhôm khối' : 'Xưởng sản xuất',
    customers: [1, 2].map(id => ({ id, name: id === 1 ? 'Nguyễn Hải <script>' : 'Trần Thuấn', phone: '0900000000', note: null, openingDebt: 0, sales: 2000000, receipts: 1500000, closingDebt: 500000 })),
    sales,
    saleItems: sales.flatMap(s => {
      const count = s.id === 1 ? 65 : 2;
      return Array.from({ length: count }, (_, i) => {
        const amount = i === count - 1 ? 100000 - Math.floor(100000 / count) * (count - 1) : Math.floor(100000 / count);
        return { documentId: s.id, product: `Nhôm đúc cửa cổng – chi tiết ${i + 1}`, category: 'Loại 1', quantity: 1, unit: 'kg', quantityKg: 1, unitPrice: amount, amount };
      });
    }),
    payments: sales.map(s => ({ documentId: s.id, date: '2025-02-21T09:00:00', amount: s.paidNow, method: 'CASH', collector: 'Hải', note: 'Khách trả sau ngày giao hàng' })),
    purchases: [{ id: 100, supplier: 'Phân phối', code: 'NOI-BO-001', date: '2025-02-18', total: 1000000, paidAsOf: 0, notes: '', internal: true }],
    purchaseItems: [{ documentId: 100, product: 'Nhôm 1', category: 'Loại 1', quantity: 100, unit: 'kg', quantityKg: 100, unitPrice: 10000, amount: 1000000 }],
    transfersOut: [{ id: 101, code: 'CHUYEN-NOI-BO-001', date: '2025-02-20', total: 1000000 }],
    transferItems: [{ documentId: 101, product: 'Nhôm 1', category: 'Loại 1', quantity: 100, unit: 'kg', quantityKg: 100, unitPrice: 10000, amount: 1000000 }],
    receipts: [], expenses: [], stock: [{ product: 'Nhôm 1', category: 'Loại 1', quantityKg: 600, value: 6000000 }],
    wages: [{ year: 2025, month: 2, employeeId: 1, employee: 'Thợ Nguyễn Văn Nam', days: 22, basePay: 7700000, overtimePay: 200000, bonusPay: 100000, allowances: 50000, deductions: 0, advanceAmount: 1000000, totalPay: 7050000 }],
    wageEvents: [{ year: 2025, month: 2, employeeId: 1, employee: 'Thợ Nguyễn Văn Nam', date: '2025-02-20', kind: 'Ứng lương', amount: 1000000, note: 'Đã nhận tiền mặt', received: true }],
    inventoryValue: 6000000, profit: null, closed: false,
  };
}

for (const shopCode of ['DISTRIBUTION', 'MANUFACTURING']) {
  test(`${shopCode}: paid and unpaid customers, all invoices, payment dates and safe names are printed`, () => {
    const html = buildAnnualBookHtml([fixture(shopCode)]);
    for (let i = 1; i <= 40; i++) assert.ok(html.includes(`TEST-${String(i).padStart(3, '0')}`));
    assert.ok(html.includes('Nguyễn Hải &lt;script&gt;'));
    assert.ok(!html.includes('<script>'));
    assert.ok(html.includes('21/2/2025') || html.includes('21/02/2025'));
    assert.ok(html.includes('4.000.000'));
    assert.ok(html.includes('3.000.000'));
    assert.ok(html.includes('1.000.000'));
    assert.ok(html.indexOf('TEST-003') < html.indexOf('TEST-001'), 'Print latest invoice dates first, regardless of API ordering');
    assert.ok(html.includes('Tiền mặt'), 'Use understandable payment method labels');
    assert.equal(html.includes('Thợ Nguyễn Văn Nam'), shopCode === 'MANUFACTURING');
    if (shopCode === 'DISTRIBUTION') {
      assert.match(html, /CHUYEN-NOI-BO-001 · 20\/0?2\/2025 · Nội bộ/);
      assert.ok(!html.includes('Phiếu chưa gắn khách còn nợ đến lúc in <b>1.000.000'));
    }
    if (process.env.AUDIT_PDF_DIRECTORY) {
      mkdirSync(process.env.AUDIT_PDF_DIRECTORY, { recursive: true });
      writeFileSync(resolve(process.env.AUDIT_PDF_DIRECTORY, `${shopCode}.html`), html);
    }
  });
}

test('factory shows an earlier internal receipt as before period without adding it to the period total', () => {
  const book = fixture('MANUFACTURING');
  book.purchases[0].date = '2025-02-10';
  const html = buildAnnualBookHtml([book]);
  assert.match(html, /NOI-BO-001 · 10\/0?2\/2025 · Nội bộ · Trước kỳ/);
  assert.ok(html.includes('Tổng nhôm nhập nội bộ trong kỳ: 0 kg'));
});
