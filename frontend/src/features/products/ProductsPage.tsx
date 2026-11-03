import {
  Alert,
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
import {
  CloseOutlined,
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
import { AppActionButton, appButtonStyle } from "../../components/AppButtons";
import { toApiError } from "../../services/error-handler";
import { formatMoney } from "../../utils/money";
import { requiredNameRules } from "../../utils/name-rules";
import { vndInputNumberProps, vndUnitPriceRules } from "../../utils/vnd-input";
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
  type Category,
  type Product,
  type ProductInput,
  type PurchasePrice,
} from "./products-api";
import { useCanWrite } from "../auth/auth-store";
import { useShop } from "../shop/shop-store";
import {
  DEFAULT_STOCK_UNIT,
  KG_PER_TA,
  KG_PER_TON,
  formatTonFromKg,
} from "./product-stock-unit";

type ProductFormValues = ProductInput & {
  purchasePriceTon?: number;
  sellingPriceTon?: number;
};

/** Form nhập giá theo tấn; API lưu đ/kg. */
function formPricesFromProduct(purchaseKg: number, sellingKg: number, sellByKg: boolean) {
  const p = Math.max(0, Math.round(Number(purchaseKg) || 0));
  const s = Math.max(0, Math.round(Number(sellingKg) || 0));
  return {
    referencePurchasePrice: p,
    purchasePriceTon: Math.round(p * KG_PER_TON),
    defaultSellingPrice: s,
    defaultSellingPriceTa: Math.round(s * KG_PER_TA),
    sellingPriceTon: sellByKg ? s : Math.round(s * KG_PER_TON),
  };
}

export function ProductsPage() {
  const canWrite = useCanWrite();
  const shop = useShop();
  const sellByKg = shop === "MANUFACTURING";
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [query, setQuery] = useState("");
  const [categoryFilter, setCategoryFilter] = useState<number | undefined>();
  const [activeFilter, setActiveFilter] = useState<boolean | undefined>();
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState<Product | "new" | null>(null);
  const [pricesFor, setPricesFor] = useState<Product | null>(null);
  const [showCategories, setShowCategories] = useState(false);
  const draftRestoredRef = useRef(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [p, c] = await Promise.all([
        listProducts({ q: query || undefined, categoryId: categoryFilter, active: activeFilter }),
        listCategories(),
      ]);
      setProducts(p);
      setCategories(c);
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [query, categoryFilter, activeFilter]);

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
      title="Sản phẩm"
      extra={
        canWrite ? (
          <Space>
            <AppActionButton tone="secondary" icon={<SwapOutlined />} onClick={() => setShowCategories(true)}>
              Loại nhôm
            </AppActionButton>
            <AppActionButton tone="create" icon={<PlusOutlined />} onClick={() => setEditing("new")}>
              Thêm sản phẩm
            </AppActionButton>
          </Space>
        ) : null
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search className="app-search" placeholder="Tìm mã hoặc tên sản phẩm" allowClear onSearch={setQuery} style={{ width: 280 }} />
        <Select
          placeholder="Loại nhôm"
          allowClear
          style={{ width: 200 }}
          value={categoryFilter}
          onChange={setCategoryFilter}
          options={categories.map((c) => ({ value: c.id, label: c.name }))}
        />
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
      <Table<Product>
        rowKey="id"
        loading={loading}
        dataSource={products}
        size="middle"
        pagination={{ pageSize: 20 }}
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
                    style={{ objectFit: "cover", borderRadius: 4, border: "1px solid #d9d9d9", cursor: "pointer" }}
                    preview={{ mask: "Phóng to" }}
                  />
                </span>
              ) : (
                <span style={{ color: "#999" }}>—</span>
              ),
          },
          { title: "Mã nhôm", dataIndex: "productCode", width: 120, align: "center" },
          { title: "Tên sản phẩm", dataIndex: "name", align: "center" },
          { title: "Loại nhôm", dataIndex: "categoryName", align: "center" },
          {
            title: sellByKg ? "Tồn (kg)" : "Tồn (tấn)",
            align: "center",
            render: (_, p) => (sellByKg ? new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(p.quantityBase) : formatTonFromKg(p.quantityBase)),
          },
          {
            title: "Giá nhập / tấn",
            align: "center",
            render: (_, p) => (
              <div>
                <div>{formatMoney(Math.round(p.referencePurchasePrice * KG_PER_TON))}</div>
                <div style={{ fontSize: 11, color: "#8c8c8c" }}>
                  {p.purchasePriceEffectiveFrom
                    ? `áp dụng từ ${dayjs(p.purchasePriceEffectiveFrom).format("DD/MM/YYYY")}`
                    : "chưa có lịch sử giá"}
                </div>
              </div>
            ),
          },
          {
            title: sellByKg ? "Giá bán / kg" : "Giá bán / tấn",
            align: "center",
            render: (_, p) => formatMoney(sellByKg ? Math.round(p.defaultSellingPrice) : Math.round(p.defaultSellingPrice * KG_PER_TON)),
          },
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
                <AppActionButton tone="secondary" icon={<HistoryOutlined />} onClick={() => setPricesFor(p)}>
                  Lịch sử giá nhập
                </AppActionButton>
              </Space>
            ),
          },
        ]}
      />
      {editing ? (
        <ProductModal
          product={editing === "new" ? null : editing}
          categories={categories.filter((c) => c.active || (editing !== "new" && c.id === editing.categoryId))}
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
        <CategoriesModal categories={categories} onClose={() => setShowCategories(false)} onChanged={() => void load()} />
      ) : null}
      {pricesFor ? (
        <PurchasePricesModal
          product={pricesFor}
          onClose={() => setPricesFor(null)}
          onChanged={() => void load()}
        />
      ) : null}
    </Card>
  );
}

function ProductModal({
  product,
  categories,
  onClose,
  onSaved,
}: {
  product: Product | null;
  categories: Category[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const shop = useShop();
  const sellByKg = shop === "MANUFACTURING";
  const [form] = Form.useForm<ProductFormValues>();
  const [saving, setSaving] = useState(false);
  const imageValue = Form.useWatch("imagePath", form);
  const imageBeforeUploadRef = useRef<string | undefined>(undefined);
  const uploadAcceptedRef = useRef(false);
  const draftAppliedRef = useRef(false);
  const targetId = product?.id ?? "new";

  const defaultCategoryId =
    categories.find((c) => c.active && c.name.trim().toLowerCase() === "nhôm loại 1")?.id ??
    categories.find((c) => c.active && c.name.trim().toLowerCase().includes("loại 1"))?.id;

  const syncPurchaseFromKg = useCallback(
    (purchaseKg: number) => {
      const p = Math.max(0, Math.round(Number(purchaseKg) || 0));
      form.setFieldsValue({
        referencePurchasePrice: p,
        purchasePriceTon: Math.round(p * KG_PER_TON),
      });
    },
    [form],
  );

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
      const shown = Number(draft.values.sellingPriceTon ?? 0);
      const sellingKg =
        shown > 0
          ? sellByKg
            ? Math.round(shown)
            : Math.round(shown / KG_PER_TON)
          : Math.max(0, Math.round(Number(draft.values.defaultSellingPrice ?? 0)));
      form.setFieldsValue({
        ...draft.values,
        ...formPricesFromProduct(Number(draft.values.referencePurchasePrice ?? 0), sellingKg, sellByKg),
      });
      return;
    }
    if (!product) {
      getNextProductCode()
        .then((code) => {
          form.setFieldValue("productCode", String(code));
          persistDraft();
        })
        .catch(() => undefined);
      if (defaultCategoryId != null) {
        form.setFieldValue("categoryId", defaultCategoryId);
      }
    }
  }, [product, form, defaultCategoryId, targetId, persistDraft, sellByKg]);

  const emptyPrices = formPricesFromProduct(0, 0, sellByKg);
  const productPrices = product
    ? formPricesFromProduct(product.referencePurchasePrice, product.defaultSellingPrice, sellByKg)
    : emptyPrices;

  return (
    <Modal
      open
      title={product ? `Sửa sản phẩm ${product.productCode}` : "Thêm sản phẩm"}
      okText="Lưu"
      cancelText="Hủy"
      okButtonProps={{ style: appButtonStyle("create"), icon: <SaveOutlined /> }}
      cancelButtonProps={{ style: appButtonStyle("cancel"), icon: <CloseOutlined /> }}
      confirmLoading={saving}
      onCancel={onClose}
      onOk={() => form.submit()}
      width={760}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={
          product
            ? {
                ...product,
                ...productPrices,
                baseUnitName: DEFAULT_STOCK_UNIT,
                imagePath: product.imagePath ?? undefined,
                description: product.description ?? undefined,
              }
            : {
                productCode: "",
                categoryId: defaultCategoryId,
                ...emptyPrices,
                baseUnitName: DEFAULT_STOCK_UNIT,
                active: true,
              }
        }
        onValuesChange={() => persistDraft()}
        onFinish={async (values) => {
          setSaving(true);
          try {
            const purchaseKg = Math.max(0, Math.round(Number(values.referencePurchasePrice ?? 0)));
            const sellingShown = Math.max(0, Math.round(Number(values.sellingPriceTon ?? 0)));
            const sellingKg = sellByKg ? sellingShown : Math.max(0, Math.round(sellingShown / KG_PER_TON));
            await saveProduct(product?.id, {
              productCode: values.productCode,
              name: values.name,
              categoryId: values.categoryId,
              specification: "",
              baseUnitName: DEFAULT_STOCK_UNIT,
              referencePurchasePrice: purchaseKg,
              defaultSellingPrice: sellingKg,
              defaultSellingPriceTa: Math.round(sellingKg * KG_PER_TA),
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
          <Col span={8}>
            <Form.Item
              name="productCode"
              label="Mã nhôm (Tự sinh)"
              tooltip="Hệ thống tự động sinh mã tăng dần (VD: NH-0001). Bạn cũng có thể sửa lại nếu muốn."
            >
              <Input
                placeholder="Tự động sinh..."
                suffix={
                  !product ? (
                    <a
                      onClick={() => {
                        void getNextProductCode().then((c) => form.setFieldValue("productCode", String(c)));
                      }}
                    >
                      Tạo mã
                    </a>
                  ) : undefined
                }
              />
            </Form.Item>
          </Col>
          <Col span={16}>
            <Form.Item
              name="name"
              label="Tên sản phẩm"
              rules={requiredNameRules("Nhập tên sản phẩm", 200)}
            >
              <Input placeholder="VD: Nhôm A7" maxLength={200} />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={[16, 32]}>
          <Col span={16}>
            <Form.Item name="categoryId" label="Loại nhôm" rules={[{ required: true, message: "Chọn loại nhôm" }]}>
              <Select options={categories.map((c) => ({ value: c.id, label: c.name }))} />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="Đơn vị tồn kho">
              <Input disabled value={sellByKg ? "kg" : "tấn"} />
            </Form.Item>
            <Form.Item name="baseUnitName" hidden>
              <Input />
            </Form.Item>
          </Col>
        </Row>

        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 32 }}
          message={sellByKg ? "Giá nhập theo tấn. Giá bán theo kg." : "Giá nhập và giá bán theo tấn."}
        />

        <Row gutter={[16, 32]}>
          <Col span={12}>
            <Form.Item name="purchasePriceTon" label="Giá nhập / tấn" rules={vndUnitPriceRules}>
              <InputNumber<number>
                {...vndInputNumberProps}
                style={{ width: "100%" }}
                placeholder="VD: 75,000,000 / tấn"
                onChange={(val) => {
                  const purchaseKg = Math.max(0, Math.round(Number(val) / KG_PER_TON) || 0);
                  syncPurchaseFromKg(purchaseKg);
                }}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="sellingPriceTon" label={sellByKg ? "Giá bán / kg" : "Giá bán / tấn"} rules={vndUnitPriceRules}>
              <InputNumber<number>
                {...vndInputNumberProps}
                style={{ width: "100%" }}
                placeholder={sellByKg ? "VD: 80,000 / kg" : "VD: 80,000,000 / tấn"}
                onChange={(val) => {
                  const shown = Math.max(0, Math.round(Number(val) || 0));
                  const sellingKg = sellByKg ? shown : Math.round(shown / KG_PER_TON);
                  form.setFieldsValue({
                    sellingPriceTon: shown,
                    defaultSellingPrice: sellingKg,
                    defaultSellingPriceTa: Math.round(sellingKg * KG_PER_TA),
                  });
                }}
              />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="referencePurchasePrice" hidden>
          <InputNumber />
        </Form.Item>
        <Form.Item name="defaultSellingPrice" hidden>
          <InputNumber />
        </Form.Item>
        <Form.Item name="defaultSellingPriceTa" hidden>
          <InputNumber />
        </Form.Item>

        <Form.Item label="Hình ảnh sản phẩm (Bấm vào ảnh đã chọn để phóng to)">
          <Space align="center" wrap size="middle">
            {imageValue ? (
              <Image
                src={imageValue}
                alt="Ảnh sản phẩm"
                width={72}
                height={72}
                style={{ objectFit: "cover", borderRadius: 6, border: "1px solid #d9d9d9", cursor: "pointer" }}
                preview={{ mask: "Phóng to" }}
              />
            ) : (
              <div
                style={{
                  width: 72,
                  height: 72,
                  borderRadius: 6,
                  border: "1px dashed #d9d9d9",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  color: "#8c8c8c",
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
          name="description"
          label="Ghi chú (Tối đa 500 ký tự)"
          rules={[{ max: 500, message: "Ghi chú không vượt quá 500 ký tự" }]}
        >
          <Input.TextArea rows={3} maxLength={500} showCount placeholder="Ghi chú thêm về sản phẩm (tối đa 500 ký tự)..." />
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

function CategoriesModal({
  categories,
  onClose,
  onChanged,
}: {
  categories: Category[];
  onClose: () => void;
  onChanged: () => void;
}) {
  const [form] = Form.useForm<{ name: string; active: boolean }>();
  const [editing, setEditing] = useState<Category | null>(null);

  return (
    <Modal open title="Loại nhôm" footer={null} onCancel={onClose} width={520}>
      <Table<Category>
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={categories}
        columns={[
          { title: "Tên loại nhôm", dataIndex: "name", align: "center" },
          {
            title: "Trạng thái",
            align: "center",
            render: (_, c) => (c.active ? <Tag color="green">Đang dùng</Tag> : <Tag>Ngừng dùng</Tag>),
          },
          {
            title: "Thao tác",
            align: "center",
            render: (_, c) => (
              <AppActionButton
                tone="create"
                icon={<EditOutlined />}
                onClick={() => {
                  setEditing(c);
                  form.setFieldsValue({
                    name: c.name,
                    active: c.active,
                  });
                }}
              >
                Sửa
              </AppActionButton>
            ),
          },
        ]}
      />
      <Form
        form={form}
        layout="vertical"
        style={{ marginTop: 16 }}
        initialValues={{ active: true }}
        onFinish={async (values) => {
          try {
            await saveCategory({
              id: editing?.id,
              name: values.name,
              active: values.active,
              description: editing?.description ?? undefined,
            });
            setEditing(null);
            form.resetFields();
            onChanged();
            message.success("Đã lưu loại nhôm");
          } catch (error) {
            message.error(toApiError(error).message);
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
        <Space wrap size={12}>
          <AppActionButton tone="create" icon={editing ? <SaveOutlined /> : <PlusOutlined />} htmlType="submit">
            {editing ? "Lưu sửa" : "Thêm loại nhôm"}
          </AppActionButton>
          {editing ? (
            <AppActionButton
              tone="delete"
              icon={<DeleteOutlined />}
              onClick={() => {
                setEditing(null);
                form.resetFields();
              }}
            >
              Hủy sửa
            </AppActionButton>
          ) : null}
        </Space>
      </Form>
    </Modal>
  );
}

/**
 * Lịch sử giá nhập theo ngày.
 *
 * Giá nhập thay đổi theo ngày nên không ghi đè: mỗi lần lưu là một mốc mới có ngày hiệu lực.
 * Mốc cũ được đóng lại, nên phiếu nhập lùi ngày vẫn lấy đúng giá của ngày đó và lãi chuẩn của
 * phiếu đã xác nhận không bị lệch.
 */
function PurchasePricesModal({
  product,
  onClose,
  onChanged,
}: {
  product: Product;
  onClose: () => void;
  onChanged: () => void;
}) {
  const canWrite = useCanWrite();
  const [rows, setRows] = useState<PurchasePrice[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ pricePerTon: number; effectiveFrom?: dayjs.Dayjs; note?: string }>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await listPurchasePrices(product.id));
    } catch (error) {
      message.error(toApiError(error).message);
    } finally {
      setLoading(false);
    }
  }, [product.id]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Modal
      open
      width={820}
      title={`Lịch sử giá nhập — ${product.productCode}`}
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
            pricePerTon: Math.round(product.referencePurchasePrice * KG_PER_TON),
          }}
          onFinish={async (values) => {
            setSaving(true);
            try {
              const pricePerTon = Math.max(0, Math.round(Number(values.pricePerTon ?? 0)));
              setRows(
                await savePurchasePrice(product.id, {
                  pricePerBase: Math.max(0, Math.round(pricePerTon / KG_PER_TON)),
                  effectiveFrom: values.effectiveFrom?.format("YYYY-MM-DD"),
                  note: values.note,
                }),
              );
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
            <Form.Item name="pricePerTon" label="Giá nhập / tấn" rules={vndUnitPriceRules}>
              <InputNumber<number> {...vndInputNumberProps} style={{ width: 220 }} />
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
