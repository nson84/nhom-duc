import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

export type BackupLog = {
  id: number;
  backupPath: string | null;
  startedAt: string;
  completedAt: string | null;
  status: "RUNNING" | "SUCCESS" | "FAILED";
  fileSizeBytes: number | null;
  checksumSha256: string | null;
  errorMessage: string | null;
  createdAt: string;
};

export type BackupStatus = {
  latestBackup: BackupLog | null;
  lastBackupFailed: boolean;
  warningMessage: string | null;
  backupTime: string;
  retentionDays: number;
  effectiveDirectory: string;
};

export type BackupSettings = {
  backupTime: string;
  backupRetentionDays: number;
  backupDirectory: string;
};

export async function listBackups(): Promise<BackupLog[]> {
  return (await api.get<Envelope<BackupLog[]>>("/api/v1/backups")).data.data;
}

export async function getBackupStatus(): Promise<BackupStatus> {
  return (await api.get<Envelope<BackupStatus>>("/api/v1/backups/status")).data.data;
}

export async function createManualBackup(): Promise<BackupLog> {
  return (await api.post<Envelope<BackupLog>>("/api/v1/backups")).data.data;
}

export async function restoreBackup(id: number, input: { confirmText: string; reason: string }): Promise<BackupLog> {
  return (await api.post<Envelope<BackupLog>>(`/api/v1/backups/${id}/restore`, input)).data.data;
}

export async function updateBackupSettings(input: BackupSettings): Promise<BackupSettings> {
  return (await api.put<Envelope<BackupSettings>>("/api/v1/backups/settings", input)).data.data;
}
