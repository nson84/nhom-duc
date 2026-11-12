import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

export type BankAccount = {
  id: number;
  label: string;
  bankBin: string;
  accountNo: string;
  accountName: string;
  imagePath: string | null;
  isDefault: boolean;
  sortOrder: number;
};

export type BankAccountInput = {
  label: string;
  bankBin: string;
  accountNo: string;
  accountName?: string;
  imagePath?: string | null;
  isDefault?: boolean;
};

export async function listBankAccounts(): Promise<BankAccount[]> {
  return (await api.get<Envelope<BankAccount[]>>("/api/v1/bank-accounts")).data.data;
}

export async function getDefaultBankAccount(): Promise<BankAccount | null> {
  return (await api.get<Envelope<BankAccount | null>>("/api/v1/bank-accounts/default")).data.data;
}

export async function createBankAccount(input: BankAccountInput): Promise<BankAccount> {
  return (await api.post<Envelope<BankAccount>>("/api/v1/bank-accounts", input)).data.data;
}

export async function updateBankAccount(id: number, input: BankAccountInput): Promise<BankAccount> {
  return (await api.put<Envelope<BankAccount>>(`/api/v1/bank-accounts/${id}`, input)).data.data;
}

export async function setDefaultBankAccount(id: number): Promise<BankAccount> {
  return (await api.post<Envelope<BankAccount>>(`/api/v1/bank-accounts/${id}/default`)).data.data;
}

export async function deleteBankAccount(id: number): Promise<void> {
  await api.delete(`/api/v1/bank-accounts/${id}`);
}
