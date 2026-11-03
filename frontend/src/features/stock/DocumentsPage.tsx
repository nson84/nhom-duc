import {
  CheckOutlined,
  DeleteOutlined,
  DollarOutlined,
  EditOutlined,
  PlusOutlined,
  PrinterOutlined,
  RetweetOutlined,
  StopOutlined,
} from "@ant-design/icons";
import { Alert, Card, Checkbox, DatePicker, Descriptions, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, message } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AppActionButton, appButtonStyle } from "../../components/AppButtons";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import {
  bindDraftUnloadGuard,
  clearSessionDraft,
  purchaseFormDraftKey,
  readSessionDraft,
  writeSessionDraft,
} from "../../utils/session-draft";
import { optionalNameRules, requiredNameRules } from "../../utils/name-rules";
import {
  documentFieldValidateTrigger,
  formatQtyInput,
  parseQtyInput,
  positiveDecimalRules,
  qtyInputNumberProps,
  roundVndToThousand,
  vndAmountRules,
  vndInputNumberProps,
  vndUnitPriceRules,
} from "../../utils/vnd-input";
import {
  KG_PER_TON,
  kgUnit,
  purchaseUnit,
  suggestPurchasePricePerUnit,
  suggestSalePricePerUnit,
  taUnit,
  tonUnit,
  type PurchaseWeightUnit,
} from "../products/product-stock-unit";
import { useCanWrite } from "../auth/auth-store";
import { useShop } from "../shop/shop-store";
import { listProducts, type Product } from "../products/products-api";
import { getSettings } from "../settings/settings-api";
import {
  addPayment,
  documentAction,
  getDocument,
  listBalances,
  listDocuments,
  reverseDocument,
  saveDocument,
  type DocumentSummary,
  type StockBalance,
  type StockDocument,
} from "./stock-api";

type Kind = "PURCHASE" | "SALE";

const copy = {
  PURCHASE: {
    title: "Nhập kho",
    newLabel: "Tạo phiếu nhập",
    counterparty: "Nhà cung cấp",
    payLabel: "Chi tiền",
    paidLabel: "Đã chi",
    printTitle: "PHIẾU NHẬP KHO",
    leftSign: "Người giao hàng",
    rightSign: "Người nhập kho",
  },
  SALE: {
    title: "Bán hàng",
    newLabel: "Tạo phiếu bán",
    counterparty: "Khách hàng",
    payLabel: "Thu tiền",
    paidLabel: "Đã thu",
    printTitle: "PHIẾU XUẤT KHO / BÁN HÀNG",
    leftSign: "Người xuất hàng",
    rightSign: "Người nhận hàng",
  },
} as const;

const statusTag: Record<string, { color: string; text: string }> = {
  DRAFT: { color: "gold", text: "Nháp" },
  CONFIRMED: { color: "green", text: "Đã xác nhận" },
  CANCELLED: { color: "default", text: "Đã hủy / đảo" },
};
const paymentTag: Record<string, { color: string; text: string }> = {
  UNPAID: { color: "red", text: "Chưa thanh toán" },
  PARTIAL: { color: "orange", text: "Thanh toán một phần" },
  PAID: { color: "green", text: "Đã thanh toán" },
};
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

/** Cột dòng phiếu — ô đơn giá / thành tiền rộng để số lớn + hậu tố đ không tràn. */
const purchaseLineColumns = "minmax(180px, 1.1fr) 88px 96px minmax(200px, 1.15fr) minmax(220px, 1.25fr) 56px";
const saleLineColumns = "minmax(180px, 1.1fr) 96px 100px minmax(200px, 1.15fr) minmax(220px, 1.25fr) 56px";

export function DocumentsPage({ kind }: { kind: Kind }) {
  const canWrite = useCanWrite();
  const text = copy[kind];
  const draftKey = purchaseFormDraftKey(kind);
  const [rows, setRows] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<string | undefined>();
  const [editing, setEditing] = useState<StockDocument | "new" | null>(null);
  const [viewing, setViewing] = useState<number | null>(null);
  const draftRestoredRef = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listDocuments({ type: kind, status, q: query || undefined }));
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [kind, status, query]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    draftRestoredRef.current = false;
  }, [kind]);

  // F5: mở lại form phiếu nếu còn bản nháp
  useEffect(() => {
    if (loading || draftRestoredRef.current || editing) return;
    const draft = readSessionDraft<DocumentDraftValues>(draftKey);
    if (!draft) return;
    draftRestoredRef.current = true;
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
        <Input.Search className="app-search" placeholder="Tìm số phiếu hoặc tên" allowClear onSearch={setQuery} style={{ width: 280 }} />
        <Select
          allowClear
          placeholder="Trạng thái"
          style={{ width: 180 }}
          value={status}
          onChange={setStatus}
          options={Object.entries(statusTag).map(([value, tag]) => ({ value, label: tag.text }))}
        />
      </Space>
      <Table<DocumentSummary>
        rowKey="id"
        loading={loading}
        dataSource={rows}
        pagination={{ pageSize: 20 }}
        onRow={(record) => ({
          onClick: () => setViewing(record.id),
          className: "app-row-clickable",
        })}
        columns={[
          { title: "Số phiếu", dataIndex: "documentCode", align: "center" },
          {
            title: "Nội bộ",
            dataIndex: "internal",
            align: "center",
            width: 90,
            render: (v: boolean) => (v ? <Tag color="gold">Nội bộ</Tag> : "—"),
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
      {editing ? (
        <EditorModal
          kind={kind}
          document={editing === "new" ? null : editing}
          onClose={() => {
            clearSessionDraft(draftKey);
            setEditing(null);
          }}
          onSaved={(saved) => {
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
  issuedBy?: string;
  receivedBy?: string;
  items: {
    productId?: number;
    unitId?: number;
    quantityDocument?: number;
    unitPrice?: number;
    weightInputUnit?: PurchaseWeightUnit;
    priceInputUnit?: PurchaseWeightUnit;
    /** Giá hệ thống đã gợi ý cho dòng này, dùng để biết Admin đã sửa tay hay chưa. */
    suggestedPrice?: number;
  }[];
  internal?: boolean;
};

type DocumentDraftValues = Omit<EditorValues, "documentDate"> & { documentDate?: string };

function EditorModal({
  kind,
  document,
  onClose,
  onSaved,
}: {
  kind: Kind;
  document: StockDocument | null;
  onClose: () => void;
  onSaved: (document: StockDocument) => void;
}) {
  const shop = useShop();
  const sellByKg = shop === "MANUFACTURING" && kind === "SALE";
  const canMarkInternal = shop === "DISTRIBUTION" && kind === "SALE";
  const text = copy[kind];
  const draftKey = purchaseFormDraftKey(kind);
  const targetId = document?.id ?? "new";
  const [form] = Form.useForm<EditorValues>();
  const [products, setProducts] = useState<Product[]>([]);
  const [balances, setBalances] = useState<StockBalance[]>([]);
  const [saving, setSaving] = useState(false);
  const idempotencyKey = useMemo(() => crypto.randomUUID(), []);
  const draftAppliedRef = useRef(false);
  const items = Form.useWatch("items", form) ?? [];
  const documentDate = Form.useWatch("documentDate", form);
  const internalTransfer = Form.useWatch("internal", form) === true;

  const persistDraft = useCallback(() => {
    const values = form.getFieldsValue(true) as EditorValues;
    writeSessionDraft(draftKey, {
      targetId,
      values: {
        ...values,
        documentDate: values.documentDate ? dayjs(values.documentDate).toISOString() : undefined,
      },
      savedAt: Date.now(),
    });
  }, [form, draftKey, targetId]);

  useEffect(() => {
    return bindDraftUnloadGuard(() => Boolean(readSessionDraft(draftKey)));
  }, [draftKey]);

  useEffect(() => {
    if (draftAppliedRef.current) return;
    draftAppliedRef.current = true;
    const draft = readSessionDraft<DocumentDraftValues>(draftKey);
    if (!draft || draft.targetId !== targetId) return;
    form.setFieldsValue({
      ...draft.values,
      documentDate: draft.values.documentDate ? dayjs(draft.values.documentDate) : dayjs(),
    });
  }, [draftKey, targetId, form]);

  // Giá nhập gợi ý phải theo đúng ngày phiếu: nhập lùi ngày thì lấy giá của ngày đó,
  // không lấy giá đang áp dụng hôm nay.
  const priceDateKey = documentDate ? documentDate.format("YYYY-MM-DD") : undefined;

  useEffect(() => {
    listProducts({ active: true, asOf: priceDateKey })
      .then(setProducts)
      .catch((error) => message.error(toApiError(error).message));
  }, [priceDateKey]);

  useEffect(() => {
    if (!canMarkInternal) return;
    listBalances({})
      .then(setBalances)
      .catch(() => setBalances([]));
  }, [canMarkInternal]);

  const productMap = useMemo(() => new Map(products.map((p) => [p.id, p])), [products]);
  const costByProductId = useMemo(
    () => new Map(balances.map((b) => [b.productId, b.averageCostPerBase])),
    [balances],
  );

  function costUnitPrice(product: Product, unitId?: number): number {
    const unit = product.units.find((u) => u.id === unitId) ?? tonUnit(product) ?? purchaseUnit(product, "tấn");
    const costPerBase = costByProductId.get(product.id) ?? 0;
    if (!unit || costPerBase <= 0) return 0;
    return roundVndToThousand(costPerBase * Number(unit.factorToBase));
  }

  function applyInternalCostPrices() {
    const list = [...((form.getFieldValue("items") as EditorValues["items"] | undefined) ?? [])];
    const next = list.map((line) => {
      const product = productMap.get(line?.productId ?? 0);
      if (!product) return line;
      const price = costUnitPrice(product, line.unitId);
      return { ...line, unitPrice: price, suggestedPrice: price };
    });
    form.setFieldValue("items", next);
  }

  useEffect(() => {
    if (!internalTransfer || balances.length === 0 || products.length === 0) return;
    applyInternalCostPrices();
    // Chỉ khi bật nội bộ / có số dư giá vốn — không phụ thuộc mọi lần gõ form.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [internalTransfer, balances, products]);

  // Đổi ngày phiếu thì giá chuẩn gợi ý đổi theo. Mỗi dòng ghi lại giá mà hệ thống đã gợi ý
  // (`suggestedPrice`); chỉ những dòng còn đúng giá gợi ý đó mới được cập nhật, để không ghi đè
  // giá Admin đã nhập tay.
  useEffect(() => {
    if (kind !== "PURCHASE" || products.length === 0) return;
    const list = (form.getFieldValue("items") as EditorValues["items"] | undefined) ?? [];
    let changed = false;
    const next = list.map((line) => {
      const product = productMap.get(line?.productId ?? 0);
      if (!product) return line;
      const weight = line?.weightInputUnit === "tạ" ? "tạ" : "tấn";
      const suggested = suggestPurchasePricePerUnit(product, weight);
      const currentPrice = Math.round(Number(line.unitPrice ?? 0));
      const untouched = line.suggestedPrice === undefined ? currentPrice === 0 : currentPrice === line.suggestedPrice;
      if (!untouched) return line;
      if (currentPrice === suggested && line.suggestedPrice === suggested) return line;
      changed = true;
      return { ...line, unitPrice: suggested, suggestedPrice: suggested };
    });
    if (changed) form.setFieldValue("items", next);
  }, [products, form, kind, productMap]);

  /** Ngày hiệu lực của giá đang gợi ý, hiển thị cạnh ô đơn giá để Admin biết giá lấy từ mốc nào. */
  function priceEffectiveHint(product: Product | undefined) {
    if (kind !== "PURCHASE" || !product?.purchasePriceEffectiveFrom) return null;
    return `Giá chuẩn từ ${dayjs(product.purchasePriceEffectiveFrom).format("DD/MM/YYYY")}`;
  }

  function weightOf(line?: EditorValues["items"][number]): PurchaseWeightUnit {
    if (sellByKg) return "kg";
    if (shop === "MANUFACTURING" && kind === "PURCHASE" && line?.weightInputUnit === "tạ") return "tạ";
    return "tấn";
  }

  function unitFor(product: Product, weight: PurchaseWeightUnit) {
    if (weight === "kg") return kgUnit(product);
    if (weight === "tạ") return taUnit(product);
    return tonUnit(product) ?? purchaseUnit(product, "tấn");
  }

  function onProductChange(index: number, productId: number) {
    const product = productMap.get(productId);
    if (!product) return;
    const list = [...(form.getFieldValue("items") as EditorValues["items"])];
    const weight = weightOf(list[index]);
    const unit = unitFor(product, weight);
    if (!unit) {
      message.warning("Sản phẩm chưa có đơn vị phù hợp. Hãy Sửa và Lưu lại trong danh mục.");
    }
    const useCost = canMarkInternal && form.getFieldValue("internal") === true;
    const suggested = useCost
      ? costUnitPrice(product, unit?.id)
      : kind === "PURCHASE"
        ? suggestPurchasePricePerUnit(product, weight === "kg" ? "tấn" : weight)
        : unit
          ? suggestSalePricePerUnit(product, unit)
          : 0;
    if (useCost && suggested <= 0) {
      message.warning(`Sản phẩm ${product.productCode} chưa có giá vốn tồn kho để chuyển nội bộ`);
    }
    list[index] = {
      ...list[index],
      productId,
      weightInputUnit: weight,
      priceInputUnit: weight,
      unitId: unit?.id,
      unitPrice: suggested,
      suggestedPrice: suggested,
      quantityDocument: list[index]?.quantityDocument ?? 1,
    };
    form.setFieldValue("items", list);
    // Xóa lỗi validate cũ (nếu có) sau khi hệ thống vừa điền đơn giá hợp lệ.
    void form.validateFields([
      ["items", index, "productId"],
      ["items", index, "unitPrice"],
      ["items", index, "quantityDocument"],
    ]);
  }

  function validateLineField(index: number, key: "quantityDocument" | "unitPrice" | "productId") {
    void form.validateFields([["items", index, key]]);
  }

  /** Blur đơn giá: chỉ báo lỗi đỏ nếu chưa làm tròn hàng nghìn — không tự sửa im lặng. */
  function onWeightChange(index: number, weight: "tạ" | "tấn") {
    const list = [...(form.getFieldValue("items") as EditorValues["items"])];
    const product = productMap.get(list[index]?.productId ?? 0);
    const unit = product ? unitFor(product, weight) : undefined;
    const currentPrice = Math.round(Number(list[index]?.unitPrice ?? 0));
    const untouched = list[index]?.suggestedPrice === undefined ? currentPrice === 0 : currentPrice === list[index]?.suggestedPrice;
    const suggested = product ? suggestPurchasePricePerUnit(product, weight) : currentPrice;
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
    <Modal
      open
      width={1120}
      title={document ? `Sửa ${document.documentCode}` : text.newLabel}
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
      onCancel={onClose}
      onOk={() => form.submit()}
    >
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
                issuedBy: document.issuedBy ?? undefined,
                receivedBy: document.receivedBy ?? undefined,
                internal: document.internal ?? false,
                items: document.items.map((i) => {
                  const product = productMap.get(i.productId);
                  const keepStored = sellByKg || (shop === "MANUFACTURING" && kind === "PURCHASE");
                  let unitPrice = Math.round(i.unitPrice);
                  let quantityDocument = Number(Number(i.quantityDocument).toFixed(6));
                  let weight: PurchaseWeightUnit = sellByKg ? "kg" : i.unitName === "tạ" ? "tạ" : "tấn";
                  if (!keepStored && i.unitName !== "tấn") {
                    unitPrice = Math.round(unitPrice * KG_PER_TON);
                    quantityDocument = Number(Math.max(0.000001, quantityDocument / KG_PER_TON).toFixed(6));
                    weight = "tấn";
                  }
                  const unit = product ? unitFor(product, weight) : undefined;
                  return {
                    productId: i.productId,
                    unitId: unit?.id ?? i.unitId ?? undefined,
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
            const saved = await saveDocument(document?.id, {
              documentType: kind,
              documentDate: values.documentDate.format("YYYY-MM-DDTHH:mm:ss"),
              counterpartyName: values.counterpartyName,
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
                  unitId: i.unitId!,
                  quantityDocument,
                  unitPrice,
                  lineDiscount: 0,
                };
              }),
            });
            message.success(kind === "PURCHASE" ? "Đã tạo phiếu nhập" : "Đã lưu phiếu");
            onSaved(saved);
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Space className="doc-form-meta-row" align="start" wrap size={[16, 32]}>
          <Form.Item name="documentDate" label="Ngày phiếu" rules={[{ required: true, message: "Chọn ngày" }]}>
            <DatePicker showTime format="DD/MM/YYYY HH:mm" />
          </Form.Item>
          <Form.Item name="counterpartyName" label={text.counterparty} rules={optionalNameRules(200)}>
            <Input style={{ width: 240 }} maxLength={200} />
          </Form.Item>
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
                    applyInternalCostPrices();
                  }
                }}
              >
                Giao dịch nội bộ (xuất sang xưởng, giá = giá vốn)
              </Checkbox>
            </Form.Item>
          ) : null}
        </Space>
        {internalTransfer ? (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="Xuất nội bộ sang xưởng"
            description="Đơn giá khóa theo giá vốn tồn (làm tròn hàng nghìn). Khi xác nhận phiếu, hệ thống tự tạo phiếu nhập xưởng cùng mã sản phẩm, cộng tồn xưởng và không tính lãi đoạn chuyển."
          />
        ) : null}
        <Form.List name="items">
          {(fields, { add, remove }) => (
            <>
              {kind === "PURCHASE" ? (
                <div
                  className="purchase-line-grid purchase-line-head"
                  style={{ gridTemplateColumns: purchaseLineColumns }}
                >
                  <span>Sản phẩm</span>
                  <span>Số lượng</span>
                  <span>Đơn vị khối lượng</span>
                  <span>{shop === "MANUFACTURING" ? "Đơn giá" : "Đơn giá / tấn"}</span>
                  <span>Thành tiền</span>
                  <span aria-hidden />
                </div>
              ) : (
                <div
                  className="purchase-line-grid purchase-line-head"
                  style={{ gridTemplateColumns: saleLineColumns }}
                >
                  <span>Sản phẩm</span>
                  <span>Đơn vị tính</span>
                  <span>Số lượng</span>
                  <span>{sellByKg ? "Đơn giá / kg" : "Đơn giá / tấn"}</span>
                  <span>Thành tiền</span>
                  <span aria-hidden />
                </div>
              )}
              {fields.map((field) => {
                const current = items[field.name];
                const product = productMap.get(current?.productId ?? 0);
                const qty = Number(current?.quantityDocument ?? 0);
                const lineTotal =
                  kind === "PURCHASE"
                    ? purchaseLineTotal(current)
                    : Math.max(0, Math.round(qty * Math.round(Number(current?.unitPrice ?? 0))));

                if (kind === "PURCHASE") {
                  const priceHint = priceEffectiveHint(product);
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
                            options={products.map((p) => ({ value: p.id, label: `${p.productCode} - ${p.name}` }))}
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
                            placeholder={current?.weightInputUnit === "tạ" ? "Giá một tạ" : "Giá một tấn"}
                            style={{ width: "100%" }}
                            title={priceHint ?? undefined}
                            disabled={internalTransfer}
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
                  <div key={field.key} className="purchase-line-block">
                    <div
                      className="purchase-line-grid purchase-line-body"
                      style={{ gridTemplateColumns: saleLineColumns }}
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
                          options={products.map((p) => ({ value: p.id, label: `${p.productCode} - ${p.name}` }))}
                        />
                      </Form.Item>
                      <span className="purchase-line-static">{sellByKg ? "kg" : "tấn"}</span>
                      <Form.Item name={[field.name, "quantityDocument"]} validateFirst rules={positiveDecimalRules}>
                        <InputNumber
                          {...qtyInputNumberProps}
                          style={{ width: "100%" }}
                          onBlur={() => onQuantityBlur(field.name)}
                        />
                      </Form.Item>
                      <Form.Item name={[field.name, "unitPrice"]} validateFirst rules={vndUnitPriceRules}>
                        <InputNumber
                          {...vndInputNumberProps}
                          style={{ width: "100%" }}
                          disabled={internalTransfer}
                          placeholder={internalTransfer ? "Giá vốn gốc" : undefined}
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
                    weightInputUnit: sellByKg ? "kg" : "tấn",
                    priceInputUnit: sellByKg ? "kg" : "tấn",
                  })
                }
              >
                Thêm dòng
              </AppActionButton>
            </>
          )}
        </Form.List>
        <div style={{ textAlign: "center", fontSize: 16, marginTop: 16 }}>
          Tổng thanh toán: <b>{formatMoney(subtotal)}</b>
        </div>
      </Form>
    </Modal>
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
  const [busy, setBusy] = useState(false);
  const [paying, setPaying] = useState(false);
  const [reversing, setReversing] = useState(false);

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
          <AppActionButton tone="print" icon={<PrinterOutlined />} onClick={() => void printDocument(doc, kind)}>
            In phiếu
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
                      ? "Trừ tồn phân phối theo giá vốn và tự tạo phiếu nhập nội bộ bên xưởng (cùng mã SP, cộng tồn xưởng)."
                      : kind === "PURCHASE"
                        ? "Phiếu sẽ tăng tồn kho và cập nhật giá vốn."
                        : "Phiếu sẽ trừ tồn kho.",
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
        </Space>
      }
    >
      <Descriptions size="small" column={2} bordered>
        <Descriptions.Item label="Loại">{typeLabel[doc.documentType]}</Descriptions.Item>
        <Descriptions.Item label="Trạng thái">
          <Tag color={statusTag[doc.status].color}>{statusTag[doc.status].text}</Tag>
          {doc.internal ? <Tag color="gold" style={{ marginLeft: 8 }}>Nội bộ</Tag> : null}
        </Descriptions.Item>
        <Descriptions.Item label="Ngày">{dayjs(doc.documentDate).format("DD/MM/YYYY HH:mm")}</Descriptions.Item>
        <Descriptions.Item label={text.counterparty}>{doc.counterpartyName}</Descriptions.Item>
        <Descriptions.Item label="Người xuất / giao">{doc.issuedBy}</Descriptions.Item>
        <Descriptions.Item label="Người nhận / nhập">{doc.receivedBy}</Descriptions.Item>
        {doc.internalPairDocumentCode ? (
          <Descriptions.Item label="Phiếu cặp nội bộ" span={2}>
            {doc.internalPairDocumentCode}
            {kind === "SALE" ? " (phiếu nhập xưởng)" : " (phiếu bán phân phối)"}
          </Descriptions.Item>
        ) : null}
        {doc.internal ? (
          <Descriptions.Item label="Giá" span={2}>
            Giá gốc / giá vốn — không thu chi tiền, không tính lãi đoạn chuyển nhà mình
          </Descriptions.Item>
        ) : null}
        {doc.reason ? <Descriptions.Item label="Lý do" span={2}>{doc.reason}</Descriptions.Item> : null}
        {doc.notes ? <Descriptions.Item label="Ghi chú phiếu" span={2}>{doc.notes}</Descriptions.Item> : null}
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
          { title: "Mã", dataIndex: "productCode", align: "center" },
          { title: "Sản phẩm", dataIndex: "productName", align: "center" },
          { title: "Đơn vị", dataIndex: "unitName", align: "center" },
          { title: "Số lượng", dataIndex: "quantityDocument", align: "center", render: (v: number) => formatQtyDisplay(v) },
          { title: "Đơn giá", dataIndex: "unitPrice", align: "center", render: (v: number) => formatMoney(v) },
          { title: "Thành tiền", dataIndex: "lineTotal", align: "center", render: (v: number) => formatMoney(v) },
          // Chỉ hiện sau khi xác nhận phiếu bán — lúc tạo/nháp không cần 3 cột giá vốn.
          ...(kind === "SALE" && confirmed
            ? [
                {
                  title: "Giá vốn (thực tế)",
                  dataIndex: "costTotal",
                  key: "costTotal",
                  align: "center" as const,
                  render: (v: number | null) => (v == null ? "—" : formatMoney(v)),
                },
                {
                  title: "Giá vốn chuẩn",
                  dataIndex: "standardCostTotal",
                  key: "standardCostTotal",
                  align: "center" as const,
                  render: (v: number | null) => (v == null ? "—" : formatMoney(v)),
                },
                {
                  title: "Chênh lệch giá nhập",
                  key: "priceVariance",
                  align: "center" as const,
                  render: (_: unknown, item: StockDocument["items"][number]) => {
                    if (item.costTotal == null || item.standardCostTotal == null) return "—";
                    const diff = item.costTotal - item.standardCostTotal;
                    if (Math.abs(diff) < 0.01) return <span style={{ color: "#8c8c8c" }}>—</span>;
                    return (
                      <span style={{ color: diff > 0 ? "#cf1322" : "#389e0d" }}>
                        {diff > 0 ? "+" : ""}
                        {formatMoney(diff)}
                      </span>
                    );
                  },
                },
              ]
            : []),
        ]}
      />
      <div style={{ textAlign: "center", marginTop: 16, lineHeight: 1.9 }}>
        <div style={{ fontSize: 16 }}>Tổng thanh toán: <b>{formatMoney(doc.totalAmount)}</b></div>
        {confirmed ? (
          <div>
            {text.paidLabel}: {formatMoney(doc.paidAmount)}
            {" · "}
            {kind === "PURCHASE" ? "Còn phải chi trả" : "Còn phải thu"}: {formatMoney(remaining)}{" "}
            <Tag color={paymentTag[doc.paymentStatus].color}>{paymentTag[doc.paymentStatus].text}</Tag>
          </div>
        ) : null}
      </div>
      {doc.payments.length > 0 ? (
        <Table
          style={{ marginTop: 16 }}
          size="small"
          rowKey="id"
          pagination={false}
          dataSource={doc.payments}
          columns={[
            {
              title: "Thời gian",
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
              render: (v: string | null) => paymentMethodLabel(v),
            },
            {
              title: kind === "PURCHASE" ? "Còn phải chi trả" : "Còn phải thu",
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
  }) => Promise<void>;
}) {
  const [form] = Form.useForm<{ amount: number; paymentMethod: string; paymentDate: Dayjs; note?: string }>();
  const key = useMemo(() => crypto.randomUUID(), []);
  const amountWatch = Form.useWatch("amount", form);
  const remainingAfter = Math.max(0, remaining - Math.round(Number(amountWatch || 0)));
  const remainingLabel = kind === "PURCHASE" ? "Còn phải chi trả" : "Còn phải thu";

  return (
    <Modal
      open
      title={label}
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
        initialValues={{ amount: remaining, paymentMethod: "CASH", paymentDate: dayjs() }}
        onFinish={(values) =>
          onSubmit({
            amount: values.amount,
            paymentMethod: values.paymentMethod,
            paymentDate: values.paymentDate.format("YYYY-MM-DDTHH:mm:ss"),
            note: values.note,
            idempotencyKey: key,
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
            ...vndAmountRules,
            {
              validator: async (_, val) => {
                if (Number(val) > remaining) throw new Error(`Không vượt ${remainingLabel.toLowerCase()}`);
              },
            },
          ]}
        >
          <InputNumber {...vndInputNumberProps} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item label={remainingLabel}>
          <Input value={formatMoney(remainingAfter)} readOnly />
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
        Phiếu gốc không bị sửa. Hệ thống tạo một phiếu đảo và hoàn tồn kho. Mỗi phiếu chỉ đảo một lần.
      </p>
      <Form form={form} layout="vertical" onFinish={({ reason }) => onSubmit(reason)}>
        <Form.Item name="reason" label="Lý do" rules={[{ required: true, message: "Nhập lý do đảo phiếu" }]}>
          <Input.TextArea rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function escapeHtml(value: string | null | undefined) {
  return (value ?? "").replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

async function printDocument(doc: StockDocument, kind: Kind) {
  const text = copy[kind];
  const shop = await getSettings().catch(() => null);
  const rows = doc.items
    .map(
      (item, index) => `<tr><td>${index + 1}</td><td>${escapeHtml(item.productCode)}</td><td>${escapeHtml(item.productName)}</td>
      <td>${escapeHtml(item.unitName)}</td><td class="r">${formatQtyDisplay(item.quantityDocument)}</td>
      <td class="r">${formatMoney(item.unitPrice)}</td><td class="r">${formatMoney(item.lineTotal)}</td></tr>`,
    )
    .join("");
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(doc.documentCode)}</title>
  <style>
    body{font-family:Arial,sans-serif;font-size:13px;margin:24px;color:#000}
    h1{text-align:center;font-size:18px;margin:12px 0 4px}
    table{width:100%;border-collapse:collapse;margin-top:12px}
    th,td{border:1px solid #333;padding:4px 6px}
    .r{text-align:right}
    .center{text-align:center}
    .sign{display:flex;justify-content:space-between;margin-top:48px;text-align:center}
    .sign div{width:40%}
  </style></head><body>
  <div><b>${escapeHtml(shop?.shopName)}</b><br/>${escapeHtml(shop?.shopAddress)}<br/>${escapeHtml(shop?.shopPhone)}</div>
  <h1>${text.printTitle}</h1>
  <div class="center">Số: ${escapeHtml(doc.documentCode)} - Ngày ${dayjs(doc.documentDate).format("DD/MM/YYYY HH:mm")}</div>
  <p>${text.counterparty}: ${escapeHtml(doc.counterpartyName)}<br/>
  ${kind === "SALE" ? "Người xuất" : "Người giao"}: ${escapeHtml(doc.issuedBy)} &nbsp; ${kind === "SALE" ? "Người nhận" : "Người nhập"}: ${escapeHtml(doc.receivedBy)}</p>
  <table><thead><tr><th>Số thứ tự</th><th>Mã hàng</th><th>Tên hàng</th><th>Đơn vị</th><th>Số lượng</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead>
  <tbody>${rows}</tbody></table>
  <p class="r"><b>Tổng thanh toán: ${formatMoney(doc.totalAmount)}</b></p>
  <div class="sign"><div>${text.leftSign}<br/><i>(Ký, ghi rõ họ tên)</i></div><div>${text.rightSign}<br/><i>(Ký, ghi rõ họ tên)</i></div></div>
  <script>window.onload=function(){window.print()}</script></body></html>`;
  const frame = window.open("", "_blank", "width=900,height=700");
  if (!frame) {
    message.error("Trình duyệt chặn cửa sổ in. Cho phép cửa sổ bật lên rồi thử lại.");
    return;
  }
  frame.document.write(html);
  frame.document.close();
}
