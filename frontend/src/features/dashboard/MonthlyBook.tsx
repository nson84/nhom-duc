import { Alert, Button, Card, Col, DatePicker, InputNumber, Modal, Row, Space, Statistic, Typography, message } from "antd";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../../services/api-client";
import { toApiError } from "../../services/error-handler";
import { ResponsiveTable } from "../../components/PagedTable";
import { useShop } from "../shop/shop-store";
import { useWorkingYear } from "./working-year";
import { useSession } from "../auth/auth-store";
import { formatMoney } from "../../utils/money";
import { OpeningInventory } from "../stock/OpeningInventory";
import { listBalances, type StockBalance } from "../stock/stock-api";

type Count = { categoryId: number; category: string; rawKg: number | null; scrapKg: number | null; scrapValue: number | null; rawValue: number | null };
type Book = { year: number; month: string; from: string; to: string; counts: Count[]; sales: number; purchases: number; expenses: number; closed: boolean; openingValue: number | null; endingValue: number | null; internalOut: number | null; profit: number | null };
type StockCount = StockBalance & { rawKg: number | null; scrapKg: number | null };

export function MonthlyBookPage() {
  const shop = useShop();
  const distribution = shop === "DISTRIBUTION";
  const working = useWorkingYear(shop);
  const [params] = useSearchParams();
  const [month, setMonth] = useState(params.get("month") ?? working.month ?? dayjs().format("YYYY-MM"));
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
    api.get<{data: Book}>("/api/v1/dashboard/monthly-book", { params: { year: working.year, month }, signal: controller.signal })
      .then(async ({data}) => {
        const balances = distribution && !data.data.closed ? await listBalances({}) : undefined;
        if (controller.signal.aborted) return;
        setBook(data.data);
        setCounts(data.data.counts.map(row => ({...row, rawKg: data.data.closed ? row.rawKg : null, scrapKg: data.data.closed ? row.scrapKg : null})));
        setStockCounts(balances?.map(row => ({...row, rawKg: null, scrapKg: null})));
      })
      .catch(error => { if (!controller.signal.aborted) setError(toApiError(error).message); });
    return () => controller.abort();
  }, [shop, working.year, month, distribution]);
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
  return <Card title="Kiểm kê và chốt sổ tháng" extra={<DatePicker picker="month" format="MM/YYYY" allowClear={false} value={dayjs(month+"-01")} onChange={value => value && setMonth(value.format("YYYY-MM"))} />}>
    {error && <Alert type="error" showIcon message={error} style={{marginBottom:16}} />}
    {book && <>
      <p>Kỳ {working.year} · {dayjs(book.from).format("DD/MM/YYYY")} – {dayjs(book.to).format("DD/MM/YYYY")} · {book.closed ? "Đã lưu bản chốt" : "Chưa chốt sổ tháng"}</p>
      <OpeningInventory year={working.year} month={month} />
      <Alert type="info" showIcon message="Kiểm kê tháng là tùy chọn"
        description={<>Nếu chỉ chốt năm, bạn có thể bỏ qua màn này. Đến ngày kết thúc kỳ, {distribution
          ? <>vào <Link to="/ton-kho">Tồn kho</Link> để kiểm kê, rồi vào <Link to="/chot-nam">Chốt năm</Link>.</>
          : <>vào <Link to="/chot-nam">Chốt năm</Link> để mở quyết toán Xưởng.</>}</>} />
      <p style={{ margin: "20px 0 12px" }}>Giá trị tồn đầu tháng: <Typography.Text strong>{formatMoney(book.openingValue)}</Typography.Text></p>
      <p style={{ marginBottom: 20 }}>Các giá trị tiền được lấy tự động. Không cần nhập tiền tồn hoặc tiền vụn ở đây; cuối năm vẫn kiểm kê vụn và tính lãi theo số chốt năm.</p>
      {distribution && !book.closed ? <ResponsiveTable<StockCount>
        rowKey={row => `${row.productId}-${row.categoryId}`} pagination={false} dataSource={stockCounts ?? []}
        columns={[
          { title: "Sản phẩm", dataIndex: "productName" },
          { title: "Loại nhôm", dataIndex: "categoryName" },
          { title: "Tồn sổ (kg)", dataIndex: "quantityBase" },
          ...(["rawKg", "scrapKg"] as const).map(key => ({
            title: key === "rawKg" ? "Số cân thực tế (kg)" : "Vụn nhôm (tùy chọn)",
            render: (_: unknown, row: StockCount) => <InputNumber
              aria-label={`${key === "rawKg" ? "Số cân" : "Vụn nhôm"} ${row.productName} ${row.categoryName}`}
              value={row[key]} min={0} precision={6} placeholder={key === "rawKg" ? "Nhập số cân" : "Có thể để trống"}
              onChange={value => setStockCounts(items => items?.map(item =>
                item.productId === row.productId && item.categoryId === row.categoryId ? {...item, [key]: value} : item))} />,
          })),
          { title: "Điều chỉnh (kg)", render: (_: unknown, row: StockCount) =>
            row.rawKg == null ? "—" : (row.rawKg - row.quantityBase).toLocaleString("vi-VN", {maximumFractionDigits: 6}) },
        ]} /> : <ResponsiveTable rowKey="categoryId" pagination={false} dataSource={counts} columns={[
        {title:"Loại nhôm", dataIndex:"category"},
        ...(["rawKg", "scrapKg"] as const).map((key) => ({title:key==="rawKg" ? "Nguyên liệu còn tồn (kg)" : "Vụn nhôm (không bắt buộc)", render: (_: unknown, row: Count) => <InputNumber aria-label={`${key === "rawKg" ? "Nguyên liệu còn tồn" : "Vụn nhôm"} ${row.category}`} min={0} precision={3} style={{ width: "100%", maxWidth: 180 }} placeholder={key === "scrapKg" ? "Có thể để trống" : "Nhập kg còn tồn"} value={row[key]} disabled={book.closed} onChange={value => setCounts(rows => rows.map(item => item.categoryId===row.categoryId ? {...item,[key]:value} : item))} />})),
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
        <Button type="primary" loading={saving} onClick={() => {
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
  }, [shop, working.year, session?.role]);
  function choose(action:"later"|"no"|"yes") {
    const key = "monthly-book-reminder:"+shop+":"+dayjs().format("YYYY-MM");
    localStorage.setItem(key, JSON.stringify(action==="later" ? {until:Date.now()+3*60*60*1000} : {dismissed:true}));
    setOpen(false);
    if (action==="yes") navigate("/chot-so-thang?month="+dayjs().format("YYYY-MM"));
  }
  return <Modal open={open} width="60vw" styles={{body:{padding:"24px 0"},wrapper:{minWidth:320}}} title="Hôm nay cuối tháng rồi, bạn có muốn chốt sổ theo tháng không?" closable={false} maskClosable={false} footer={<Space wrap><Button onClick={()=>choose("later")}>Nhắc tôi sau 3 tiếng</Button><Button onClick={()=>choose("no")}>Không</Button><Button type="primary" onClick={()=>choose("yes")}>Có, kiểm kê cuối tháng</Button></Space>}>
    Chọn Có để nhập số nhôm còn tồn và lưu sổ tháng {dayjs().format("MM/YYYY")}.
  </Modal>;
}
