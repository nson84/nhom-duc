import { ReloadOutlined } from "@ant-design/icons";
import { AutoComplete, Input, Space } from "antd";
import { useEffect, useMemo, useState } from "react";
import { AppActionButton } from "./AppButtons";

type Props = {
  value: string;
  onChange: (value: string) => void;
  suggestions?: string[];
  placeholder: string;
  style?: React.CSSProperties;
};

/** Search that filters while typing and offers matching values with a reset action. */
export function LiveSearch({ value, onChange, suggestions = [], placeholder, style }: Props) {
  const [text, setText] = useState(value);
  useEffect(() => setText(value), [value]);
  useEffect(() => {
    const timer = window.setTimeout(() => onChange(text), 180);
    return () => window.clearTimeout(timer);
  }, [text, onChange]);
  const options = useMemo(() => {
    const q = text.trim().toLocaleLowerCase();
    if (!q) return [];
    return [...new Set(suggestions.filter((s) => s.toLocaleLowerCase().includes(q)))].slice(0, 10).map((s) => ({ value: s }));
  }, [suggestions, text]);
  const reset = () => { setText(""); onChange(""); };
  return (
    <Space.Compact className="live-search" style={style}>
      <AutoComplete
        value={text}
        options={options}
        style={{ width: style?.width ?? 280 }}
        onChange={setText}
        onSelect={(next) => { setText(next); onChange(next); }}
        filterOption={(input, option) => String(option?.value ?? "").toLocaleLowerCase().includes(input.toLocaleLowerCase())}
      >
        <Input.Search className="app-search" allowClear placeholder={placeholder} onSearch={(next) => { setText(next); onChange(next); }} />
      </AutoComplete>
      <AppActionButton
        className="live-search-reset"
        tone="secondary"
        size="middle"
        icon={<ReloadOutlined />}
        title="Đặt lại tìm kiếm"
        aria-label="Đặt lại tìm kiếm"
        onClick={reset}
      >
      </AppActionButton>
    </Space.Compact>
  );
}
