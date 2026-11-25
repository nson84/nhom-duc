package com.aluminum.manager.backup;

import com.aluminum.manager.backup.BackupDtos.BackupLogDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/system/desktop-backup")
class DesktopBackupControl {
    private final BackupService backups;
    private volatile byte[] token;

    DesktopBackupControl(BackupService backups) { this.backups = backups; }

    @EventListener(ApplicationReadyEvent.class)
    void createToken() throws IOException {
        Path directory = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "AluminumManager");
        Files.createDirectories(directory);
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String value = HexFormat.of().formatHex(random);
        Path file = directory.resolve("desktop-backup-token");
        Path temp = Files.createTempFile(directory, "desktop-backup-token-", ".tmp");
        try {
            Files.writeString(temp, value, StandardCharsets.US_ASCII);
            R2CloudService.setOwnerOnly(temp);
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            R2CloudService.setOwnerOnly(file);
            token = random;
        } finally { Files.deleteIfExists(temp); }
    }

    @PostMapping("/{reason}")
    BackupLogDto backup(@PathVariable String reason, @RequestHeader(value = "X-Desktop-Backup-Token", required = false) String supplied,
                        HttpServletRequest request) {
        authorize(supplied, request);
        if (!"startup".equals(reason) && !"shutdown".equals(reason))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return checkedBackup(reason);
    }

    @PostMapping("/dev-handoff")
    void handoff(@RequestHeader(value = "X-Desktop-Backup-Token", required = false) String supplied,
                 HttpServletRequest request, HttpServletResponse response) throws IOException {
        requireDevHandoff(supplied, request);
        checkedBackup("shutdown");
        response.setStatus(200);
        response.setContentLength(0);
        response.flushBuffer();
        Thread shutdown = new Thread(() -> System.exit(0), "desktop-dev-handoff");
        shutdown.start();
    }

    @PostMapping("/dev-handoff-check")
    java.util.Map<String, String> handoffCheck(
            @RequestHeader(value = "X-Desktop-Backup-Token", required = false) String supplied,
            HttpServletRequest request) {
        requireDevHandoff(supplied, request);
        String imageDirectory = System.getenv("ALUMINUM_IMAGE_DIR");
        if (imageDirectory == null || !Path.of(imageDirectory).isAbsolute())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thư mục ảnh của backend dev không hợp lệ");
        return java.util.Map.of("imageDirectory", imageDirectory);
    }

    private void requireDevHandoff(String supplied, HttpServletRequest request) {
        authorize(supplied, request);
        if (!"true".equals(System.getenv("ALUMINUM_DEV_HANDOFF")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Backend này không bật bàn giao cho desktop");
    }

    private void authorize(String supplied, HttpServletRequest request) {
        byte[] expected = token;
        byte[] provided;
        try { provided = HexFormat.of().parseHex(supplied == null ? "" : supplied); }
        catch (IllegalArgumentException e) { provided = new byte[0]; }
        if (!request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().equals("0:0:0:0:0:0:0:1"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (expected == null || !MessageDigest.isEqual(expected, provided))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private BackupLogDto checkedBackup(String reason) {
        BackupLogDto result = backups.createDesktopBackup(reason);
        if (!"SUCCESS".equals(result.status()))
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, result.errorMessage());
        return result;
    }
}
