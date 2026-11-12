package com.aluminum.manager.system;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.system.BankReceivingAccountDtos.BankAccountDto;
import com.aluminum.manager.system.BankReceivingAccountDtos.BankAccountRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankReceivingAccountService {

	private final JdbcTemplate jdbc;
	private final AuditService audit;

	public BankReceivingAccountService(JdbcTemplate jdbc, AuditService audit) {
		this.jdbc = jdbc;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<BankAccountDto> list() {
		return jdbc.query("""
				SELECT id, label, bank_bin, account_no, account_name, image_path, is_default, sort_order
				FROM bank_receiving_accounts
				ORDER BY is_default DESC, sort_order ASC, id ASC
				""", (rs, i) -> new BankAccountDto(
				rs.getLong("id"),
				rs.getString("label"),
				rs.getString("bank_bin"),
				rs.getString("account_no"),
				rs.getString("account_name"),
				rs.getString("image_path"),
				rs.getBoolean("is_default"),
				rs.getInt("sort_order")));
	}

	@Transactional(readOnly = true)
	public BankAccountDto getDefault() {
		return list().stream().filter(BankAccountDto::isDefault).findFirst()
				.or(() -> list().stream().findFirst())
				.orElse(null);
	}

	@Transactional
	public BankAccountDto create(BankAccountRequest request) {
		String label = request.label().trim();
		String bin = request.bankBin().trim();
		String accountNo = request.accountNo().trim();
		String accountName = blankToEmpty(request.accountName()).toUpperCase();
		String image = blankToNull(request.imagePath());
		boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || count() == 0;
		if (makeDefault) {
			clearDefault();
		}
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO bank_receiving_accounts
					(label, bank_bin, account_no, account_name, image_path, is_default, sort_order)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, label);
			ps.setString(2, bin);
			ps.setString(3, accountNo);
			ps.setString(4, accountName);
			ps.setString(5, image);
			ps.setBoolean(6, makeDefault);
			ps.setInt(7, nextSort());
			return ps;
		}, keys);
		long id = keys.getKey().longValue();
		audit.record(CurrentAdmin.get().id(), "CREATE_BANK_ACCOUNT", "bank_receiving_accounts",
				String.valueOf(id), "Thêm TK QR " + accountNo);
		return get(id);
	}

	@Transactional
	public BankAccountDto update(long id, BankAccountRequest request) {
		get(id);
		String label = request.label().trim();
		String bin = request.bankBin().trim();
		String accountNo = request.accountNo().trim();
		String accountName = blankToEmpty(request.accountName()).toUpperCase();
		String image = blankToNull(request.imagePath());
		boolean makeDefault = Boolean.TRUE.equals(request.isDefault());
		if (makeDefault) {
			clearDefault();
		}
		jdbc.update("""
				UPDATE bank_receiving_accounts
				SET label = ?, bank_bin = ?, account_no = ?, account_name = ?, image_path = ?,
				    is_default = CASE WHEN ? THEN 1 ELSE is_default END
				WHERE id = ?
				""", label, bin, accountNo, accountName, image, makeDefault, id);
		ensureOneDefault();
		audit.record(CurrentAdmin.get().id(), "UPDATE_BANK_ACCOUNT", "bank_receiving_accounts",
				String.valueOf(id), "Sửa TK QR " + accountNo);
		return get(id);
	}

	@Transactional
	public void delete(long id) {
		BankAccountDto existing = get(id);
		jdbc.update("DELETE FROM bank_receiving_accounts WHERE id = ?", id);
		if (existing.isDefault()) {
			ensureOneDefault();
		}
		audit.record(CurrentAdmin.get().id(), "DELETE_BANK_ACCOUNT", "bank_receiving_accounts",
				String.valueOf(id), "Xóa TK QR " + existing.accountNo());
	}

	@Transactional
	public BankAccountDto setDefault(long id) {
		get(id);
		clearDefault();
		jdbc.update("UPDATE bank_receiving_accounts SET is_default = 1 WHERE id = ?", id);
		audit.record(CurrentAdmin.get().id(), "DEFAULT_BANK_ACCOUNT", "bank_receiving_accounts",
				String.valueOf(id), "Đặt TK QR mặc định");
		return get(id);
	}

	private BankAccountDto get(long id) {
		List<BankAccountDto> rows = jdbc.query("""
				SELECT id, label, bank_bin, account_no, account_name, image_path, is_default, sort_order
				FROM bank_receiving_accounts WHERE id = ?
				""", (rs, i) -> new BankAccountDto(
				rs.getLong("id"),
				rs.getString("label"),
				rs.getString("bank_bin"),
				rs.getString("account_no"),
				rs.getString("account_name"),
				rs.getString("image_path"),
				rs.getBoolean("is_default"),
				rs.getInt("sort_order")), id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy tài khoản QR", null);
		}
		return rows.get(0);
	}

	private void clearDefault() {
		jdbc.update("UPDATE bank_receiving_accounts SET is_default = 0");
	}

	private void ensureOneDefault() {
		Integer defaults = jdbc.queryForObject(
				"SELECT COUNT(*) FROM bank_receiving_accounts WHERE is_default = 1", Integer.class);
		if (defaults != null && defaults > 0) {
			return;
		}
		Long first = jdbc.query("""
				SELECT id FROM bank_receiving_accounts ORDER BY sort_order ASC, id ASC LIMIT 1
				""", (rs, i) -> rs.getLong("id")).stream().findFirst().orElse(null);
		if (first != null) {
			jdbc.update("UPDATE bank_receiving_accounts SET is_default = 1 WHERE id = ?", first);
		}
	}

	private int count() {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM bank_receiving_accounts", Integer.class);
		return n == null ? 0 : n;
	}

	private int nextSort() {
		Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), -1) FROM bank_receiving_accounts", Integer.class);
		return (max == null ? -1 : max) + 1;
	}

	private static String blankToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
