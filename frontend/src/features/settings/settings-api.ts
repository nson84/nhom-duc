import { api } from "../../services/api-client";

export type ShopSettings = {
  shopName: string;
  shopAddress: string;
  shopPhone: string;
  backupTime: string;
  backupRetentionDays: number;
  backupDirectory: string;
};

type Envelope<T> = { success: boolean; data: T; message?: string };

export async function getSettings(): Promise<ShopSettings> {
  const response = await api.get<Envelope<ShopSettings>>("/api/v1/settings");
  return response.data.data;
}

export async function updateSettings(settings: ShopSettings): Promise<ShopSettings> {
  const response = await api.put<Envelope<ShopSettings>>("/api/v1/settings", settings);
  return response.data.data;
}
