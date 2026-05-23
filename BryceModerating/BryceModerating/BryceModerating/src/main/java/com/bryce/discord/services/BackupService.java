package com.bryce.discord.services;

import java.io.*;
import java.nio.file.*;
import java.util.Timer;
import java.util.TimerTask;

public class BackupService {
    private static final String DB_FILE = "modbot.db";
    private static final String BACKUP_DIR = "backups";
    private static final String BACKUP_FILE = BACKUP_DIR + "/modbot_backup.db";
    private static final int BACKUP_INTERVAL_MINUTES = 30;

    private Timer backupTimer;

    public BackupService() {

        try {
            Files.createDirectories(Paths.get(BACKUP_DIR));
            System.out.println("[BackupService] Backup directory created/verified: " + BACKUP_DIR);
        } catch (IOException e) {
            System.err.println("[BackupService] Failed to create backup directory: " + e.getMessage());
        }
    }

    public void restoreFromBackup() {
        File backupFile = new File(BACKUP_FILE);
        File dbFile = new File(DB_FILE);

        if (backupFile.exists() && backupFile.length() > 0) {
            try {

                if (!dbFile.exists() || dbFile.length() < backupFile.length()) {
                    Files.copy(backupFile.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[BackupService] ✅ Database restored from backup successfully!");
                    System.out.println("[BackupService] Restored " + backupFile.length() + " bytes");
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
        File dbFile = new File(DB_FILE);
        File backupFile = new File(BACKUP_FILE);

        if (!dbFile.exists()) {
            System.out.println("[BackupService] No database file to backup");
            return;
        }

        try {
            Files.copy(dbFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[BackupService] ✅ Database backed up successfully!");
            System.out.println("[BackupService] Backed up " + dbFile.length() + " bytes to " + BACKUP_FILE);
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
        }, BACKUP_INTERVAL_MINUTES * 60 * 1000, BACKUP_INTERVAL_MINUTES * 60 * 1000);

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
}


