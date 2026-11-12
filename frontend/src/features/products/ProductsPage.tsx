import {
  Alert,
  AutoComplete,
  Card,
  Col,
  DatePicker,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Upload,
  message,
} from "antd";
import type { Rule } from "antd/es/form";
import {
  CloseOutlined,
  CloudSyncOutlined,
  DeleteOutlined,
  EditOutlined,
  HistoryOutlined,
  PlusOutlined,
  SaveOutlined,
  SwapOutlined,
  UploadOutlined,
} from "@ant-design/icons";
import dayjs from "dayjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { AppActionButton, appButtonStyle } from "../../components/AppButtons";
import { PagedTable } from "../../components/PagedTable";
import { LiveSearch } from "../../components/LiveSearch";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { requiredNameRules } from "../../utils/name-rules";
import {
  UNIT_PRICE_STEP_ERROR,
  commitUnitPriceOnBlur,
  documentFieldValidateTrigger,
  isValidUnitPriceVnd,
  toVndInteger,
  vndInputNumberProps,
  vndUnitPriceRules,
} from "../../utils/vnd-input";
import {
  PRODUCT_FORM_DRAFT_KEY,
  bindDraftUnloadGuard,
  clearSessionDraft,
  readSessionDraft,
  writeSessionDraft,
} from "../../utils/session-draft";
import {
  getNextProductCode,
  listCategories,
  listProducts,
  listPurchasePrices,
  saveCategory,
  saveProduct,
  savePurchasePrice,
  syncCatalogToOtherShop,
  type Category,
  type Product,
  type ProductInput,
  type PurchasePrice,
} from "./products-api";
import { useCanWrite } from "../auth/auth-store";
import { SHOP_LABELS, useShop } from "../shop/shop-store";
import { KG_PER_TA, KG_PER_TON, formatTonFromKg } from "./product-stock-unit";

type ProductFormValues = ProductInput;

export function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedCategoryId = Number(searchParams.get("loai")) || undefined;
  const canWrite = useCanWrite();
  const shop = useShop();
  const sellByKg = shop === "MANUFACTURING";
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [query, setQuery] = useState("");
  const [activeFilter, setActiveFilter] = useState<boolean | undefined>();
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<Product | "new" | null>(null);
  const [showCategories, setShowCategories] = useState(false);
  useEffect(() => {
    if (requestedCategoryId) setShowCategories(true);
  }, [requestedCategoryId]);
  const [syncing, setSyncing] = useState(false);
  const draftRestoredRef = useRef(false);

  const canSyncCatalog = canWrite && shop === "DISTRIBUTION";

  const syncCatalog = () => {
    Modal.confirm({
      title: "Đồng bộ danh mục sang Xưởng?",
      content: (
        <>
          Đẩy <strong>sản phẩm</strong> và <strong>loại nhôm</strong> từ Phân phối sang{" "}
          <strong>{SHOP_LABELS.MANUFACTURING}</strong>. Khớp theo mã SP / tên loại. Loại mới copy giá;
          loại đã có giữ giá bên Xưởng. <strong>Tồn kho không đồng bộ</strong>. Chỉ Phân phối đồng bộ
          sang Xưởng (Xưởng không đồng bộ ngược).
        </>
      ),
      okText: "Đồng bộ",
      cancelText: "Hủy",
      onOk: async () => {
        setSyncing(true);
        try {
          const result = await syncCatalogToOtherShop();
          message.success(
            `Đã đồng bộ sang ${result.targetShopName}: loại +${result.categoriesCreated}/cập nhật ${result.categoriesUpdated}, SP +${result.productsCreated}/cập nhật ${result.productsUpdated}`,
          );
          await load();
        } catch (error) {
          message.error(toApiError(error).message);
          throw error;
        } finally {
          setSyncing(false);
        }
      },
    });
  };

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [p, c] = await Promise.all([
        listProducts({ q: query || undefined, active: activeFilter }),
        listCategories("all"),
      ]);
      setProducts(p);
      setCategories(c);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [query, activeFilter]);

  useEffect(() => {
    void load();
  }, [load]);

  // F5: mở lại form sửa/thêm nếu còn bản nháp trong tab
  useEffect(() => {
    if (loading || draftRestoredRef.current || editing) return;
    const draft = readSessionDraft<ProductFormValues>(PRODUCT_FORM_DRAFT_KEY);
    if (!draft) return;
    draftRestoredRef.current = true;
    if (draft.targetId === "new") {
      setEditing("new");
      message.info("Đã khôi phục bản nháp thêm sản phẩm");
      return;
    }
    const found = products.find((p) => p.id === draft.targetId);
    if (found) {
      setEditing(found);
      message.info("Đã khôi phục bản nháp sửa sản phẩm");
    } else {
      clearSessionDraft(PRODUCT_FORM_DRAFT_KEY);
    }
  }, [loading, products, editing]);

  return (
    <Card
      className="app-fill-card"
      title="Sản phẩm"
      extra={
        canWrite ? (
          <Space>
            {canSyncCatalog ? (
              <AppActionButton
                tone="sync"
                icon={<CloudSyncOutlined />}
                loading={syncing}
                onClick={syncCatalog}
              >
                Đồng bộ sang Xưởng
              </AppActionButton>
            ) : null}
            <AppActionButton tone="secondary" icon={<SwapOutlined />} onClick={() => setShowCategories(true)}>
              Loại nhôm
            </AppActionButton>
            <AppActionButton tone="create" icon={<PlusOutlined />} onClick={() => setEditing("new")}>
              Sản phẩm
            </AppActionButton>
          </Space>
        ) : null
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <LiveSearch value={query} onChange={setQuery} placeholder="Tìm mã hoặc tên sản phẩm" style={{ width: 360 }} suggestions={products.flatMap((p) => [p.productCode, p.name])} />
        <Select
          placeholder="Trạng thái"
          allowClear
          style={{ width: 170 }}
          value={activeFilter}
          onChange={setActiveFilter}
          options={[
            { value: true, label: "Đang kinh doanh" },
            { value: false, label: "Ngừng kinh doanh" },
          ]}
        />
      </Space>
      <PagedTable<Product>
        rowKey="id"
        loading={loading}
        dataSource={products}
        size="middle"
        onRow={(record) => ({
          onClick: () => setEditing(record),
          className: "app-row-clickable",
        })}
        columns={[
          {
            title: "Ảnh",
            width: 68,
            align: "center",
            render: (_, p) =>
              p.imagePath ? (
                <span onClick={(e) => e.stopPropagation()}>
                  <Image
                    src={p.imagePath}
                    alt={p.name}
                    width={42}
                    height={42}
                    style={{ objectFit: "cover", borderRadius: 4, border: "1px solid var(--color-border)", cursor: "pointer" }}
                    preview={{ mask: "Phóng to" }}
                  />
                </span>
              ) : (
                <span style={{ color: "var(--color-text-secondary)" }}>—</span>
              ),
          },
          { title: "Mã nhôm", dataIndex: "productCode", width: 120, align: "center" },
          { title: "Tên sản phẩm", dataIndex: "name", align: "center" },
          ...(!sellByKg ? [{
            title: "Tồn (tấn)",
            align: "center" as const,
            render: (_: unknown, p: Product) => formatTonFromKg(p.quantityBase),
          }] : []),
          {
            title: "Trạng thái",
            align: "center",
            render: (_, p) => (p.active ? <Tag color="green">Đang bán</Tag> : <Tag>Ngừng bán</Tag>),
          },
          {
            title: "Thao tác",
            align: "center",
            render: (_, p) => (
              <Space onClick={(e) => e.stopPropagation()}>
                {canWrite ? (
                  <AppActionButton tone="edit" icon={<EditOutlined />} onClick={() => setEditing(p)}>
                    Sửa
                  </AppActionButton>
                ) : null}
              </Space>
            ),
          },
        ]}
      />
      {editing ? (
          <ProductModal
            categories={categories}
            products={products}
          product={editing === "new" ? null : editing}
          onClose={() => {
            clearSessionDraft(PRODUCT_FORM_DRAFT_KEY);
            setEditing(null);
          }}
          onSaved={() => {
            clearSessionDraft(PRODUCT_FORM_DRAFT_KEY);
            setEditing(null);
            void load();
          }}
        />
      ) : null}
      {showCategories ? (
        <CategoriesModal categories={categories} initialCategoryId={requestedCategoryId} onClose={() => {
          setShowCategories(false);
          if (requestedCategoryId) setSearchParams({}, { replace: true });
        }} onChanged={() => void load()} />
      ) : null}
    </Card>
  );
}

/** Sản phẩm chỉ gồm mã, tên, ảnh, trạng thái — giá nằm ở loại nhôm. */
function ProductModal({
  categories,
  products,
  product,
  onClose,
  onSaved,
}: {
  categories: Category[];
  products: Product[];
  product: Product | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<ProductFormValues>();
  const [saving, setSaving] = useState(false);
  const imageValue = Form.useWatch("imagePath", form);
  const imageBeforeUploadRef = useRef<string | undefined>(undefined);
  const uploadAcceptedRef = useRef(false);
  const draftAppliedRef = useRef(false);
  const targetId = product?.id ?? "new";
  const normalizedProductName = (value: string) => value.trim().normalize("NFC").toLocaleLowerCase();
  const normalizedName = (value: string) => value.toLocaleLowerCase().replace(/[^\p{L}\p{N}]/gu, "");
  const matchingCategories = product
    ? categories.filter((category) => {
        const productName = normalizedName(product.name);
        const categoryName = normalizedName(category.name);
        return categoryName.length > 0 && productName.includes(categoryName);
      })
    : [];
  const initialCategoryId = product?.categoryId ?? (matchingCategories.length === 1 ? matchingCategories[0].id : undefined);

  /** Hộp thoại chọn file: hủy thì giữ ảnh đang hiển thị (focus quay lại mà không chọn file). */
  const armUploadRevertOnCancel = useCallback(() => {
    imageBeforeUploadRef.current = form.getFieldValue("imagePath");
    uploadAcceptedRef.current = false;
    const onWindowFocus = () => {
      window.removeEventListener("focus", onWindowFocus);
      window.setTimeout(() => {
        if (!uploadAcceptedRef.current) {
          form.setFieldValue("imagePath", imageBeforeUploadRef.current);
        }
      }, 350);
    };
    window.addEventListener("focus", onWindowFocus);
  }, [form]);

  const persistDraft = useCallback(() => {
    writeSessionDraft(PRODUCT_FORM_DRAFT_KEY, {
      targetId,
      values: form.getFieldsValue(true) as ProductFormValues,
      savedAt: Date.now(),
    });
  }, [form, targetId]);

  useEffect(() => {
    return bindDraftUnloadGuard(() => Boolean(readSessionDraft(PRODUCT_FORM_DRAFT_KEY)));
  }, []);

  useEffect(() => {
    if (draftAppliedRef.current) return;
    draftAppliedRef.current = true;
    const draft = readSessionDraft<ProductFormValues>(PRODUCT_FORM_DRAFT_KEY);
    if (draft && draft.targetId === targetId) {
      form.setFieldsValue(draft.values);
      return;
    }
    if (!product) {
      getNextProductCode()
        .then((code) => {
          form.setFieldValue("productCode", String(code));
          persistDraft();
        })
        .catch(() => undefined);
    }
  }, [product, form, targetId, persistDraft]);

  return (
    <Modal
      open
      title={product ? "Sửa sản phẩm" : "Thêm sản phẩm"}
      okText="Lưu"
      cancelText="Hủy"
      okButtonProps={{ style: appButtonStyle("create"), icon: <SaveOutlined /> }}
      cancelButtonProps={{ style: appButtonStyle("cancel"), icon: <CloseOutlined /> }}
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
      width={720}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={
          product
            ? {
                productCode: product.productCode,
                name: product.name,
                categoryId: initialCategoryId,
                imagePath: product.imagePath ?? undefined,
                description: product.description ?? undefined,
                active: product.active,
              }
            : { productCode: "", active: true }
        }
        onValuesChange={() => persistDraft()}
        onFinish={async (values) => {
          setSaving(true);
          try {
            await saveProduct(product?.id, {
              productCode: values.productCode,
              name: values.name,
              categoryId: values.categoryId ?? null,
              imagePath: values.imagePath,
              description: values.description,
              active: values.active,
            });
            message.success("Đã lưu sản phẩm");
            onSaved();
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Row gutter={[16, 32]}>
          <Form.Item name="productCode" hidden>
            <Input />
          </Form.Item>
          <Col xs={24}>
            <Form.Item
              name="name"
              label="Tên sản phẩm"
              rules={[
                ...requiredNameRules("Nhập tên sản phẩm", 200),
                {
                  validator: async (_, value?: string) => {
                    if (!value) return;
                    const duplicate = products.some(
                      (item) => item.id !== product?.id && normalizedProductName(item.name) === normalizedProductName(value),
                    );
                    if (duplicate) throw new Error("Đã có sản phẩm này rồi, vui lòng chọn sản phẩm khác");
                  },
                },
              ]}
            >
              <AutoComplete
                placeholder="Chọn gợi ý hoặc tự nhập tên sản phẩm"
                options={categories.map((category) => ({ value: category.name }))}
                filterOption={(input, option) =>
                  String(option?.value ?? "").toLocaleLowerCase().includes(input.toLocaleLowerCase())
                }
              />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="categoryId" label="Loại nhôm" rules={[{ required: true, message: "Chọn loại nhôm" }]}>
          <Select
            placeholder="Chọn loại nhôm của sản phẩm"
            options={categories.map((category) => ({ value: category.id, label: category.name }))}
            showSearch
            optionFilterProp="label"
          />
        </Form.Item>

        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 32 }}
          message="Giá nhập và giá bán được khai báo theo Loại nhôm. Trên phiếu, mỗi dòng chọn sản phẩm rồi chọn loại nhôm để tự điền giá."
        />

        <Form.Item name="description" hidden>
          <Input />
        </Form.Item>

        <Form.Item label="Hình ảnh sản phẩm (Bấm vào ảnh đã chọn để phóng to)">
          <Space align="center" wrap size="middle">
            {imageValue ? (
              <Image
                src={imageValue}
                alt="Ảnh sản phẩm"
                width={72}
                height={72}
                style={{ objectFit: "cover", borderRadius: 6, border: "1px solid var(--color-border)", cursor: "pointer" }}
                preview={{ mask: "Phóng to" }}
              />
            ) : (
              <div
                style={{
                  width: 72,
                  height: 72,
                  borderRadius: 6,
                  border: "1px dashed var(--color-border)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  color: "var(--color-text-secondary)",
                  fontSize: 12,
                  textAlign: "center",
                  padding: 4,
                }}
              >
                Chưa có ảnh
              </div>
            )}
            <Upload
              accept="image/*"
              showUploadList={false}
              beforeUpload={(file) => {
                uploadAcceptedRef.current = true;
                if (file.size > 1.5 * 1024 * 1024) {
                  message.error("Vui lòng chọn ảnh nhỏ hơn 1.5 MB");
                  uploadAcceptedRef.current = false;
                  form.setFieldValue("imagePath", imageBeforeUploadRef.current);
                  return Upload.LIST_IGNORE;
                }
                const revert = imageBeforeUploadRef.current;
                const reader = new FileReader();
                reader.onload = () => {
                  if (typeof reader.result === "string") {
                    form.setFieldValue("imagePath", reader.result);
                  }
                };
                reader.onerror = () => {
                  message.error("Không đọc được ảnh");
                  form.setFieldValue("imagePath", revert);
                };
                reader.readAsDataURL(file);
                return false;
              }}
            >
              <AppActionButton tone="create" icon={<UploadOutlined />} onClick={armUploadRevertOnCancel}>
                Chọn ảnh từ máy
              </AppActionButton>
            </Upload>
          </Space>
          <Form.Item name="imagePath" noStyle>
            <Input type="hidden" />
          </Form.Item>
        </Form.Item>

        <Form.Item
          name="active"
          label="Trạng thái kinh doanh (Tắt để ngừng bán, không xóa lịch sử)"
          valuePropName="checked"
        >
          <Switch checkedChildren="Đang bán" unCheckedChildren="Ngừng bán" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

type CategoryFormValues = {
  name: string;
  active: boolean;
  /** Ô hiển thị: giá nhập / tấn; giá bán / kg, / tạ, / tấn. */
  purchasePriceTon?: number;
  sellingPriceKg?: number;
  sellingPriceTa?: number;
  sellingPriceTon?: number;
};

type CategoryPriceField = keyof Omit<CategoryFormValues, "name" | "active">;

/** Ô giá không bắt buộc: để trống được, nhưng nếu nhập thì phải bội 500 (…000 / …500). */
const optionalVndPriceRules: Rule[] = [
  {
    validator: async (_, val) => {
      if (val === undefined || val === null || val === "") return;
      const n = toVndInteger(val);
      if (n == null || n < 0) throw new Error("Không nhập số thập phân, chỉ số nguyên");
      if (!isValidUnitPriceVnd(n)) throw new Error(UNIT_PRICE_STEP_ERROR);
    },
  },
];

function categoryFormValues(c: Category): CategoryFormValues {
  return {
    name: c.name,
    active: c.active,
    purchasePriceTon: Math.round((Number(c.referencePurchasePrice) || 0) * KG_PER_TON),
    sellingPriceKg: Math.round(Number(c.defaultSellingPrice) || 0),
    sellingPriceTa: Math.round(Number(c.defaultSellingPriceTa) || 0),
    sellingPriceTon: Math.round(Number(c.defaultSellingPriceTon) || 0),
  };
}

const emptyCategoryValues: CategoryFormValues = { name: "", active: true };

function CategoriesModal({
  categories,
  initialCategoryId,
  onClose,
  onChanged,
}: {
  categories: Category[];
  initialCategoryId?: number;
  onClose: () => void;
  onChanged: () => void;
}) {
  const [form] = Form.useForm<CategoryFormValues>();
  const [editing, setEditing] = useState<Category | null>(null);
  const [pricesFor, setPricesFor] = useState<Category | null>(null);
  const [saving, setSaving] = useState(false);
  /** Giá đúng gần nhất của từng ô — blur ô gõ dở (không bội 500) thì trả về số này. */
  const lastValid = useRef<Partial<Record<CategoryPriceField, number>>>({});

  const resetForm = useCallback(() => {
    form.resetFields();
    lastValid.current = {};
  }, [form]);

  /** Chốt giá khi blur: không làm tròn / ghi đè trong lúc đang gõ. */
  function commitPrice(field: CategoryPriceField) {
    const raw = form.getFieldValue(field);
    if (raw === undefined || raw === null || raw === "") {
      lastValid.current[field] = undefined;
      return;
    }
    const { value, reverted, error } = commitUnitPriceOnBlur(raw, lastValid.current[field] ?? 0);
    if (!reverted) lastValid.current[field] = value;
    form.setFieldValue(field, value);
    if (error) form.setFields([{ name: field, errors: [error] }]);
    else void form.validateFields([field]).catch(() => undefined);
    // Giá bán / kg đổi: gợi ý sẵn / tạ, / tấn nếu các ô đó còn trống.
    if (field === "sellingPriceKg" && !reverted) {
      if (form.getFieldValue("sellingPriceTa") == null) {
        form.setFieldValue("sellingPriceTa", Math.round(value * KG_PER_TA));
        lastValid.current.sellingPriceTa = Math.round(value * KG_PER_TA);
      }
      if (form.getFieldValue("sellingPriceTon") == null) {
        form.setFieldValue("sellingPriceTon", Math.round(value * KG_PER_TON));
        lastValid.current.sellingPriceTon = Math.round(value * KG_PER_TON);
      }
    }
  }

  function startEdit(c: Category) {
    setEditing(c);
    const values = categoryFormValues(c);
    form.setFieldsValue(values);
    lastValid.current = {
      purchasePriceTon: values.purchasePriceTon,
      sellingPriceKg: values.sellingPriceKg,
      sellingPriceTa: values.sellingPriceTa,
      sellingPriceTon: values.sellingPriceTon,
    };
  }

  useEffect(() => {
    const category = categories.find((c) => c.id === initialCategoryId);
    if (category) startEdit(category);
  }, [initialCategoryId, categories]);

  const priceInput = (field: CategoryPriceField, placeholder: string) => (
    <InputNumber<number>
      {...vndInputNumberProps}
      style={{ width: 180 }}
      placeholder={placeholder}
      onBlur={() => commitPrice(field)}
    />
  );

  return (
    <Modal open title="Loại nhôm" footer={null} onCancel={onClose} width={1040}>
      <Table<Category>
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={categories}
        scroll={{ x: "max-content" }}
        columns={[
          { title: "Tên loại nhôm", dataIndex: "name", align: "center" },
          {
            title: "Giá nhập / tấn",
            align: "center",
            render: (_, c) => (
              <div>
                <div>{formatMoney(Math.round(c.referencePurchasePrice * KG_PER_TON))}</div>
                <div style={{ fontSize: 11, color: "var(--color-text-secondary)" }}>
                  {c.purchasePriceEffectiveFrom
                    ? `áp dụng từ ${dayjs(c.purchasePriceEffectiveFrom).format("DD/MM/YYYY")}`
                    : "chưa có lịch sử giá"}
                </div>
              </div>
            ),
          },
          { title: "Giá bán / kg", align: "center", render: (_, c) => formatMoney(Math.round(c.defaultSellingPrice)) },
          { title: "Giá bán / tạ", align: "center", render: (_, c) => formatMoney(Math.round(c.defaultSellingPriceTa)) },
          { title: "Giá bán / tấn", align: "center", render: (_, c) => formatMoney(Math.round(c.defaultSellingPriceTon)) },
          {
            title: "Trạng thái",
            align: "center",
            render: (_, c) => (c.active ? <Tag color="green">Đang dùng</Tag> : <Tag>Ngừng dùng</Tag>),
          },
          {
            title: "Thao tác",
            align: "center",
            render: (_, c) => (
              <Space>
                <AppActionButton tone="create" icon={<EditOutlined />} onClick={() => startEdit(c)}>
                  Sửa
                </AppActionButton>
                <AppActionButton tone="secondary" icon={<HistoryOutlined />} onClick={() => setPricesFor(c)}>
                  Lịch sử giá nhập
                </AppActionButton>
              </Space>
            ),
          },
        ]}
      />
      <Form<CategoryFormValues>
        form={form}
        layout="vertical"
        style={{ marginTop: 16 }}
        initialValues={emptyCategoryValues}
        onFinish={async (values) => {
          const fields: CategoryPriceField[] = [
            "purchasePriceTon",
            "sellingPriceKg",
            "sellingPriceTa",
            "sellingPriceTon",
          ];
          for (const field of fields) {
            const n = toVndInteger(form.getFieldValue(field));
            if (n != null && !isValidUnitPriceVnd(n)) {
              commitPrice(field);
              return;
            }
          }
          for (const field of fields) commitPrice(field);
          const purchaseTon = Math.max(0, lastValid.current.purchasePriceTon ?? 0);
          const sellingKg = Math.max(0, lastValid.current.sellingPriceKg ?? 0);
          if (purchaseTon <= 0) {
            form.setFields([{ name: "purchasePriceTon", errors: ["Nhập giá nhập / tấn"] }]);
            return;
          }
          if (sellingKg <= 0) {
            form.setFields([{ name: "sellingPriceKg", errors: ["Nhập giá bán / kg"] }]);
            return;
          }
          setSaving(true);
          try {
            await saveCategory({
              id: editing?.id,
              name: values.name,
              active: values.active,
              description: editing?.description ?? undefined,
              referencePurchasePrice: Math.round(purchaseTon / KG_PER_TON),
              defaultSellingPrice: sellingKg,
              defaultSellingPriceTa: Math.max(0, lastValid.current.sellingPriceTa ?? Math.round(sellingKg * KG_PER_TA)),
              defaultSellingPriceTon: Math.max(0, lastValid.current.sellingPriceTon ?? Math.round(sellingKg * KG_PER_TON)),
              defaultSellingPriceM2: editing?.defaultSellingPriceM2 ?? 0,
            });
            setEditing(null);
            resetForm();
            onChanged();
            message.success("Đã lưu loại nhôm");
          } catch (error) {
            message.error(toApiError(error).message);
          } finally {
            setSaving(false);
          }
        }}
      >
        <Space align="start" wrap size={[16, 32]}>
          <Form.Item name="name" label="Tên loại nhôm" rules={requiredNameRules("Nhập tên loại", 100)}>
            <Input placeholder="Tên loại nhôm" style={{ width: 220 }} maxLength={100} />
          </Form.Item>
          <Form.Item name="active" label="Trạng thái" valuePropName="checked">
            <Switch checkedChildren="Đang dùng" unCheckedChildren="Ngừng" />
          </Form.Item>
        </Space>
        <Space align="start" wrap size={[16, 32]} style={{ marginBottom: 16 }}>
          <Form.Item
            name="purchasePriceTon"
            label="Giá nhập / tấn"
            rules={vndUnitPriceRules}
            validateTrigger={[...documentFieldValidateTrigger]}
          >
            {priceInput("purchasePriceTon", "VD: 75,000,000")}
          </Form.Item>
          <Form.Item
            name="sellingPriceKg"
            label="Giá bán / kg"
            rules={vndUnitPriceRules}
            validateTrigger={[...documentFieldValidateTrigger]}
          >
            {priceInput("sellingPriceKg", "VD: 80,000")}
          </Form.Item>
          <Form.Item
            name="sellingPriceTa"
            label="Giá bán / tạ"
            rules={optionalVndPriceRules}
            validateTrigger={[...documentFieldValidateTrigger]}
            extra="Để trống = giá / kg × 100"
          >
            {priceInput("sellingPriceTa", "VD: 8,000,000")}
          </Form.Item>
          <Form.Item
            name="sellingPriceTon"
            label="Giá bán / tấn"
            rules={optionalVndPriceRules}
            validateTrigger={[...documentFieldValidateTrigger]}
            extra="Để trống = giá / kg × 1.000"
          >
            {priceInput("sellingPriceTon", "VD: 80,000,000")}
          </Form.Item>
        </Space>
        <div>
          <Space wrap size={12}>
            <AppActionButton
              tone="create"
              icon={editing ? <SaveOutlined /> : <PlusOutlined />}
              htmlType="submit"
              loading={saving}
            >
              {editing ? "Lưu sửa" : "Thêm loại nhôm"}
            </AppActionButton>
            {editing ? (
              <AppActionButton
                tone="delete"
                icon={<DeleteOutlined />}
                onClick={() => {
                  setEditing(null);
                  resetForm();
                }}
              >
                Hủy sửa
              </AppActionButton>
            ) : null}
          </Space>
        </div>
      </Form>
      {pricesFor ? (
        <PurchasePricesModal
          category={pricesFor}
          onClose={() => setPricesFor(null)}
          onChanged={() => onChanged()}
        />
      ) : null}
    </Modal>
  );
}

/**
 * Lịch sử giá nhập theo ngày của loại nhôm.
 *
 * Giá nhập thay đổi theo ngày nên không ghi đè: mỗi lần lưu là một mốc mới có ngày hiệu lực.
 * Mốc cũ được đóng lại, nên phiếu nhập lùi ngày vẫn lấy đúng giá của ngày đó và lãi chuẩn của
 * phiếu đã xác nhận không bị lệch.
 */
function PurchasePricesModal({
  category,
  onClose,
  onChanged,
}: {
  category: Category;
  onClose: () => void;
  onChanged: () => void;
}) {
  const canWrite = useCanWrite();
  const [rows, setRows] = useState<PurchasePrice[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ pricePerTon: number; effectiveFrom?: dayjs.Dayjs; note?: string }>();
  const lastValidPrice = useRef(Math.round(category.referencePurchasePrice * KG_PER_TON));

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listPurchasePrices(category.id));
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [category.id]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Modal
      open
      width={820}
      title={`Lịch sử giá nhập — ${category.name}`}
      footer={
        <AppActionButton tone="cancel" icon={<CloseOutlined />} onClick={onClose}>
          Đóng
        </AppActionButton>
      }
      onCancel={onClose}
    >
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="Giá nhập theo tấn, hiệu lực từ ngày ghi"
        description="Lưu giá mới sẽ đóng mốc cũ lại. Phiếu nhập lùi ngày lấy đúng giá của ngày phiếu, và phiếu đã xác nhận giữ nguyên chi phí chuẩn nên lãi đã chốt không bị tính lại."
      />
      <Table<PurchasePrice>
        rowKey="id"
        size="small"
        loading={loading}
        pagination={false}
        dataSource={rows}
        columns={[
          {
            title: "Hiệu lực từ",
            dataIndex: "effectiveFrom",
            align: "center",
            render: (v: string) => dayjs(v).format("DD/MM/YYYY"),
          },
          {
            title: "Đến",
            dataIndex: "effectiveTo",
            align: "center",
            render: (v: string | null, r) =>
              r.open ? (
                <Tag color="green">Đang áp dụng</Tag>
              ) : r.future ? (
                <Tag color="blue">Sắp áp dụng</Tag>
              ) : rows.some((newer) => newer.id > r.id && newer.effectiveFrom === r.effectiveFrom) ? (
                <Tag>Đã thay trong ngày {dayjs(r.effectiveFrom).format("DD/MM/YYYY")}</Tag>
              ) : v ? (
                dayjs(v).format("DD/MM/YYYY")
              ) : (
                "—"
              ),
          },
          {
            title: "Giá nhập / tấn",
            dataIndex: "pricePerBase",
            align: "center",
            render: (v: number) => <b>{formatMoney(Math.round(v * KG_PER_TON))}</b>,
          },
          { title: "Ghi chú", dataIndex: "note", align: "center", render: (v: string | null) => v ?? "—" },
        ]}
      />
      {canWrite ? (
        <Form
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
          initialValues={{
            effectiveFrom: dayjs(),
            pricePerTon: Math.round(category.referencePurchasePrice * KG_PER_TON),
          }}
          onFinish={async (values) => {
            setSaving(true);
            try {
              const shown = Math.max(0, Math.round(Number(values.pricePerTon ?? 0)));
              setRows(
                await savePurchasePrice(category.id, {
                  pricePerBase: Math.max(0, Math.round(shown / KG_PER_TON)),
                  effectiveFrom: values.effectiveFrom?.format("YYYY-MM-DD"),
                  note: values.note,
                }),
              );
              lastValidPrice.current = shown;
              message.success("Đã ghi giá nhập mới");
              onChanged();
            } catch (error) {
              message.error(toApiError(error).message);
            } finally {
              setSaving(false);
            }
          }}
        >
          <Space align="start" wrap size={[16, 32]}>
            <Form.Item
              name="pricePerTon"
              label="Giá nhập / tấn"
              rules={vndUnitPriceRules}
              validateTrigger={[...documentFieldValidateTrigger]}
            >
              <InputNumber<number>
                {...vndInputNumberProps}
                style={{ width: 220 }}
                onBlur={() => {
                  const raw = form.getFieldValue("pricePerTon");
                  const { value, error } = commitUnitPriceOnBlur(raw, lastValidPrice.current);
                  form.setFieldsValue({ pricePerTon: value });
                  if (!error) lastValidPrice.current = value;
                  if (error) {
                    form.setFields([{ name: "pricePerTon", errors: [error] }]);
                  }
                }}
              />
            </Form.Item>
            <Form.Item
              name="effectiveFrom"
              label="Hiệu lực từ ngày"
              tooltip="Mặc định hôm nay. Có thể đặt giá cho ngày mai. Không chọn ngày trước mốc giá sớm nhất, để không làm lệch chi phí đã chốt."
            >
              <DatePicker format="DD/MM/YYYY" style={{ width: 160 }} />
            </Form.Item>
            <Form.Item name="note" label="Ghi chú">
              <Input placeholder="VD: nhà cung cấp tăng giá" style={{ width: 240 }} />
            </Form.Item>
            <Form.Item label=" ">
              <AppActionButton tone="create" icon={<SaveOutlined />} htmlType="submit" loading={saving}>
                Ghi giá mới
              </AppActionButton>
            </Form.Item>
          </Space>
        </Form>
      ) : null}
    </Modal>
  );
}
