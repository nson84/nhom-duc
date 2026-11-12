import { Alert, Button, Card, DatePicker, Radio, Space, Typography } from "antd";
import dayjs from "dayjs";
import { useRef, useState } from "react";
import { useSession } from "../auth/auth-store";
import { SHOP_LABELS, useShop, type ShopCode } from "../shop/shop-store";
import { toApiError } from "../../services/error-handler";
import { getAnnualBook, type AnnualBook } from "./annual-book-api";
import { buildAnnualBookHtml } from "./annual-book-print";

export function AnnualBookPage() {
  const session = useSession();
  const currentShop = useShop();
  const admin = session?.role === "ADMIN";
  const [year, setYear] = useState(dayjs().subtract(1, "year").year());
  const [selected, setSelected] = useState<"BOTH" | ShopCode>(admin ? "BOTH" : currentShop);
  const [books, setBooks] = useState<AnnualBook[]>([]);
  const [html, setHtml] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const frame = useRef<HTMLIFrameElement>(null);

  async function prepare() {
    setLoading(true);
    setError(undefined);
    try {
      const shops: ShopCode[] = selected === "BOTH" ? ["DISTRIBUTION", "MANUFACTURING"] : [selected];
      const result = await Promise.all(shops.map((shop) => getAnnualBook(year, shop)));
      setBooks(result);
      setHtml(buildAnnualBookHtml(result));
    } catch (reason) {
      setError(toApiError(reason).message);
    } finally {
      setLoading(false);
    }
  }

  return <div className="page-content">
    <div className="page-toolbar"><h2>Sổ cuối năm</h2></div>
    <Card>
      <Typography.Paragraph>In toàn bộ báo cáo thành một quyển: từng khách mua và các phiếu, công nợ cuối năm, sổ thu, sổ chi, công thợ và tồn kho. Mỗi sổ có trang bìa riêng.</Typography.Paragraph>
      <Space wrap>
        <DatePicker picker="year" value={dayjs(`${year}-01-01`)} allowClear={false} aria-label="Năm in sổ"
          onChange={(value) => { if (value) { setYear(value.year()); setHtml(""); } }} />
        {admin && <Radio.Group value={selected} onChange={(event) => { setSelected(event.target.value); setHtml(""); }}
          options={[{ label: "Cả hai sổ", value: "BOTH" }, ...(["DISTRIBUTION", "MANUFACTURING"] as ShopCode[]).map((code) => ({ label: SHOP_LABELS[code], value: code }))]} />}
        <Button type="primary" loading={loading} onClick={() => void prepare()}>Tạo bản xem trước</Button>
        {html && <Button onClick={() => frame.current?.contentWindow?.print()}>In / Lưu thành PDF</Button>}
      </Space>
      <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
        Bản in lấy dữ liệu tại lúc tạo; kiểm tra năm và sổ trước khi in. Trong hộp thoại in, chọn “Lưu thành PDF” hoặc “Microsoft Print to PDF”.
      </Typography.Paragraph>
    </Card>
    {error && <Alert type="error" showIcon message={error} style={{ marginTop: 16 }} />}
    {books.some((book) => !book.closed) && <Alert type="warning" showIcon style={{ marginTop: 16 }}
      message="Có sổ chưa chốt năm" description="Tồn kho, công nợ và lãi của sổ chưa chốt có thể thay đổi khi bổ sung phiếu hoặc kiểm kê." />}
    {html && <Card title={`Xem trước quyển sổ năm ${year}`} style={{ marginTop: 16 }}>
      <iframe ref={frame} title={`Sổ cuối năm ${year}`} srcDoc={html} style={{ width: "100%", height: "72vh", border: "1px solid #d9d9d9" }} />
    </Card>}
  </div>;
}
