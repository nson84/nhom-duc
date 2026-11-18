package com.aluminum.manager.backup;

import java.io.IOException;

/** Bounds the current in-memory SQL format before growing its buffer. */
final class BoundedSqlDump {
    static final int MAX_CHARACTERS = 32 * 1024 * 1024;
    private final int limit;
    private final StringBuilder content = new StringBuilder(65536);

    BoundedSqlDump(int limit) { this.limit = limit; }

    BoundedSqlDump append(Object value) throws IOException {
        String text = String.valueOf(value);
        if (text.length() > remaining()) throw new IOException("Dữ liệu sao lưu vượt giới hạn bộ nhớ an toàn; cần sao lưu MySQL chuyên dụng cho dữ liệu lớn");
        content.append(text);
        return this;
    }

    int remaining() { return limit - content.length(); }

    @Override public String toString() { return content.toString(); }
}
