import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

export type PayRate = {
  id: number;
  employeeId: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  dailyRate: number;
  standardHoursPerDay: number;
  overtimeWeekdayRate: number;
  overtimeRestdayRate?: number;
  overtimeHolidayRate?: number;
};

export type Employee = {
  id: number;
  employeeCode: string;
  fullName: string;
  phone: string | null;
  address: string | null;
  hireDate: string | null;
  terminationDate: string | null;
  active: boolean;
  notes: string | null;
  currentPayRate: PayRate | null;
};

export type EmployeeInput = {
  fullName: string;
  phone: string;
  address?: string;
  hireDate?: string;
  terminationDate?: string;
  active: boolean;
  notes?: string;
  initialDailyRate?: number;
  initialOvertimeWeekdayRate?: number;
  initialOvertimeRestdayRate?: number;
  initialOvertimeHolidayRate?: number;
};

export type PayRateInput = {
  effectiveFrom: string;
  effectiveTo?: string;
  dailyRate: number;
  standardHoursPerDay: number;
  overtimeWeekdayRate: number;
  overtimeRestdayRate?: number;
  overtimeHolidayRate?: number;
};

export type HolidayKind = "SHOP_CLOSED" | "NATIONAL_HOLIDAY" | "CATHOLIC_HOLIDAY";

export type Holiday = {
  id: number;
  holidayDate: string;
  name: string;
  holidayType: HolidayKind;
  paid: boolean;
  notes: string | null;
  dayPay: number;
  bonusAmount: number;
  receivedEmployeeIds: number[];
};

export type AttendancePolicy = {
  id: number;
  effectiveFrom: string;
  effectiveTo: string | null;
  standardStartTime: string;
  standardEndTime: string;
  breakMinutes: number;
  lateGraceMinutes: number;
  earlyLeaveGraceMinutes: number;
  roundingMinutes: number;
  halfDayThresholdHours: number;
  fullDayCreditedDays: number;
  halfDayCreditedDays: number;
  overnightShiftAllowed: boolean;
  notes: string | null;
};

export type OvertimeRecord = {
  id: number;
  attendanceId: number;
  overtimeType: "WEEKDAY" | "RESTDAY" | "HOLIDAY";
  startAt: string | null;
  endAt: string | null;
  overtimeHours: number;
  hourlyRateSnapshot: number;
  amount: number;
  approved: boolean;
  notes: string | null;
};

export type AttendanceDay = {
  id: number | null;
  employeeId: number;
  workDate: string;
  status: "UNMARKED" | "PRESENT" | "HALF_DAY" | "ABSENT" | "PAID_LEAVE" | "UNPAID_LEAVE" | "HOLIDAY";
  morningPresent: boolean;
  afternoonPresent: boolean;
  clockIn: string | null;
  clockOut: string | null;
  workedHours: number;
  creditedDays: number;
  lateMinutes: number;
  earlyLeaveMinutes: number;
  notes: string | null;
  dailyRateSnapshot: number;
  bonusAmount: number;
  bonusNotes: string | null;
  locked: boolean;
  overtimes: OvertimeRecord[];
};

export type EmployeeMonthlyRow = {
  employeeId: number;
  employeeCode: string;
  fullName: string;
  active: boolean;
  currentDailyRate: number;
  totalCreditedDays: number;
  totalWorkedHours: number;
  totalLateMinutes: number;
  totalEarlyLeaveMinutes: number;
  approvedOvertimeHours: number;
  approvedOvertimePay: number;
  days: AttendanceDay[];
};

export type MonthlyAttendanceBoard = {
  year: number;
  month: number;
  daysInMonth: number;
  periodClosed: boolean;
  policy: AttendancePolicy;
  holidays: Holiday[];
  employees: EmployeeMonthlyRow[];
  lockedDates: string[];
};

export type PayrollDetail = {
  id: number;
  employeeId: number;
  employeeCode: string;
  fullName: string;
  creditedDays: number;
  basePay: number;
  overtimePay: number;
  bonusPay: number;
  allowances: number;
  deductions: number;
  advanceAmount: number;
  overtimeHours: number | null;
  holidayBonusPay: number;
  holidayBonusReceived: number | null;
  totalPay: number;
};

export type PayrollPeriod = {
  id: number;
  year: number;
  month: number;
  status: "OPEN" | "CLOSED";
  closedAt: string | null;
  reopenedAt: string | null;
  reopenReason: string | null;
  notes: string | null;
  totalBasePay: number;
  totalOvertimePay: number;
  totalAllowances: number;
  totalDeductions: number;
  grandTotalPay: number;
  paidAmount: number;
  details: PayrollDetail[];
};

export async function listEmployees(params: { q?: string; active?: boolean }): Promise<Employee[]> {
  return (await api.get<Envelope<Employee[]>>("/api/v1/employees", { params })).data.data;
}

export async function saveEmployee(id: number | undefined, input: EmployeeInput): Promise<Employee> {
  const response = id
    ? await api.put<Envelope<Employee>>(`/api/v1/employees/${id}`, input)
    : await api.post<Envelope<Employee>>("/api/v1/employees", input);
  return response.data.data;
}

export async function listPayRates(employeeId: number): Promise<PayRate[]> {
  return (await api.get<Envelope<PayRate[]>>(`/api/v1/employees/${employeeId}/pay-rates`)).data.data;
}

export async function deletePayRate(employeeId: number, rateId: number): Promise<void> {
  await api.delete(`/api/v1/employees/${employeeId}/pay-rates/${rateId}`);
}

export async function addPayRate(employeeId: number, input: PayRateInput): Promise<PayRate> {
  return (await api.post<Envelope<PayRate>>(`/api/v1/employees/${employeeId}/pay-rates`, input)).data.data;
}

export async function getMonthlyBoard(year: number, month: number): Promise<MonthlyAttendanceBoard> {
  return (await api.get<Envelope<MonthlyAttendanceBoard>>("/api/v1/attendance/monthly", { params: { year, month } })).data.data;
}

export async function saveAttendanceDay(
  employeeId: number,
  date: string,
  input: { status: AttendanceDay["status"]; morningPresent?: boolean; afternoonPresent?: boolean; clockIn?: string | null; clockOut?: string | null; notes?: string },
): Promise<AttendanceDay> {
  return (await api.put<Envelope<AttendanceDay>>(`/api/v1/attendance/${employeeId}/${date}`, input)).data.data;
}

export async function bulkSaveAttendance(
  items: { employeeId: number; workDate: string; status: AttendanceDay["status"]; clockIn?: string | null; clockOut?: string | null; notes?: string }[],
): Promise<AttendanceDay[]> {
  return (await api.post<Envelope<AttendanceDay[]>>("/api/v1/attendance/bulk", { items })).data.data;
}

export async function saveOvertime(
  attendanceId: number,
  overtimeId: number | undefined,
  input: { overtimeHours: number; approved: boolean; notes?: string },
): Promise<AttendanceDay> {
  const response = overtimeId
    ? await api.put<Envelope<AttendanceDay>>(`/api/v1/overtime/${overtimeId}`, input)
    : await api.post<Envelope<AttendanceDay>>(`/api/v1/attendance/${attendanceId}/overtime`, input);
  return response.data.data;
}

export async function saveAttendanceBonus(
  attendanceId: number,
  input: { amount: number; notes?: string },
): Promise<AttendanceDay> {
  return (await api.put<Envelope<AttendanceDay>>(`/api/v1/attendance/${attendanceId}/bonus`, input)).data.data;
}

export async function listHolidays(year?: number): Promise<Holiday[]> {
  return (await api.get<Envelope<Holiday[]>>("/api/v1/holidays", { params: { year } })).data.data;
}

export async function saveHoliday(
  id: number | undefined,
  input: { holidayDate: string; name: string; holidayType: Holiday["holidayType"]; paid: boolean; notes?: string; dayPay?: number; bonusAmount: number; receivedEmployeeIds: number[] },
): Promise<Holiday> {
  const response = id
    ? await api.put<Envelope<Holiday>>(`/api/v1/holidays/${id}`, input)
    : await api.post<Envelope<Holiday>>("/api/v1/holidays", input);
  return response.data.data;
}

export async function saveDayKind(input: {
  holidayDate: string;
  holidayType: HolidayKind | null;
  dayPay: number;
}): Promise<Holiday | null> {
  return (await api.put<Envelope<Holiday | null>>("/api/v1/attendance/day-kind", input)).data.data;
}

export async function saveRollCall(input: {
  workDate: string;
  items: { employeeId: number; morningPresent: boolean; afternoonPresent: boolean; overtimeHours: number }[];
}): Promise<void> {
  await api.post("/api/v1/attendance/roll-call", input);
}

export async function unlockRollCall(workDate: string): Promise<void> {
  await api.post(`/api/v1/attendance/roll-call/${workDate}/unlock`);
}

export async function saveAttendancePolicy(input: Omit<AttendancePolicy, "id" | "effectiveTo">): Promise<AttendancePolicy> {
  return (await api.post<Envelope<AttendancePolicy>>("/api/v1/attendance/policy", input)).data.data;
}

export async function createOrGetPayrollPeriod(year: number, month: number, notes?: string): Promise<PayrollPeriod> {
  return (await api.post<Envelope<PayrollPeriod>>("/api/v1/payroll/periods", { year, month, notes })).data.data;
}

export async function calculatePayroll(periodId: number): Promise<PayrollPeriod> {
  return (await api.post<Envelope<PayrollPeriod>>(`/api/v1/payroll/periods/${periodId}/calculate`)).data.data;
}

export async function adjustPayrollDetail(
  periodId: number,
  detailId: number,
  input: { allowances: number; advanceAmount: number },
): Promise<PayrollPeriod> {
  return (await api.put<Envelope<PayrollPeriod>>(`/api/v1/payroll/periods/${periodId}/details/${detailId}`, input)).data.data;
}

export async function closePayrollPeriod(periodId: number): Promise<PayrollPeriod> {
  return (await api.post<Envelope<PayrollPeriod>>(`/api/v1/payroll/periods/${periodId}/close`)).data.data;
}

export async function reopenPayrollPeriod(periodId: number, reason: string): Promise<PayrollPeriod> {
  return (await api.post<Envelope<PayrollPeriod>>(`/api/v1/payroll/periods/${periodId}/reopen`, { reason })).data.data;
}

export async function payPayrollPeriod(
  periodId: number,
  input: { amount: number; description?: string; idempotencyKey: string },
): Promise<PayrollPeriod> {
  return (await api.post<Envelope<PayrollPeriod>>(`/api/v1/payroll/periods/${periodId}/pay`, input)).data.data;
}
