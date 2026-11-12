import {
  DoubleLeftOutlined,
  DoubleRightOutlined,
  LeftOutlined,
  RightOutlined,
} from "@ant-design/icons";
import { Button, Table, type TableProps } from "antd";
import { useEffect, useMemo, useState } from "react";

/** Số bản ghi tối đa mỗi trang. */
export const PAGE_SIZE = 30;

type PageItem = number | "gap";

/**
 * Cách hiển thị số trang (N = tổng số trang, c = trang đang chọn):
 *  - Ít trang (N ≤ 6): hiện hết.
 *  - Nhiều trang: trang đang chọn + 2 trang kế tiếp, "…", rồi 2 trang cuối.
 *    Ví dụ N = 90: c=1 → 1 2 3 … 89 90; c=3 → 3 4 5 … 89 90.
 *  - Gần cuối: hiện liền 5 trang cuối (không còn dấu "…").
 */
export function buildPageItems(current: number, total: number): PageItem[] {
  if (total <= 6) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }
  if (current + 2 >= total - 2) {
    const start = Math.max(1, total - 4);
    return Array.from({ length: total - start + 1 }, (_, i) => start + i);
  }
  return [current, current + 1, current + 2, "gap", total - 1, total];
}

export function AppPagination({
  current,
  total,
  pageSize = PAGE_SIZE,
  onChange,
}: {
  current: number;
  total: number;
  pageSize?: number;
  onChange: (page: number) => void;
}) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  if (total <= 0) return null;
  const items = buildPageItems(current, pageCount);

  return (
    <nav className="app-pager" aria-label="Phân trang">
      <div className="app-pager-controls">
        <Button
          className="app-pager-btn"
          icon={<DoubleLeftOutlined />}
          aria-label="Về trang đầu"
          title="Về trang đầu"
          disabled={current <= 1}
          onClick={() => onChange(1)}
        />
        <Button
          className="app-pager-btn"
          icon={<LeftOutlined />}
          aria-label="Trang trước"
          title="Trang trước"
          disabled={current <= 1}
          onClick={() => onChange(current - 1)}
        />
        {items.map((item, index) =>
          item === "gap" ? (
            <span key={`gap-${index}`} className="app-pager-gap" aria-hidden>
              …
            </span>
          ) : (
            <Button
              key={item}
              className={`app-pager-btn app-pager-page${item === current ? " is-active" : ""}`}
              aria-label={`Trang ${item}`}
              aria-current={item === current ? "page" : undefined}
              onClick={() => onChange(item)}
            >
              {item}
            </Button>
          ),
        )}
        <Button
          className="app-pager-btn"
          icon={<RightOutlined />}
          aria-label="Trang sau"
          title="Trang sau"
          disabled={current >= pageCount}
          onClick={() => onChange(current + 1)}
        />
        <Button
          className="app-pager-btn"
          icon={<DoubleRightOutlined />}
          aria-label="Đến trang cuối"
          title="Đến trang cuối"
          disabled={current >= pageCount}
          onClick={() => onChange(pageCount)}
        />
      </div>
      <div className="app-pager-summary">
        Trang {current} / {pageCount} · Tổng {total} bản ghi · Tối đa {pageSize} bản ghi mỗi trang
      </div>
    </nav>
  );
}

/** Bảng có phân trang chuẩn của ứng dụng: tối đa 30 bản ghi / trang, căn giữa. */
export function PagedTable<T extends object>(props: TableProps<T>) {
  const { dataSource, pagination: _ignored, scroll, ...rest } = props;
  const [page, setPage] = useState(1);
  const rows = (dataSource ?? []) as readonly T[];
  const total = rows.length;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));

  useEffect(() => {
    if (page > pageCount) setPage(pageCount);
  }, [page, pageCount]);

  const pageRows = useMemo(
    () => rows.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE),
    [rows, page],
  );

  return (
    <>
      <Table<T>
        {...(rest as TableProps<T>)}
        dataSource={pageRows as T[]}
        pagination={false}
        scroll={{ x: "max-content", ...scroll }}
      />
      <AppPagination current={page} total={total} onChange={setPage} />
    </>
  );
}
