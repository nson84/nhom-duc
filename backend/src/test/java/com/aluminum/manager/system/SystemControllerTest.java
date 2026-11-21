package com.aluminum.manager.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemControllerTest {
    @Test void readinessReturnsThePackagedRevision() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("44");
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("aluminum_app");
        var controller = new SystemController(jdbc);
        var values = new java.util.Properties();
        values.setProperty("version", "0.0.1-SNAPSHOT");
        values.setProperty("revision", "fixture-revision-dirty");
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "build", new org.springframework.boot.info.BuildProperties(values));
        assertEquals("fixture-revision-dirty", controller.ready().data().revision());
        assertEquals("0.0.1-SNAPSHOT", controller.ready().data().version());
        assertEquals("aluminum_app", controller.ready().data().databaseName());
    }

    @Test void readinessIdentifiesUnversionedDevelopmentBuildsExplicitly() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(String.class))).thenReturn("44");
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("aluminum_app");
        var status = new SystemController(jdbc).ready().data();
        assertTrue(status.toString().contains("revision=unversioned"), "Do not imply that a development backend is the latest Git revision");
        assertTrue(status.toString().contains("schemaVersion=44"));
    }
}
