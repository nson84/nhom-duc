import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

export type BackupLog = {
  id: number;
  backupPath: string | null;
  startedAt: string;
  completedAt: string | null;
  status: "RUNNING" | "SUCCESS" | "FAILED" | "PRUNED";
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
  frequencyHours: number;
  nextBackupAt: string;
  retentionDays: number;
  effectiveDirectory: string;
};

export type BackupSettings = {
  backupTime: string;
  frequencyHours: number;
  backupRetentionDays: number;
  backupDirectory: string;
};

export type CloudSettings = {
  endpoint: string;
  bucket: string;
  accessKeyId: string;
  configured: boolean;
};

export type CloudInput = Omit<CloudSettings, "configured"> & { secretAccessKey?: string };

export type CloudStatus = { configured: boolean; pending: number; latestKey: string | null; lastError: string | null };
export type CloudObject = { key: string; size: number; uploadedAt: string };
export type PreparedBackup = { databaseName: string; sqlStatements: number; imagesVerified: number; imageDirectory: string; profilePath: string };

export async function getCloudStatus(): Promise<CloudStatus> {
  return (await api.get<Envelope<CloudStatus>>("/api/v1/backups/cloud/status")).data.data;
}

export async function listCloudObjects(): Promise<CloudObject[]> {
  return (await api.get<Envelope<CloudObject[]>>("/api/v1/backups/cloud/objects")).data.data;
}

export async function syncCloud(): Promise<number> {
  return (await api.post<Envelope<number>>("/api/v1/backups/cloud/sync")).data.data;
}

export async function downloadCloud(key: string): Promise<string> {
  return (await api.post<Envelope<string>>("/api/v1/backups/cloud/download", null, { params: { key } })).data.data;
}

export async function verifyCloud(key: string, recoveryKey = ""): Promise<void> {
  await api.post("/api/v1/backups/cloud/verify", { key, recoveryKey });
}

export async function prepareCloud(key: string, recoveryKey: string,
  databaseUsername: string, databasePassword: string): Promise<PreparedBackup> {
  return (await api.post<Envelope<PreparedBackup>>("/api/v1/backups/cloud/prepare",
    { key, recoveryKey, databaseUsername, databasePassword })).data.data;
}

export async function prepareLocal(id: number, username: string, password: string): Promise<PreparedBackup> {
  return (await api.post<Envelope<PreparedBackup>>(`/api/v1/backups/${id}/prepare`, { username, password })).data.data;
}

export async function verifyLocal(id: number): Promise<void> {
  await api.post(`/api/v1/backups/${id}/verify`);
}

export async function getRecoveryKey(): Promise<string> {
  return (await api.get<Envelope<string>>("/api/v1/backups/recovery-key")).data.data;
}

export async function importRecoveryKey(value: string): Promise<void> {
  await api.post("/api/v1/backups/recovery-key/import", { value });
}

export async function getCloudSettings(): Promise<CloudSettings> {
  return (await api.get<Envelope<CloudSettings>>("/api/v1/backups/cloud")).data.data;
}

export async function saveCloudSettings(input: CloudInput): Promise<CloudSettings> {
  return (await api.put<Envelope<CloudSettings>>("/api/v1/backups/cloud", input)).data.data;
}

export async function testCloudConnection(): Promise<void> {
  await api.post("/api/v1/backups/cloud/test");
}

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
