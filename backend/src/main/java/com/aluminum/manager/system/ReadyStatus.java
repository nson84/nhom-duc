package com.aluminum.manager.system;

public record ReadyStatus(String status, String database, String databaseName, String schemaVersion, String application, String version, String revision) {
    public ReadyStatus(String status, String database, String databaseName, String schemaVersion) {
        this(status, database, databaseName, schemaVersion, "aluminum-manager", "development", "unversioned");
    }
}
