import { Alert, Button, Card, Space, Typography } from "antd";
import { useRef, useState } from "react";
import { useShop } from "../shop/shop-store";
import { useWorkingYear } from "../dashboard/working-year";
import { useWorkingPeriodInfo } from "../dashboard/working-period";
import { toApiError } from "../../services/error-handler";
import { getAnnualBook, type AnnualBook } from "./annual-book-api";
import { buildAnnualBookHtml } from "./annual-book-print";

export function AnnualBookPage() {
  const currentShop = useShop();
  const { year } = useWorkingYear(currentShop);
  return <AnnualBookForPeriod key={`${currentShop}:${year}`} year={year} currentShop={currentShop} />;
}

function AnnualBookForPeriod({ year, currentShop }: { year: number; currentShop: ReturnType<typeof useShop> }) {
  const periodInfo = useWorkingPeriodInfo(year);
  const [books, setBooks] = useState<AnnualBook[]>([]);
  const [html, setHtml] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const frame = useRef<HTMLIFrameElement>(null);

  async function prepare() {
    setLoading(true);
    setError(undefined);
    try {
      const book = await getAnnualBook(year, currentShop);
      const result = [{ ...book, periodName: /^Năm \d{4}$/i.test(book.periodName.trim()) ? periodInfo.name : book.periodName }];
      setBooks(result);
      setHtml(buildAnnualBookHtml(result));
    } catch (reason) {
      setError(toApiError(reason).message);
    } finally {
      setLoading(false);
    }
  }

  return <div className="page-content">
    <div className="page-toolbar"><h2>Sổ cuối kỳ</h2></div>
    <Card>
      <Typography.Paragraph>In thành một quyển: mỗi khách một mục chi tiết phiếu mua, số đã trả và nợ hiện tại; tiếp theo là sổ thu, sổ chi, phiếu nhập, tồn kho{currentShop === "MANUFACTURING" ? ", nhập nội bộ và công thợ" : ""}. Nợ tại ngày chốt được ghi riêng để đối chiếu.</Typography.Paragraph>
      <Space wrap>
        <Typography.Text>{periodInfo.label}</Typography.Text>
        <Button type="primary" loading={loading} onClick={() => void prepare()}>Tạo bản xem trước</Button>
        {html && <Button onClick={() => frame.current?.contentWindow?.print()}>In / Lưu thành PDF</Button>}
      </Space>
      <Typography.Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0 }}>
        Bản in lấy dữ liệu tại lúc tạo. Để in kỳ khác, đổi kỳ ở Tổng quan. Trong hộp thoại in, chọn “Lưu thành PDF” hoặc “Microsoft Print to PDF”.
      </Typography.Paragraph>
    </Card>
    {error && <Alert type="error" showIcon message={error} style={{ marginTop: 16 }} />}
    {books.some((book) => !book.closed) && <Alert type="warning" showIcon style={{ marginTop: 16 }}
      message="Kỳ chưa chốt" description="Tồn kho, công nợ và lãi có thể thay đổi khi bổ sung phiếu hoặc kiểm kê." />}
    {html && <Card title={`Xem trước sổ · ${periodInfo.name}`} style={{ marginTop: 16 }}>
      <iframe ref={frame} title="Sổ cuối kỳ" srcDoc={html} style={{ width: "100%", height: "72vh", border: "1px solid #d9d9d9" }} />
    </Card>}
  </div>;
}
