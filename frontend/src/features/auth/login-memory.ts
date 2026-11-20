type LoginAccount = "admin" | "phanphoi" | "xuong";
const KEY = "aluminum.last-login";
// Only the account is persisted. The password lives for this tab's lifetime.
let lastPassword = "";
let passwordAccount: LoginAccount | null = null;

export function getLastLogin(): { username: LoginAccount; password: string } {
  let saved: string | null = null;
  try { saved = localStorage.getItem(KEY); } catch { /* Storage can be disabled. */ }
  const username = saved === "phanphoi" || saved === "xuong" ? saved : "admin";
  return { username, password: passwordForAccount(username) };
}

export function passwordForAccount(username: LoginAccount): string {
  return username === passwordAccount ? lastPassword : "";
}

export function rememberLogin(username: LoginAccount, password: string) {
  passwordAccount = username;
  lastPassword = password;
  rememberAccount(username);
}

export function rememberAccount(username: string) {
  if (username !== "admin" && username !== "phanphoi" && username !== "xuong") return;
  try { localStorage.setItem(KEY, username); } catch { /* Login still succeeds. */ }
}

export function forgetLoginPassword() {
  lastPassword = "";
  passwordAccount = null;
}
