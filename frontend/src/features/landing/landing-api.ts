import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

export type LeadInput = {
  fullName: string;
  phone: string;
  email?: string;
  message?: string;
  requestType: string;
  /** Trường bẫy spam, người thật luôn để trống. */
  website?: string;
  sourcePage?: string;
};

export type Lead = {
  id: number;
  fullName: string;
  phone: string;
  email: string | null;
  message: string | null;
  requestType: string;
  status: "NEW" | "CONTACTED" | "CLOSED";
  internalNote: string | null;
  contactedAt: string | null;
  contactedBy: string | null;
  sourcePage: string | null;
  createdAt: string;
};

export type LeadSummary = {
  newCount: number;
  contactedCount: number;
  closedCount: number;
  todayCount: number;
};

export const requestTypeLabels: Record<string, string> = {
  BAO_GIA: "Báo giá",
  TU_VAN_KY_THUAT: "Tư vấn kỹ thuật",
  DAT_HANG: "Đặt hàng",
  KHAC: "Khác",
};

export const leadStatusLabels: Record<string, { text: string; color: string }> = {
  NEW: { text: "Mới", color: "gold" },
  CONTACTED: { text: "Đã gọi", color: "blue" },
  CLOSED: { text: "Đã xử lý", color: "green" },
};

/** Công khai — khách gửi form liên hệ từ landing page, không cần đăng nhập. */
export async function submitLead(input: LeadInput): Promise<void> {
  await api.post<Envelope<{ id: number }>>("/api/v1/public/leads", input);
}

export async function listLeads(params: { status?: string; q?: string }): Promise<Lead[]> {
  return (await api.get<Envelope<Lead[]>>("/api/v1/leads", { params })).data.data;
}

export async function getLeadSummary(): Promise<LeadSummary> {
  return (await api.get<Envelope<LeadSummary>>("/api/v1/leads/summary")).data.data;
}

export async function updateLead(
  id: number,
  input: { status: string; internalNote?: string },
): Promise<Lead> {
  return (await api.put<Envelope<Lead>>(`/api/v1/leads/${id}`, input)).data.data;
}

export type LandingStrength = { title: string; text: string };

/** Chữ từng phần trên trang giới thiệu — Admin sửa; khách chỉ đọc qua shop-info. */
export type LandingContent = {
  heroTitle: string;
  heroSubtitle: string;
  ctaHeader: string;
  ctaPrimary: string;
  categoriesTitle: string;
  contactTitle: string;
  contactDescription: string;
  privacyNote: string;
  formSubmitLabel: string;
  formSuccessTitle: string;
  formSuccessDescription: string;
  formAgainLabel: string;
  strengths: LandingStrength[];
};

export async function getLandingContent(): Promise<LandingContent> {
  return (await api.get<Envelope<LandingContent>>("/api/v1/landing-content")).data.data;
}

export async function updateLandingContent(input: LandingContent): Promise<LandingContent> {
  return (await api.put<Envelope<LandingContent>>("/api/v1/landing-content", input)).data.data;
}
