import { Alert, Button, Card, Col, DatePicker, InputNumber, Modal, Row, Space, Statistic, Typography, message } from "antd";
import dayjs from "dayjs";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../../services/api-client";
import { toApiError } from "../../services/error-handler";
import { ResponsiveTable } from "../../components/PagedTable";
import { useShop } from "../shop/shop-store";
import { useWorkingYear } from "./working-year";
import { useSession } from "../auth/auth-store";
import { formatMoney } from "../../utils/money";
import { OpeningInventory } from "../stock/OpeningInventory";

type Count = { categoryId: number; category: string; rawKg: number | null; scrapKg: number | null; scrapValue: number | null; rawValue: number | null };
type Book = { year: number; month: string; from: string; to: string; counts: Count[]; sales: number; purchases: number; expenses: number; closed: boolean; openingValue: number | null; endingValue: number | null; internalOut: number | null; profit: number | null };

export function MonthlyBookPage() {
  const shop = useShop();
  const working = useWorkingYear(shop);
  const [params] = useSearchParams();
  const [month, setMonth] = useState(params.get("month") ?? working.month ?? dayjs().format("YYYY-MM"));
  const [book, setBook] = useState<Book>();
  const [counts, setCounts] = useState<Count[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setBook(undefined);
    setError("");
    api.get<{data: Book}>("/api/v1/dashboard/monthly-book", { params: { year: working.year, month }, signal: controller.signal })
      .then(({data}) => { if (!controller.signal.aborted) { setBook(data.data); setCounts(data.data.counts.map(row => ({...row, rawKg: data.data.closed ? row.rawKg : null, scrapKg: data.data.closed ? row.scrapKg : null}))); } })
      .catch(error => { if (!controller.signal.aborted) setError(toApiError(error).message); });
    return () => controller.abort();
  }, [shop, working.year, month]);
  async function close() {
    setSaving(true);
    try {
      const result = await api.post<{data: Book}>("/api/v1/dashboard/monthly-book", {year: working.year, month, counts: counts.map(row => ({categoryId: row.categoryId, category: row.category, rawKg: row.rawKg ?? 0, scrapKg: row.scrapKg, scrapValue: null}))});
      setBook(result.data.data);
      setCounts(result.data.data.counts);
      message.success("Đã lưu sổ tháng");
    } catch (error) { setError(toApiError(error).message); throw error; }
    finally { setSaving(false); }
  }
  return <Card title="Kiểm kê và chốt sổ tháng" extra={<DatePicker picker="month" format="MM/YYYY" allowClear={false} value={dayjs(month+"-01")} onChange={value => value && setMonth(value.format("YYYY-MM"))} />}>
    {error && <Alert type="error" showIcon message={error} style={{marginBottom:16}} />}
    {book && <>
      <p>Kỳ {working.year} · {dayjs(book.from).format("DD/MM/YYYY")} – {dayjs(book.to).format("DD/MM/YYYY")} · {book.closed ? "Đã lưu bản chốt" : "Chưa chốt sổ tháng"}</p>
      <OpeningInventory year={working.year} month={month} />
      <Alert type="info" showIcon message="Chỉ cần nhập kg nguyên liệu còn trong kho. Vụn nhôm có thể để trống; tháng này tạm ghi 0 kg và vẫn được kiểm kê lại khi chốt năm." />
      <p style={{ margin: "20px 0 12px" }}>Giá trị tồn đầu tháng: <Typography.Text strong>{formatMoney(book.openingValue)}</Typography.Text></p>
      <p style={{ marginBottom: 20 }}>Các giá trị tiền được lấy tự động. Không cần nhập tiền tồn hoặc tiền vụn ở đây; cuối năm vẫn kiểm kê vụn và tính lãi theo số chốt năm.</p>
      <ResponsiveTable rowKey="categoryId" pagination={false} dataSource={counts} columns={[
        {title:"Loại nhôm", dataIndex:"category"},
        ...(["rawKg", "scrapKg"] as const).map((key) => ({title:key==="rawKg" ? "Nguyên liệu còn tồn (kg)" : "Vụn nhôm (không bắt buộc)", render: (_: unknown, row: Count) => <InputNumber aria-label={`${key === "rawKg" ? "Nguyên liệu còn tồn" : "Vụn nhôm"} ${row.category}`} min={0} precision={3} style={{ width: "100%", maxWidth: 180 }} placeholder={key === "scrapKg" ? "Có thể để trống" : "Nhập kg còn tồn"} value={row[key]} disabled={book.closed} onChange={value => setCounts(rows => rows.map(item => item.categoryId===row.categoryId ? {...item,[key]:value} : item))} />})),
      ]} />
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
      {!book.closed && <div><Button type="primary" loading={saving} onClick={() => Modal.confirm({title:"Xác nhận kiểm kê và chốt sổ tháng?", content:"Các ô để trống sẽ lưu là 0 kg. Hãy kiểm tra đủ các loại trước khi xác nhận.", okText:"Xác nhận chốt sổ", cancelText:"Quay lại", autoFocusButton:"cancel", onOk:close})}>Chốt sổ tháng</Button></div>}
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
