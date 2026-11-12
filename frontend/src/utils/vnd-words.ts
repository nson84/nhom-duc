/** Đọc số tiền VND thành chữ tiếng Việt (ví dụ: Một trăm nghìn đồng). */
const ONES = [
  "không",
  "một",
  "hai",
  "ba",
  "bốn",
  "năm",
  "sáu",
  "bảy",
  "tám",
  "chín",
];

function readTriple(n: number, full: boolean): string {
  const hundreds = Math.floor(n / 100);
  const tens = Math.floor((n % 100) / 10);
  const ones = n % 10;
  const parts: string[] = [];

  if (hundreds > 0 || full) {
    if (hundreds > 0) {
      parts.push(`${ONES[hundreds]} trăm`);
    } else if (full && (tens > 0 || ones > 0)) {
      parts.push("không trăm");
    }
  }

  if (tens > 1) {
    parts.push(`${ONES[tens]} mươi`);
    if (ones === 1) parts.push("mốt");
    else if (ones === 4) parts.push("tư");
    else if (ones === 5) parts.push("lăm");
    else if (ones > 0) parts.push(ONES[ones]);
  } else if (tens === 1) {
    parts.push("mười");
    if (ones === 5) parts.push("lăm");
    else if (ones > 0) parts.push(ONES[ones]);
  } else if (ones > 0) {
    if (full && hundreds > 0) parts.push("lẻ");
    parts.push(ONES[ones]);
  }

  return parts.join(" ");
}

export function vndToWords(amount: number): string {
  const rounded = Math.round(Math.abs(amount));
  if (!Number.isFinite(rounded) || rounded === 0) {
    return "Không đồng";
  }

  const scales = ["", "nghìn", "triệu", "tỷ", "nghìn tỷ"];
  const groups: number[] = [];
  let remaining = rounded;
  while (remaining > 0) {
    groups.push(remaining % 1000);
    remaining = Math.floor(remaining / 1000);
  }

  const parts: string[] = [];
  for (let i = groups.length - 1; i >= 0; i -= 1) {
    const value = groups[i];
    if (value === 0) continue;
    const full = i < groups.length - 1;
    const text = readTriple(value, full);
    if (!text) continue;
    parts.push(scales[i] ? `${text} ${scales[i]}` : text);
  }

  const body = parts.join(" ").replace(/\s+/g, " ").trim();
  const capitalized = body.charAt(0).toUpperCase() + body.slice(1);
  return `${amount < 0 ? "Âm " : ""}${capitalized} đồng`;
}
