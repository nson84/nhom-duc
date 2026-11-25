import { Button, DatePicker, Modal, message } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useState } from "react";
import { toApiError } from "../../services/error-handler";
import { editPaymentDate, type Payment, type StockDocument } from "./stock-api";

export function PaymentDateEditor({ document, payment, onSaved }: {
  document: StockDocument;
  payment: Payment;
  onSaved: (updated: StockDocument) => void;
}) {
  const [date, setDate] = useState<Dayjs | null>(null);
  const [saving, setSaving] = useState(false);
  return <>
    <Button size="small" color="orange" variant="solid" aria-label="Sửa ngày thanh toán" style={{ marginInlineStart: 8 }}
      onClick={() => setDate(dayjs(payment.paymentDate))}>Sửa</Button>
    <Modal open={date !== null} title="Sửa ngày thanh toán" okText="Lưu" cancelText="Hủy"
      confirmLoading={saving} onCancel={() => { if (!saving) setDate(null); }}
      onOk={async () => {
        if (!date || saving) return;
        setSaving(true);
        try {
          onSaved(await editPaymentDate(document.id, payment.id, date.format("YYYY-MM-DD")));
          setDate(null);
        } catch (error) {
          message.error(toApiError(error).message);
        } finally {
          setSaving(false);
        }
      }}>
      <DatePicker aria-label="Ngày thanh toán" allowClear={false} value={date} onChange={setDate}
        format="DD/MM/YYYY" disabledDate={(day) => day.isBefore(document.documentDate, "day") || day.isAfter(dayjs(), "day")} />
    </Modal>
  </>;
}
