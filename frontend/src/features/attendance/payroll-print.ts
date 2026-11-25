import { printDate, printEscape as e, printMoney as money, printNumber as number, printPage } from "../../components/print-document";
import type { MonthlyAttendanceBoard, PayrollPeriod } from "./attendance-api";

const status: Record<string, string> = {
  UNMARKED: "Chưa chấm", PRESENT: "Đủ công", HALF_DAY: "Nửa công", ABSENT: "Vắng",
  PAID_LEAVE: "Nghỉ hưởng lương", UNPAID_LEAVE: "Nghỉ không lương", HOLIDAY: "Ngày lễ",
};

function wageRow(label: string, amount: number, quantity = 0, unit = "") {
  if (!amount && !quantity) return "";
  return `<tr><td>${label}</td><td>${quantity ? `${number(quantity)} ${unit}` : "—"}</td><td>${money(amount)}</td></tr>`;
}

export function buildPayrollMonthHtml(period: PayrollPeriod, board: MonthlyAttendanceBoard) {
  const rows = [...period.details].sort((a, b) => a.fullName.localeCompare(b.fullName, "vi"));
  const body = `<h1>Bảng chấm công và tính lương tháng ${String(period.month).padStart(2, "0")}/${period.year}</h1>
    <p>Xưởng sản xuất · Trạng thái kỳ công: ${period.status === "CLOSED" ? "Đã chốt" : "Đang mở, số liệu tạm tính"}</p>
    ${rows.map((wage) => {
      const employee = board.employees.find((item) => item.employeeId === wage.employeeId);
      const days = (employee?.days ?? []).filter((day) => day.status !== "UNMARKED" || day.bonusAmount > 0 || day.overtimes.length > 0);
      return `<section class="invoice"><h2>${e(wage.fullName)} · ${e(wage.employeeCode)}</h2>
        ${days.length ? `<table><thead><tr><th>Ngày</th><th>Chấm công</th><th>Sáng</th><th>Chiều</th><th>Công</th><th>Tăng ca được duyệt</th><th>Thưởng ngày / ghi chú</th></tr></thead><tbody>
        ${days.map((day) => {
          const overtime = day.overtimes.filter((item) => item.approved);
          const sessions = overtime.filter((item) => item.payUnit === "SESSION").length;
          return `<tr><td>${printDate(day.workDate)}</td><td>${e(status[day.status] ?? day.status)}</td><td>${day.morningPresent ? "Có" : "—"}</td><td>${day.afternoonPresent ? "Có" : "—"}</td><td>${number(day.creditedDays)}</td><td>${number(overtime.reduce((hours, item) => hours + item.overtimeHours, 0))} giờ${sessions ? ` · ${sessions} buổi` : ""} · ${money(overtime.reduce((amount, item) => amount + item.amount, 0))}</td><td>${day.bonusAmount ? money(day.bonusAmount) : "—"}${day.bonusNotes ? ` · ${e(day.bonusNotes)}` : ""}${day.notes ? ` · ${e(day.notes)}` : ""}</td></tr>`;
        }).join("")}</tbody></table>` : ""}
        ${(wage.advances ?? []).length ? `<h3>Lịch sử ứng lương</h3><table><thead><tr><th>Ngày</th><th>Số tiền</th><th>Ghi chú</th></tr></thead><tbody>${wage.advances.map(entry => `<tr><td>${printDate(entry.date)}</td><td>${money(entry.amount)}</td><td>${e(entry.note || "")}</td></tr>`).join("")}</tbody></table>` : ""}<h3>Chi tiết tính lương</h3><table><thead><tr><th>Khoản</th><th>Số công / giờ</th><th>Số tiền</th></tr></thead><tbody>
        ${wageRow("Lương cơ bản", wage.basePay, wage.creditedDays, "công")}
        ${wageRow("Tăng ca đã duyệt", wage.overtimePay, wage.overtimeHours ?? 0, "giờ")}
        ${wageRow("Thưởng khác", wage.bonusPay - (wage.holidayBonusPay ?? 0))}
        ${wageRow("Thưởng lễ trả cùng lương", wage.holidayBonusPay ?? 0)}
        ${wageRow("Thưởng lễ đã nhận trực tiếp", wage.holidayBonusReceived ?? 0)}
        ${wageRow("Phụ cấp", wage.allowances)}
        ${wageRow("Khấu trừ khác", -wage.deductions)}
        ${wageRow("Đã ứng lương", -wage.advanceAmount)}
        <tr class="total"><td>Thực lĩnh còn lại</td><td>—</td><td>${money(wage.totalPay)}</td></tr></tbody></table></section>`;
    }).join("")}
    <h2>Tổng kết tháng</h2><table><thead><tr><th>Số nhân viên</th><th>Tổng công</th><th>Lương cơ bản</th><th>Tăng ca</th><th>Thưởng</th><th>Phụ cấp</th><th>Đã ứng</th><th>Thực lĩnh</th></tr></thead><tbody><tr class="total"><td>${rows.length}</td><td>${number(rows.reduce((sum, row) => sum + row.creditedDays, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.basePay, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.overtimePay, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.bonusPay, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.allowances, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.advanceAmount, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.totalPay, 0))}</td></tr></tbody></table>
    ${period.paidAmount ? `<p>Đã chi cho kỳ công: ${money(period.paidAmount)}</p>` : ""}`;
  return printPage(`Bảng chấm công và lương ${period.month}/${period.year}`, body, true);
}
