import QRCode from "qrcode";

/** Ngân hàng hỗ trợ VietQR (mã BIN do NAPAS cấp). */
export const VIETQR_BANKS: { bin: string; name: string }[] = [
  { bin: "970436", name: "Vietcombank" },
  { bin: "970415", name: "VietinBank" },
  { bin: "970418", name: "BIDV" },
  { bin: "970405", name: "Agribank" },
  { bin: "970407", name: "Techcombank" },
  { bin: "970422", name: "MB Bank" },
  { bin: "970416", name: "ACB" },
  { bin: "970432", name: "VPBank" },
  { bin: "970423", name: "TPBank" },
  { bin: "970403", name: "Sacombank" },
  { bin: "970437", name: "HDBank" },
  { bin: "970441", name: "VIB" },
  { bin: "970443", name: "SHB" },
  { bin: "970431", name: "Eximbank" },
  { bin: "970426", name: "MSB" },
  { bin: "970448", name: "OCB" },
  { bin: "970440", name: "SeABank" },
  { bin: "970449", name: "LPBank" },
  { bin: "970428", name: "Nam A Bank" },
  { bin: "970409", name: "Bac A Bank" },
  { bin: "970427", name: "VietABank" },
  { bin: "970425", name: "ABBANK" },
  { bin: "970412", name: "PVcomBank" },
  { bin: "970452", name: "Kienlongbank" },
  { bin: "970438", name: "BaoViet Bank" },
  { bin: "970400", name: "Saigonbank" },
  { bin: "970419", name: "NCB" },
  { bin: "970433", name: "Vietbank" },
];

export function bankName(bin: string | null | undefined): string {
  return VIETQR_BANKS.find((b) => b.bin === bin)?.name ?? (bin || "");
}

/** Nội dung chuyển khoản tối đa 50 ký tự, không dấu — nhiều app ngân hàng cắt hoặc từ chối nếu dài hơn. */
export const TRANSFER_NOTE_MAX = 50;

export function toTransferNote(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .replace(/[^0-9A-Za-z \-.]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, TRANSFER_NOTE_MAX)
    .trim();
}

function tlv(id: string, value: string): string {
  return `${id}${String(value.length).padStart(2, "0")}${value}`;
}

function crc16(payload: string): string {
  let crc = 0xffff;
  for (let i = 0; i < payload.length; i++) {
    crc ^= payload.charCodeAt(i) << 8;
    for (let bit = 0; bit < 8; bit++) {
      crc = crc & 0x8000 ? (crc << 1) ^ 0x1021 : crc << 1;
      crc &= 0xffff;
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, "0");
}

/** Chuỗi VietQR (chuẩn EMVCo / NAPAS 247) chuyển khoản vào tài khoản, có sẵn số tiền và nội dung. */
export function buildVietQrPayload(params: {
  bankBin: string;
  accountNo: string;
  amount: number;
  note: string;
}): string {
  const beneficiary = tlv("00", params.bankBin) + tlv("01", params.accountNo);
  const merchant = tlv("00", "A000000727") + tlv("01", beneficiary) + tlv("02", "QRIBFTTA");
  const amount = Math.round(params.amount);
  const body =
    tlv("00", "01") +
    tlv("01", amount > 0 ? "12" : "11") +
    tlv("38", merchant) +
    tlv("53", "704") +
    (amount > 0 ? tlv("54", String(amount)) : "") +
    tlv("58", "VN") +
    (params.note ? tlv("62", tlv("08", params.note)) : "") +
    "6304";
  return body + crc16(body);
}

export async function vietQrDataUrl(payload: string): Promise<string> {
  return QRCode.toDataURL(payload, { errorCorrectionLevel: "M", margin: 1, width: 360 });
}
