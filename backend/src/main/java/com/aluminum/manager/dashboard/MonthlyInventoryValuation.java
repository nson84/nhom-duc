package com.aluminum.manager.dashboard;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.shop.ShopAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MonthlyInventoryValuation {
    private final JdbcTemplate jdbc;
    private final ShopAccess shops;

    public MonthlyInventoryValuation(JdbcTemplate jdbc, ShopAccess shops) {
        this.jdbc = jdbc;
        this.shops = shops;
    }

    public BigDecimal rawValue(long categoryId, BigDecimal kg, LocalDate to) {
        if (kg.signum() == 0) return BigDecimal.ZERO;
        var layers = jdbc.query("""
                SELECT i.quantity_base, i.line_total
                FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
                WHERE d.shop_id = ? AND i.category_id = ? AND d.document_type = 'PURCHASE'
                  AND d.status = 'CONFIRMED' AND d.confirmed_at IS NOT NULL AND i.quantity_base > 0
                  AND d.document_date < ?
                  AND NOT EXISTS (SELECT 1 FROM stock_documents r
                      WHERE r.reversal_of_document_id = d.id AND r.document_date < ?)
                ORDER BY d.document_date DESC, d.id DESC, i.id DESC
                """, (rs, i) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2)},
                shops.currentId(), categoryId, to.plusDays(1).atStartOfDay(), to.plusDays(1).atStartOfDay());
        BigDecimal remaining = kg;
        BigDecimal value = BigDecimal.ZERO;
        for (var layer : layers) {
            BigDecimal allocated = remaining.min(layer[0]);
            value = value.add(allocated.multiply(layer[1]).divide(layer[0], 8, RoundingMode.HALF_UP));
            remaining = remaining.subtract(allocated);
            if (remaining.signum() == 0) break;
        }
        if (remaining.signum() > 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Không đủ lịch sử phiếu nhập để định giá tồn của loại " + categoryId
                        + "; còn thiếu " + remaining.stripTrailingZeros().toPlainString() + " kg", null);
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal profit(BigDecimal revenue, BigDecimal opening, BigDecimal purchases,
                             BigDecimal internalOut, BigDecimal ending, BigDecimal expenses) {
        return revenue.subtract(opening).subtract(purchases).add(internalOut).add(ending).subtract(expenses);
    }
}
