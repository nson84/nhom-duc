package com.aluminum.manager.inventory;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentSequenceService {

	private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");

	private final JdbcTemplate jdbc;

	public DocumentSequenceService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public String nextCode(String documentType, LocalDate documentDate) {
		String key = DocumentCodes.sequenceKey(documentType);
		String periodKey = documentDate.format(PERIOD);
		jdbc.update("""
				INSERT IGNORE INTO document_sequences (document_type, period_key, last_number)
				VALUES (?, ?, 0)
				""", key, periodKey);
		Integer current = jdbc.queryForObject("""
				SELECT last_number
				FROM document_sequences
				WHERE document_type = ? AND period_key = ?
				FOR UPDATE
				""", Integer.class, key, periodKey);
		if (current == null) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không cấp được số phiếu", null);
		}
		int next = current + 1;
		jdbc.update("""
				UPDATE document_sequences
				SET last_number = ?
				WHERE document_type = ? AND period_key = ?
				""", next, key, periodKey);
		return DocumentCodes.format(documentType, YearMonth.from(documentDate), next);
	}
}
