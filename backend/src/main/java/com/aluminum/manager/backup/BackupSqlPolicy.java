package com.aluminum.manager.backup;

import java.io.IOException;
import java.util.regex.Pattern;

/** Only the literal INSERT/DELETE/SET grammar emitted by our dump writer may run in staging. */
final class BackupSqlPolicy {
    private static final String NAME = "`[a-z_][a-z0-9_]*`";
    private static final Pattern INSERT = Pattern.compile("INSERT INTO " + NAME + " \\((" + NAME + "(?:, " + NAME + ")*)\\) VALUES \\((.*)\\);");
    private static final Pattern DELETE = Pattern.compile("DELETE FROM " + NAME + ";");
    private static final Pattern VALUES = Pattern.compile("(?:NULL|TRUE|FALSE|\\?|[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)(?:,\\s*(?:NULL|TRUE|FALSE|\\?|[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?))*");

    static void validate(String line) throws IOException {
        if (line.isBlank() || line.startsWith("--")) return;
        if (line.equals("SET FOREIGN_KEY_CHECKS = 0;") || line.equals("SET FOREIGN_KEY_CHECKS = 1;") || DELETE.matcher(line).matches()) return;
        var insert = INSERT.matcher(line);
        if (!insert.matches()) throw invalid();
        String values = insert.group(2);
        var structure = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            char c = values.charAt(i);
            boolean hex = c == 'X' && i + 1 < values.length() && values.charAt(i + 1) == '\'';
            if (c != '\'' && !hex) { structure.append(c); continue; }
            if (hex) i++;
            int start = ++i;
            boolean closed = false;
            for (; i < values.length(); i++) {
                char quoted = values.charAt(i);
                if (quoted == '\'') { closed = true; break; }
                if (hex && Character.digit(quoted, 16) < 0) throw invalid();
                if (!hex && quoted == '\\' && ++i >= values.length()) throw invalid();
            }
            if (!closed || (hex && (i - start) % 2 != 0)) throw invalid();
            structure.append('?');
        }
        String literals = structure.toString();
        if (!VALUES.matcher(literals).matches() || literals.split(",", -1).length != insert.group(1).split(",", -1).length)
            throw invalid();
    }

    private static IOException invalid() {
        return new IOException("Câu lệnh ngoài định dạng sao lưu an toàn; không được phép thực thi trong phục hồi");
    }
}
