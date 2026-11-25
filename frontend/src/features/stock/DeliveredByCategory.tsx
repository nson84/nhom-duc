import { useEffect, useState } from "react";
import { getDeliveredByCategory, type DeliveredByCategoryRow } from "./stock-api";

const quantity = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });

export function DeliveredByCategory({ from, to, customerIds, categoryId, scope, shopKey, unit = "kg", inline = false }: {
  from?: string;
  to?: string;
  customerIds?: number[];
  categoryId?: number;
  scope?: "all";
  shopKey?: string;
  unit?: "kg" | "tấn";
  inline?: boolean;
}) {
  const [rows, setRows] = useState<DeliveredByCategoryRow[]>([]);
  const ids = customerIds?.join(",");

  useEffect(() => {
    let active = true;
    if (ids === "") { setRows([]); return; }
    setRows([]);
    getDeliveredByCategory({ from, to, categoryId, scope, customerIds: ids ? ids.split(",").map(Number) : undefined })
      .then((result) => { if (active) setRows(result); })
      .catch(() => { if (active) setRows([]); });
    return () => { active = false; };
  }, [from, to, ids, categoryId, scope, shopKey]);

  if (!rows.length) return null;
  return <div style={{ display: "flex", flexWrap: "wrap", gap: "4px 14px", marginTop: inline ? 0 : 8, fontSize: 13 }}>
    {rows.map((row) => <span key={row.categoryName}>
      {row.categoryName}: {quantity.format(unit === "tấn" ? row.quantityKg / 1000 : row.quantityKg)} {unit}
    </span>)}
  </div>;
}
