package com.aluminum.manager.backup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class DesktopShutdownBackup {
    private final BackupService backups;

    DesktopShutdownBackup(BackupService backups) { this.backups = backups; }

    @EventListener(ApplicationReadyEvent.class)
    void listen() {
        Thread reader = new Thread(() -> {
            try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                if ("BACKUP_AND_EXIT".equals(input.readLine())) {
                    try { backups.createShutdownBackup(); }
                    catch (Exception e) { System.err.println("Sao lưu khi đóng desktop thất bại: " + e.getMessage()); }
                    finally { System.exit(0); }
                }
            } catch (Exception e) {
                System.err.println("Không thực hiện được sao lưu khi đóng desktop: " + e.getMessage());
            }
        }, "desktop-shutdown-backup");
        reader.setDaemon(true);
        reader.start();
    }
}
