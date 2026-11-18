import { Alert, Button, Card, DatePicker, Space, Typography } from "antd";
import dayjs from "dayjs";
import { useRef, useState } from "react";
import { useShop } from "../shop/shop-store";
import { useWorkingYear } from "../dashboard/working-year";
import { toApiError } from "../../services/error-handler";
import { getAnnualBook, type AnnualBook } from "./annual-book-api";
import { buildAnnualBookHtml } from "./annual-book-print";

export function AnnualBookPage() {
  const currentShop = useShop();
  const workingYear = useWorkingYear(currentShop);
  const [year, setYear] = useState(workingYear.year);
  const [books, setBooks] = useState<AnnualBook[]>([]);
  const [html, setHtml] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const frame = useRef<HTMLIFrameElement>(null);

  async function prepare() {
    setLoading(true);
    setError(undefined);
    try {
      const result = [await getAnnualBook(year, currentShop)];
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
      <Typography.Paragraph>In thành một quyển: mỗi khách một mục chi tiết phiếu mua, số đã trả và nợ hiện tại; tiếp theo là sổ thu, sổ chi, phiếu nhập, tồn kho{currentShop === "MANUFACTURING" ? ", nhập nội bộ và công thợ" : ""}. Nợ tại ngày chốt được ghi riêng để đối chiếu.</Typography.Paragraph>
      <Space wrap>
        <DatePicker picker="year" value={dayjs(`${year}-01-01`)} allowClear={false} aria-label="Năm in sổ"
          onChange={(value) => { if (value) { setYear(value.year()); setHtml(""); } }} />
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
