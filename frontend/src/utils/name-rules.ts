import type { Rule } from "antd/es/form";

/** Bắt đầu bằng chữ số, hoặc dấu +/- rồi chữ số (vd. 10c, -1, +2). */
export function startsWithNumber(value: string): boolean {
  return /^[+-]?\d/u.test(value.trim());
}

/** Tên người / đối tác / danh mục: nếu có nội dung thì không được bắt đầu bằng số. */
export const noLeadingDigitRule: Rule = {
  validator: async (_, value) => {
    if (value == null || String(value).trim() === "") {
      return;
    }
    if (startsWithNumber(String(value))) {
      throw new Error("Không được bắt đầu bằng số");
    }
  },
};

export function requiredNameRules(message: string, max?: number): Rule[] {
  const rules: Rule[] = [
    { required: true, whitespace: true, message },
    noLeadingDigitRule,
  ];
  if (max != null) {
    rules.push({ max, message: `Tối đa ${max} ký tự` });
  }
  return rules;
}

export function optionalNameRules(max?: number): Rule[] {
  const rules: Rule[] = [noLeadingDigitRule];
  if (max != null) {
    rules.push({ max, message: `Tối đa ${max} ký tự` });
  }
  return rules;
}
