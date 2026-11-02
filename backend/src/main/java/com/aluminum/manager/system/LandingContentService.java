package com.aluminum.manager.system;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.security.CurrentAdmin;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lưu chữ từng phần landing page dưới dạng key trong {@code app_settings}
 * (cùng kiểu với cấu hình cửa hàng), tránh phụ thuộc Jackson trên classpath biên dịch.
 */
@Service
public class LandingContentService {

	private static final String HERO_TITLE = "landing.hero_title";
	private static final String HERO_SUBTITLE = "landing.hero_subtitle";
	private static final String CTA_HEADER = "landing.cta_header";
	private static final String CTA_PRIMARY = "landing.cta_primary";
	private static final String CATEGORIES_TITLE = "landing.categories_title";
	private static final String CONTACT_TITLE = "landing.contact_title";
	private static final String CONTACT_DESCRIPTION = "landing.contact_description";
	private static final String PRIVACY_NOTE = "landing.privacy_note";
	private static final String FORM_SUBMIT = "landing.form_submit_label";
	private static final String FORM_SUCCESS_TITLE = "landing.form_success_title";
	private static final String FORM_SUCCESS_DESCRIPTION = "landing.form_success_description";
	private static final String FORM_AGAIN = "landing.form_again_label";

	private final AppSettingRepository settings;
	private final AuditService auditService;

	public LandingContentService(AppSettingRepository settings, AuditService auditService) {
		this.settings = settings;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public LandingContent get() {
		LandingContent defaults = LandingContent.defaults();
		List<LandingContent.Strength> strengths = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			LandingContent.Strength fallback = defaults.strengths().get(i);
			strengths.add(new LandingContent.Strength(
					value(strengthTitleKey(i), fallback.title()),
					value(strengthTextKey(i), fallback.text())));
		}
		return new LandingContent(
				value(HERO_TITLE, defaults.heroTitle()),
				value(HERO_SUBTITLE, defaults.heroSubtitle()),
				value(CTA_HEADER, defaults.ctaHeader()),
				value(CTA_PRIMARY, defaults.ctaPrimary()),
				value(CATEGORIES_TITLE, defaults.categoriesTitle()),
				value(CONTACT_TITLE, defaults.contactTitle()),
				value(CONTACT_DESCRIPTION, defaults.contactDescription()),
				value(PRIVACY_NOTE, defaults.privacyNote()),
				value(FORM_SUBMIT, defaults.formSubmitLabel()),
				value(FORM_SUCCESS_TITLE, defaults.formSuccessTitle()),
				value(FORM_SUCCESS_DESCRIPTION, defaults.formSuccessDescription()),
				value(FORM_AGAIN, defaults.formAgainLabel()),
				List.copyOf(strengths));
	}

	@Transactional
	public LandingContent update(LandingContent request) {
		LandingContent defaults = LandingContent.defaults();
		LandingContent normalized = normalize(request, defaults);
		save(HERO_TITLE, normalized.heroTitle());
		save(HERO_SUBTITLE, normalized.heroSubtitle());
		save(CTA_HEADER, normalized.ctaHeader());
		save(CTA_PRIMARY, normalized.ctaPrimary());
		save(CATEGORIES_TITLE, normalized.categoriesTitle());
		save(CONTACT_TITLE, normalized.contactTitle());
		save(CONTACT_DESCRIPTION, normalized.contactDescription());
		save(PRIVACY_NOTE, normalized.privacyNote());
		save(FORM_SUBMIT, normalized.formSubmitLabel());
		save(FORM_SUCCESS_TITLE, normalized.formSuccessTitle());
		save(FORM_SUCCESS_DESCRIPTION, normalized.formSuccessDescription());
		save(FORM_AGAIN, normalized.formAgainLabel());
		for (int i = 0; i < 4; i++) {
			LandingContent.Strength item = normalized.strengths().get(i);
			save(strengthTitleKey(i), item.title());
			save(strengthTextKey(i), item.text());
		}
		var admin = CurrentAdmin.get();
		auditService.record(
				admin.id(), "UPDATE_LANDING_CONTENT", "app_settings", null, "Cập nhật nội dung trang giới thiệu");
		return normalized;
	}

	private LandingContent normalize(LandingContent input, LandingContent defaults) {
		if (input == null) {
			return defaults;
		}
		List<LandingContent.Strength> strengths = new ArrayList<>();
		List<LandingContent.Strength> source =
				input.strengths() == null || input.strengths().isEmpty()
						? defaults.strengths()
						: input.strengths();
		for (int i = 0; i < 4; i++) {
			LandingContent.Strength fallback = defaults.strengths().get(i);
			LandingContent.Strength item = i < source.size() && source.get(i) != null ? source.get(i) : fallback;
			strengths.add(new LandingContent.Strength(
					requireText(item.title(), fallback.title(), 120),
					requireText(item.text(), fallback.text(), 500)));
		}
		return new LandingContent(
				requireText(input.heroTitle(), defaults.heroTitle(), 200),
				requireText(input.heroSubtitle(), defaults.heroSubtitle(), 1000),
				requireText(input.ctaHeader(), defaults.ctaHeader(), 80),
				requireText(input.ctaPrimary(), defaults.ctaPrimary(), 80),
				requireText(input.categoriesTitle(), defaults.categoriesTitle(), 120),
				requireText(input.contactTitle(), defaults.contactTitle(), 120),
				requireText(input.contactDescription(), defaults.contactDescription(), 1000),
				requireText(input.privacyNote(), defaults.privacyNote(), 300),
				requireText(input.formSubmitLabel(), defaults.formSubmitLabel(), 80),
				requireText(input.formSuccessTitle(), defaults.formSuccessTitle(), 120),
				requireText(input.formSuccessDescription(), defaults.formSuccessDescription(), 500),
				requireText(input.formAgainLabel(), defaults.formAgainLabel(), 80),
				List.copyOf(strengths));
	}

	private void save(String key, String value) {
		AppSetting setting = settings.findById(key).orElseGet(() -> new AppSetting(key, value));
		setting.setSettingValue(value);
		settings.save(setting);
	}

	private String value(String key, String fallback) {
		return settings.findById(key).map(AppSetting::getSettingValue).filter(v -> v != null && !v.isBlank()).orElse(fallback);
	}

	private static String strengthTitleKey(int index) {
		return "landing.strength_" + (index + 1) + "_title";
	}

	private static String strengthTextKey(int index) {
		return "landing.strength_" + (index + 1) + "_text";
	}

	private static String requireText(String value, String fallback, int max) {
		String text = value == null ? "" : value.trim();
		if (text.isEmpty()) {
			text = fallback;
		}
		if (text.length() > max) {
			throw new BusinessException(
					ErrorCode.VALIDATION_ERROR, "Nội dung quá dài (tối đa " + max + " ký tự)", null);
		}
		return text;
	}
}
