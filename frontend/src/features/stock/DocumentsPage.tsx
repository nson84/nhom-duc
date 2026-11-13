import {
  CheckOutlined,
  CloseOutlined,
  DeleteOutlined,
  DollarOutlined,
  EditOutlined,
  PlusOutlined,
  PrinterOutlined,
  RetweetOutlined,
  RollbackOutlined,
  StopOutlined,
  UserAddOutlined,
} from "@ant-design/icons";
import { Alert, AutoComplete, Button, Card, Checkbox, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Select, Space, Tag, message } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useBlocker } from "react-router-dom";
import { AppActionButton, appButtonStyle } from "../../components/AppButtons";
import { PagedTable, ResponsiveTable as Table } from "../../components/PagedTable";
import { LiveSearch } from "../../components/LiveSearch";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import {
  bindDraftUnloadGuard,
  clearSessionDraft,
  purchaseFormDraftKey,
  readSessionDraft,
  writeSessionDraft,
  type SessionDraftEnvelope,
} from "../../utils/session-draft";
import { optionalNameRules, requiredNameRules } from "../../utils/name-rules";
import {
  documentFieldValidateTrigger,
  formatQtyInput,
  parseQtyInput,
  positiveDecimalRules,
  qtyInputNumberProps,
  vndInputNumberProps,
  isValidPaymentAmountVnd,
  vndUnitPriceRules,
} from "../../utils/vnd-input";
import {
  KG_PER_TON,
  kgUnit,
  purchaseUnit,
  suggestPurchasePricePerUnit,
  suggestSalePricePerUnit,
  suggestSalePricePerWeightUnit,
  taUnit,
  tonUnit,
  type PurchaseWeightUnit,
} from "../products/product-stock-unit";
import { useCanWrite, useSession } from "../auth/auth-store";
import { useShop } from "../shop/shop-store";
import { CustomerFormModal } from "../customers/CustomersPage";
import { CustomerSuggestSearch } from "../customers/CustomerSuggestSearch";
import { getCustomer, linkDocumentCustomer, listCustomers, type Customer } from "../customers/customers-api";
import { listCategories, listProducts, savePurchasePrice, type Category, type Product } from "../products/products-api";
import { addPriceReviewAlert } from "../products/price-review-alerts";
import { buildSaleInvoiceHtml, printStockDocument } from "./document-print";
import { listBankAccounts, type BankAccount } from "../settings/bank-accounts-api";
import { getSettings, type ShopSettings } from "../settings/settings-api";
import {
  addPayment,
  documentAction,
  getDocument,
  listDocuments,
  restoreDocument,
  reverseDocument,
  saveDocument,
  trashDocument,
  type DocumentListFilter,
  type DocumentSummary,
  type StockDocument,
} from "./stock-api";

type Kind = "PURCHASE" | "SALE";

const copy = {
  PURCHASE: {
    title: "Nhập kho",
    newLabel: "Phiếu nhập",
    counterparty: "Nhà cung cấp",
    payLabel: "Chi tiền",
    paidLabel: "Đã chi",
  },
  SALE: {
    title: "Bán hàng",
    newLabel: "Phiếu bán",
    counterparty: "Khách hàng",
    payLabel: "Thu tiền",
    paidLabel: "Đã thu",
  },
} as const;

const statusTag: Record<string, { color: string; text: string }> = {
  DRAFT: { color: "gold", text: "Nháp" },
  CONFIRMED: { color: "green", text: "Đã xác nhận" },
  CANCELLED: { color: "default", text: "Đảo / Hủy" },
};
const paymentTag: Record<string, { color: string; text: string }> = {
  UNPAID: { color: "red", text: "Chưa thanh toán" },
  PARTIAL: { color: "orange", text: "Thanh toán một phần" },
  PAID: { color: "green", text: "Đã thanh toán" },
};

const LIST_FILTER_OPTIONS: { value: DocumentListFilter; label: string }[] = [
  { value: "all", label: "Tất cả" },
  { value: "unpaid", label: "Chưa thanh toán" },
  { value: "partial", label: "Thanh toán một phần" },
  { value: "paid", label: "Đã thanh toán" },
  { value: "draft", label: "Nháp" },
  { value: "voided", label: "Đảo / Hủy" },
  { value: "trash", label: "Thùng rác" },
];

const TRASH_RETENTION_DAYS = 90;
const typeLabel: Record<string, string> = {
  PURCHASE: "Nhập",
  SALE: "Bán",
  ADJUSTMENT_IN: "Đảo bán",
  ADJUSTMENT_OUT: "Đảo nhập",
};

function formatQtyDisplay(value: number): string {
  return formatQtyInput(value);
}

function paymentMethodLabel(raw: string | null | undefined): string {
  if (!raw) return "—";
  const value = raw.trim();
  const upper = value.toUpperCase();
  if (upper === "CASH" || upper.includes("CASH") || value === "Tiền mặt") return "Tiền mặt";
  if (upper === "TRANSFER" || upper.includes("TRANSFER") || value === "Chuyển khoản") return "Chuyển khoản";
  return value;
}

/** Cột dòng phiếu nhập — ô đơn giá / thành tiền rộng để số lớn + hậu tố đ không tràn. */
const purchaseLineColumns =
  "minmax(160px, 1fr) minmax(140px, 0.8fr) 88px 96px minmax(190px, 1.1fr) minmax(200px, 1.1fr) 56px";

export function DocumentsPage({ kind }: { kind: Kind }) {
  const canWrite = useCanWrite();
  const text = copy[kind];
  const shop = useShop();
  const session = useSession();
  const draftKey = `${purchaseFormDraftKey(kind)}:${shop}:${session?.username}`;
  const parkedKey = `${draftKey}:parked`;
  const [rows, setRows] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [listFilter, setListFilter] = useState<DocumentListFilter>("all");
  const [range, setRange] = useState<[Dayjs, Dayjs] | null>(null);
  const [editing, setEditing] = useState<StockDocument | "new" | null>(null);
  const [viewing, setViewing] = useState<number | null>(null);
  const [parked, setParked] = useState(() => readParkedDrafts(parkedKey));
  const [parkedId, setParkedId] = useState<string>();
  const draftRestoredRef = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(
        await listDocuments({
          type: kind,
          filter: listFilter,
          q: query || undefined,
          from: range?.[0]?.format("YYYY-MM-DD"),
          to: range?.[1]?.format("YYYY-MM-DD"),
        }),
      );
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [kind, listFilter, query, range]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    draftRestoredRef.current = false;
  }, [kind]);

  // F5: mở lại form phiếu nếu còn bản nháp
  useEffect(() => {
    if (loading || draftRestoredRef.current || editing) return;
    const draft = readSessionDraft<DocumentDraftValues>(draftKey) as DocumentDraft | null;
    if (!draft) return;
    draftRestoredRef.current = true;
    setParkedId(draft.parkedId);
    if (draft.targetId === "new") {
      setEditing("new");
      message.info(kind === "PURCHASE" ? "Đã khôi phục bản nháp phiếu nhập" : "Đã khôi phục bản nháp phiếu bán");
      return;
    }
    void getDocument(draft.targetId)
      .then((doc) => {
        if (doc.documentType !== kind || doc.status !== "DRAFT") {
          clearSessionDraft(draftKey);
          return;
        }
        setEditing(doc);
        message.info("Đã khôi phục bản nháp sửa phiếu");
      })
      .catch(() => clearSessionDraft(draftKey));
  }, [loading, editing, draftKey, kind]);

  function updateParked(next: ParkedDraft[]) {
    localStorage.setItem(parkedKey, JSON.stringify(next));
    setParked(next);
  }

  async function resumeDraft(draft: ParkedDraft) {
    try {
      const doc = draft.targetId === "new" ? "new" : await getDocument(draft.targetId);
      if (doc !== "new" && doc.status !== "DRAFT") {
        message.warning("Phiếu này không còn ở trạng thái nháp.");
        return;
      }
      writeSessionDraft(draftKey, draft);
      setParkedId(draft.parkedId);
      setEditing(doc);
    } catch (error) {
      message.error(toApiError(error).message);
    }
  }

  return (
    <Card
      title={text.title}
      extra={
        canWrite ? (
          <AppActionButton tone="create" icon={<PlusOutlined />} onClick={() => setEditing("new")}>
            {text.newLabel}
          </AppActionButton>
        ) : null
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        {kind === "SALE" ? (
          <CustomerSuggestSearch
            value={query}
            style={{ width: 340 }}
            placeholder="Tìm số phiếu, tên hoặc số điện thoại khách"
            onSearch={setQuery}
          />
        ) : (
          <LiveSearch value={query} onChange={setQuery} placeholder="Tìm số phiếu hoặc tên nhà cung cấp" style={{ width: 400 }} suggestions={rows.flatMap((r) => [r.documentCode, r.counterpartyName ?? ""])} />
        )}
        <DatePicker.RangePicker
          format="DD/MM/YYYY"
          value={range}
          onChange={(value) => setRange(value as [Dayjs, Dayjs] | null)}
          allowClear
          placeholder={["Từ ngày", "Đến ngày"]}
        />
        <Select
          style={{ width: 220 }}
          value={listFilter}
          onChange={setListFilter}
          options={LIST_FILTER_OPTIONS}
        />
      </Space>
      <PagedTable<DocumentSummary>
        rowKey="id"
        loading={loading}
        dataSource={rows}
        onRow={(record) => ({
          onClick: () => setViewing(record.id),
          className: "app-row-clickable",
        })}
        columns={[
          {
            title: "Số phiếu",
            dataIndex: "documentCode",
            align: "center",
            render: (code: string, r) => (
              <div style={{ lineHeight: 1.35 }}>
                <div>
                  {r.internal ? (
                    <Tag color="gold" style={{ marginInlineEnd: 0 }}>
                      Nội bộ
                    </Tag>
                  ) : (
                    <Tag style={{ marginInlineEnd: 0 }}>{kind === "SALE" ? "Khách hàng" : "Nhà cung cấp"}</Tag>
                  )}
                </div>
                <div style={{ marginTop: 4, fontWeight: 500 }}>{code}</div>
                {listFilter === "trash" && r.trashedAt && r.status === "CANCELLED" ? (
                  <div style={{ marginTop: 4, fontSize: 12, color: "var(--color-text-secondary)" }}>
                    Còn {Math.max(0, TRASH_RETENTION_DAYS - dayjs().diff(dayjs(r.trashedAt), "day"))} ngày trong thùng
                    rác
                  </div>
                ) : null}
              </div>
            ),
          },
          { title: "Ngày", dataIndex: "documentDate", align: "center", render: (v: string) => dayjs(v).format("DD/MM/YYYY HH:mm") },
          { title: text.counterparty, dataIndex: "counterpartyName", align: "center" },
          { title: "Tổng tiền", dataIndex: "totalAmount", align: "center", render: (v: number) => formatMoney(v) },
          {
            title: "Trạng thái",
            dataIndex: "status",
            align: "center",
            render: (v: string) => <Tag color={statusTag[v].color}>{statusTag[v].text}</Tag>,
          },
          {
            title: "Thanh toán",
            dataIndex: "paymentStatus",
            align: "center",
            render: (v: string, r) => (r.status === "CONFIRMED" ? <Tag color={paymentTag[v].color}>{paymentTag[v].text}</Tag> : null),
          },
        ]}
      />
      {(listFilter === "all" || listFilter === "draft") && parked.length > 0 && (
        <Card size="small" title="Phiếu nháp trên thiết bị này" style={{ marginTop: 12 }}>
          <Space direction="vertical">
            {parked.map((draft) => (
              <Button key={draft.parkedId} disabled={editing != null}
                onClick={() => void resumeDraft(draft)}>
                Sửa phiếu nháp · {draft.values.counterpartyName || "Chưa nhập đối tác"} · {dayjs(draft.savedAt).format("DD/MM/YYYY HH:mm")}
              </Button>
            ))}
          </Space>
        </Card>
      )}
      {editing ? (
        <EditorModal
          kind={kind}
          draftKey={draftKey}
          parkedId={parkedId}
          document={editing === "new" ? null : editing}
          onPark={(draft, saved) => {
            const id = draft.parkedId ?? crypto.randomUUID();
            const others = readParkedDrafts(parkedKey).filter((row) => row.parkedId !== id
              && (draft.targetId === "new" || row.targetId !== draft.targetId));
            if (!saved || others.length !== parked.length) {
              updateParked(saved ? others : [{ ...draft, parkedId: id }, ...others]);
            }
            clearSessionDraft(draftKey);
            setParkedId(undefined);
            setEditing(null);
            setListFilter("draft");
            if (saved) void load();
          }}
          onClose={() => {
            clearSessionDraft(draftKey);
            setParkedId(undefined);
            setEditing(null);
          }}
          onSaved={(saved) => {
            if (parkedId) {
              try {
                updateParked(parked.filter((draft) => draft.parkedId !== parkedId));
              } catch {
                message.warning("Đã lưu phiếu nhưng chưa dọn được bản nháp trên thiết bị.");
              }
            }
            setParkedId(undefined);
            clearSessionDraft(draftKey);
            setEditing(null);
            setViewing(saved.id);
            void load();
          }}
        />
      ) : null}
      {viewing ? (
        <DetailModal
          kind={kind}
          id={viewing}
          onClose={() => setViewing(null)}
          onChanged={() => void load()}
          onEdit={(doc) => {
            setViewing(null);
            setEditing(doc);
          }}
        />
      ) : null}
    </Card>
  );
}
type EditorValues = {
  documentDate: Dayjs;
  counterpartyName?: string;
  customerId?: number;
  issuedBy?: string;
  receivedBy?: string;
  items: {
    productId?: number;
    /** Loại nhôm của dòng — chọn sau sản phẩm; giá gợi ý lấy từ loại nhôm. */
    categoryId?: number;
    unitId?: number;
    quantityDocument?: number;
    unitPrice?: number;
    weightInputUnit?: PurchaseWeightUnit;
    priceInputUnit?: PurchaseWeightUnit;
    /** kg | m2 — đơn vị tính tiền trên phiếu bán. */
    billingUnit?: "kg" | "m2";
    widthM?: number;
    heightM?: number;
    /** Bán m²: kg thực tế để trừ tồn (tách với diện tích tính tiền). */
    stockQuantityKg?: number;
    /** Giá hệ thống đã gợi ý cho dòng này, dùng để biết Admin đã sửa tay hay chưa. */
    suggestedPrice?: number;
  }[];
  internal?: boolean;
};

type DocumentDraftValues = Omit<EditorValues, "documentDate"> & { documentDate?: string };
type DocumentDraft = SessionDraftEnvelope<DocumentDraftValues> & { parkedId?: string };
type ParkedDraft = DocumentDraft & { parkedId: string };

function readParkedDrafts(key: string): ParkedDraft[] {
  try {
    const drafts = JSON.parse(localStorage.getItem(key) ?? "[]");
    return Array.isArray(drafts) ? drafts : [];
  } catch {
    return [];
  }
}

function EditorModal({
  kind,
  draftKey,
  parkedId,
  document,
  onPark,
  onClose,
  onSaved,
}: {
  kind: Kind;
  draftKey: string;
  parkedId?: string;
  document: StockDocument | null;
  onPark: (draft: DocumentDraft, saved: boolean) => void;
  onClose: () => void;
  onSaved: (document: StockDocument) => void;
}) {
  const shop = useShop();
  const sellByKg = shop === "MANUFACTURING" && kind === "SALE";
  const canMarkInternal = shop === "DISTRIBUTION" && kind === "SALE";
  const text = copy[kind];
  const targetId = document?.id ?? "new";
  const [form] = Form.useForm<EditorValues>();
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [customerSuggesting, setCustomerSuggesting] = useState(false);
  const customerSuggestSeq = useRef(0);
  const customerSuggestTimer = useRef<number | null>(null);
  const [saving, setSaving] = useState(false);
  const dirty = useRef(false);
  const blocker = useBlocker(() => dirty.current || saving);
  const [offerSaveCustomer, setOfferSaveCustomer] = useState<StockDocument | null>(null);
  const [saveCustomerForm, setSaveCustomerForm] = useState<StockDocument | null>(null);
  const idempotencyKey = useMemo(() => parkedId ?? crypto.randomUUID(), [parkedId]);
  const draftAppliedRef = useRef(false);
  const items = Form.useWatch("items", form) ?? [];
  const internalTransfer = Form.useWatch("internal", form) === true;

  const legacyAreaDraft = kind === "SALE" && document?.items.some(
    (item) => item.billingUnit === "m2" || item.unitName === "m²",
  );

  const snapshotDraft = useCallback((): DocumentDraft => {
    const values = form.getFieldsValue(true) as EditorValues;
    return {
      targetId,
      parkedId: idempotencyKey,
      values: {
        ...values,
        documentDate: values.documentDate ? dayjs(values.documentDate).toISOString() : undefined,
      },
      savedAt: Date.now(),
    };
  }, [form, targetId, idempotencyKey]);

  const persistDraft = useCallback(() => {
    dirty.current = true;
    writeSessionDraft(draftKey, snapshotDraft());
  }, [draftKey, snapshotDraft]);

  const saveDraftDocument = useCallback((values: EditorValues, confirm = false) => {
    return saveDocument(document?.id, {
      documentType: kind,
      documentDate: values.documentDate.format("YYYY-MM-DDTHH:mm:ss"),
      counterpartyName: values.counterpartyName,
      customerId: kind === "SALE" ? values.customerId ?? null : undefined,
      issuedBy: values.issuedBy,
      receivedBy: values.receivedBy,
      discountAmount: 0,
      internal: canMarkInternal ? Boolean(values.internal) : false,
      idempotencyKey: document ? undefined : idempotencyKey,
      items: values.items.map((i) => {
        const unitPrice = Math.round(Number(i.unitPrice ?? 0));
        const quantityDocument = Number(Number(i.quantityDocument ?? 0).toFixed(6));
        return {
          productId: i.productId!,
          categoryId: i.categoryId!,
          unitId: i.unitId!,
          quantityDocument,
          unitPrice,
          lineDiscount: 0,
          billingUnit: "kg",
        };
      }),
    }, confirm);
  }, [document?.id, kind, canMarkInternal, idempotencyKey]);

  const askToPark = useCallback((onLeave?: () => void, onContinue?: () => void) => Modal.confirm({
    title: "Bạn muốn tiếp tục hoàn thành phiếu?",
    content: "Phiếu sẽ nằm trong danh sách Nháp. Phiếu còn thiếu thông tin được giữ trên thiết bị này để bạn nhập tiếp.",
    okText: "Lưu nháp và rời",
    cancelText: "Tiếp tục nhập",
    onOk: async () => {
      let saved = false;
      try {
        const values = await form.validateFields({ validateOnly: true });
        await saveDraftDocument(values);
        saved = true;
      } catch {
        // A partially completed form must remain recoverable even if the API cannot save it yet.
      }
      try {
        onPark(snapshotDraft(), saved);
        message.success(saved ? "Đã lưu phiếu nháp" : "Đã lưu phiếu nháp trên thiết bị này");
        dirty.current = false;
        onLeave?.();
      } catch {
        message.error("Không lưu được bản nháp trên thiết bị. Hãy tiếp tục nhập hoặc thử lưu phiếu.");
        onContinue?.();
      }
    },
    onCancel: () => onContinue?.(),
  }), [form, onPark, saveDraftDocument, snapshotDraft]);

  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (saving) {
      blocker.reset();
      return;
    }
    const prompt = askToPark(blocker.proceed, blocker.reset);
    return () => prompt.destroy();
  }, [blocker, saving, askToPark]);

  useEffect(() => {
    return bindDraftUnloadGuard(() => dirty.current);
  }, [draftKey]);

  useEffect(() => {
    if (draftAppliedRef.current) return;
    draftAppliedRef.current = true;
    const draft = readSessionDraft<DocumentDraftValues>(draftKey);
    if (!draft || draft.targetId !== targetId) return;
    dirty.current = true;
    form.setFieldsValue({
      ...draft.values,
      items: kind === "SALE" ? draft.values.items.map((line) =>
        line.billingUnit === "m2"
          ? {
              ...line,
              billingUnit: "kg" as const,
              quantityDocument: line.stockQuantityKg,
              unitPrice: undefined,
              widthM: undefined,
              heightM: undefined,
              stockQuantityKg: undefined,
            }
          : line,
      ) : draft.values.items,
      documentDate: draft.values.documentDate ? dayjs(draft.values.documentDate) : dayjs(),
    });
  }, [draftKey, targetId, form]);

  useEffect(() => {
    Promise.all([listProducts({ active: true }), listCategories("all")])
      .then(([p, c]) => {
        setProducts(p);
        setCategories(c);
      })
      .catch((error) => message.error(toApiError(error).message));
  }, []);

  useEffect(() => {
    if (kind !== "SALE") return;
    listCustomers({ active: true })
      .then(setCustomers)
      .catch(() => setCustomers([]));
  }, [kind]);


  function suggestCustomers(q: string) {
    if (kind !== "SALE") return;
    const text = q.trim();
    if (customerSuggestTimer.current != null) {
      window.clearTimeout(customerSuggestTimer.current);
    }
    const id = ++customerSuggestSeq.current;
    customerSuggestTimer.current = window.setTimeout(() => {
      setCustomerSuggesting(Boolean(text));
      listCustomers({ q: text || undefined, active: true })
        .then((rows) => {
          if (id !== customerSuggestSeq.current) return;
          setCustomers(rows);
        })
        .catch(() => {
          if (id === customerSuggestSeq.current) setCustomers([]);
        })
        .finally(() => {
          if (id === customerSuggestSeq.current) setCustomerSuggesting(false);
        });
    }, 180);
  }

  const productMap = useMemo(() => new Map(products.map((p) => [p.id, p])), [products]);
  const categoryMap = useMemo(() => new Map(categories.map((c) => [c.id, c])), [categories]);
  const customerOptions = useMemo(
    () =>
      customers.map((c) => ({
        value: c.name,
        label: c.phone ? `${c.name} — ${c.phone}` : c.name,
        customerId: c.id,
        phone: c.phone,
      })),
    [customers],
  );
  /** Giá vốn BQ theo từng cặp sản phẩm + loại nhôm. */


  useEffect(() => {
    if (!internalTransfer) return;
    // Xuất nội bộ chỉ theo khối lượng — ép các dòng m² về kg.
    const list = [...((form.getFieldValue("items") as EditorValues["items"] | undefined) ?? [])];
    if (list.some((line) => line?.billingUnit === "m2")) {
      const next = [...list];
      next.forEach((line, index) => {
        if (line?.billingUnit !== "m2") return;
        const product = productMap.get(line.productId ?? 0);
        if (!product) {
          next[index] = {
            ...line,
            billingUnit: "kg",
            widthM: undefined,
            heightM: undefined,
            stockQuantityKg: undefined,
          };
          return;
        }
        applySaleBilling(next, index, product, categoryMap.get(line.categoryId ?? 0));
      });
      form.setFieldValue("items", next);
    }
  }, [internalTransfer, form, productMap, categoryMap]);

  // Khi danh mục loại nhôm tải xong (hoặc khôi phục bản nháp), cập nhật giá gợi ý cho các dòng
  // chưa bị sửa tay. Mỗi dòng ghi lại giá mà hệ thống đã gợi ý (`suggestedPrice`); chỉ những dòng
  // còn đúng giá gợi ý đó mới được cập nhật, để không ghi đè giá Admin đã nhập tay.
  useEffect(() => {
    if (kind !== "PURCHASE" || products.length === 0 || categories.length === 0) return;
    const list = (form.getFieldValue("items") as EditorValues["items"] | undefined) ?? [];
    let changed = false;
    const next = list.map((line) => {
      const product = productMap.get(line?.productId ?? 0);
      const category = categoryMap.get(line?.categoryId ?? 0);
      if (!product || !category) return line;
      const weight = line?.weightInputUnit === "tạ" ? "tạ" : "tấn";
      const suggested = suggestPurchasePricePerUnit(category, weight);
      const currentPrice = Math.round(Number(line.unitPrice ?? 0));
      const untouched = line.suggestedPrice === undefined ? currentPrice === 0 : currentPrice === line.suggestedPrice;
      if (!untouched) return line;
      if (currentPrice === suggested && line.suggestedPrice === suggested) return line;
      changed = true;
      return { ...line, unitPrice: suggested, suggestedPrice: suggested };
    });
    if (changed) form.setFieldValue("items", next);
  }, [products, categories, form, kind, productMap, categoryMap]);

  /** Loại nhôm đang dùng, giữ lại loại đã chọn trên dòng dù đã ngừng dùng. */
  function categoryOptions(selectedId?: number) {
    return categories
      .filter((c) => c.active || c.id === selectedId)
      .map((c) => ({ value: c.id, label: c.name }));
  }

  /** Ngày hiệu lực của giá đang gợi ý, hiển thị cạnh ô đơn giá để Admin biết giá lấy từ mốc nào. */
  function priceEffectiveHint(category: Category | undefined) {
    if (kind !== "PURCHASE" || !category?.purchasePriceEffectiveFrom) return null;
    return `Giá chuẩn từ ${dayjs(category.purchasePriceEffectiveFrom).format("DD/MM/YYYY")}`;
  }

  function weightOf(line?: EditorValues["items"][number]): PurchaseWeightUnit {
    if (sellByKg) return line?.weightInputUnit === "tạ" || line?.weightInputUnit === "tấn" ? line.weightInputUnit : "kg";
    if (shop === "MANUFACTURING" && kind === "PURCHASE" && line?.weightInputUnit === "tạ") return "tạ";
    return "tấn";
  }

  function unitFor(product: Product, weight: PurchaseWeightUnit) {
    if (weight === "kg") return kgUnit(product);
    if (weight === "tạ") return taUnit(product);
    return tonUnit(product) ?? purchaseUnit(product, "tấn");
  }

  function applySaleBilling(
    list: EditorValues["items"],
    index: number,
    product: Product,
    category: Category | undefined,
  ) {
    const weight = weightOf(list[index]);
    const unit = unitFor(product, weight);
    if (!unit) {
      message.warning("Sản phẩm chưa có đơn vị phù hợp. Hãy Sửa và Lưu lại trong danh mục.");
    }
    const suggested = category && unit ? suggestSalePricePerUnit(category, unit) : undefined;
    list[index] = {
      ...list[index],
      productId: product.id,
      billingUnit: "kg",
      widthM: undefined,
      heightM: undefined,
      stockQuantityKg: undefined,
      weightInputUnit: weight,
      priceInputUnit: weight,
      unitId: unit?.id,
      unitPrice: suggested,
      suggestedPrice: suggested,
      quantityDocument: list[index]?.quantityDocument && list[index]?.billingUnit !== "m2"
        ? list[index].quantityDocument
        : 1,
    };
    return list;
  }

  /** Chọn sản phẩm rồi loại nhôm: đơn vị theo sản phẩm, đơn giá gợi ý theo loại nhôm của dòng. */
  function applyLineSelection(index: number, productId: number, categoryId: number | undefined) {
    const product = productMap.get(productId);
    if (!product) return;
    const category = categoryId != null ? categoryMap.get(categoryId) : undefined;
    let list = [...(form.getFieldValue("items") as EditorValues["items"])];
    list[index] = { ...list[index], categoryId };

    if (kind === "SALE") {
      list = applySaleBilling(list, index, product, category);
      form.setFieldValue("items", list);
      void form.validateFields([
        ["items", index, "productId"],
        ["items", index, "categoryId"],
        ["items", index, "unitPrice"],
        ["items", index, "quantityDocument"],
      ]);
      return;
    }

    const weight = weightOf(list[index]);
    const unit = unitFor(product, weight);
    if (!unit) {
      message.warning("Sản phẩm chưa có đơn vị phù hợp. Hãy Sửa và Lưu lại trong danh mục.");
    }
    const suggested = category
      ? suggestPurchasePricePerUnit(category, weight === "kg" ? "tấn" : weight)
      : undefined;
    list[index] = {
      ...list[index],
      productId,
      billingUnit: "kg",
      weightInputUnit: weight,
      priceInputUnit: weight,
      unitId: unit?.id,
      unitPrice: suggested,
      suggestedPrice: suggested,
      quantityDocument: list[index]?.quantityDocument ?? 1,
    };
    form.setFieldValue("items", list);
    void form.validateFields([
      ["items", index, "productId"],
      ["items", index, "categoryId"],
      ["items", index, "unitPrice"],
      ["items", index, "quantityDocument"],
    ]);
  }

  function onProductChange(index: number, productId: number) {
    const product = productMap.get(productId);
    if (!product) return;
    const normalize = (value: string) => value.toLocaleLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
    const name = normalize(product.name);
    const matches = categories.filter((category) => {
      const categoryName = normalize(category.name);
      return categoryName.length > 0 && name.includes(categoryName);
    });
    const categoryId = product.categoryId && categoryMap.has(product.categoryId)
      ? product.categoryId
      : matches.length === 1 ? matches[0].id : undefined;
    applyLineSelection(index, productId, categoryId);
  }

  function onCategoryChange(index: number, categoryId: number) {
    const list = form.getFieldValue("items") as EditorValues["items"];
    const productId = list[index]?.productId;
    if (!productId) {
      // Chưa chọn sản phẩm: chỉ ghi nhận loại nhôm, giá sẽ gợi ý khi chọn sản phẩm.
      const next = [...list];
      next[index] = { ...next[index], categoryId };
      form.setFieldValue("items", next);
      return;
    }
    applyLineSelection(index, productId, categoryId);
  }

  function validateLineField(index: number, key: "quantityDocument" | "unitPrice" | "productId") {
    void form.validateFields([["items", index, key]]);
  }

  /** Blur đơn giá: chỉ báo lỗi đỏ nếu không phải bội 500 (…000 / …500) — không tự sửa im lặng. */
  function onWeightChange(index: number, weight: "tạ" | "tấn") {
    const list = [...(form.getFieldValue("items") as EditorValues["items"])];
    const product = productMap.get(list[index]?.productId ?? 0);
    const unit = product ? unitFor(product, weight) : undefined;
    const currentPrice = Math.round(Number(list[index]?.unitPrice ?? 0));
    const untouched = list[index]?.suggestedPrice === undefined ? currentPrice === 0 : currentPrice === list[index]?.suggestedPrice;
    const category = categoryMap.get(list[index]?.categoryId ?? 0);
    const suggested = category ? suggestPurchasePricePerUnit(category, weight) : currentPrice;
    list[index] = {
      ...list[index],
      weightInputUnit: weight,
      priceInputUnit: weight,
      unitId: unit?.id ?? list[index]?.unitId,
      unitPrice: untouched ? suggested : currentPrice,
      suggestedPrice: untouched ? suggested : list[index]?.suggestedPrice,
    };
    form.setFieldValue("items", list);
  }

  function onSaleWeightChange(index: number, weight: PurchaseWeightUnit) {
    const list = [...(form.getFieldValue("items") as EditorValues["items"])];
    const line = list[index];
    const product = productMap.get(line?.productId ?? 0);
    const oldFactor = line?.weightInputUnit === "tấn" ? 1000 : line?.weightInputUnit === "tạ" ? 100 : 1;
    const nextFactor = weight === "tấn" ? 1000 : weight === "tạ" ? 100 : 1;
    const category = categoryMap.get(line?.categoryId ?? 0);
    const currentPrice = Number(line?.unitPrice ?? 0);
    const suggested = category ? suggestSalePricePerWeightUnit(category, weight) : currentPrice * nextFactor / oldFactor;
    const untouched = line?.suggestedPrice === undefined ? currentPrice === 0 : currentPrice === line.suggestedPrice;
    list[index] = {
      ...line,
      weightInputUnit: weight,
      priceInputUnit: weight,
      unitId: product ? unitFor(product, weight)?.id : line?.unitId,
      quantityDocument: Number(((Number(line?.quantityDocument ?? 0) * oldFactor) / nextFactor).toFixed(6)),
      unitPrice: Math.round(untouched ? suggested : currentPrice * nextFactor / oldFactor),
      suggestedPrice: untouched ? Math.round(suggested) : undefined,
    };
    form.setFieldValue("items", list);
  }

  function onUnitPriceBlur(index: number) {
    validateLineField(index, "unitPrice");
  }

  /** Blur số lượng: 1.5 và 1,5 đều chuẩn hóa về giá trị số, ô hiện 1,5. */
  function onQuantityBlur(index: number) {
    const list = [...(form.getFieldValue("items") as EditorValues["items"])];
    const raw = list[index]?.quantityDocument;
    const normalized =
      typeof raw === "number" ? raw : parseQtyInput(raw == null ? "" : String(raw));
    list[index] = { ...list[index], quantityDocument: normalized };
    form.setFieldValue("items", list);
    validateLineField(index, "quantityDocument");
  }

  function purchaseLineTotal(item: EditorValues["items"][number]) {
    const qty = Number(item?.quantityDocument ?? 0);
    const displayPrice = Math.round(Number(item?.unitPrice ?? 0));
    return Math.max(0, Math.round(qty * displayPrice));
  }

  const subtotal = items.reduce((sum, item) => {
    if (kind === "PURCHASE") return sum + purchaseLineTotal(item);
    const qty = Number(item?.quantityDocument ?? 0);
    const price = Math.round(Number(item?.unitPrice ?? 0));
    return sum + Math.max(0, Math.round(qty * price));
  }, 0);

  return (
    <>
    <Modal
      open={!offerSaveCustomer && !saveCustomerForm}
      width={1120}
      title={
        document
          ? `Sửa ${document.documentCode}`
          : kind === "PURCHASE"
            ? "Tạo phiếu nhập"
            : "Tạo phiếu bán"
      }
      okText={
        document
          ? kind === "PURCHASE"
            ? "Lưu phiếu nhập"
            : "Lưu phiếu bán"
          : kind === "PURCHASE"
            ? "Tạo phiếu nhập"
            : "Tạo phiếu bán"
      }
      cancelText="Đóng"
      confirmLoading={saving}
      okButtonProps={{ style: appButtonStyle(document ? "edit" : "create") }}
      cancelButtonProps={{ style: appButtonStyle("cancel") }}
      onCancel={() => {
        if (saving) return;
        if (dirty.current) askToPark();
        else onClose();
      }}
      onOk={() => form.submit()}
    >
      {legacyAreaDraft ? (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Phiếu nháp m² đã được chuyển sang bán theo cân. Hãy kiểm tra lại khối lượng và nhập đơn giá / kg trước khi lưu."
        />
      ) : null}
      <Form<EditorValues>
        form={form}
        layout="vertical"
        className="document-editor-form"
        validateTrigger={[...documentFieldValidateTrigger]}
        initialValues={
          document
            ? {
                documentDate: dayjs(document.documentDate),
                counterpartyName: document.counterpartyName ?? undefined,
                customerId: document.customerId ?? undefined,
                issuedBy: document.issuedBy ?? undefined,
                receivedBy: document.receivedBy ?? undefined,
                internal: document.internal ?? false,
                items: document.items.map((i) => {
                  const product = productMap.get(i.productId);
                  const billing: "kg" | "m2" =
                    i.billingUnit === "m2" || i.unitName === "m²" || i.unitName?.toLowerCase() === "m2"
                      ? "m2"
                      : "kg";
                  if (billing === "m2") {
                    return {
                      productId: i.productId,
                      categoryId: i.categoryId ?? undefined,
                      unitId: i.unitId ?? undefined,
                      billingUnit: "kg" as const,
                      quantityDocument: Number(Number(i.quantityBase).toFixed(6)),
                      unitPrice: undefined,
                      suggestedPrice: undefined,
                      weightInputUnit: sellByKg ? "kg" : "tấn",
                      priceInputUnit: sellByKg ? "kg" : "tấn",
                    };
                  }
                  const keepStored = sellByKg || (shop === "MANUFACTURING" && kind === "PURCHASE");
                  let unitPrice = Math.round(i.unitPrice);
                  let quantityDocument = Number(Number(i.quantityDocument).toFixed(6));
                  let weight: PurchaseWeightUnit = sellByKg
                    ? i.unitName === "tạ" ? "tạ" : i.unitName === "tấn" ? "tấn" : "kg"
                    : i.unitName === "tạ" ? "tạ" : "tấn";
                  if (!keepStored && i.unitName !== "tấn") {
                    unitPrice = Math.round(unitPrice * KG_PER_TON);
                    quantityDocument = Number(Math.max(0.000001, quantityDocument / KG_PER_TON).toFixed(6));
                    weight = "tấn";
                  }
                  const unit = product ? unitFor(product, weight) : undefined;
                  return {
                    productId: i.productId,
                    categoryId: i.categoryId ?? undefined,
                    unitId: unit?.id ?? i.unitId ?? undefined,
                    billingUnit: "kg" as const,
                    quantityDocument,
                    unitPrice,
                    weightInputUnit: weight,
                    priceInputUnit: weight,
                    suggestedPrice: unitPrice,
                  };
                }),
              }
            : {
                documentDate: dayjs(),
                internal: false,
                items: [
                  {
                    quantityDocument: 1,
                    billingUnit: "kg" as const,
                    weightInputUnit: sellByKg ? "kg" : "tấn",
                    priceInputUnit: sellByKg ? "kg" : "tấn",
                  },
                ],
              }
        }
        onValuesChange={() => persistDraft()}
        onFinish={async (values) => {
          setSaving(true);
          try {
            const changed = new Map<number, { name: string; oldPrice: number; oldFrom?: string | null; newPrice: number }>();
            const conflicting = new Set<number>();
            if (kind === "PURCHASE" && !document) {
              for (const item of values.items) {
                const category = categoryMap.get(item.categoryId ?? 0);
                const unit = productMap.get(item.productId ?? 0)?.units.find((u) => u.id === item.unitId);
                if (!category || !unit || !Number(unit.factorToBase)) continue;
                const newPrice = Number((Number(item.unitPrice ?? 0) / Number(unit.factorToBase)).toFixed(6));
                if (newPrice <= 0 || Math.abs(newPrice - category.referencePurchasePrice) < 0.000001) continue;
                const previous = changed.get(category.id);
                if (previous && previous.newPrice !== newPrice) conflicting.add(category.id);
                else changed.set(category.id, {
                  name: category.name,
                  oldPrice: category.referencePurchasePrice,
                  oldFrom: category.purchasePriceEffectiveFrom,
                  newPrice,
                });
              }
            }
            const updates = [...changed.entries()].filter(([id]) => !conflicting.has(id));
            if (conflicting.size) message.warning("Một loại nhôm có nhiều đơn giá trong phiếu; hãy cập nhật giá chuẩn của loại đó thủ công.");
            const shouldUpdatePrices = updates.length > 0 && await new Promise<boolean>((resolve) => {
              Modal.confirm({
                title: "Cập nhật giá nhập loại nhôm?",
                content: (
                  <div>
                    <p>Đơn giá phiếu nhập khác giá hiện tại. Bạn có muốn ghi giá nhập chuẩn mới khi lưu phiếu không?</p>
                    {updates.map(([id, price]) => (
                      <div key={id} style={{ marginBottom: 8 }}>
                        <b>{price.name}</b><br />
                        Giá cũ: {formatMoney(price.oldPrice * KG_PER_TON)} / tấn
                        {price.oldFrom ? `, áp dụng từ ${dayjs(price.oldFrom).format("DD/MM/YYYY")}` : ", chưa có mốc hiệu lực"}<br />
                        Giá mới: {formatMoney(price.newPrice * KG_PER_TON)} / tấn,
                        {` áp dụng từ ${values.documentDate.format("DD/MM/YYYY")}`}
                      </div>
                    ))}
                    <p>Lịch sử giá nhập sẽ giữ cả hai mốc. Chuông thông báo sẽ nhắc kiểm tra giá bán.</p>
                  </div>
                ),
                okText: "Lưu phiếu và cập nhật giá",
                cancelText: "Chỉ lưu phiếu",
                onOk: () => resolve(true),
                onCancel: () => resolve(false),
              });
            });
            const saved = await saveDraftDocument(values, canMarkInternal);
            message.success(kind === "PURCHASE" ? "Đã tạo phiếu nhập" : "Đã lưu phiếu");
            if (shouldUpdatePrices) {
              for (const [id, price] of updates) {
                try {
                  await savePurchasePrice(id, {
                    pricePerBase: price.newPrice,
                    effectiveFrom: values.documentDate.format("YYYY-MM-DD"),
                    note: `Cập nhật từ phiếu nhập ${saved.documentCode}`,
                  });
                  addPriceReviewAlert(shop, id, price.name, price.newPrice);
                } catch (error) {
                  message.error(`Đã lưu phiếu nhưng chưa cập nhật giá nhập ${price.name}: ${toApiError(error).message}`);
                }
              }
              onSaved(saved);
              return;
            }
            if (
              kind === "SALE" &&
              !saved.customerId &&
              saved.counterpartyName &&
              saved.counterpartyName.trim()
            ) {
              setOfferSaveCustomer(saved);
            } else {
              onSaved(saved);
            }
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Form.Item name="customerId" hidden>
          <InputNumber />
        </Form.Item>
        <Space className="doc-form-meta-row" align="start" wrap size={[16, 32]}>
          <Form.Item name="documentDate" label="Ngày phiếu" rules={[{ required: true, message: "Chọn ngày" }]}>
            <DatePicker showTime format="DD/MM/YYYY HH:mm" />
          </Form.Item>
          {kind === "SALE" ? (
            <Form.Item name="counterpartyName" label={text.counterparty} rules={optionalNameRules(200)}>
              <AutoComplete
                style={{ width: 280 }}
                options={customerOptions}
                placeholder="Gõ tên hoặc số điện thoại khách"
                notFoundContent={customerSuggesting ? "Đang tìm…" : "Không có gợi ý"}
                filterOption={false}
                onSearch={suggestCustomers}
                onSelect={(value, option) => {
                  const opt = option as { customerId?: number; phone?: string | null };
                  form.setFieldsValue({
                    counterpartyName: String(value),
                    customerId: opt.customerId,
                  });
                }}
                onChange={(value) => {
                  const match =
                    customers.find((c) => c.name === value) ??
                    customers.find((c) => c.phone && c.phone === value);
                  form.setFieldsValue({
                    counterpartyName: value,
                    customerId: match?.id,
                  });
                }}
              />
            </Form.Item>
          ) : (
            <Form.Item name="counterpartyName" label={text.counterparty} rules={optionalNameRules(200)}>
              <Input style={{ width: 240 }} maxLength={200} />
            </Form.Item>
          )}
          <Form.Item
            name="issuedBy"
            label={kind === "SALE" ? "Người xuất" : "Người giao"}
            rules={
              kind === "SALE"
                ? requiredNameRules("Nhập người xuất", 150)
                : optionalNameRules(150)
            }
          >
            <Input style={{ width: 200 }} maxLength={150} showCount={kind === "SALE"} />
          </Form.Item>
          <Form.Item
            name="receivedBy"
            label={kind === "SALE" ? "Người nhận" : "Người nhập"}
            rules={
              kind === "PURCHASE"
                ? requiredNameRules("Nhập người nhập", 50)
                : optionalNameRules(50)
            }
          >
            <Input style={{ width: 200 }} maxLength={50} showCount={kind === "PURCHASE"} />
          </Form.Item>
          {canMarkInternal ? (
            <Form.Item name="internal" valuePropName="checked" label=" ">
              <Checkbox
                onChange={(e) => {
                  if (e.target.checked) {
                  }
                }}
              >
                Giao dịch nội bộ
              </Checkbox>
            </Form.Item>
          ) : null}
        </Space>
        <Form.List name="items">
          {(fields, { add, remove }) => (
            <div className="purchase-lines">
              {kind === "PURCHASE" ? (
                <div
                  className="purchase-line-grid purchase-line-head"
                  style={{ gridTemplateColumns: purchaseLineColumns }}
                >
                  <span>Sản phẩm</span>
                  <span>Loại nhôm</span>
                  <span>Số lượng</span>
                  <span>Đơn vị khối lượng</span>
                  <span>{shop === "MANUFACTURING" ? "Đơn giá" : "Đơn giá / tấn"}</span>
                  <span>Thành tiền</span>
                  <span aria-hidden />
                </div>
              ) : null}
              {fields.map((field) => {
                const current = items[field.name];
                const qty = Number(current?.quantityDocument ?? 0);
                const lineTotal =
                  kind === "PURCHASE"
                    ? purchaseLineTotal(current)
                    : Math.max(0, Math.round(qty * Math.round(Number(current?.unitPrice ?? 0))));

                if (kind === "PURCHASE") {
                  const priceHint = priceEffectiveHint(categoryMap.get(current?.categoryId ?? 0));
                  return (
                    <div key={field.key} className="purchase-line-block">
                      <div
                        className="purchase-line-grid purchase-line-body"
                        style={{ gridTemplateColumns: purchaseLineColumns }}
                      >
                        <Form.Item
                          name={[field.name, "productId"]}
                          validateFirst
                          rules={[{ required: true, message: "Chọn sản phẩm" }]}
                        >
                          <Select
                            showSearch
                            placeholder="Chọn sản phẩm"
                            optionFilterProp="label"
                            onChange={(value) => onProductChange(field.name, value)}
                            options={products.map((p) => ({
                              value: p.id,
                              label: `${p.productCode} - ${p.name}`,
                            }))}
                          />
                        </Form.Item>
                        <Form.Item
                          name={[field.name, "categoryId"]}
                          validateFirst
                          rules={[{ required: true, message: "Chọn loại nhôm" }]}
                        >
                          <Select
                            showSearch
                            placeholder="Chọn loại nhôm"
                            optionFilterProp="label"
                            onChange={(value) => onCategoryChange(field.name, value)}
                            options={categoryOptions(current?.categoryId)}
                          />
                        </Form.Item>
                        <Form.Item name={[field.name, "quantityDocument"]} validateFirst rules={positiveDecimalRules}>
                          <InputNumber
                            {...qtyInputNumberProps}
                            placeholder={shop === "MANUFACTURING" ? "Khối lượng" : "Tấn"}
                            style={{ width: "100%" }}
                            onBlur={() => onQuantityBlur(field.name)}
                          />
                        </Form.Item>
                        {shop === "MANUFACTURING" ? (
                          <Select
                            value={current?.weightInputUnit === "tạ" ? "tạ" : "tấn"}
                            options={[
                              { value: "tạ", label: "tạ" },
                              { value: "tấn", label: "tấn" },
                            ]}
                            onChange={(value) => onWeightChange(field.name, value === "tạ" ? "tạ" : "tấn")}
                          />
                        ) : (
                          <span className="purchase-line-static">tấn</span>
                        )}
                        <Form.Item name={[field.name, "unitPrice"]} validateFirst rules={vndUnitPriceRules}>
                          <InputNumber
                            {...vndInputNumberProps}
                            placeholder={
                              current?.weightInputUnit === "tạ" ? "Giá một tạ" : "Giá một tấn"
                            }
                            style={{ width: "100%" }}
                            title={priceHint ?? undefined}

                            onBlur={() => onUnitPriceBlur(field.name)}
                          />
                        </Form.Item>
                        <span className="purchase-line-static">{formatMoney(lineTotal)}</span>
                        {fields.length > 1 ? (
                          <AppActionButton
                            tone="delete"
                            className="purchase-line-remove"
                            icon={<DeleteOutlined />}
                            aria-label="Xóa dòng"
                            onClick={() => remove(field.name)}
                          />
                        ) : (
                          <span aria-hidden />
                        )}
                      </div>
                      <Form.Item name={[field.name, "billingUnit"]} hidden initialValue="kg">
                        <Input />
                      </Form.Item>
                      <Form.Item name={[field.name, "weightInputUnit"]} hidden initialValue="tấn">
                        <Input />
                      </Form.Item>
                      <Form.Item name={[field.name, "priceInputUnit"]} hidden initialValue="tấn">
                        <Input />
                      </Form.Item>
                      <Form.Item name={[field.name, "unitId"]} hidden rules={[{ required: true }]}>
                        <InputNumber />
                      </Form.Item>
                    </div>
                  );
                }

                return (
                  <div key={field.key} className="sale-line-card">
                    <div className="sale-line-card-top">
                      <Form.Item
                        name={[field.name, "productId"]}
                        label="Sản phẩm"
                        validateFirst
                        rules={[{ required: true, message: "Chọn sản phẩm" }]}
                        style={{ flex: 1, marginBottom: 0 }}
                      >
                        <Select
                          showSearch
                          placeholder="Chọn sản phẩm"
                          optionFilterProp="label"
                          onChange={(value) => onProductChange(field.name, value)}
                          options={products.map((p) => ({
                            value: p.id,
                            label: `${p.productCode} - ${p.name}`,
                          }))}
                        />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, "categoryId"]}
                        label="Loại nhôm"
                        validateFirst
                        rules={[{ required: true, message: "Chọn loại nhôm" }]}
                        style={{ flex: 1, marginBottom: 0 }}
                      >
                        <Select
                          showSearch
                          placeholder="Chọn loại nhôm"
                          optionFilterProp="label"
                          onChange={(value) => onCategoryChange(field.name, value)}
                          options={categoryOptions(current?.categoryId)}
                        />
                      </Form.Item>
                      {fields.length > 1 ? (
                        <AppActionButton
                          tone="delete"
                          className="sale-line-remove"
                          icon={<DeleteOutlined />}
                          aria-label="Xóa dòng"
                          onClick={() => remove(field.name)}
                        />
                      ) : null}
                    </div>

                    <div className="sale-line-fields sale-line-fields-kg">
                        <Form.Item
                          name={[field.name, "quantityDocument"]}
                          label={sellByKg ? "Khối lượng" : "Khối lượng (tấn)"}
                          extra={sellByKg ? "Khối lượng nhôm trong thành phẩm đã giao; không gồm kính và phụ kiện." : undefined}
                          validateFirst
                          rules={positiveDecimalRules}
                        >
                          <InputNumber
                            {...qtyInputNumberProps}
                            style={{ width: "100%" }}
                            onBlur={() => onQuantityBlur(field.name)}
                          />
                        </Form.Item>
                        {sellByKg ? (
                          <Form.Item label="Đơn vị">
                            <Select
                              aria-label="Đơn vị khối lượng"
                              value={current?.weightInputUnit ?? "kg"}
                              onChange={(value: PurchaseWeightUnit) => onSaleWeightChange(field.name, value)}
                              options={[
                                { value: "kg", label: "kg" },
                                { value: "tạ", label: "tạ" },
                                { value: "tấn", label: "tấn" },
                              ]}
                              style={{ width: 88 }}
                            />
                          </Form.Item>
                        ) : null}
                        <Form.Item
                          name={[field.name, "unitPrice"]}
                          label={sellByKg ? `Đơn giá / ${current?.weightInputUnit ?? "kg"}` : "Đơn giá / tấn"}
                          validateFirst
                          rules={vndUnitPriceRules}
                          extra={
                            internalTransfer
                              ? undefined
                              : "Lấy từ giá bán của loại nhôm; có thể sửa trên phiếu"
                          }
                        >
                          <InputNumber
                            {...vndInputNumberProps}
                            style={{ width: "100%" }}

                            onBlur={() => onUnitPriceBlur(field.name)}
                          />
                        </Form.Item>
                    </div>

                    <div className="sale-line-total">{formatMoney(lineTotal)}</div>

                    <Form.Item name={[field.name, "billingUnit"]} hidden initialValue="kg">
                      <Input />
                    </Form.Item>
                    <Form.Item name={[field.name, "weightInputUnit"]} hidden initialValue={sellByKg ? "kg" : "tấn"}>
                      <Input />
                    </Form.Item>
                    <Form.Item name={[field.name, "priceInputUnit"]} hidden initialValue={sellByKg ? "kg" : "tấn"}>
                      <Input />
                    </Form.Item>
                    <Form.Item name={[field.name, "unitId"]} hidden rules={[{ required: true, message: "Chọn đơn vị" }]}>
                      <InputNumber />
                    </Form.Item>
                  </div>
                );
              })}
              <AppActionButton
                tone="create"
                icon={<PlusOutlined />}
                onClick={() =>
                  add({
                    quantityDocument: 1,
                    billingUnit: "kg" as const,
                    weightInputUnit: sellByKg ? "kg" : "tấn",
                    priceInputUnit: sellByKg ? "kg" : "tấn",
                  })
                }
              >
                Thêm dòng
              </AppActionButton>
            </div>
          )}
        </Form.List>
        <div style={{ textAlign: "center", fontSize: 16, marginTop: 16 }}>
          Tổng thanh toán: <b>{formatMoney(subtotal)}</b>
        </div>
      </Form>
    </Modal>
      {offerSaveCustomer ? (
        <Modal
          title="Lưu khách hàng?"
          open
          onCancel={() => {
            const doc = offerSaveCustomer;
            setOfferSaveCustomer(null);
            onSaved(doc);
          }}
          footer={[
            <AppActionButton
              key="skip"
              tone="cancel"
              icon={<CloseOutlined />}
              onClick={() => {
                const doc = offerSaveCustomer;
                setOfferSaveCustomer(null);
                onSaved(doc);
              }}
            >
              Bỏ qua
            </AppActionButton>,
            <AppActionButton
              key="save"
              tone="create"
              icon={<UserAddOutlined />}
              onClick={() => {
                setSaveCustomerForm(offerSaveCustomer);
                setOfferSaveCustomer(null);
              }}
            >
              Lưu khách
            </AppActionButton>,
          ]}
        >
          <p>
            Tên <b>{offerSaveCustomer.counterpartyName}</b> chưa có trong sổ khách. Bạn có muốn lưu để lần sau
            chọn nhanh và theo dõi công nợ?
          </p>
        </Modal>
      ) : null}
      {saveCustomerForm ? (
        <CustomerFormModal
          initialName={saveCustomerForm.counterpartyName ?? undefined}
          onClose={() => {
            const doc = saveCustomerForm;
            setSaveCustomerForm(null);
            onSaved(doc);
          }}
          onSaved={async (customer) => {
            try {
              await linkDocumentCustomer(saveCustomerForm.id, customer.id);
              message.success("Đã lưu và gắn khách vào phiếu");
            } catch (error) {
              message.error(toApiError(error).message);
            }
            const doc = saveCustomerForm;
            setSaveCustomerForm(null);
            onSaved(doc);
          }}
        />
      ) : null}
    </>
  );
}

function DetailModal({
  kind,
  id,
  onClose,
  onChanged,
  onEdit,
}: {
  kind: Kind;
  id: number;
  onClose: () => void;
  onChanged: () => void;
  onEdit: (document: StockDocument) => void;
}) {
  const text = copy[kind];
  const [doc, setDoc] = useState<StockDocument | null>(null);
  const shop = useShop();
  const [busy, setBusy] = useState(false);
  const [paying, setPaying] = useState(false);
  const [reversing, setReversing] = useState(false);
  const [invoiceOpen, setInvoiceOpen] = useState(false);

  const refresh = useCallback(async () => {
    try {
      setDoc(await getDocument(id));
    } catch (error) {
      message.error(toApiError(error).message);
    }
  }, [id]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Phải gọi hook trước mọi early-return — nếu để sau `if (!doc)` sẽ trắng màn hình.
  const canWrite = useCanWrite();

  async function run(action: () => Promise<StockDocument>, success: string) {
    setBusy(true);
    try {
      setDoc(await action());
      message.success(success);
      onChanged();
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setBusy(false);
    }
  }

  if (!doc) {
    return <Modal open footer={null} onCancel={onClose} loading title="Đang tải phiếu" />;
  }

  const remaining = doc.totalAmount - doc.paidAmount;
  const confirmed = doc.status === "CONFIRMED";

  return (
    <Modal
      open
      width={960}
      title={`${doc.documentCode}`}
      className="doc-detail-modal"
      onCancel={onClose}
      footer={
        <Space wrap>
          <AppActionButton
            tone="print"
            icon={<PrinterOutlined />}
            onClick={() => (kind === "SALE" ? setInvoiceOpen(true) : void printStockDocument(doc, kind))}
          >
            In PDF
          </AppActionButton>
          {canWrite && doc.status === "DRAFT" ? (
            <>
              <AppActionButton tone="edit" icon={<EditOutlined />} onClick={() => onEdit(doc)}>
                Sửa
              </AppActionButton>
              <AppActionButton
                tone="delete"
                icon={<StopOutlined />}
                loading={busy}
                onClick={() => void run(() => documentAction(doc.id, "cancel"), "Đã hủy phiếu nháp")}
              >
                Hủy nháp
              </AppActionButton>
              <AppActionButton
                tone="confirm"
                icon={<CheckOutlined />}
                loading={busy}
                onClick={() =>
                  Modal.confirm({
                    title: "Xác nhận phiếu?",
                    content: doc.internal
                      ? undefined
                      : kind === "PURCHASE"
                        ? shop === "MANUFACTURING"
                          ? "Phiếu sẽ ghi khối lượng và tiền nhôm nhập cho quyết toán năm."
                          : "Phiếu sẽ tăng số lượng tồn kho."
                        : shop === "MANUFACTURING"
                          ? "Xác nhận đã giao thành phẩm; khối lượng sẽ được cộng vào báo cáo năm."
                          : undefined,
                    okText: "Xác nhận",
                    cancelText: "Quay lại",
                    okButtonProps: { style: appButtonStyle("confirm") },
                    cancelButtonProps: { style: appButtonStyle("cancel") },
                    onOk: () =>
                      run(
                        () => documentAction(doc.id, "confirm"),
                        doc.internal ? "Đã xác nhận và tạo phiếu nhập xưởng" : "Đã xác nhận phiếu",
                      ),
                  })
                }
              >
                Xác nhận
              </AppActionButton>
            </>
          ) : null}
          {canWrite && confirmed && remaining > 0 && !doc.internal ? (
            <AppActionButton tone="pay" icon={<DollarOutlined />} onClick={() => setPaying(true)}>
              {text.payLabel}
            </AppActionButton>
          ) : null}
          {canWrite && confirmed && !doc.reversedByDocumentId && (doc.internal || doc.paidAmount === 0) ? (
            <AppActionButton tone="reverse" icon={<RetweetOutlined />} onClick={() => setReversing(true)}>
              Đảo phiếu
            </AppActionButton>
          ) : null}
          {canWrite &&
          !doc.trashedAt &&
          (doc.status === "CANCELLED" || (confirmed && doc.reversedByDocumentId)) ? (
            <AppActionButton
              tone="delete"
              icon={<DeleteOutlined />}
              loading={busy}
              onClick={() =>
                Modal.confirm({
                  title: "Chuyển vào thùng rác?",
                  content:
                    doc.status === "CANCELLED"
                      ? `Phiếu đã hủy sẽ ẩn khỏi danh sách, giữ tối đa ${TRASH_RETENTION_DAYS} ngày rồi xóa vĩnh viễn.`
                      : "Phiếu đã đảo sẽ ẩn khỏi danh sách. Số liệu kho và phiếu đảo đi kèm vẫn giữ nguyên.",
                  okText: "Chuyển thùng rác",
                  cancelText: "Quay lại",
                  okButtonProps: { style: appButtonStyle("delete") },
                  cancelButtonProps: { style: appButtonStyle("cancel") },
                  onOk: () => run(() => trashDocument(doc.id), "Đã chuyển vào thùng rác"),
                })
              }
            >
              Chuyển thùng rác
            </AppActionButton>
          ) : null}
          {canWrite && doc.trashedAt ? (
            <AppActionButton
              tone="secondary"
              icon={<RollbackOutlined />}
              loading={busy}
              onClick={() => void run(() => restoreDocument(doc.id), "Đã khôi phục phiếu")}
            >
              Khôi phục
            </AppActionButton>
          ) : null}
        </Space>
      }
    >
      <Descriptions size="small" column={{ xs: 1, md: 2 }} bordered>
        <Descriptions.Item label="Loại">{typeLabel[doc.documentType]}</Descriptions.Item>
        <Descriptions.Item label="Trạng thái">
          <Tag color={statusTag[doc.status].color}>{statusTag[doc.status].text}</Tag>
          {doc.internal ? (
            <Tag color="gold" style={{ marginLeft: 8 }}>
              Nội bộ
            </Tag>
          ) : (
            <Tag style={{ marginLeft: 8 }}>{kind === "SALE" ? "Khách hàng" : "Nhà cung cấp"}</Tag>
          )}
          {doc.trashedAt ? (
            <Tag color="red" style={{ marginLeft: 8 }}>
              Trong thùng rác
            </Tag>
          ) : null}
        </Descriptions.Item>
        <Descriptions.Item label="Ngày">{dayjs(doc.documentDate).format("DD/MM/YYYY HH:mm")}</Descriptions.Item>
        <Descriptions.Item label={text.counterparty}>{doc.counterpartyName}</Descriptions.Item>
        <Descriptions.Item label="Người xuất / giao">{doc.issuedBy}</Descriptions.Item>
        <Descriptions.Item label="Người nhận / nhập">{doc.receivedBy}</Descriptions.Item>
        {doc.internalPairDocumentCode ? (
          <Descriptions.Item label="Phiếu cặp nội bộ" span={2}>
            {doc.internalPairDocumentCode}
          </Descriptions.Item>
        ) : null}
        {!doc.internal && doc.reason ? <Descriptions.Item label="Lý do" span={2}>{doc.reason}</Descriptions.Item> : null}
        {!doc.internal && doc.notes ? <Descriptions.Item label="Ghi chú phiếu" span={2}>{doc.notes}</Descriptions.Item> : null}
        {doc.reversedByDocumentId ? (
          <Descriptions.Item label="Ghi chú" span={2}>Phiếu này đã được đảo, không còn tính vào báo cáo.</Descriptions.Item>
        ) : null}
      </Descriptions>
      <Table
        style={{ marginTop: 16 }}
        size="small"
        rowKey="id"
        pagination={false}
        dataSource={doc.items}
        columns={[
          { title: "Sản phẩm", dataIndex: "productName", align: "center" },
          { title: "Loại nhôm", dataIndex: "categoryName", align: "center", render: (v: string | null) => v || "—" },
          {
            title: "Đơn vị",
            align: "center",
            render: (_: unknown, item: StockDocument["items"][number]) => {
              if (item.billingUnit === "m2" || item.unitName === "m²") {
                const w = item.widthM;
                const h = item.heightM;
                return w && h ? `m² (${formatQtyDisplay(w)}×${formatQtyDisplay(h)})` : "m²";
              }
              return item.unitName;
            },
          },
          {
            title: "Số lượng",
            align: "center",
            render: (_: unknown, item: StockDocument["items"][number]) => {
              if (item.billingUnit === "m2" || item.unitName === "m²") {
                return (
                  <div>
                    <div>{formatQtyDisplay(item.quantityDocument)} m²</div>
                    <div style={{ fontSize: 12, color: "var(--color-text-secondary)" }}>
                      trừ tồn {formatQtyDisplay(item.quantityBase)} kg
                    </div>
                  </div>
                );
              }
              return formatQtyDisplay(item.quantityDocument);
            },
          },
          {
            title: "Đơn giá",
            dataIndex: "unitPrice",
            align: "center",
            render: (v: number, item: StockDocument["items"][number]) =>
              `${formatMoney(v)}${item.billingUnit === "m2" || item.unitName === "m²" ? " / m²" : ""}`,
          },
          { title: "Thành tiền", dataIndex: "lineTotal", align: "center", render: (v: number) => formatMoney(v) },
        ]}
      />
      <div style={{ textAlign: "center", marginTop: 16, lineHeight: 1.9 }}>
        <div style={{ fontSize: 16 }}>Tổng thanh toán: <b>{formatMoney(doc.totalAmount)}</b></div>
        {confirmed && !doc.internal ? (
          <div>
            {text.paidLabel} đến nay: {formatMoney(doc.paidAmount)}
            {doc.payments.length > 0 ? ` (${doc.payments.length} lần)` : ""}
            {" · "}
            {kind === "PURCHASE" ? "Còn phải chi trả" : "Còn phải thu"}: {formatMoney(remaining)}{" "}
            <Tag color={paymentTag[doc.paymentStatus].color}>{paymentTag[doc.paymentStatus].text}</Tag>
          </div>
        ) : null}
      </div>
      {doc.payments.length > 0 ? (
        <>
          <div style={{ marginTop: 12, marginBottom: 4, textAlign: "center", color: "var(--color-text-secondary)" }}>
            Lịch sử từng lần thanh toán — mỗi lần ghi rõ ngày giờ
          </div>
          <Table
            style={{ marginTop: 8 }}
            size="small"
            rowKey="id"
            pagination={false}
            dataSource={doc.payments}
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
                render: (v: string | null) =>
                  paymentMethodLabel(kind === "PURCHASE" ? v?.split(/\. Người (?:trả|thu):/u)[0] : v),
              },
              ...(kind === "PURCHASE" ? [
                {
                  title: "Người trả",
                  key: "paidBy",
                  align: "center" as const,
                  render: (_: unknown, row: StockDocument["payments"][number]) =>
                    row.paymentMethod?.match(/\. Người trả: (.*?)(?=\. Người thu:|$)/u)?.[1]?.trim() || "—",
                },
                {
                  title: "Người nhận",
                  key: "receivedBy",
                  align: "center" as const,
                  render: (_: unknown, row: StockDocument["payments"][number]) =>
                    row.paymentMethod?.match(/\. Người thu: (.*)$/u)?.[1]?.trim() || "—",
                },
              ] : [{
                title: "Người thu",
                dataIndex: "collectedBy",
                align: "center" as const,
                render: (v: string | null) => v || "—",
              }]),
              {
                title: kind === "PURCHASE" ? "Còn phải chi trả sau lần này" : "Còn phải thu sau lần này",
                key: "remainingAfter",
                align: "center",
                render: (_: unknown, _row, index) => {
                  const paidThrough = doc.payments
                    .slice(0, index + 1)
                    .reduce((sum, payment) => sum + Number(payment.amount), 0);
                  return formatMoney(Math.max(0, doc.totalAmount - paidThrough));
                },
              },
              {
                title: "Ghi chú",
                dataIndex: "note",
                align: "center",
                render: (v: string | null) => v || "—",
              },
            ]}
          />
        </>
      ) : null}
      {paying ? (
        <PaymentModal
          label={text.payLabel}
          remaining={remaining}
          kind={kind}
          onClose={() => setPaying(false)}
          onSubmit={async (values) => {
            await run(() => addPayment(doc.id, values), "Đã ghi thanh toán");
            setPaying(false);
          }}
        />
      ) : null}
      {invoiceOpen ? <SaleInvoiceModal doc={doc} onClose={() => setInvoiceOpen(false)} /> : null}
      {reversing ? (
        <ReverseModal
          onClose={() => setReversing(false)}
          onSubmit={async (reason) => {
            setBusy(true);
            try {
              const reversal = await reverseDocument(doc.id, reason);
              message.success(`Đã đảo bằng phiếu ${reversal.documentCode}`);
              setReversing(false);
              onChanged();
              await refresh();
            } catch (error) {
              message.error(toApiError(error).message);
            } finally {
              setBusy(false);
            }
          }}
        />
      ) : null}
    </Modal>
  );
}

function SaleInvoiceModal({ doc, onClose }: { doc: StockDocument; onClose: () => void }) {
  const frameRef = useRef<HTMLIFrameElement>(null);
  const [shop, setShop] = useState<ShopSettings | null>(null);
  const [accounts, setAccounts] = useState<BankAccount[]>([]);
  const [bankId, setBankId] = useState<number | null>(null);
  const [customerName, setCustomerName] = useState(doc.counterpartyName?.trim() || "");
  const [html, setHtml] = useState<string | null>(null);
  const bank = accounts.find((account) => account.id === bankId) ?? null;
  const bankReady = Boolean(bank?.bankBin && bank.accountNo);

  useEffect(() => {
    void Promise.all([
      getSettings().catch(() => null),
      listBankAccounts().catch(() => [] as BankAccount[]),
      doc.customerId ? getCustomer(doc.customerId).catch(() => null) : Promise.resolve(null),
    ]).then(([settings, banks, customer]) => {
      setShop(settings);
      setAccounts(banks);
      setBankId(banks.find((account) => account.isDefault)?.id ?? banks[0]?.id ?? null);
      const linkedName = customer?.name?.trim();
      if (linkedName) setCustomerName(linkedName);
    });
  }, [doc.id, doc.customerId]);

  useEffect(() => {
    let active = true;
    void buildSaleInvoiceHtml({ doc, shop, bank, customerName }).then((next) => {
      if (active) setHtml(next);
    });
    return () => {
      active = false;
    };
  }, [doc, shop, bank, customerName]);

  return (
    <Modal
      open
      width={920}
      title="Hóa đơn bán hàng"
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
        {accounts.length > 1 ? (
          <Select
            style={{ minWidth: 280 }}
            value={bankId ?? undefined}
            onChange={setBankId}
            options={accounts.map((account) => ({
              value: account.id,
              label: `${account.label}${account.isDefault ? " (mặc định)" : ""}`,
            }))}
          />
        ) : null}
        {doc.internal ? (
          <Alert type="info" showIcon message="Phiếu nội bộ không gắn mã chuyển khoản." />
        ) : !bankReady ? (
          <Alert type="info" showIcon message="Chưa có tài khoản QR. Hóa đơn vẫn in được, chưa kèm mã chuyển khoản." />
        ) : null}
        {html ? (
          <iframe
            ref={frameRef}
            title="Xem trước hóa đơn bán hàng"
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

function PaymentModal({
  label,
  remaining,
  kind,
  onClose,
  onSubmit,
}: {
  label: string;
  remaining: number;
  kind: Kind;
  onClose: () => void;
  onSubmit: (values: {
    amount: number;
    paymentMethod: string;
    paymentDate: string;
    note?: string;
    idempotencyKey: string;
    collectedBy?: string;
    paidBy?: string;
  }) => Promise<void>;
}) {
  const session = useSession();
  const [form] = Form.useForm<{ amount: number; paymentMethod: string; paymentDate: Dayjs; note?: string; collectedBy?: string; paidBy?: string }>();
  const key = useMemo(() => crypto.randomUUID(), []);
  const amountWatch = Form.useWatch("amount", form);
  const enteredAmount = Number(amountWatch ?? 0);
  const isPartialPayment = enteredAmount > 0 && enteredAmount < remaining;
  const remainingAfter = Math.max(0, remaining - Math.round(enteredAmount));
  const remainingLabel = kind === "PURCHASE" ? "Còn phải chi trả" : "Còn phải thu";

  return (
    <Modal
      open
      title={label}
      width={680}
      okText="Ghi nhận"
      cancelText="Hủy"
      okButtonProps={{ style: appButtonStyle("pay") }}
      cancelButtonProps={{ style: appButtonStyle("cancel") }}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <Form
        form={form}
        layout="vertical"
        className="stock-payment-form"
        initialValues={{
          amount: remaining,
          paymentMethod: "CASH",
          paymentDate: dayjs(),
          collectedBy: kind === "SALE" ? session?.username ?? "" : undefined,
        }}
        onFinish={(values) =>
          Modal.confirm({
            title: kind === "PURCHASE" ? "Xác nhận ghi chi?" : "Xác nhận ghi thu?",
            content: (
              <div>
                <p style={{ margin: "8px 0 4px" }}>
                  Số tiền: <b>{formatMoney(values.amount)}</b> (
                  {values.paymentMethod === "TRANSFER" ? "Chuyển khoản" : "Tiền mặt"})
                </p>
                <p style={{ margin: "4px 0" }}>
                  Thời gian: <b>{values.paymentDate.format("DD/MM/YYYY HH:mm")}</b>
                </p>
                {kind === "SALE" ? (
                  <p style={{ margin: "4px 0" }}>
                    Người thu tiền: <b>{values.collectedBy?.trim()}</b>
                  </p>
                ) : null}
                <p style={{ margin: "4px 0" }}>
                  {remainingLabel} sau lần này: <b>{formatMoney(Math.max(0, remaining - Math.round(values.amount)))}</b>
                </p>
              </div>
            ),
            okText: "Ghi nhận",
            cancelText: "Quay lại",
            okButtonProps: { style: appButtonStyle("pay") },
            cancelButtonProps: { style: appButtonStyle("cancel") },
            onOk: () =>
              onSubmit({
                amount: values.amount,
                paymentMethod: values.paymentMethod,
                paymentDate: values.paymentDate.format("YYYY-MM-DDTHH:mm:ss"),
                note: values.note,
                idempotencyKey: key,
                collectedBy: values.collectedBy?.trim(),
                paidBy: values.paidBy?.trim(),
              }),
          })
        }
      >
        <Form.Item
          name="paymentDate"
          label="Thời gian thanh toán"
          rules={[{ required: true, message: "Chọn thời gian thanh toán" }]}
        >
          <DatePicker showTime format="DD/MM/YYYY HH:mm" style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item
          name="amount"
          label={`Số tiền (${remainingLabel} ${formatMoney(remaining)})`}
          rules={[
            {
              validator: async (_, val) => {
                if (Number(val) !== remaining && !isValidPaymentAmountVnd(Number(val))) throw new Error("Nhập số tiền dương và là bội 500 đ");
                if (Number(val) > remaining) throw new Error(`Không vượt ${remainingLabel.toLowerCase()}`);
              },
            },
          ]}
          extra={isPartialPayment ? (
            <span role="status">Thanh toán 1 phần</span>
          ) : (
            <AppActionButton
              className="stock-payment-fill"
              tone="pay"
              icon={<DollarOutlined />}
              htmlType="button"
              onClick={() => form.setFields([{ name: "amount", value: remaining, errors: [] }])}
            >
              Thanh toán hết số còn lại
            </AppActionButton>
          )}
        >
          <InputNumber {...vndInputNumberProps} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item label={remainingLabel}>
          <Input value={formatMoney(remainingAfter)} readOnly />
        </Form.Item>
        {kind === "PURCHASE" && <Form.Item name="paidBy" label="Người trả tiền" rules={requiredNameRules("Nhập người trả tiền", 80)}><Input maxLength={80} /></Form.Item>}
          <Form.Item
            name="collectedBy"
            label={kind === "PURCHASE" ? "Người nhận tiền" : "Người thu tiền"}
            rules={requiredNameRules("Nhập người thu tiền", 80)}
            extra="Tên này in trên hóa đơn nợ, mỗi lần thu một người"
          >
            <Input maxLength={80} />
          </Form.Item>
        <Form.Item
          name="paymentMethod"
          label="Hình thức thanh toán"
          rules={[{ required: true, message: "Chọn hình thức thanh toán" }]}
        >
          <Select
            options={[
              { value: "CASH", label: "Tiền mặt" },
              { value: "TRANSFER", label: "Chuyển khoản" },
            ]}
          />
        </Form.Item>
        <Form.Item name="note" label="Ghi chú">
          <Input maxLength={500} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function ReverseModal({ onClose, onSubmit }: { onClose: () => void; onSubmit: (reason: string) => Promise<void> }) {
  const [form] = Form.useForm<{ reason: string }>();
  return (
    <Modal
      open
      title="Đảo phiếu"
      okText="Đảo phiếu"
      cancelText="Hủy"
      okButtonProps={{ style: appButtonStyle("reverse") }}
      cancelButtonProps={{ style: appButtonStyle("cancel") }}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <p style={{ marginTop: 0 }}>
        Phiếu gốc không bị sửa. Hệ thống tạo phiếu đảo để loại phiếu gốc khỏi báo cáo; biến động kho cũ sẽ được hoàn nếu có. Mỗi phiếu chỉ đảo một lần.
      </p>
      <Form form={form} layout="vertical" onFinish={({ reason }) => onSubmit(reason)}>
        <Form.Item name="reason" label="Lý do" rules={[{ required: true, message: "Nhập lý do đảo phiếu" }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
