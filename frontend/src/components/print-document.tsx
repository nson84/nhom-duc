import { PrinterOutlined } from "@ant-design/icons";
import { Button, Modal, Space } from "antd";
import { useRef } from "react";

export const printEscape = (value: unknown) => String(value ?? "").replace(/[&<>"']/g, (char) => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
})[char] ?? char);

export const printMoney = (value: number) => `${new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 0 }).format(value)} đ`;
export const printNumber = (value: number) => new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 }).format(value);
export const printDate = (value: string) => `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)}${value.length >= 16 ? ` ${value.slice(11, 16)}` : ""}`;

export function printPage(title: string, body: string, landscape = false) {
  return `<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>${printEscape(title)}</title><style>
    @page{size:A4 ${landscape ? "landscape" : "portrait"};margin:15mm}*{box-sizing:border-box}body{font:11pt Arial,sans-serif;color:#142238;margin:0;line-height:1.45}
    h1{font-size:18pt;margin:0 0 4mm}h2{font-size:13pt;margin:9mm 0 3mm;border-bottom:1px solid #64748b;padding-bottom:2mm}
    h3{font-size:11pt;margin:5mm 0 2mm}p{margin:2mm 0}table{width:100%;border-collapse:collapse;margin:3mm 0 5mm;table-layout:fixed}
    th,td{border:1px solid #9aa9bb;padding:2mm;vertical-align:top;overflow-wrap:anywhere;text-align:left}th{background:#e8eef5}
    .invoice{break-inside:avoid-page;margin:6mm 0;padding:3mm;border:1px solid #9aa9bb}
    .total{font-weight:bold;background:#e8eef5}.muted{color:#52657d}.warn{color:#9b1c1c;font-weight:bold}
    @media screen{body{max-width:${landscape ? "297mm" : "210mm"};margin:auto;padding:15mm;background:white}}
  </style></head><body>${body}</body></html>`;
}

export function PrintDocument({ title, html, onClose }: { title: string; html: string; onClose: () => void }) {
  const frame = useRef<HTMLIFrameElement>(null);
  return <Modal open width={980} title={title} onCancel={onClose} footer={<Space><Button onClick={onClose}>Đóng</Button><Button type="primary" icon={<PrinterOutlined />} onClick={() => frame.current?.contentWindow?.print()}>In / Lưu PDF</Button></Space>}>
    <iframe ref={frame} title={title} srcDoc={html} style={{ width: "100%", height: "65vh", border: "1px solid var(--color-border)", borderRadius: 8, background: "white" }} />
  </Modal>;
}
