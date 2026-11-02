/** Lưu nháp form trong tab hiện tại — F5 vẫn khôi phục được. */

export type SessionDraftEnvelope<T> = {
  targetId: number | "new";
  values: T;
  savedAt: number;
};

export function readSessionDraft<T>(key: string): SessionDraftEnvelope<T> | null {
  try {
    const raw = sessionStorage.getItem(key);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as SessionDraftEnvelope<T>;
    if (!parsed || typeof parsed !== "object" || parsed.values == null) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function writeSessionDraft<T>(key: string, draft: SessionDraftEnvelope<T>): void {
  try {
    sessionStorage.setItem(key, JSON.stringify(draft));
  } catch {
    /* quota / private mode — bỏ qua */
  }
}

export function clearSessionDraft(key: string): void {
  try {
    sessionStorage.removeItem(key);
  } catch {
    /* ignore */
  }
}

/** Cảnh báo khi F5 / đóng tab nếu còn bản nháp. */
export function bindDraftUnloadGuard(hasDraft: () => boolean): () => void {
  const onBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!hasDraft()) return;
    event.preventDefault();
    event.returnValue = "";
  };
  window.addEventListener("beforeunload", onBeforeUnload);
  return () => window.removeEventListener("beforeunload", onBeforeUnload);
}

export const PRODUCT_FORM_DRAFT_KEY = "aluminum.draft.product";
export const purchaseFormDraftKey = (kind: "PURCHASE" | "SALE") =>
  kind === "PURCHASE" ? "aluminum.draft.purchase" : "aluminum.draft.sale";
