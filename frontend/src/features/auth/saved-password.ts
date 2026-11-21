import { invoke, isTauri } from "@tauri-apps/api/core";

export async function savedPassword(account: string): Promise<string | null> {
  if (!isTauri()) return null;
  return invoke<string | null>("saved_password", { account });
}

export async function savePassword(account: string, password: string): Promise<void> {
  if (!isTauri()) return;
  await invoke("save_password", { account, password });
}
