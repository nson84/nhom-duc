import { printDate, printEscape as e, printMoney as money, printNumber as number, printPage } from "../../components/print-document";
import type { MonthlyAttendanceBoard, PayrollPeriod } from "./attendance-api";

const status: Record<string, string> = {
  UNMARKED: "Chưa chấm", PRESENT: "Đủ công", HALF_DAY: "Nửa công", ABSENT: "Vắng",
  PAID_LEAVE: "Nghỉ hưởng lương", UNPAID_LEAVE: "Nghỉ không lương", HOLIDAY: "Ngày lễ",
};

export function buildPayrollMonthHtml(period: PayrollPeriod, board: MonthlyAttendanceBoard) {
  const rows = [...period.details].sort((a, b) => a.fullName.localeCompare(b.fullName, "vi"));
  const body = `<h1>Bảng chấm công và tính lương tháng ${String(period.month).padStart(2, "0")}/${period.year}</h1>
    <p>Xưởng sản xuất · Trạng thái kỳ công: ${period.status === "CLOSED" ? "Đã chốt" : "Đang mở, số liệu tạm tính"}</p>
    <p class="muted">Tăng ca chỉ tính khi đã duyệt. Tiền ứng đang lưu theo tổng tháng, chưa có ngày ứng. Các khoản thưởng theo ngày ghi bên dưới từng nhân viên.</p>
    ${rows.map((wage) => {
      const employee = board.employees.find((item) => item.employeeId === wage.employeeId);
      const days = (employee?.days ?? []).filter((day) => day.status !== "UNMARKED" || day.bonusAmount > 0 || day.overtimes.length > 0);
      return `<section class="invoice"><h2>${e(wage.fullName)} · ${e(wage.employeeCode)}</h2>
        <table><thead><tr><th>Ngày</th><th>Chấm công</th><th>Sáng</th><th>Chiều</th><th>Công</th><th>Tăng ca được duyệt</th><th>Thưởng ngày / ghi chú</th></tr></thead><tbody>
        ${days.map((day) => {
          const overtime = day.overtimes.filter((item) => item.approved);
          return `<tr><td>${printDate(day.workDate)}</td><td>${e(status[day.status] ?? day.status)}</td><td>${day.morningPresent ? "Có" : "—"}</td><td>${day.afternoonPresent ? "Có" : "—"}</td><td>${number(day.creditedDays)}</td><td>${number(overtime.reduce((hours, item) => hours + item.overtimeHours, 0))} giờ · ${money(overtime.reduce((amount, item) => amount + item.amount, 0))}</td><td>${day.bonusAmount ? money(day.bonusAmount) : "—"}${day.bonusNotes ? ` · ${e(day.bonusNotes)}` : ""}${day.notes ? ` · ${e(day.notes)}` : ""}</td></tr>`;
        }).join("") || `<tr><td colspan="7">Chưa có ngày chấm công.</td></tr>`}</tbody></table>
        <h3>Chi tiết tính lương</h3><table><thead><tr><th>Khoản</th><th>Số công / giờ</th><th>Số tiền</th><th>Ghi chú</th></tr></thead><tbody>
        <tr><td>Lương cơ bản</td><td>${number(wage.creditedDays)} công</td><td>${money(wage.basePay)}</td><td>Ngày công trong tháng</td></tr>
        <tr><td>Tăng ca đã duyệt</td><td>${number(wage.overtimeHours ?? 0)} giờ</td><td>${money(wage.overtimePay)}</td><td>Chỉ gồm tăng ca đã duyệt</td></tr>
        <tr><td>Thưởng khác</td><td>—</td><td>${money(wage.bonusPay - (wage.holidayBonusPay ?? 0))}</td><td>Gồm thưởng ngày đã tính vào lương</td></tr>
        <tr><td>Thưởng lễ trả cùng lương</td><td>—</td><td>${money(wage.holidayBonusPay ?? 0)}</td><td>Chưa nhận trực tiếp</td></tr>
        <tr><td>Thưởng lễ đã nhận trực tiếp</td><td>—</td><td>${money(wage.holidayBonusReceived ?? 0)}</td><td>Không cộng lại vào thực lĩnh</td></tr>
        <tr><td>Phụ cấp</td><td>—</td><td>${money(wage.allowances)}</td><td></td></tr>
        <tr><td>Khấu trừ khác</td><td>—</td><td>−${money(wage.deductions)}</td><td></td></tr>
        <tr><td>Đã ứng lương</td><td>—</td><td>−${money(wage.advanceAmount)}</td><td>Hệ thống chưa lưu ngày ứng</td></tr>
        <tr class="total"><td>Thực lĩnh còn lại</td><td>—</td><td>${money(wage.totalPay)}</td><td>Đã trừ tiền ứng</td></tr></tbody></table></section>`;
    }).join("") || "<p>Chưa có bảng lương cho tháng này.</p>"}
    <h2>Tổng kết tháng</h2><table><thead><tr><th>Số nhân viên</th><th>Tổng công</th><th>Lương cơ bản</th><th>Tăng ca</th><th>Thưởng</th><th>Phụ cấp</th><th>Đã ứng</th><th>Thực lĩnh</th></tr></thead><tbody><tr class="total"><td>${rows.length}</td><td>${number(rows.reduce((sum, row) => sum + row.creditedDays, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.basePay, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.overtimePay, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.bonusPay, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.allowances, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.advanceAmount, 0))}</td><td>${money(rows.reduce((sum, row) => sum + row.totalPay, 0))}</td></tr></tbody></table>
    <p class="muted">Đã chi cho kỳ công: ${money(period.paidAmount)}. Tiền ứng là khoản đã trừ khi tính thực lĩnh; không cộng lại vào khoản đã chi.</p>`;
  return printPage(`Bảng chấm công và lương ${period.month}/${period.year}`, body, true);
}
