import { AutoComplete, Input, Space } from "antd";
import { ReloadOutlined } from "@ant-design/icons";
import { AppActionButton } from "../../components/AppButtons";
import { useEffect, useMemo, useRef, useState } from "react";
import { listCustomers, type Customer } from "./customers-api";

type Option = {
  value: string;
  label: string;
  customer?: Customer;
};

type Props = {
  placeholder?: string;
  className?: string;
  style?: React.CSSProperties;
  /** Từ khóa đã áp dụng lọc danh sách. */
  value?: string;
  scope?: "all";
  onSearch: (query: string) => void;
};

function toOptions(customers: Customer[]): Option[] {
  return customers.map((c) => ({
    value: c.name,
    label: c.phone ? `${c.name} — ${c.phone}` : c.name,
    customer: c,
  }));
}

/** Thanh tìm khách: gợi ý tên / số điện thoại khi gõ từng ký tự. */
export function CustomerSuggestSearch({
  placeholder = "Tìm tên hoặc số điện thoại khách",
  className = "app-search",
  style = { width: 320 },
  value = "",
  scope,
  onSearch,
}: Props) {
  const [text, setText] = useState(value);
  const [options, setOptions] = useState<Option[]>([]);
  const [fetching, setFetching] = useState(false);
  const seq = useRef(0);

  useEffect(() => {
    setText(value);
  }, [value]);

  useEffect(() => {
    const next = text.trim();
    const timer = window.setTimeout(() => onSearch(next), 180);
    return () => window.clearTimeout(timer);
  }, [text, onSearch]);

  useEffect(() => {
    const q = text.trim();
    if (q.length < 1) {
      setOptions([]);
      return;
    }
    const id = ++seq.current;
    const timer = window.setTimeout(() => {
      setFetching(true);
      listCustomers({ q, active: true, scope })
        .then((rows) => {
          if (id !== seq.current) return;
          setOptions(toOptions(rows.slice(0, 12)));
        })
        .catch(() => {
          if (id !== seq.current) return;
          setOptions([]);
        })
        .finally(() => {
          if (id === seq.current) setFetching(false);
        });
    }, 200);
    return () => window.clearTimeout(timer);
  }, [text, scope]);

  const uniqOptions = useMemo(() => {
    const seen = new Set<string>();
    return options.filter((o) => {
      const key = o.value.toLowerCase();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }, [options]);

  function commit(raw: string) {
    const next = raw.trim();
    setText(next);
    onSearch(next);
  }

  return (
    <Space.Compact className="live-search" style={style}>
    <AutoComplete
      style={{ width: style?.width ?? 320 }}
      value={text}
      options={uniqOptions}
      defaultActiveFirstOption={false}
      notFoundContent={fetching ? "Đang tìm…" : text.trim() ? "Không có gợi ý" : null}
      onSearch={setText}
      onSelect={(_selected, option) => {
        const customer = (option as Option).customer;
        const preferPhone = /^\d/.test(text.trim()) && customer?.phone;
        commit(preferPhone ? customer!.phone! : (customer?.name ?? String(_selected)));
      }}
      onChange={(next) => setText(next)}
    >
      <Input.Search
        className={className}
        allowClear
        placeholder={placeholder}
        loading={fetching}
        onSearch={(q) => commit(q)}
        onClear={() => commit("")}
      />
    </AutoComplete>
    <AppActionButton
      className="live-search-reset"
      tone="secondary"
      size="middle"
      icon={<ReloadOutlined />}
      title="Đặt lại tìm kiếm"
      aria-label="Đặt lại tìm kiếm"
      onClick={() => commit("")}
    />
    </Space.Compact>
  );
}
