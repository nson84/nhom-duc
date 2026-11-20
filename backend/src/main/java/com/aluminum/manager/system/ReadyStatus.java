package com.aluminum.manager.system;

public record ReadyStatus(String status, String database, String schemaVersion, String application, String version, String revision) {
    public ReadyStatus(String status, String database, String schemaVersion) {
        this(status, database, schemaVersion, "aluminum-manager", "development", "unversioned");
    }
}
