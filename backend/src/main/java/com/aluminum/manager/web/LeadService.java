package com.aluminum.manager.web;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.common.NameValidation;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.system.AuditService;
import com.aluminum.manager.web.LandingDtos.LeadCreatedResponse;
import com.aluminum.manager.web.LandingDtos.LeadDto;
import com.aluminum.manager.web.LandingDtos.LeadRequest;
import com.aluminum.manager.web.LandingDtos.LeadSummary;
import com.aluminum.manager.web.LandingDtos.LeadUpdateRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thu thông tin khách để lại trên landing page.
 *
 * <p>Đây là API công khai duy nhất của hệ thống, nên phải chịu được spam: giới hạn số lần gửi theo IP,
 * bẫy trường ẩn cho bot, và trùng số điện thoại trong ngày thì gộp lại thay vì tạo bản ghi rác.
 * Dữ liệu khách gửi không bao giờ được trả ngược ra cho người khác đọc, chỉ Admin đăng nhập mới xem.
 */
@Service
public class LeadService {

	private static final Logger log = LoggerFactory.getLogger(LeadService.class);
	private static final Set<String> STATUSES = Set.of("NEW", "CONTACTED", "CLOSED");
	private static final Set<String> REQUEST_TYPES = Set.of("BAO_GIA", "TU_VAN_KY_THUAT", "DAT_HANG", "KHAC");
	private static final int MAX_LEADS_PER_IP_PER_DAY = 5;

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final Clock clock;

	public LeadService(JdbcTemplate jdbc, AuditService audit, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * Ghi nhận khách để lại thông tin. Trả về id để giao diện hiện lời cảm ơn.
	 *
	 * <p>Không ném lỗi chi tiết khi bị chặn, tránh để bot dò được luật chống spam.
	 */
	@Transactional
	public LeadCreatedResponse submit(LeadRequest request, String ipAddress, String userAgent) {
		// Trường ẩn chỉ bot điền. Trả về thành công giả để bot không biết đã bị nhận diện.
		if (request.website() != null && !request.website().isBlank()) {
			log.info("Bỏ qua lead nghi spam (honeypot), ip={}", ipAddress);
			return new LeadCreatedResponse(0L);
		}
		assertRateLimitOk(ipAddress);

		NameValidation.requireNoLeadingDigit("Họ và tên", request.fullName());
		String phone = request.phone().trim().replaceAll("\\s|-|\\.", "");
		String name = request.fullName().trim();
		String type = REQUEST_TYPES.contains(String.valueOf(request.requestType()))
				? request.requestType()
				: "BAO_GIA";

		// Cùng một số điện thoại trong 1 giờ: cập nhật đơn cũ để không làm rối danh sách.
		List<Long> recent = jdbc.queryForList("""
				SELECT id FROM customer_leads
				WHERE phone = ? AND status = 'NEW' AND created_at >= ?
				ORDER BY created_at DESC LIMIT 1
				""", Long.class, phone, Timestamp.valueOf(LocalDateTime.now(clock).minusHours(1)));
		if (!recent.isEmpty()) {
			long id = recent.get(0);
			jdbc.update("""
					UPDATE customer_leads
					SET full_name = ?, email = ?, message = ?, request_type = ?, source_page = ?
					WHERE id = ?
					""", name, blank(request.email()), blank(request.message()), type,
					blank(request.sourcePage()), id);
			return new LeadCreatedResponse(id);
		}

		long id = insert(name, phone, request, type, ipAddress, userAgent);
		return new LeadCreatedResponse(id);
	}

	@Transactional(readOnly = true)
	public List<LeadDto> list(String status, String query) {
		StringBuilder sql = new StringBuilder("""
				SELECT l.*, a.username AS contacted_by_name
				FROM customer_leads l
				LEFT JOIN admin_users a ON a.id = l.contacted_by_admin_id
				WHERE 1 = 1
				""");
		List<Object> args = new ArrayList<>();
		if (status != null && !status.isBlank() && STATUSES.contains(status.trim())) {
			sql.append(" AND l.status = ?");
			args.add(status.trim());
		}
		if (query != null && !query.isBlank()) {
			sql.append(" AND (l.full_name LIKE ? OR l.phone LIKE ? OR l.email LIKE ?)");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		sql.append(" ORDER BY l.created_at DESC, l.id DESC LIMIT 500");
		return jdbc.query(sql.toString(), (rs, i) -> new LeadDto(
				rs.getLong("id"),
				rs.getString("full_name"),
				rs.getString("phone"),
				rs.getString("email"),
				rs.getString("message"),
				rs.getString("request_type"),
				rs.getString("status"),
				rs.getString("internal_note"),
				rs.getTimestamp("contacted_at") == null ? null : rs.getTimestamp("contacted_at").toLocalDateTime(),
				rs.getString("contacted_by_name"),
				rs.getString("source_page"),
				rs.getTimestamp("created_at").toLocalDateTime()), args.toArray());
	}

	@Transactional(readOnly = true)
	public LeadSummary summary() {
		return jdbc.queryForObject("""
				SELECT
				  COALESCE(SUM(CASE WHEN status = 'NEW' THEN 1 ELSE 0 END), 0) AS new_count,
				  COALESCE(SUM(CASE WHEN status = 'CONTACTED' THEN 1 ELSE 0 END), 0) AS contacted_count,
				  COALESCE(SUM(CASE WHEN status = 'CLOSED' THEN 1 ELSE 0 END), 0) AS closed_count,
				  COALESCE(SUM(CASE WHEN DATE(created_at) = ? THEN 1 ELSE 0 END), 0) AS today_count
				FROM customer_leads
				""", (rs, i) -> new LeadSummary(
				rs.getLong("new_count"),
				rs.getLong("contacted_count"),
				rs.getLong("closed_count"),
				rs.getLong("today_count")), java.sql.Date.valueOf(LocalDate.now(clock)));
	}

	@Transactional
	public LeadDto update(long id, LeadUpdateRequest request) {
		if (!STATUSES.contains(request.status().trim())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái không hợp lệ", null);
		}
		Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM customer_leads WHERE id = ?", Integer.class, id);
		if (exists == null || exists == 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy thông tin khách", null);
		}
		String status = request.status().trim();
		LocalDateTime now = LocalDateTime.now(clock);
		Long adminId = CurrentAdmin.get().id();
		if ("NEW".equals(status)) {
			jdbc.update("""
					UPDATE customer_leads
					SET status = ?, internal_note = ?, contacted_at = NULL, contacted_by_admin_id = NULL
					WHERE id = ?
					""", status, blank(request.internalNote()), id);
		} else {
			jdbc.update("""
					UPDATE customer_leads
					SET status = ?, internal_note = ?,
					    contacted_at = COALESCE(contacted_at, ?),
					    contacted_by_admin_id = COALESCE(contacted_by_admin_id, ?)
					WHERE id = ?
					""", status, blank(request.internalNote()), Timestamp.valueOf(now), adminId, id);
		}
		audit.record(adminId, "UPDATE_LEAD", "customer_leads", String.valueOf(id), "Cập nhật khách để lại thông tin: " + status);
		return get(id);
	}

	@Transactional(readOnly = true)
	public LeadDto get(long id) {
		List<LeadDto> rows = jdbc.query("""
				SELECT l.*, a.username AS contacted_by_name
				FROM customer_leads l
				LEFT JOIN admin_users a ON a.id = l.contacted_by_admin_id
				WHERE l.id = ?
				""", (rs, i) -> new LeadDto(
				rs.getLong("id"),
				rs.getString("full_name"),
				rs.getString("phone"),
				rs.getString("email"),
				rs.getString("message"),
				rs.getString("request_type"),
				rs.getString("status"),
				rs.getString("internal_note"),
				rs.getTimestamp("contacted_at") == null ? null : rs.getTimestamp("contacted_at").toLocalDateTime(),
				rs.getString("contacted_by_name"),
				rs.getString("source_page"),
				rs.getTimestamp("created_at").toLocalDateTime()), id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy thông tin khách", null);
		}
		return rows.get(0);
	}

	private void assertRateLimitOk(String ipAddress) {
		if (ipAddress == null || ipAddress.isBlank()) {
			return;
		}
		try {
			Integer count = jdbc.queryForObject("""
					SELECT COUNT(*) FROM customer_leads
					WHERE ip_address = ? AND created_at >= ?
					""", Integer.class, ipAddress, Timestamp.valueOf(LocalDate.now(clock).atStartOfDay()));
			if (count != null && count >= MAX_LEADS_PER_IP_PER_DAY) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Hôm nay đã gửi nhiều lần. Vui lòng gọi trực tiếp để được tư vấn ngay", null);
			}
		} catch (BusinessException ex) {
			throw ex;
		} catch (DataAccessException ex) {
			log.warn("Không kiểm tra được giới hạn gửi form", ex);
		}
	}

	private long insert(String name, String phone, LeadRequest request, String type,
			String ipAddress, String userAgent) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO customer_leads (full_name, phone, email, message, request_type,
					    status, source_page, ip_address, user_agent)
					VALUES (?, ?, ?, ?, ?, 'NEW', ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, name);
			ps.setString(2, phone);
			ps.setString(3, blank(request.email()));
			ps.setString(4, blank(request.message()));
			ps.setString(5, type);
			ps.setString(6, blank(request.sourcePage()));
			ps.setString(7, blank(ipAddress));
			ps.setString(8, truncate(userAgent, 300));
			return ps;
		}, keys);
		return keys.getKey().longValue();
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
