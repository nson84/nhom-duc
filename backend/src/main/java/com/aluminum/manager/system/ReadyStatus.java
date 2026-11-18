package com.aluminum.manager.system;

public record ReadyStatus(String status, String database, String schemaVersion, String application) {
    public ReadyStatus(String status, String database, String schemaVersion) {
        this(status, database, schemaVersion, "aluminum-manager");
    }
}
