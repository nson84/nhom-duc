import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** One-time, transactional demo data for an otherwise empty business ledger. */
public final class DemoDataSeeder {
    private static final long DISTRIBUTION_KG = 1_000_000;
    private static final long TRANSFER_KG = 500_000;
    private static final long DISTRIBUTION_END_KG = 1_000;
    private static final long FACTORY_SOLD_KG = 499_600;
    private static final long FACTORY_END_KG = 200;
    private final Connection db;
    private final int year;
    private final Random random;
    private final Map<Long, Balance> balances = new HashMap<>();
    private int financeNumber;

    private record Product(long id, long categoryId, long unitId, String code, String name, String category) {}
    private record Line(Product product, long kg, long pricePerKg, Long sourceItem) {}
    private record Document(long id, long amount, List<Long> itemIds) {}
    private record PlannedSale(int month, int category, int ordinal, long kg) {}
    private record Worker(long id, long rateId, String name, long dailyRate) {}
    private static final class Balance {
        long kg;
        BigDecimal average = BigDecimal.ZERO;
    }

    private DemoDataSeeder(Connection db, int year) {
        this.db = db;
        this.year = year;
        this.random = new Random(20251031L + year);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || (args.length == 2 && !"--dry-run".equals(args[1])))
            throw new IllegalArgumentException("Cách dùng: DemoDataSeeder 2025 [--dry-run]");
        int year = Integer.parseInt(args[0]);
        if (year > LocalDate.now().getYear() - 1 || year < 2000)
            throw new IllegalArgumentException("Chỉ tạo dữ liệu cho năm đã kết thúc");
        String password = System.getenv("ALUMINUM_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Thiếu ALUMINUM_DB_PASSWORD");
        String name = System.getenv().getOrDefault("ALUMINUM_DB_NAME", "aluminum_app");
        String user = System.getenv().getOrDefault("ALUMINUM_DB_USERNAME", "aluminum_app");
        String url = "jdbc:mysql://127.0.0.1:3306/" + name
                + "?useUnicode=true&characterEncoding=utf8&connectionTimeZone=Asia/Ho_Chi_Minh&sslMode=DISABLED";
        try (Connection db = DriverManager.getConnection(url, user, password)) {
            db.setAutoCommit(false);
            try {
                DemoDataSeeder seed = new DemoDataSeeder(db, year);
                seed.run();
                seed.verify();
                if (args.length == 2) {
                    db.rollback();
                    System.out.println("Đã thử tạo dữ liệu mẫu và rollback; dữ liệu hiện tại không đổi.");
                } else {
                    db.commit();
                    System.out.println("Đã tạo dữ liệu mẫu năm " + year + " cho cả hai sổ.");
                }
            } catch (Exception ex) {
                db.rollback();
                throw ex;
            }
        }
    }

    private void run() throws Exception {
        long distribution = oneLong("SELECT id FROM shops WHERE code = 'DISTRIBUTION'");
        long factory = oneLong("SELECT id FROM shops WHERE code = 'MANUFACTURING'");
        if (oneLong("SELECT COUNT(*) FROM stock_documents") != 0
                || oneLong("SELECT COUNT(*) FROM customers") != 0
                || oneLong("SELECT COUNT(*) FROM employees") != 0
                || oneLong("SELECT COUNT(*) FROM financial_transactions") != 0
                || oneLong("SELECT COUNT(*) FROM factory_year_counts") != 0
                || oneLong("SELECT COUNT(*) FROM year_end_balances") != 0)
            throw new IllegalStateException("Sổ nghiệp vụ không còn trống; dừng để tránh trộn hoặc nhân đôi dữ liệu mẫu");
        List<Product> distProducts = products(distribution);
        List<Product> factoryProducts = products(factory);
        if (distProducts.size() != 3 || factoryProducts.size() != 3)
            throw new IllegalStateException("Cần đúng ba loại nhôm và ba sản phẩm ở mỗi bên");
        for (int i = 0; i < 3; i++) {
            if (!distProducts.get(i).category().equals(factoryProducts.get(i).category()))
                throw new IllegalStateException("Ba loại nhôm hai bên chưa khớp tên");
        }
        long[] distCustomers = customers(distribution, "Phân phối");
        long[] factoryCustomers = customers(factory, "Xưởng");
        prices(distProducts, factoryProducts);
        trade(distribution, factory, distProducts, factoryProducts, distCustomers, factoryCustomers);
        scrap(factory);
        expenses(distribution, factory);
        workers(factory);
        factoryCount(factoryProducts);
    }

    private List<Product> products(long shopId) throws Exception {
        List<Product> result = new ArrayList<>();
        try (PreparedStatement statement = db.prepareStatement("""
                SELECT p.id, c.id, u.id, p.product_code, p.name, c.name
                FROM product_categories c JOIN products p ON p.shop_id = c.shop_id
                  AND (p.category_id = c.id OR (p.category_id IS NULL AND p.name = c.name))
                JOIN product_units u ON u.product_id = p.id AND u.unit_name = 'kg' AND u.is_active = 1
                WHERE c.shop_id = ? AND c.is_active = 1 AND p.is_active = 1 ORDER BY c.name
                """)) {
            statement.setLong(1, shopId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.add(new Product(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)));
            }
        }
        return result;
    }

    private long[] customers(long shopId, String prefix) throws Exception {
        long[] ids = new long[30];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = insert("INSERT INTO customers (shop_id, name, phone, note, created_at) VALUES (?, ?, ?, ?, ?)",
                    shopId, "Khách mẫu " + prefix + " " + String.format("%02d", i + 1),
                    "090000" + String.format("%04d", i + 1 + (shopId * 100)),
                    "Dữ liệu thử nghiệm năm " + year, at(1, 1, 8, 0));
        }
        return ids;
    }

    private void prices(List<Product> dist, List<Product> factory) throws Exception {
        int[] base = {75_000, 70_000, 65_000};
        int[] swing = {0, 650, 1_400, 900, -350, -1_050, -450, 1_100, 1_950, 1_250, 400, 1_700};
        for (int month = 1; month <= 12; month++) {
            LocalDate from = LocalDate.of(year, month, 1);
            LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
            for (int cat = 0; cat < 3; cat++) {
                long price = base[cat] + swing[month - 1] + cat * 120;
                for (Product product : List.of(dist.get(cat), factory.get(cat))) {
                    exec("INSERT INTO category_purchase_prices (category_id, effective_from, effective_to, price_per_base, note) VALUES (?, ?, ?, ?, ?)",
                            product.categoryId(), from, to, price, "Giá mẫu: tăng giảm theo thị trường " + month + "/" + year);
                }
            }
        }
    }

    private int[][] allocations(long[] totals, int count, int jitterPercent) {
        int[][] result = new int[totals.length][];
        for (int i = 0; i < totals.length; i++) result[i] = allocate(totals[i], count, jitterPercent);
        return result;
    }

    private int[] allocate(long total, int count, int jitterPercent) {
        int[] weights = new int[count];
        long weightSum = 0;
        for (int i = 0; i < count; i++) {
            weights[i] = 100 + random.nextInt(jitterPercent * 2 + 1) - jitterPercent;
            weightSum += weights[i];
        }
        int[] values = new int[count];
        long assigned = 0;
        for (int i = 0; i < count - 1; i++) {
            values[i] = Math.toIntExact(total * weights[i] / weightSum);
            assigned += values[i];
        }
        values[count - 1] = Math.toIntExact(total - assigned);
        return values;
    }

    private long price(int category, int month) {
        int[] base = {75_000, 70_000, 65_000};
        int[] swing = {0, 650, 1_400, 900, -350, -1_050, -450, 1_100, 1_950, 1_250, 400, 1_700};
        return base[category] + swing[month - 1] + category * 120;
    }

    private List<PlannedSale> plan(int count, long[] categoryTotals) {
        List<PlannedSale> result = new ArrayList<>();
        int[] categoryCounts = new int[3];
        for (int i = 0; i < count; i++) {
            int month = i % 12 + 1;
            int category = ((i / 12) + month - 1) % 3;
            result.add(new PlannedSale(month, category, i, 0));
            categoryCounts[category]++;
        }
        int[][] quantities = new int[3][];
        for (int cat = 0; cat < 3; cat++) quantities[cat] = allocate(categoryTotals[cat], categoryCounts[cat], 5);
        int[] cursor = new int[3];
        return result.stream().map(s -> new PlannedSale(s.month(), s.category(), s.ordinal(),
                quantities[s.category()][cursor[s.category()]++])).toList();
    }

    private void trade(long distribution, long factory, List<Product> dist, List<Product> mfg,
                       long[] distCustomers, long[] factoryCustomers) throws Exception {
        long[] purchases = {333_333, 333_333, 333_334};
        long[] transfer = {166_667, 166_667, 166_666};
        long[] distEnding = {333, 333, 334};
        long[] distSales = new long[3];
        for (int cat = 0; cat < 3; cat++) distSales[cat] = purchases[cat] - transfer[cat] - distEnding[cat];
        long[] factorySales = {166_533, 166_534, 166_533};
        int[][] purchaseQty = allocations(purchases, 10, 7);
        int[][] transferQty = allocations(transfer, 12, 3);
        List<PlannedSale> distributionSales = plan(100, distSales);
        List<PlannedSale> manufacturingSales = plan(80, factorySales);
        int docNumber = 0;
        for (int month = 1; month <= 12; month++) {
            if (month <= 10) {
                for (int cat = 0; cat < 3; cat++) {
                    docNumber++;
                    long kg = purchaseQty[cat][month - 1];
                    long unitPrice = price(cat, month);
                    Document purchase = document(distribution, "PURCHASE", "DEMO" + year + "-D-P-" + pad(docNumber),
                            at(month, 2, 9 + cat, 0), "Nhà cung cấp mẫu " + (cat + 1), null, false,
                            List.of(new Line(dist.get(cat), kg, unitPrice, null)));
                    payPurchase(purchase, month, docNumber);
                }
            }
            List<Line> distributionLines = new ArrayList<>();
            List<Line> factoryLines = new ArrayList<>();
            for (int cat = 0; cat < 3; cat++) {
                long kg = transferQty[cat][month - 1];
                distributionLines.add(new Line(dist.get(cat), kg, price(cat, month), null));
                factoryLines.add(new Line(mfg.get(cat), kg, price(cat, month), null));
            }
            Document out = document(distribution, "SALE", "DEMO" + year + "-D-INT-" + pad(month),
                    at(month, 5, 9, 0), "Xưởng sản xuất (nội bộ)", null, true, distributionLines);
            Document in = document(factory, "PURCHASE", "DEMO" + year + "-X-INT-" + pad(month),
                    at(month, 5, 9, 0), "Phân phối nhôm khối (nội bộ)", null, true, factoryLines);
            exec("UPDATE stock_documents SET internal_pair_document_id = ? WHERE id = ?", in.id(), out.id());
            exec("UPDATE stock_documents SET internal_pair_document_id = ? WHERE id = ?", out.id(), in.id());
            for (PlannedSale sale : distributionSales) {
                if (sale.month() != month) continue;
                long customerId = distCustomers[(sale.ordinal() * 17 + 3) % 30];
                long unitPrice = price(sale.category(), month) + 13_000 + (sale.ordinal() % 7) * 500L;
                Document invoice = document(distribution, "SALE", "DEMO" + year + "-D-S-" + pad(sale.ordinal() + 1),
                        at(month, 9 + (sale.ordinal() / 12) * 2, 10, 0), "Khách mẫu Phân phối", customerId, false,
                        List.of(new Line(dist.get(sale.category()), sale.kg(), unitPrice, null)));
                paySale(invoice, sale.ordinal(), month, "Phân phối");
            }
            for (PlannedSale sale : manufacturingSales) {
                if (sale.month() != month) continue;
                long customerId = factoryCustomers[(sale.ordinal() * 11 + 2) % 30];
                long unitPrice = price(sale.category(), month) + 24_000 + (sale.ordinal() % 9) * 750L;
                Document invoice = document(factory, "SALE", "DEMO" + year + "-X-S-" + pad(sale.ordinal() + 1),
                        at(month, 10 + (sale.ordinal() / 12) * 2, 14, 0), "Khách mẫu Xưởng", customerId, false,
                        List.of(new Line(mfg.get(sale.category()), sale.kg(), unitPrice,
                                in.itemIds().get(sale.category()))));
                paySale(invoice, sale.ordinal() + 2, month, "Xưởng");
            }
        }
    }

    private Document document(long shopId, String type, String code, LocalDateTime when, String partner,
                              Long customerId, boolean internal, List<Line> lines) throws Exception {
        long total = 0;
        for (Line line : lines) total = Math.addExact(total, Math.multiplyExact(line.kg(), line.pricePerKg()));
        long id = insert("""
                INSERT INTO stock_documents (document_code, document_type, status, document_date,
                  counterparty_name, customer_id, issued_by, received_by, subtotal, discount_amount,
                  total_amount, payment_status, shop_id, is_internal, confirmed_at, notes)
                VALUES (?, ?, 'CONFIRMED', ?, ?, ?, 'Dữ liệu mẫu', ?, ?, 0, ?, ?, ?, ?, ?, ?)
                """, code, type, when, partner, customerId, type.equals("PURCHASE") ? "Kho" : partner,
                total, total, internal ? "PAID" : "UNPAID", shopId, internal, when,
                "Dữ liệu mẫu " + year + " để thử quyết toán");
        List<Long> itemIds = new ArrayList<>();
        for (Line line : lines) {
            long value = Math.multiplyExact(line.kg(), line.pricePerKg());
            boolean factorySale = shopId == oneLong("SELECT id FROM shops WHERE code = 'MANUFACTURING'") && type.equals("SALE");
            BigDecimal cost = type.equals("PURCHASE") ? BigDecimal.valueOf(line.pricePerKg())
                    : factorySale ? currentBalance(line.product()).average : null;
            BigDecimal costTotal = cost == null ? null : cost.multiply(BigDecimal.valueOf(line.kg())).setScale(4, RoundingMode.HALF_UP);
            BigDecimal sourceCost = factorySale && line.sourceItem() != null
                    ? BigDecimal.valueOf(oneLong("SELECT unit_price FROM stock_document_items WHERE id = ?", line.sourceItem())) : null;
            long itemId = insert("""
                    INSERT INTO stock_document_items (document_id, product_id, category_id, unit_id,
                      product_code_snapshot, product_name_snapshot, category_name_snapshot,
                      unit_name_snapshot, billing_unit, quantity_document, factor_to_base_snapshot,
                      quantity_base, unit_price, line_discount, line_total, cost_per_base_snapshot,
                      cost_total_snapshot, factory_purchase_item_id, factory_reference_cost_per_kg)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'kg', 'kg', ?, 1, ?, ?, 0, ?, ?, ?, ?, ?)
                    """, id, line.product().id(), line.product().categoryId(), line.product().unitId(),
                    line.product().code(), line.product().name(), line.product().category(),
                    line.kg(), line.kg(), line.pricePerKg(), value, cost, costTotal,
                    factorySale ? line.sourceItem() : null,
                    sourceCost);
            itemIds.add(itemId);
            if (type.equals("PURCHASE")) move(id, itemId, line.product(), line.kg(), line.pricePerKg(), when);
            else if (shopId != oneLong("SELECT id FROM shops WHERE code = 'MANUFACTURING'"))
                move(id, itemId, line.product(), -line.kg(), 0, when);
        }
        return new Document(id, total, itemIds);
    }

    private Balance currentBalance(Product product) {
        return balances.computeIfAbsent(product.id(), ignored -> new Balance());
    }

    private void move(long documentId, long itemId, Product product, long delta, long purchasePrice,
                      LocalDateTime when) throws Exception {
        Balance balance = currentBalance(product);
        if (delta > 0) {
            BigDecimal oldValue = balance.average.multiply(BigDecimal.valueOf(balance.kg));
            BigDecimal newValue = BigDecimal.valueOf(delta).multiply(BigDecimal.valueOf(purchasePrice));
            balance.kg += delta;
            balance.average = oldValue.add(newValue).divide(BigDecimal.valueOf(balance.kg), 6, RoundingMode.HALF_UP);
        } else {
            balance.kg += delta;
            if (balance.kg < 0) throw new IllegalStateException("Tồn âm khi tạo " + when + " cho " + product.category());
        }
        exec("""
                INSERT INTO stock_balances (product_id, category_id, quantity_base, average_cost_per_base)
                VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE
                  quantity_base = VALUES(quantity_base), average_cost_per_base = VALUES(average_cost_per_base)
                """, product.id(), product.categoryId(), balance.kg, balance.average);
        exec("""
                INSERT INTO stock_movements (document_id, document_item_id, product_id, category_id,
                  movement_type, quantity_delta_base, unit_cost, movement_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, documentId, itemId, product.id(), product.categoryId(), delta > 0 ? "IN" : "OUT",
                delta, delta > 0 ? purchasePrice : balance.average, when);
    }

    private void payPurchase(Document invoice, int month, int number) throws Exception {
        int pattern = number % 4;
        LocalDateTime base = at(month, 2, 9, 0);
        if (pattern == 0) purchasePayment(invoice, invoice.amount(), base.plusDays(5));
        else if (pattern == 1) purchasePayment(invoice, invoice.amount() / 2, base.plusDays(20));
        else if (pattern == 2) {
            purchasePayment(invoice, invoice.amount() / 3, base.plusDays(8));
            purchasePayment(invoice, invoice.amount() / 3, base.plusDays(42));
        }
        if (month == 10 && pattern == 1)
            purchasePayment(invoice, invoice.amount() - invoice.amount() / 2, LocalDate.of(year + 1, 1, 12).atTime(10, 0));
        updatePaymentStatus(invoice.id(), invoice.amount(), false);
    }

    private void purchasePayment(Document invoice, long amount, LocalDateTime when) throws Exception {
        insertFinance(oneLong("SELECT shop_id FROM stock_documents WHERE id = ?", invoice.id()), when,
                "EXPENSE", "PURCHASE_PAYMENT", amount, "Trả tiền nhập nhôm dữ liệu mẫu", "STOCK_DOCUMENT",
                invoice.id(), "Chủ cửa hàng");
    }

    private void paySale(Document invoice, int ordinal, int month, String shopName) throws Exception {
        LocalDateTime base = at(month, 10 + (ordinal / 12) * 2, 10, 0);
        switch (ordinal % 6) {
            case 0 -> salePayment(invoice, invoice.amount(), base.plusDays(2), shopName);
            case 1 -> {
                long first = invoice.amount() * 40 / 100;
                salePayment(invoice, first, base.plusDays(4), shopName);
                salePayment(invoice, invoice.amount() - first, base.plusDays(23), shopName);
            }
            case 2 -> salePayment(invoice, invoice.amount() * 35 / 100, base.plusDays(8), shopName);
            case 3 -> { /* nợ toàn bộ */ }
            case 4 -> {
                long first = invoice.amount() / 5;
                long second = invoice.amount() * 30 / 100;
                salePayment(invoice, first, base.plusDays(5), shopName);
                salePayment(invoice, second, base.plusDays(28), shopName);
                salePayment(invoice, invoice.amount() - first - second, base.plusDays(65), shopName);
            }
            case 5 -> salePayment(invoice, invoice.amount(), base.plusDays(55), shopName);
            default -> throw new IllegalStateException();
        }
        updatePaymentStatus(invoice.id(), invoice.amount(), true);
    }

    private void salePayment(Document invoice, long amount, LocalDateTime when, String shopName) throws Exception {
        long shopId = oneLong("SELECT shop_id FROM stock_documents WHERE id = ?", invoice.id());
        long financeId = insertFinance(shopId, when, "INCOME", "SALE_RECEIPT", amount,
                "Thu tiền khách mua nhôm dữ liệu mẫu", "STOCK_DOCUMENT", invoice.id(), null);
        exec("""
                INSERT INTO sales_payments (document_id, payment_date, amount, payment_method, note,
                  collected_by_name, financial_transaction_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, invoice.id(), when, amount, (invoice.id() % 3 == 0) ? "Chuyển khoản" : "Tiền mặt",
                "Thanh toán mẫu " + year, "Thu ngân " + shopName, financeId);
    }

    private void updatePaymentStatus(long documentId, long total, boolean sale) throws Exception {
        long paid = sale
                ? oneLong("SELECT COALESCE(SUM(amount),0) FROM sales_payments WHERE document_id = ?", documentId)
                : oneLong("""
                    SELECT COALESCE(SUM(amount),0) FROM financial_transactions
                    WHERE reference_type='STOCK_DOCUMENT' AND reference_id=? AND category='PURCHASE_PAYMENT'
                    """, documentId);
        String status = paid == 0 ? "UNPAID" : paid >= total ? "PAID" : "PARTIAL";
        exec("UPDATE stock_documents SET payment_status = ? WHERE id = ?", status, documentId);
    }

    private long insertFinance(long shopId, LocalDateTime when, String type, String category,
                               long amount, String description, String referenceType,
                               Long referenceId, String spentBy) throws Exception {
        if (amount <= 0) throw new IllegalArgumentException("Khoản thu chi phải dương");
        return insert("""
                INSERT INTO financial_transactions (transaction_code, transaction_date, transaction_type,
                  category, amount, description, reference_type, reference_id, status, shop_id, is_internal, spent_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, 0, ?)
                """, "DEMO" + year + "-F-" + String.format("%05d", ++financeNumber), when, type,
                category, amount, description, referenceType, referenceId, shopId, spentBy);
    }

    private void scrap(long factory) throws Exception {
        for (int i = 0; i < 4; i++) {
            int month = (i + 1) * 3;
            LocalDateTime when = at(month, 26, 15, 0);
            long id = insert("""
                    INSERT INTO factory_scrap_sales (shop_id, sale_date, buyer_name, seller_name,
                      quantity_kg, unit_price, total_amount, notes, status)
                    VALUES (?, ?, ?, 'Xưởng mẫu', 25, 12000, 300000, ?, 'CONFIRMED')
                    """, factory, when, "Người mua vụn mẫu " + (i + 1), "Dữ liệu mẫu " + year);
            long paid = i == 1 ? 150_000 : i == 2 ? 0 : 300_000;
            if (paid > 0) {
                LocalDateTime paidAt = when.plusDays(i == 3 ? 15 : 2);
                long financeId = insertFinance(factory, paidAt, "INCOME", "OTHER_INCOME", paid,
                        "Thu bán phế liệu mẫu #" + id, "SCRAP_SALE", id, null);
                exec("""
                        INSERT INTO factory_scrap_payments (scrap_sale_id, payment_date, amount,
                          payment_method, collected_by, financial_transaction_id)
                        VALUES (?, ?, ?, 'Tiền mặt', 'Thu ngân Xưởng', ?)
                        """, id, paidAt, paid, financeId);
            }
        }
    }

    private void expenses(long distribution, long factory) throws Exception {
        for (LocalDate day = LocalDate.of(year, 1, 1); day.getYear() == year; day = day.plusDays(1)) {
            insertFinance(distribution, day.atTime(12, 5), "EXPENSE", "OPERATING_EXPENSE", 10_000,
                    "Tiền nước + bánh kẹo giữa giờ: nước uống dữ liệu mẫu", null, null, "Chủ cửa hàng");
            insertFinance(factory, day.atTime(12, 10), "EXPENSE", "OPERATING_EXPENSE", 40_000,
                    "Tiền nước + bánh kẹo giữa giờ: nước uống cho thợ dữ liệu mẫu", null, null, "Quản đốc xưởng");
        }
        for (int month = 1; month <= 12; month++) {
            insertFinance(distribution, at(month, 15, 16, 0), "EXPENSE", "OPERATING_EXPENSE",
                    1_600_000 + month * 35_000L, "Tiền điện: cửa hàng mẫu", null, null, "Chủ cửa hàng");
            insertFinance(factory, at(month, 15, 16, 5), "EXPENSE", "OPERATING_EXPENSE",
                    2_500_000 + month * 55_000L, "Tiền điện: xưởng mẫu", null, null, "Quản đốc xưởng");
            insertFinance(factory, at(month, 20, 12, 0), "EXPENSE", "OPERATING_EXPENSE", 850_000,
                    "Tiền ăn mỗi ngày: bữa ăn cho thợ dữ liệu mẫu", null, null, "Quản đốc xưởng");
            if (month % 3 == 0) insertFinance(factory, at(month, 22, 15, 0), "EXPENSE", "OPERATING_EXPENSE",
                    1_200_000, "Tiền vật tư máy móc: dầu và đá cắt dữ liệu mẫu", null, null, "Quản đốc xưởng");
        }
        insertFinance(distribution, at(6, 18, 11, 0), "EXPENSE", "MAINTENANCE", 3_600_000,
                "Tiền sửa chữa hoặc thay thế bảo dưỡng: xe giao hàng mẫu", null, null, "Chủ cửa hàng");
        insertFinance(factory, at(8, 18, 11, 0), "EXPENSE", "MAINTENANCE", 5_700_000,
                "Tiền sửa chữa hoặc thay thế bảo dưỡng: máy ép mẫu", null, null, "Quản đốc xưởng");
    }

    private void workers(long factory) throws Exception {
        long[] rates = {350_000, 370_000, 390_000, 420_000, 450_000};
        List<Worker> workers = new ArrayList<>();
        for (int i = 0; i < rates.length; i++) {
            String name = "Thợ mẫu " + (i + 1);
            long employeeId = insert("""
                    INSERT INTO employees (employee_code, full_name, phone, hire_date, is_active, notes, shop_id)
                    VALUES (?, ?, ?, ?, 1, ?, ?)
                    """, "DEMO" + year + "-NV-" + (i + 1), name, "091000" + String.format("%04d", i + 1),
                    LocalDate.of(year, 1, 1), "Nhân viên dữ liệu mẫu", factory);
            long rateId = insert("""
                    INSERT INTO employee_pay_rates (employee_id, effective_from, daily_rate,
                      standard_hours_per_day, overtime_weekday_rate, overtime_restday_rate,
                      overtime_holiday_rate)
                    VALUES (?, ?, ?, 8, ?, ?, ?)
                    """, employeeId, LocalDate.of(year, 1, 1), rates[i],
                    BigDecimal.valueOf(rates[i]).divide(BigDecimal.valueOf(8), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(1.5)),
                    BigDecimal.valueOf(rates[i]).divide(BigDecimal.valueOf(8), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(2)),
                    BigDecimal.valueOf(rates[i]).divide(BigDecimal.valueOf(8), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(3)));
            workers.add(new Worker(employeeId, rateId, name, rates[i]));
        }
        for (int month = 1; month <= 12; month++) {
            LocalDate first = LocalDate.of(year, month, 1);
            LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
            long periodId = insert("""
                    INSERT INTO payroll_periods (period_year, period_month, status, closed_at, notes)
                    VALUES (?, ?, 'CLOSED', ?, ?)
                    """, year, month, last.atTime(18, 0), "Kỳ công dữ liệu mẫu");
            long grandTotal = 0;
            for (int i = 0; i < workers.size(); i++) {
                Worker worker = workers.get(i);
                List<LocalDate> dates = new ArrayList<>();
                for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
                    if (day.getDayOfWeek() != DayOfWeek.SUNDAY) dates.add(day);
                }
                Collections.shuffle(dates, random);
                int workedDays = Math.min(dates.size(), 20 + (month + i) % 5);
                dates = new ArrayList<>(dates.subList(0, workedDays));
                Collections.sort(dates);
                BigDecimal hourlyOvertime = BigDecimal.valueOf(worker.dailyRate())
                        .divide(BigDecimal.valueOf(8), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(1.5));
                for (int dayIndex = 0; dayIndex < dates.size(); dayIndex++) {
                    LocalDate day = dates.get(dayIndex);
                    long attendanceId = insert("""
                            INSERT INTO attendance_records (employee_id, work_date, attendance_status,
                              morning_present, afternoon_present, clock_in, clock_out, worked_hours,
                              credited_days, pay_rate_id, daily_rate_snapshot, is_locked, notes)
                            VALUES (?, ?, 'PRESENT', 1, 1, ?, ?, 8, 1, ?, ?, 1, ?)
                            """, worker.id(), day, day.atTime(7, 30), day.atTime(17, 0),
                            worker.rateId(), worker.dailyRate(), "Đủ sáng và chiều - dữ liệu mẫu");
                    if (dayIndex < 4) {
                        exec("""
                                INSERT INTO overtime_records (attendance_id, overtime_type, start_at, end_at,
                                  overtime_hours, hourly_rate_snapshot, amount, is_approved, notes)
                                VALUES (?, 'WEEKDAY', ?, ?, 1, ?, ?, 1, ?)
                                """, attendanceId, day.atTime(17, 0), day.atTime(18, 0), hourlyOvertime,
                                hourlyOvertime, "Tăng ca mẫu 1 giờ");
                    }
                }
                long base = workedDays * worker.dailyRate();
                long overtime = hourlyOvertime.multiply(BigDecimal.valueOf(4))
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
                long total = base + overtime;
                grandTotal += total;
                exec("""
                        INSERT INTO payroll_details (payroll_period_id, employee_id, credited_days,
                          base_pay, overtime_pay, bonus_pay, allowances, deductions, total_pay,
                          holiday_bonus_pay, overtime_hours, holiday_bonus_received, advance_amount)
                        VALUES (?, ?, ?, ?, ?, 0, 0, 0, ?, 0, 4, 0, 0)
                        """, periodId, worker.id(), workedDays, base, overtime, total);
            }
            insertFinance(factory, last.atTime(18, 30), "EXPENSE", "PAYROLL", grandTotal,
                    "Chi trả tiền công tháng " + month + "/" + year + " dữ liệu mẫu",
                    "PAYROLL_PERIOD", periodId, "Quản đốc xưởng");
        }
    }

    private void factoryCount(List<Product> products) throws Exception {
        long rawValue = 0;
        long[] ending = {67, 67, 66};
        for (int cat = 0; cat < 3; cat++) {
            Product product = products.get(cat);
            long purchaseItem = oneLong("""
                    SELECT i.id FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
                    WHERE d.shop_id = ? AND d.document_type = 'PURCHASE' AND d.is_internal = 1
                      AND i.category_id = ? ORDER BY d.document_date DESC, i.id DESC LIMIT 1
                    """, oneLong("SELECT id FROM shops WHERE code = 'MANUFACTURING'"), product.categoryId());
            long unitCost = oneLong("SELECT unit_price FROM stock_document_items WHERE id = ?", purchaseItem);
            long value = ending[cat] * unitCost;
            rawValue += value;
            exec("INSERT INTO factory_raw_counts (year_number, category_id, quantity_kg, value_amount) VALUES (?, ?, ?, ?)",
                    year, product.categoryId(), ending[cat], value);
            exec("""
                    INSERT INTO factory_raw_count_layers (year_number, category_id, purchase_item_id,
                      quantity_kg, unit_cost_per_kg, value_amount)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, year, product.categoryId(), purchaseItem, ending[cat], unitCost, value);
        }
        exec("""
                INSERT INTO factory_year_counts (year_number, period_from, period_to,
                  opening_kg, opening_value, raw_kg, raw_value, scrap_kg, scrap_value,
                  work_in_progress_kg, work_in_progress_value, finished_kg, finished_value,
                  status, notes)
                VALUES (?, ?, ?, 0, 0, 200, ?, 0, 0, 0, 0, 0, 0, 'DRAFT', ?)
                """, year, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), rawValue,
                "Kiểm kê mẫu: 200 kg còn kho; hao hụt dự kiến 100 kg");
    }

    private void verify() throws Exception {
        check("Nhôm nhập Phân phối", DISTRIBUTION_KG, """
                SELECT COALESCE(SUM(i.quantity_base),0) FROM stock_document_items i
                JOIN stock_documents d ON d.id=i.document_id WHERE d.document_code LIKE ?
                """, "DEMO" + year + "-D-P-%");
        check("Chuyển nội bộ", TRANSFER_KG, """
                SELECT COALESCE(SUM(i.quantity_base),0) FROM stock_document_items i
                JOIN stock_documents d ON d.id=i.document_id WHERE d.document_code LIKE ?
                """, "DEMO" + year + "-D-INT-%");
        check("Tồn Phân phối", DISTRIBUTION_END_KG, """
                SELECT COALESCE(SUM(b.quantity_base),0) FROM stock_balances b
                JOIN products p ON p.id=b.product_id WHERE p.shop_id=(SELECT id FROM shops WHERE code='DISTRIBUTION')
                """);
        check("Thành phẩm bán Xưởng", FACTORY_SOLD_KG, """
                SELECT COALESCE(SUM(i.quantity_base),0) FROM stock_document_items i
                JOIN stock_documents d ON d.id=i.document_id WHERE d.document_code LIKE ?
                """, "DEMO" + year + "-X-S-%");
        check("Tồn kiểm kê Xưởng", FACTORY_END_KG,
                "SELECT COALESCE(SUM(quantity_kg),0) FROM factory_raw_counts WHERE year_number=?", year);
        check("Khách Phân phối", 30, "SELECT COUNT(*) FROM customers WHERE shop_id=(SELECT id FROM shops WHERE code='DISTRIBUTION')");
        check("Khách Xưởng", 30, "SELECT COUNT(*) FROM customers WHERE shop_id=(SELECT id FROM shops WHERE code='MANUFACTURING')");
        check("Thợ Xưởng", 5, "SELECT COUNT(*) FROM employees WHERE shop_id=(SELECT id FROM shops WHERE code='MANUFACTURING')");
        check("Phiếu nhập ngoài", 30, "SELECT COUNT(*) FROM stock_documents WHERE document_code LIKE ?", "DEMO" + year + "-D-P-%");
        check("Phiếu bán Phân phối", 100, "SELECT COUNT(*) FROM stock_documents WHERE document_code LIKE ?", "DEMO" + year + "-D-S-%");
        check("Phiếu bán Xưởng", 80, "SELECT COUNT(*) FROM stock_documents WHERE document_code LIKE ?", "DEMO" + year + "-X-S-%");
        for (String shop : List.of("DISTRIBUTION", "MANUFACTURING")) {
            int min = shop.equals("DISTRIBUTION") ? 3 : 2;
            int max = min + 1;
            check("Khách đều có phiếu mua " + shop, 30, """
                    SELECT COUNT(*) FROM (
                      SELECT c.id FROM customers c JOIN shops s ON s.id=c.shop_id
                      JOIN stock_documents d ON d.customer_id=c.id AND d.shop_id=c.shop_id
                        AND d.document_type='SALE' AND d.status='CONFIRMED' AND d.is_internal=0
                      WHERE s.code=? GROUP BY c.id HAVING COUNT(d.id) BETWEEN ? AND ?
                    ) x
                    """, shop, min, max);
        }
        check("Hao hụt Xưởng", 100, """
                SELECT (SELECT COALESCE(SUM(i.quantity_base),0) FROM stock_document_items i
                        JOIN stock_documents d ON d.id=i.document_id WHERE d.document_code LIKE ?)
                     - (SELECT COALESCE(SUM(i.quantity_base),0) FROM stock_document_items i
                        JOIN stock_documents d ON d.id=i.document_id WHERE d.document_code LIKE ?)
                     - (SELECT COALESCE(SUM(quantity_kg),0) FROM factory_scrap_sales)
                     - (SELECT COALESCE(SUM(quantity_kg),0) FROM factory_raw_counts WHERE year_number=?)
                """, "DEMO" + year + "-X-INT-%", "DEMO" + year + "-X-S-%", year);
        check("Chi nước Phân phối", LocalDate.of(year, 1, 1).lengthOfYear(), """
                SELECT COUNT(*) FROM financial_transactions WHERE shop_id=(SELECT id FROM shops WHERE code='DISTRIBUTION')
                  AND description LIKE 'Tiền nước + bánh kẹo giữa giờ:%'
                """);
        check("Chi nước Xưởng", LocalDate.of(year, 1, 1).lengthOfYear(), """
                SELECT COUNT(*) FROM financial_transactions WHERE shop_id=(SELECT id FROM shops WHERE code='MANUFACTURING')
                  AND description LIKE 'Tiền nước + bánh kẹo giữa giờ:%'
                """);
        check("Kỳ công", 12, "SELECT COUNT(*) FROM payroll_periods WHERE period_year=?", year);
        check("Tăng ca", 240, "SELECT COUNT(*) FROM overtime_records WHERE is_approved=1");
        for (String shop : List.of("DISTRIBUTION", "MANUFACTURING")) {
            for (String status : List.of("PAID", "PARTIAL", "UNPAID")) {
                if (oneLong("""
                        SELECT COUNT(*) FROM stock_documents d JOIN shops s ON s.id=d.shop_id
                        WHERE s.code=? AND d.document_type='SALE' AND d.is_internal=0
                          AND d.payment_status=?
                        """, shop, status) == 0)
                    throw new IllegalStateException("Thiếu tình huống khách " + status + " ở " + shop);
            }
            if (oneLong("""
                    SELECT COUNT(*) FROM sales_payments p JOIN stock_documents d ON d.id=p.document_id
                    JOIN shops s ON s.id=d.shop_id WHERE s.code=? AND p.payment_date >= ?
                    """, shop, LocalDate.of(year + 1, 1, 1)) == 0)
                throw new IllegalStateException("Thiếu khoản thu nợ chuyển kỳ ở " + shop);
            if (oneLong("""
                    SELECT COUNT(*) FROM (SELECT p.document_id FROM sales_payments p
                    JOIN stock_documents d ON d.id=p.document_id JOIN shops s ON s.id=d.shop_id
                    WHERE s.code=? GROUP BY p.document_id HAVING COUNT(*) > 1) x
                    """, shop) == 0)
                throw new IllegalStateException("Thiếu khách trả nhiều lần ở " + shop);
        }
        check("Ngày công đủ 20-24 buổi/tháng", 60, """
                SELECT COUNT(*) FROM (
                  SELECT employee_id,YEAR(work_date) y,MONTH(work_date) m,COUNT(*) days
                  FROM attendance_records GROUP BY employee_id,y,m HAVING days BETWEEN 20 AND 24
                ) x
                """);
        System.out.println("Đối chiếu: Phân phối nhập 1.000.000 kg, chuyển Xưởng 500.000 kg, còn 1.000 kg;");
        System.out.println("Xưởng bán 499.600 kg, bán vụn 100 kg, kiểm kê 200 kg, hao hụt 100 kg.");
    }

    private void check(String label, long expected, String sql, Object... args) throws Exception {
        long actual = oneLong(sql, args);
        if (actual != expected) throw new IllegalStateException(label + ": cần " + expected + ", thực tế " + actual);
    }

    private static String pad(int value) { return String.format("%03d", value); }
    private LocalDateTime at(int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute);
    }

    private static void bind(PreparedStatement statement, Object... args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            if (value instanceof LocalDateTime date) value = Timestamp.valueOf(date);
            else if (value instanceof LocalDate date) value = java.sql.Date.valueOf(date);
            statement.setObject(i + 1, value);
        }
    }

    private long insert(String sql, Object... args) throws Exception {
        try (PreparedStatement statement = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, args);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("Không có ID từ lệnh INSERT");
                return keys.getLong(1);
            }
        }
    }

    private void exec(String sql, Object... args) throws Exception {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            bind(statement, args);
            statement.executeUpdate();
        }
    }

    private long oneLong(String sql, Object... args) throws Exception {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Không tìm thấy dữ liệu: " + sql);
                return rs.getLong(1);
            }
        }
    }
}
