import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

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
