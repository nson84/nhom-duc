import { ArrowLeftOutlined, BellOutlined, CheckOutlined, CloseOutlined, DollarOutlined, PlusOutlined, PrinterOutlined } from "@ant-design/icons";
import {
  Alert,
  Card,
  Checkbox,
  DatePicker,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  message,
} from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AppActionButton, appButtonStyle } from "../../components/AppButtons";
import { PagedTable } from "../../components/PagedTable";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { requiredNameRules } from "../../utils/name-rules";
import { formatQtyInput } from "../../utils/vnd-input";
import { isValidPaymentAmountVnd, PAYMENT_AMOUNT_STEP_ERROR, vndInputNumberProps, vndPaymentAmountRules } from "../../utils/vnd-input";
import { useCanWrite, useSession } from "../auth/auth-store";
import { listBankAccounts, type BankAccount } from "../settings/bank-accounts-api";
import { getSettings, type ShopSettings } from "../settings/settings-api";
import { getCustomerYear, type CustomerYear } from "../factory/factory-api";
import { SHOP_LABELS } from "../shop/shop-store";
import { addPayment, getDocument, type StockDocument } from "../stock/stock-api";
import { bankName } from "../../utils/vietqr";
import { buildDebtListHtml, hasBankAccount, openDebtDocuments } from "./debt-print";
import {
  createCustomerReminder,
  getCustomer,
  listCustomerDocuments,
  listCustomerReminders,
  setReminderDone,
  type CustomerDocument,
  type CustomerReminder,
  type CustomerSummary,
} from "./customers-api";

const paymentTag: Record<string, { color: string; text: string }> = {
  UNPAID: { color: "red", text: "Chưa thanh toán" },
  PARTIAL: { color: "orange", text: "Thanh toán một phần" },
  PAID: { color: "green", text: "Đã thanh toán" },
};

function paymentMethodLabel(raw: string | null | undefined): string {
  if (!raw) return "—";
  const value = raw.trim();
  const upper = value.toUpperCase();
  if (upper === "CASH" || upper.includes("CASH") || value === "Tiền mặt") return "Tiền mặt";
  if (upper === "TRANSFER" || upper.includes("TRANSFER") || value === "Chuyển khoản") return "Chuyển khoản";
  if (upper === "OTHER" || value === "Khác") return "Khác";
  return value;
}

export function CustomerDetailPage() {
  const { id } = useParams();
  const customerId = Number(id);
  const navigate = useNavigate();
  const canWrite = useCanWrite();
  const session = useSession();
  const isAdmin = session?.role === "ADMIN";
  const scope = isAdmin ? ("all" as const) : undefined;

  const [summary, setSummary] = useState<CustomerSummary | null>(null);
  const [salesYear, setSalesYear] = useState(dayjs().year());
  const [yearSummary, setYearSummary] = useState<CustomerYear | null>(null);
  const [docs, setDocs] = useState<CustomerDocument[]>([]);
  const [reminders, setReminders] = useState<CustomerReminder[]>([]);
  const [paymentFilter, setPaymentFilter] = useState<string | undefined>();
  const initializedYearFor = useRef<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [payingDoc, setPayingDoc] = useState<CustomerDocument | null>(null);
  const [viewingId, setViewingId] = useState<number | null>(null);
  const [remindersOpen, setRemindersOpen] = useState(false);
  const [addingReminder, setAddingReminder] = useState(false);
  const [printingDebt, setPrintingDebt] = useState(false);

  const load = useCallback(async () => {
    if (!customerId) return;
    setLoading(true);
    try {
      const [s, d, r] = await Promise.all([
        getCustomer(customerId, scope),
        listCustomerDocuments(customerId, paymentFilter, scope),
        listCustomerReminders(customerId, scope),
      ]);
      setSummary(s);
      setDocs(d);
      setReminders(r);
      if (initializedYearFor.current !== customerId && d.length > 0) {
        setSalesYear(Math.max(...d.map((doc) => dayjs(doc.documentDate).year())));
        initializedYearFor.current = customerId;
      }
    } catch (error) {
      message.error(toApiError(error).message);
      navigate("/khach-mua");
    } finally {
      setLoading(false);
    }
  }, [customerId, paymentFilter, scope, navigate]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!customerId || summary?.shopCode !== "MANUFACTURING") {
      setYearSummary(null);
      return;
    }
    let active = true;
    getCustomerYear(customerId, salesYear)
      .then((value) => { if (active) setYearSummary(value); })
      .catch((error) => { if (active) message.error(toApiError(error).message); });
    return () => { active = false; };
  }, [customerId, salesYear, summary?.shopCode]);

  if (!summary) {
    return <Card loading={loading} title="Khách mua" />;
  }

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <Card
        title={
          <Space wrap>
            <AppActionButton tone="secondary" icon={<ArrowLeftOutlined />} onClick={() => navigate("/khach-mua")}>
              Danh sách
            </AppActionButton>
            <span>{summary.name}</span>
          </Space>
        }
        extra={
          <AppActionButton tone="print" icon={<PrinterOutlined />} onClick={() => setPrintingDebt(true)}>
            In danh sách nợ
          </AppActionButton>
        }
      >
        <Descriptions column={{ xs: 1, md: 3 }} size="small">
          {isAdmin ? (
            <Descriptions.Item label="Cửa hàng">
              {SHOP_LABELS[summary.shopCode as keyof typeof SHOP_LABELS] ?? summary.shopName}
            </Descriptions.Item>
          ) : null}
          <Descriptions.Item label="SĐT">{summary.phone || "—"}</Descriptions.Item>
          <Descriptions.Item label="Doanh thu">{formatMoney(summary.totalSales)}</Descriptions.Item>
          <Descriptions.Item label="Còn nợ">
            {summary.outstandingDebt > 0 ? (
              <Tag color="red">{formatMoney(summary.outstandingDebt)}</Tag>
            ) : (
              <Tag color="green">0</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="Số phiếu bán">{summary.saleCount}</Descriptions.Item>
        </Descriptions>
      </Card>

      {summary.shopCode === "MANUFACTURING" ? (
        <Card
          title="Sản lượng thành phẩm đã giao"
          extra={<DatePicker picker="year" value={dayjs(`${salesYear}-01-01`)} onChange={(value) => value && setSalesYear(value.year())} />}
        >
          <Descriptions column={{ xs: 1, md: 4 }} size="small">
            <Descriptions.Item label="Đã giao">{yearSummary ? `${formatQtyInput(yearSummary.quantityKg)} kg` : "—"}</Descriptions.Item>
            <Descriptions.Item label="Doanh thu">{formatMoney(yearSummary?.revenue ?? 0)}</Descriptions.Item>
            <Descriptions.Item label="Đã thu của các phiếu">{formatMoney(yearSummary?.paidAmount ?? 0)}</Descriptions.Item>
            <Descriptions.Item label="Còn nợ các phiếu">{formatMoney(yearSummary?.outstandingAmount ?? 0)}</Descriptions.Item>
          </Descriptions>
          <PagedTable
            rowKey={(row: CustomerYear["rows"][number], index) => `${row.documentId}-${row.productName}-${index ?? 0}`}
            dataSource={yearSummary?.rows ?? []}
            size="middle"
            columns={[
              { title: "Ngày giờ", dataIndex: "documentDate", render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm") },
              { title: "Số phiếu", dataIndex: "documentCode" },
              { title: "Sản phẩm", dataIndex: "productName" },
              { title: "Kg", dataIndex: "quantityKg", render: (v: number) => formatQtyInput(v) },
              { title: "Đơn giá", render: (_, row) => `${formatMoney(row.unitPrice)} / ${row.unitName}` },
              { title: "Thành tiền", dataIndex: "lineTotal", render: (v: number) => formatMoney(v) },
              { title: "Người bán", dataIndex: "sellerName" },
            ]}
            onRow={(row) => ({ className: "app-row-clickable", onClick: () => setViewingId(row.documentId) })}
          />
        </Card>
      ) : null}

      <Card
        title="Hóa đơn"
        extra={
          <Select
            allowClear
            placeholder="Tất cả hóa đơn"
            style={{ width: 200 }}
            value={paymentFilter}
            onChange={setPaymentFilter}
            options={[
              { value: "OPEN", label: "Còn nợ" },
              { value: "UNPAID", label: "Chưa thanh toán" },
              { value: "PARTIAL", label: "Thanh toán một phần" },
              { value: "PAID", label: "Đã thanh toán" },
            ]}
          />
        }
      >
        <PagedTable<CustomerDocument>
          rowKey="id"
          loading={loading}
          dataSource={docs}
          size="middle"
          onRow={(row) => ({
            className: "app-row-clickable",
            onClick: () => setViewingId(row.id),
          })}
          columns={[
            { title: "Số phiếu", dataIndex: "documentCode", align: "center" },
            {
              title: "Ngày",
              dataIndex: "documentDate",
              align: "center",
              render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm"),
            },
            {
              title: "Tổng",
              dataIndex: "totalAmount",
              align: "center",
              render: (v: number) => formatMoney(v),
            },
            {
              title: "Đã thu đến nay",
              dataIndex: "paidAmount",
              align: "center",
              render: (v: number) => formatMoney(v),
            },
            {
              title: "Còn phải thu",
              dataIndex: "remainingAmount",
              align: "center",
              render: (v: number) => formatMoney(v),
            },
            {
              title: "Thanh toán",
              dataIndex: "paymentStatus",
              align: "center",
              render: (v: string) => {
                const tag = paymentTag[v] ?? { color: "default", text: v };
                return <Tag color={tag.color}>{tag.text}</Tag>;
              },
            },
            {
              title: "Thao tác",
              align: "center",
              render: (_, row) =>
                canWrite &&
                row.status === "CONFIRMED" &&
                (row.paymentStatus === "UNPAID" || row.paymentStatus === "PARTIAL") ? (
                  <AppActionButton
                    tone="pay"
                    icon={<DollarOutlined />}
                    onClick={(e) => {
                      e.stopPropagation();
                      setPayingDoc(row);
                    }}
                  >
                    Trả nợ
                  </AppActionButton>
                ) : null,
            },
          ]}
        />
      </Card>

      {viewingId ? <InvoiceDetailModal documentId={viewingId} onClose={() => setViewingId(null)} /> : null}

      <AppActionButton tone="secondary" icon={<BellOutlined />} onClick={() => setRemindersOpen(true)}>Lịch nhắc nợ</AppActionButton>
      <Modal open={remindersOpen} onCancel={() => setRemindersOpen(false)} footer={null} width={850}>
      <Card
        title="Lịch nhắc nợ"
        extra={
          canWrite && summary.outstandingDebt > 0 ? (
            <AppActionButton tone="secondary" icon={<PlusOutlined />} onClick={() => setAddingReminder(true)}>
              Thêm nhắc
            </AppActionButton>
          ) : null
        }
      >
        <Table<CustomerReminder>
          rowKey="id"
          loading={loading}
          dataSource={reminders}
          size="small"
          pagination={false}
          columns={[
            {
              title: "Ngày nhắc",
              dataIndex: "remindOn",
              align: "center",
              render: (v: string, r) => (
                <Space>
                  {!r.done && !dayjs(v).isAfter(dayjs(), "day") ? <BellOutlined style={{ color: "var(--color-warning)" }} /> : null}
                  {dayjs(v).format("DD/MM/YYYY")}
                </Space>
              ),
            },
            {
              title: "Phiếu",
              dataIndex: "documentCode",
              align: "center",
              render: (v: string | null) => v || "—",
            },
            { title: "Ghi chú", dataIndex: "note", align: "center", render: (v: string | null) => v || "—" },
            {
              title: "Trạng thái",
              dataIndex: "done",
              align: "center",
              render: (done: boolean) => (done ? <Tag>Xong</Tag> : <Tag color="orange">Chờ</Tag>),
            },
            {
              title: "Thao tác",
              align: "center",
              render: (_, r) =>
                canWrite ? (
                  <AppActionButton
                    tone="secondary"
                    icon={<CheckOutlined />}
                    onClick={() => {
                      void setReminderDone(customerId, r.id, !r.done)
                        .then(() => {
                          message.success(r.done ? "Đã mở lại nhắc" : "Đã đánh dấu xong");
                          void load();
                        })
                        .catch((error) => message.error(toApiError(error).message));
                    }}
                  >
                    {r.done ? "Mở lại" : "Xong"}
                  </AppActionButton>
                ) : null,
            },
          ]}
        />
      </Card>
      </Modal>

      {payingDoc ? (
        <PayDebtModal
          doc={payingDoc}
          onClose={() => setPayingDoc(null)}
          onPaid={() => {
            setPayingDoc(null);
            void load();
          }}
        />
      ) : null}

      {addingReminder ? (
        <ReminderModal
          customerId={customerId}
          openDocs={docs.filter((d) => d.paymentStatus === "UNPAID" || d.paymentStatus === "PARTIAL")}
          onClose={() => setAddingReminder(false)}
          onSaved={() => {
            setAddingReminder(false);
            void load();
          }}
        />
      ) : null}

      {printingDebt ? (
        <DebtPrintModal customer={summary} scope={scope} onClose={() => setPrintingDebt(false)} />
      ) : null}
    </Space>
  );
}

function DebtPrintModal({
  customer,
  scope,
  onClose,
}: {
  customer: CustomerSummary;
  scope?: "all";
  onClose: () => void;
}) {
  const frameRef = useRef<HTMLIFrameElement>(null);
  const [shop, setShop] = useState<ShopSettings | null>(null);
  const [accounts, setAccounts] = useState<BankAccount[]>([]);
  const [bankId, setBankId] = useState<number | null>(null);
  const [debts, setDebts] = useState<StockDocument[] | null>(null);
  const [withQr, setWithQr] = useState(true);
  const [html, setHtml] = useState<string | null>(null);
  const selectedBank = accounts.find((a) => a.id === bankId) ?? accounts.find((a) => a.isDefault) ?? accounts[0] ?? null;
  const bankReady = hasBankAccount(selectedBank);

  useEffect(() => {
    Promise.all([
      getSettings().catch(() => null),
      listBankAccounts().catch(() => [] as BankAccount[]),
      listCustomerDocuments(customer.id, "OPEN", scope),
    ])
      .then(async ([settings, banks, docs]) => {
        setShop(settings);
        setAccounts(banks);
        setBankId(banks.find((a) => a.isDefault)?.id ?? banks[0]?.id ?? null);
        const open = openDebtDocuments(docs);
        setDebts(await Promise.all(open.map((row) => getDocument(row.id))));
      })
      .catch((error) => {
        message.error(toApiError(error).message);
        onClose();
      });
  }, [customer.id, scope]);

  useEffect(() => {
    if (!debts) return;
    let active = true;
    void buildDebtListHtml({
      customer,
      docs: debts,
      shop,
      bank: selectedBank,
      withQr: withQr && bankReady,
    }).then((next) => {
      if (active) setHtml(next);
    });
    return () => {
      active = false;
    };
  }, [customer, debts, shop, selectedBank, withQr, bankReady]);

  return (
    <Modal
      open
      width={920}
      title={`Danh sách nợ — ${customer.name}`}
      onCancel={onClose}
      footer={
        <Space wrap>
          <AppActionButton tone="cancel" icon={<CloseOutlined />} onClick={onClose}>
            Đóng
          </AppActionButton>
          <AppActionButton
            tone="print"
            icon={<PrinterOutlined />}
            disabled={!html}
            onClick={() => frameRef.current?.contentWindow?.print()}
          >
            In / Tải PDF
          </AppActionButton>
        </Space>
      }
    >
      <Space direction="vertical" size={12} style={{ width: "100%" }}>
        <Space wrap style={{ width: "100%" }}>
          <Select
            style={{ minWidth: 280 }}
            placeholder="Chọn tài khoản QR"
            value={bankId ?? undefined}
            onChange={setBankId}
            options={accounts.map((a) => ({
              value: a.id,
              label: `${a.label} — ${bankName(a.bankBin)} ${a.accountNo}${a.isDefault ? " (mặc định)" : ""}`,
            }))}
            disabled={accounts.length === 0}
          />
          <Checkbox checked={withQr && bankReady} disabled={!bankReady} onChange={(e) => setWithQr(e.target.checked)}>
            Gắn mã QR chuyển khoản (số tiền = tổng còn nợ, kèm nội dung)
          </Checkbox>
        </Space>
        {!bankReady ? (
          <Alert
            type="info"
            showIcon
            message="Chưa có tài khoản QR"
            description="Mở menu avatar → Tài khoản QR nhận tiền để thêm ảnh QR và số tài khoản (có thể thêm nhiều tài khoản)."
          />
        ) : null}
        {html ? (
          <iframe
            ref={frameRef}
            title="Xem trước danh sách nợ"
            srcDoc={html}
            style={{ width: "100%", height: "65vh", border: "1px solid var(--color-border)", borderRadius: 8, background: "#e8e8e8" }}
          />
        ) : (
          <Card loading />
        )}
        <span style={{ color: "var(--color-text-secondary)", fontSize: 13 }}>
          Bấm “In / Tải PDF” rồi chọn máy in “Lưu thành PDF” hoặc “Microsoft Print to PDF” để tải file PDF.
        </span>
      </Space>
    </Modal>
  );
}

function lineQtyLabel(item: { billingUnit?: string | null; unitName: string; quantityDocument: number; widthM?: number | null; heightM?: number | null }) {
  const byArea = item.billingUnit === "m2" || item.unitName === "m²";
  if (byArea && item.widthM && item.heightM) {
    return `${formatQtyInput(item.quantityDocument)} m² (${formatQtyInput(item.widthM)}×${formatQtyInput(item.heightM)})`;
  }
  if (byArea) return `${formatQtyInput(item.quantityDocument)} m²`;
  return `${formatQtyInput(item.quantityDocument)} ${item.unitName || ""}`.trim();
}

function payMethodLabel(raw: string | null | undefined): string {
  if (!raw) return "";
  const value = raw.trim();
  const upper = value.toUpperCase();
  if (upper === "CASH" || value === "Tiền mặt") return "Tiền mặt";
  if (upper === "TRANSFER" || value === "Chuyển khoản") return "Chuyển khoản";
  if (upper === "OTHER" || value === "Khác") return "Khác";
  return value;
}

function InvoiceDetailModal({ documentId, onClose }: { documentId: number; onClose: () => void }) {
  const [doc, setDoc] = useState<StockDocument | null>(null);

  useEffect(() => {
    void getDocument(documentId)
      .then(setDoc)
      .catch((error) => {
        message.error(toApiError(error).message);
        onClose();
      });
  }, [documentId]);

  const status = doc ? (paymentTag[doc.paymentStatus] ?? { color: "default", text: doc.paymentStatus }) : null;
  const methods = doc
    ? [...new Set((doc.payments ?? []).map((p) => payMethodLabel(p.paymentMethod)).filter(Boolean))]
    : [];
  const methodText = methods.length > 0 ? methods.join(", ") : payMethodLabel(doc?.paymentMethod);

  return (
    <Modal open width={880} title="Chi tiết hóa đơn" footer={null} onCancel={onClose}>
      {!doc || !status ? (
        <Card loading />
      ) : (
        <>
          <Descriptions size="small" column={{ xs: 1, md: 2 }} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Ngày">{dayjs(doc.documentDate).format("DD/MM/YYYY HH:mm")}</Descriptions.Item>
            <Descriptions.Item label="Thanh toán">
              <Tag color={status.color}>{methodText ? `${status.text} · ${methodText}` : status.text}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Người xuất">{doc.issuedBy || "—"}</Descriptions.Item>
            <Descriptions.Item label="Người nhận">{doc.receivedBy || "—"}</Descriptions.Item>
          </Descriptions>
          <Table
            size="small"
            pagination={false}
            rowKey="id"
            dataSource={doc.items}
            scroll={{ x: "max-content" }}
            columns={[
              { title: "Sản phẩm", dataIndex: "productName", align: "center" },
              { title: "Loại nhôm", dataIndex: "categoryName", align: "center", render: (v: string | null) => v || "—" },
              { title: "Số lượng", align: "center", render: (_, item) => lineQtyLabel(item) },
              { title: "Đơn giá", dataIndex: "unitPrice", align: "center", render: (v: number) => formatMoney(v) },
              { title: "Thành tiền", dataIndex: "lineTotal", align: "center", render: (v: number) => formatMoney(v) },
            ]}
          />
          <p style={{ textAlign: "center", marginTop: 12, marginBottom: (doc.payments ?? []).length > 0 ? 8 : 0 }}>
            Thành tiền: <b>{formatMoney(doc.totalAmount)}</b>
            {" · "}Đã thu: <b>{formatMoney(doc.paidAmount)}</b>
            {" · "}Còn phải thu: <b>{formatMoney(Math.max(0, doc.totalAmount - doc.paidAmount))}</b>
          </p>
          {(doc.payments ?? []).length > 0 ? (
            <Table
              size="small"
              pagination={false}
              rowKey="id"
              dataSource={doc.payments}
              scroll={{ x: "max-content" }}
              columns={[
                {
                  title: "Ngày giờ",
                  dataIndex: "paymentDate",
                  align: "center",
                  render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm"),
                },
                {
                  title: "Số tiền",
                  dataIndex: "amount",
                  align: "center",
                  render: (v: number) => formatMoney(v),
                },
                {
                  title: "Hình thức thanh toán",
                  dataIndex: "paymentMethod",
                  align: "center",
                  render: (v: string | null) => payMethodLabel(v) || "—",
                },
                {
                  title: "Người thu",
                  dataIndex: "collectedBy",
                  align: "center",
                  render: (v: string | null) => v || "—",
                },
              ]}
            />
          ) : null}
        </>
      )}
    </Modal>
  );
}

function PayDebtModal({
  doc,
  onClose,
  onPaid,
}: {
  doc: CustomerDocument;
  onClose: () => void;
  onPaid: () => void;
}) {
  const session = useSession();
  const [form] = Form.useForm<{ mode: "full" | "partial"; amount?: number; paymentMethod: string; collectedBy: string; note?: string }>();
  const [saving, setSaving] = useState(false);
  const [payments, setPayments] = useState<
    { id: number; paymentDate: string; amount: number; paymentMethod: string | null; note: string | null; collectedBy?: string | null }[]
  >([]);
  const mode = Form.useWatch("mode", form) ?? "full";
  const remaining = doc.remainingAmount;
  const [idempotencyKey] = useState(() => crypto.randomUUID());

  useEffect(() => {
    void getDocument(doc.id)
      .then((full) => setPayments(full.payments ?? []))
      .catch(() => undefined);
  }, [doc.id]);

  return (
    <Modal
      title={`Trả nợ — ${doc.documentCode}`}
      open
      onCancel={onClose}
      okText="Ghi thu"
      cancelText="Đóng"
      confirmLoading={saving}
      okButtonProps={{ style: appButtonStyle("pay") }}
      onOk={() => form.submit()}
    >
      <p>
        Còn phải thu: <b>{formatMoney(remaining)}</b>
        {" · "}
        Tổng phiếu: {formatMoney(doc.totalAmount)}
      </p>
      {payments.length > 0 ? (
        <>
          <p style={{ marginBottom: 8 }}>
            Lịch sử từng lần thanh toán ({payments.length} lần) — mỗi lần ghi rõ ngày giờ:
          </p>
          <Table
            size="small"
            pagination={false}
            style={{ marginBottom: 16 }}
            rowKey="id"
            dataSource={payments}
            columns={[
              {
                title: "Thời gian thanh toán",
                dataIndex: "paymentDate",
                align: "center",
                render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm"),
              },
              {
                title: "Số tiền lần này",
                dataIndex: "amount",
                align: "center",
                render: (v: number) => formatMoney(v),
              },
              {
                title: "Hình thức thanh toán",
                dataIndex: "paymentMethod",
                align: "center",
                render: (v: string | null) => paymentMethodLabel(v),
              },
              {
                title: "Người thu",
                dataIndex: "collectedBy",
                align: "center",
                render: (v: string | null) => v || "—",
              },
              {
                title: "Còn phải thu sau lần này",
                key: "remainingAfter",
                align: "center",
                render: (_: unknown, _row, index) => {
                  const paidThrough = payments
                    .slice(0, index + 1)
                    .reduce((sum, payment) => sum + Number(payment.amount), 0);
                  return formatMoney(Math.max(0, doc.totalAmount - paidThrough));
                },
              },
            ]}
          />
        </>
      ) : (
        <p style={{ marginBottom: 16, color: "var(--color-text-secondary)" }}>Chưa có lần thanh toán nào.</p>
      )}
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          mode: isValidPaymentAmountVnd(remaining) ? "full" : "partial",
          paymentMethod: "CASH",
          amount: remaining,
          collectedBy: session?.username ?? "",
        }}
        onFinish={async (values) => {
          const amount = values.mode === "full" ? remaining : Number(values.amount ?? 0);
          if (amount <= 0 || amount > remaining) {
            message.error("Số tiền không hợp lệ");
            return;
          }
          if (!isValidPaymentAmountVnd(amount)) {
            message.error(PAYMENT_AMOUNT_STEP_ERROR);
            return;
          }
          Modal.confirm({
            title: "Xác nhận ghi thu?",
            content: (
              <div>
                <p style={{ margin: "8px 0 4px" }}>
                  Phiếu <b>{doc.documentCode}</b>
                </p>
                <p style={{ margin: "4px 0" }}>
                  Số tiền thu: <b>{formatMoney(amount)}</b> ({paymentMethodLabel(values.paymentMethod)})
                </p>
                <p style={{ margin: "4px 0" }}>
                  Người thu tiền: <b>{values.collectedBy.trim()}</b>
                </p>
                <p style={{ margin: "4px 0" }}>
                  Còn phải thu sau lần này: <b>{formatMoney(remaining - amount)}</b>
                </p>
              </div>
            ),
            okText: "Ghi thu",
            cancelText: "Quay lại",
            okButtonProps: { style: appButtonStyle("pay") },
            cancelButtonProps: { style: appButtonStyle("cancel") },
            onOk: async () => {
              setSaving(true);
              try {
                await addPayment(doc.id, {
                  amount,
                  paymentMethod: values.paymentMethod,
                  collectedBy: values.collectedBy.trim(),
                  idempotencyKey,
                });
                message.success("Đã ghi lần trả nợ");
                onPaid();
              } catch (error) {
                message.error(toApiError(error).message);
              } finally {
                setSaving(false);
              }
            },
          });
        }}
      >
        <Form.Item name="mode" label="Hình thức trả">
          <Select
            options={[
              {
                value: "full",
                label: `Trả hết (${formatMoney(remaining)})`,
                disabled: !isValidPaymentAmountVnd(remaining),
              },
              { value: "partial", label: "Trả một phần" },
            ]}
          />
        </Form.Item>
        {mode === "partial" ? (
          <Form.Item
            name="amount"
            label="Số tiền"
            rules={[
              ...vndPaymentAmountRules,
              {
                validator: async (_, val) => {
                  if (Number(val) > remaining) throw new Error("Không vượt số còn lại");
                },
              },
            ]}
            extra="Chỉ nhập bội 500 đ (VD: 1,000 hoặc 1,500)"
          >
            <InputNumber {...vndInputNumberProps} style={{ width: "100%" }} min={500} max={remaining} />
          </Form.Item>
        ) : null}
        <Form.Item
          name="collectedBy"
          label="Người thu tiền"
          rules={requiredNameRules("Nhập người thu tiền", 80)}
          extra="Tên này in trên danh sách nợ, mỗi lần thu một người"
        >
          <Input maxLength={80} />
        </Form.Item>
        <Form.Item
          name="paymentMethod"
          label="Hình thức thanh toán"
          rules={[{ required: true, message: "Chọn hình thức thanh toán" }]}
          extra="Khách trả bằng tiền mặt, chuyển khoản hay cách khác"
        >
          <Select
            options={[
              { value: "CASH", label: "Tiền mặt" },
              { value: "TRANSFER", label: "Chuyển khoản" },
              { value: "OTHER", label: "Khác" },
            ]}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function ReminderModal({
  customerId,
  openDocs,
  onClose,
  onSaved,
}: {
  customerId: number;
  openDocs: CustomerDocument[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<{ remindOn: Dayjs; documentId?: number; note?: string }>();
  const [saving, setSaving] = useState(false);

  return (
    <Modal
      title="Thêm lịch nhắc nợ"
      open
      onCancel={onClose}
      okText="Lưu"
      cancelText="Hủy"
      confirmLoading={saving}
      onOk={() => form.submit()}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{ remindOn: dayjs().add(7, "day") }}
        onFinish={async (values) => {
          setSaving(true);
          try {
            await createCustomerReminder(customerId, {
              remindOn: values.remindOn.format("YYYY-MM-DD"),
              documentId: values.documentId,
              note: values.note,
            });
            message.success("Đã tạo lịch nhắc");
            onSaved();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item name="remindOn" label="Ngày nhắc" rules={[{ required: true, message: "Chọn ngày" }]}>
          <DatePicker style={{ width: "100%" }} format="DD/MM/YYYY" />
        </Form.Item>
        <Form.Item name="documentId" label="Gắn phiếu (tuỳ chọn)">
          <Select
            allowClear
            placeholder="Chọn phiếu còn nợ"
            options={openDocs.map((d) => ({
              value: d.id,
              label: `${d.documentCode} — còn ${formatMoney(d.remainingAmount)}`,
            }))}
          />
        </Form.Item>
        <Form.Item name="note" label="Ghi chú">
          <Input maxLength={500} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
