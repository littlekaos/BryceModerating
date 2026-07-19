package com.bryce.discord.services;

import java.io.*;
import java.nio.file.*;
import java.util.Timer;
import java.util.TimerTask;

public class BackupService {
    private static final String DB_NAME = "modbot.db";
    private static final String BACKUP_DIR_NAME = "backups";
    private static final String BACKUP_NAME = "modbot_backup.db";
    private static final int BACKUP_INTERVAL_MINUTES = 30;

    private final Path dataRoot;
    private final Path dbFile;
    private final Path backupFile;
    private Timer backupTimer;

    public BackupService() {
        dataRoot = resolveDataRoot();
        dbFile = dataRoot.resolve(DB_NAME);
        backupFile = dataRoot.resolve(BACKUP_DIR_NAME).resolve(BACKUP_NAME);

        try {
            Files.createDirectories(backupFile.getParent());
            System.out.println("[BackupService] Using data root: " + dataRoot);
            System.out.println("[BackupService] Backup directory created/verified: " + backupFile.getParent());
        } catch (IOException e) {
            System.err.println("[BackupService] Failed to create backup directory: " + e.getMessage());
        }
    }

    /**
     * Always use the repo root (where .env / modbot.db live), not whatever
     * folder the process was started from — avoids duplicate backups/.
     */
    private static Path resolveDataRoot() {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path current = cwd;
        for (int i = 0; i < 8 && current != null; i++) {
            if (Files.exists(current.resolve(".env")) || Files.exists(current.resolve(DB_NAME))) {
                return current;
            }
            current = current.getParent();
        }
        return cwd;
    }

    public void restoreFromBackup() {
        if (Files.exists(backupFile) && fileSize(backupFile) > 0) {
            try {
                if (!Files.exists(dbFile) || fileSize(dbFile) < fileSize(backupFile)) {
                    Files.copy(backupFile, dbFile, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[BackupService] ✅ Database restored from backup successfully!");
                    System.out.println("[BackupService] Restored " + fileSize(backupFile) + " bytes");
                } else {
                    System.out.println("[BackupService] Main database exists and is up to date, skipping restore");
                }
            } catch (IOException e) {
                System.err.println("[BackupService] ❌ Failed to restore database from backup: " + e.getMessage());
            }
        } else {
            System.out.println("[BackupService] No backup file found, starting fresh");
        }
    }

    public void createBackup() {
        if (!Files.exists(dbFile)) {
            System.out.println("[BackupService] No database file to backup");
            return;
        }

        try {
            Files.createDirectories(backupFile.getParent());
            Files.copy(dbFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[BackupService] ✅ Database backed up successfully!");
            System.out.println("[BackupService] Backed up " + fileSize(dbFile) + " bytes to " + backupFile);
        } catch (IOException e) {
            System.err.println("[BackupService] ❌ Failed to create backup: " + e.getMessage());
        }
    }

    public void startAutoBackup() {
        if (backupTimer != null) {
            backupTimer.cancel();
        }

        backupTimer = new Timer("DatabaseBackupTimer", true);
        backupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                createBackup();
            }
        }, BACKUP_INTERVAL_MINUTES * 60 * 1000L, BACKUP_INTERVAL_MINUTES * 60 * 1000L);

        System.out.println("[BackupService] ✅ Auto-backup started (every " + BACKUP_INTERVAL_MINUTES + " minutes)");
    }

    public void stopAutoBackup() {
        if (backupTimer != null) {
            backupTimer.cancel();
            backupTimer = null;
            System.out.println("[BackupService] Auto-backup stopped");
        }
    }

    public void onShutdown() {
        System.out.println("[BackupService] Creating final backup before shutdown...");
        createBackup();
        stopAutoBackup();
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }
}
