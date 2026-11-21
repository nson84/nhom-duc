package com.aluminum.manager.backup;

import com.aluminum.manager.backup.BackupDtos.BackupLogDto;
import jakarta.servlet.http.HttpServletRequest;
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
        byte[] expected = token;
        byte[] provided;
        try { provided = HexFormat.of().parseHex(supplied == null ? "" : supplied); }
        catch (IllegalArgumentException e) { provided = new byte[0]; }
        if (!request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().equals("0:0:0:0:0:0:0:1"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (expected == null || !MessageDigest.isEqual(expected, provided))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!"startup".equals(reason) && !"shutdown".equals(reason))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        BackupLogDto result = backups.createDesktopBackup(reason);
        if (!"SUCCESS".equals(result.status()))
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, result.errorMessage());
        return result;
    }
}
