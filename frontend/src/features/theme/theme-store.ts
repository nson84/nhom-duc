import { useEffect, useState } from "react";
import { applyPaletteToDom, paletteFor, type AppThemeMode, type ThemePalette } from "./palette";

export type { AppThemeMode } from "./palette";

const KEY = "aluminum-theme";
const EVENT = "aluminum-theme";

function systemMode(): AppThemeMode {
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

/** Đã lưu → dùng; lần đầu → theo hệ điều hành. */
function resolve(): AppThemeMode {
  const raw = localStorage.getItem(KEY);
  if (raw === "dark" || raw === "light") return raw;
  return systemMode();
}

export const themeStore = {
  get(): AppThemeMode {
    return resolve();
  },
  set(mode: AppThemeMode) {
    localStorage.setItem(KEY, mode);
    applyPaletteToDom(mode);
    window.dispatchEvent(new Event(EVENT));
  },
  toggle() {
    this.set(resolve() === "dark" ? "light" : "dark");
  },
  init() {
    applyPaletteToDom(resolve());
    // Chưa chọn thủ công → đi theo hệ điều hành khi người dùng đổi.
    window.matchMedia?.("(prefers-color-scheme: dark)").addEventListener("change", () => {
      if (!localStorage.getItem(KEY)) {
        applyPaletteToDom(systemMode());
        window.dispatchEvent(new Event(EVENT));
      }
    });
  },
};

export function useThemeMode(): AppThemeMode {
  const [mode, setMode] = useState<AppThemeMode>(resolve);
  useEffect(() => {
    const sync = () => setMode(resolve());
    window.addEventListener(EVENT, sync);
    window.addEventListener("storage", sync);
    return () => {
      window.removeEventListener(EVENT, sync);
      window.removeEventListener("storage", sync);
    };
  }, []);
  return mode;
}

/** Màu dạng JS (biểu đồ, thuộc tính SVG không dùng được var()). */
export function useThemeColors(): ThemePalette {
  return paletteFor(useThemeMode());
}

themeStore.init();
