import { Alert, Button, Card, Col, InputNumber, Modal, Row, Space, Statistic, Typography, message } from "antd";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../services/api-client";
import { toApiError } from "../../services/error-handler";
import { ResponsiveTable } from "../../components/PagedTable";
import { useShop } from "../shop/shop-store";
import { useWorkingYear } from "./working-year";
import { useWorkingPeriodInfo } from "./working-period";
import { useSession } from "../auth/auth-store";
import { formatMoney } from "../../utils/money";
import { OpeningInventory } from "../stock/OpeningInventory";

type Count = { categoryId: number; category: string; rawKg: number | null; scrapKg: number | null; scrapValue: number | null; rawValue: number | null };
type CategoryFlow = { categoryId: number; openingKg: number; purchasedKg: number; deliveredKg: number };
type StockAtClose = { productId: number; categoryId: number; productName: string; categoryName: string; quantityBase: number };
type Book = { categoryFlows?: CategoryFlow[]; stockAtClose?: StockAtClose[]; reconciliationBlock?: string | null; year: number; month: string; from: string; to: string; counts: Count[]; sales: number; purchases: number; expenses: number; closed: boolean; openingValue: number | null; endingValue: number | null; internalOut: number | null; profit: number | null };
type StockCount = StockAtClose & { rawKg: number | null; scrapKg: number | null };

function formatCountKg(value: number | string | undefined): string {
  if (value === undefined || value === null || value === "") return "";
  const [whole, fraction] = String(value).replace(/,/g, "").split(".");
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return fraction === undefined ? grouped : `${grouped}.${fraction}`;
}

function roundKg(value: number): number {
  return Math.max(0, Math.round(value * 1000) / 1000);
}

export function MonthlyBookPage() {
  const shop = useShop();
  const distribution = shop === "DISTRIBUTION";
  const working = useWorkingYear(shop);
  const periodInfo = useWorkingPeriodInfo(working.year);
  const month = working.month;
  const [book, setBook] = useState<Book>();
  const [counts, setCounts] = useState<Count[]>([]);
  const [stockCounts, setStockCounts] = useState<StockCount[]>();
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setBook(undefined);
    setStockCounts(undefined);
    setError("");
    if (!month) return () => controller.abort();
    api.get<{data: Book}>("/api/v1/dashboard/monthly-book", { params: { year: working.year, month }, signal: controller.signal })
      .then(({data}) => {
        if (controller.signal.aborted) return;
        setBook(data.data);
        setCounts(data.data.counts.map(row => ({...row, rawKg: data.data.closed ? row.rawKg : null, scrapKg: data.data.closed ? row.scrapKg : null})));
        setStockCounts(data.data.stockAtClose?.map(row => ({...row, rawKg: null, scrapKg: null})));
      })
      .catch(error => { if (!controller.signal.aborted) setError(toApiError(error).message); });
    return () => controller.abort();
  }, [shop, working.year, month, distribution]);
  const categoryFlowById = new Map(book?.categoryFlows?.map(row => [row.categoryId, row]));
  async function close() {
    setSaving(true);
    try {
      const base = {year: working.year, month};
      const result = distribution
        ? await api.post<{data: Book}>("/api/v1/dashboard/monthly-book/reconcile", {
          ...base,
          items: stockCounts?.map(row => ({
            productId: row.productId, categoryId: row.categoryId, countedQuantityKg: row.rawKg, scrapKg: row.scrapKg,
          })),
        })
        : await api.post<{data: Book}>("/api/v1/dashboard/monthly-book", {...base, counts});
      setBook(result.data.data);
      setCounts(result.data.data.counts);
      message.success(distribution ? "Đã cập nhật tồn kho và lưu sổ tháng" : "Đã lưu sổ tháng");
    } catch (error) { setError(toApiError(error).message); throw error; }
    finally { setSaving(false); }
  }
  if (!month) return <Card title="Kiểm kê và chốt sổ tháng"><Link to="/">Chọn tháng ở Tổng quan</Link></Card>;
  return <Card title="Kiểm kê và chốt sổ tháng" extra={<Typography.Text>Tháng {dayjs(`${month}-01`).format("MM/YYYY")}</Typography.Text>}>
    {error && <Alert type="error" showIcon message={error} style={{marginBottom:16}} />}
    {book && <>
      <p>{periodInfo.name} · {dayjs(book.from).format("DD/MM/YYYY")} – {dayjs(book.to).format("DD/MM/YYYY")} · {book.closed ? "Đã lưu bản chốt" : "Chưa chốt sổ tháng"}</p>
      <OpeningInventory year={working.year} month={month} />
      <Alert type="info" showIcon message="Kiểm kê tháng là tùy chọn"
        description={<>Nếu chỉ chốt năm, bạn có thể bỏ qua màn này. Đến ngày kết thúc kỳ, {distribution
          ? <>vào <Link to="/ton-kho">Tồn kho</Link> để kiểm kê, rồi vào <Link to="/chot-nam">Chốt năm</Link>.</>
          : <>vào <Link to="/chot-nam">Chốt năm</Link> để mở quyết toán Xưởng.</>}</>} />
      <p style={{ margin: "20px 0 12px" }}>Giá trị tồn đầu tháng: <Typography.Text strong>{formatMoney(book.openingValue)}</Typography.Text></p>
      <p style={{ marginBottom: 20 }}>Các giá trị tiền được lấy tự động. Không cần nhập tiền tồn hoặc tiền vụn ở đây; cuối năm vẫn kiểm kê vụn và tính lãi theo số chốt năm.</p>
      {book.reconciliationBlock && <Alert type="warning" showIcon message={book.reconciliationBlock} style={{ marginBottom: 16 }} />}
      {distribution && !book.closed ? <ResponsiveTable<StockCount>
        rowKey={row => `${row.productId}-${row.categoryId}`} pagination={false} dataSource={stockCounts ?? []}
        columns={[
          { title: "Sản phẩm", dataIndex: "productName" },
          { title: "Loại nhôm", dataIndex: "categoryName" },
          { title: `Tồn sổ đến ${dayjs(book.to).format("DD/MM/YYYY")} (kg)`, dataIndex: "quantityBase", render: (kg: number) => formatCountKg(kg) },
          ...(["rawKg", "scrapKg"] as const).map(key => ({
            title: key === "rawKg" ? "Số cân thực tế (kg)" : "Vụn nhôm (tùy chọn)",
            render: (_: unknown, row: StockCount) => {
              const max = key === "rawKg"
                ? roundKg(row.quantityBase - (row.scrapKg ?? 0))
                : roundKg(row.quantityBase - (row.rawKg ?? 0));
              return <InputNumber
                aria-label={`${key === "rawKg" ? "Số cân" : "Vụn nhôm"} ${row.productName} ${row.categoryName}`}
                value={row[key]} disabled={!!book.reconciliationBlock} min={0} max={max} step={1} formatter={formatCountKg}
                parser={(v) => (v ?? "").replace(/,/g, "") as unknown as number}
                placeholder={key === "rawKg" ? `≤ ${formatCountKg(max)}` : "Có thể để trống"}
                onChange={value => setStockCounts(items => items?.map(item =>
                  item.productId === row.productId && item.categoryId === row.categoryId ? {...item, [key]: value} : item))} />;
            },
          })),
          { title: "Điều chỉnh (kg)", render: (_: unknown, row: StockCount) =>
            row.rawKg == null ? "—" : (row.rawKg - row.quantityBase).toLocaleString("vi-VN", {maximumFractionDigits: 6}) },
        ]} /> : <ResponsiveTable rowKey="categoryId" pagination={false} dataSource={counts} columns={[
        {title:"Loại nhôm", dataIndex:"category"},
        ...(!book.closed ? [
          { title: "Tồn đầu tháng (kg)", align: "center" as const, render: (_: unknown, row: Count) => formatCountKg(roundKg(categoryFlowById.get(row.categoryId)?.openingKg ?? 0)) },
          { title: "Nhập trong tháng (kg)", align: "center" as const, render: (_: unknown, row: Count) => formatCountKg(roundKg(categoryFlowById.get(row.categoryId)?.purchasedKg ?? 0)) },
          { title: "Giao trong tháng (kg)", align: "center" as const, render: (_: unknown, row: Count) => formatCountKg(roundKg(categoryFlowById.get(row.categoryId)?.deliveredKg ?? 0)) },
        ] : []),
        ...(["rawKg", "scrapKg"] as const).map((key) => ({
          title: key === "rawKg" ? "Nguyên liệu còn tồn (kg)" : "Vụn nhôm (không bắt buộc)",
          render: (_: unknown, row: Count) => {
            const flow = categoryFlowById.get(row.categoryId) ?? { openingKg: 0, purchasedKg: 0, deliveredKg: 0 };
            const netKg = roundKg(flow.openingKg + flow.purchasedKg - flow.deliveredKg);
            const max = book.closed ? undefined : (key === "rawKg" ? roundKg(netKg - (row.scrapKg ?? 0)) : roundKg(netKg - (row.rawKg ?? 0)));
            return <InputNumber
              aria-label={`${key === "rawKg" ? "Nguyên liệu còn tồn" : "Vụn nhôm"} ${row.category}`}
              min={0} max={max} step={1} formatter={formatCountKg}
              parser={(v) => (v ?? "").replace(/,/g, "") as unknown as number}
              style={{ width: "100%", maxWidth: 180 }}
              placeholder={key === "scrapKg" ? "Có thể để trống" : (max !== undefined ? `≤ ${formatCountKg(max)}` : "Nhập kg còn tồn")}
              value={row[key]} disabled={book.closed}
              onChange={value => setCounts(rows => rows.map(item => item.categoryId === row.categoryId ? {...item, [key]: value} : item))} />;
          },
        })),
      ]} />}
      <Row gutter={[16, 16]} style={{ marginTop: 20, marginBottom: 20 }}>
        {[
          { title: "Tiền bán hàng trong tháng", value: book.sales },
          { title: "Giá trị nhập trong tháng", value: book.purchases },
          { title: "Chi phí hoạt động và tiền công", value: book.expenses },
          ...(book.closed ? [
            { title: "Giá trị tồn cuối tháng", value: book.endingValue },
            { title: "Chuyển nội bộ ra Xưởng", value: book.internalOut },
            { title: "Lãi quản trị tháng (tham khảo)", value: book.profit },
          ] : []),
        ].map(item => <Col key={item.title} xs={24} sm={12} lg={8}>
          <Card size="small" style={{ height: "100%" }} styles={{ body: { padding: 20, minHeight: 124 } }}>
            <Statistic title={item.title} value={formatMoney(item.value)} />
          </Card>
        </Col>)}
      </Row>
      {book.closed && book.profit == null && <Alert type="info" showIcon style={{ marginBottom: 16 }} message="Đã lưu kiểm kê. Lãi tháng chưa đủ dữ liệu giá trị tồn/vụn; khi chốt năm vẫn kiểm kê và tính lại độc lập." />}
      {!book.closed && <Space wrap>
        <Button type="primary" loading={saving} disabled={!!book.reconciliationBlock} onClick={() => {
          const rows = distribution ? stockCounts : counts;
          if (!rows || rows.some(row => row.rawKg == null)) {
            message.warning("Nhập đủ số cân thực tế. Nếu không kiểm kê tháng, chọn Bỏ qua.");
            return;
          }
          Modal.confirm({
            title: "Xác nhận kiểm kê và chốt sổ tháng?",
            content: distribution ? "Tồn kho dùng để bán sẽ được cập nhật theo số cân đã nhập." : "Bản chốt sẽ lưu số cân đã nhập.",
            okText: "Xác nhận chốt sổ", cancelText: "Quay lại", autoFocusButton: "cancel", onOk: close,
          });
        }}>Chốt sổ tháng</Button>
        <Link to="/">Bỏ qua, về Tổng quan</Link>
      </Space>}
    </>}
  </Card>;
}

export function MonthlyBookReminder() {
  const shop = useShop();
  const working = useWorkingYear(shop);
  const session = useSession();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  useEffect(() => {
    if (session?.role === "ADMIN") return;
    let active = true;
    async function check() {
      const now = dayjs();
      if (now.date() !== now.endOf("month").date()) { setOpen(false); return; }
      const month = now.format("YYYY-MM");
      if (working.month !== month) { setOpen(false); return; }
      const key = "monthly-book-reminder:"+shop+":"+month;
      try {
        const state = JSON.parse(localStorage.getItem(key) ?? "{}");
        if (state.dismissed || state.until > Date.now()) return;
        const response = await api.get<{data:Book}>("/api/v1/dashboard/monthly-book", {params:{year:working.year,month}});
        if (active && !response.data.data.closed) setOpen(true);
      } catch { /* Nhắc lại khi kết nối sẵn sàng. */ }
    }
    void check();
    const timer = window.setInterval(() => void check(), 60000);
    return () => { active=false; clearInterval(timer); };
  }, [shop, working.year, working.month, session?.role]);
  function choose(action:"later"|"no"|"yes") {
    const key = "monthly-book-reminder:"+shop+":"+dayjs().format("YYYY-MM");
    localStorage.setItem(key, JSON.stringify(action==="later" ? {until:Date.now()+3*60*60*1000} : {dismissed:true}));
    setOpen(false);
    if (action==="yes") navigate("/chot-so-thang");
  }
  return <Modal open={open} width="60vw" styles={{body:{padding:"24px 0"},wrapper:{minWidth:320}}} title="Hôm nay cuối tháng rồi, bạn có muốn chốt sổ theo tháng không?" closable={false} maskClosable={false} footer={<Space wrap><Button onClick={()=>choose("later")}>Nhắc tôi sau 3 tiếng</Button><Button onClick={()=>choose("no")}>Không</Button><Button type="primary" onClick={()=>choose("yes")}>Có, kiểm kê cuối tháng</Button></Space>}>
    Chọn Có để nhập số nhôm còn tồn và lưu sổ tháng {dayjs().format("MM/YYYY")}.
  </Modal>;
}
