import { invoke, isTauri } from "@tauri-apps/api/core";

export async function savedPassword(account: string): Promise<string | null> {
  if (!isTauri()) return null;
  return invoke<string | null>("saved_password", { account });
}

export async function savePassword(account: string, password: string): Promise<void> {
  if (!isTauri()) return;
  await invoke("save_password", { account, password });
}

export async function offerBrowserPassword(account: string, password: string): Promise<void> {
  if (isTauri() || !("PasswordCredential" in window) || !navigator.credentials) return;
  const PasswordCredential = window.PasswordCredential as new (data: { id: string; password: string }) => Credential;
  await navigator.credentials.store(new PasswordCredential({ id: account, password }));
}
