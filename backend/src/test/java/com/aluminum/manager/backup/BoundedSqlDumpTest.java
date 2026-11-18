package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BoundedSqlDumpTest {
    @Test void refusesOversizedAppendBeforeGrowingTheBuffer() throws Exception {
        var dump = new BoundedSqlDump(8);
        dump.append("1234567").append('\n');
        assertThrows(java.io.IOException.class, () -> dump.append("extra"));
        assertEquals("1234567\n", dump.toString());
        assertEquals(0, dump.remaining());
    }
}
