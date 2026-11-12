import { api } from "../../services/api-client";

type Envelope<T> = { success: boolean; data: T; message?: string };

export type Customer = {
  id: number;
  name: string;
  phone: string | null;
  note: string | null;
  active: boolean;
  shopCode: string;
  shopName: string;
  totalSales: number;
  outstandingDebt: number;
  openInvoiceCount: number;
  receivedAmount: number;
  deliveredKg: number;
};

export type CustomerSummary = {
  id: number;
  name: string;
  phone: string | null;
  note: string | null;
  active: boolean;
  shopCode: string;
  shopName: string;
  totalSales: number;
  outstandingDebt: number;
  saleCount: number;
  openInvoiceCount: number;
};

export type CustomerDocument = {
  id: number;
  documentCode: string;
  documentDate: string;
  status: string;
  totalAmount: number;
  paidAmount: number;
  remainingAmount: number;
  paymentStatus: "UNPAID" | "PARTIAL" | "PAID" | string;
};

export type CustomerReminder = {
  id: number;
  customerId: number;
  customerName: string;
  documentId: number | null;
  documentCode: string | null;
  remindOn: string;
  note: string | null;
  done: boolean;
  shopCode: string;
  shopName: string;
};

export type CustomerInput = {
  name: string;
  phone?: string;
  note?: string;
  active?: boolean;
};

export async function listCustomers(params?: {
  q?: string;
  active?: boolean;
  from?: string;
  to?: string;
  scope?: "all";
}): Promise<Customer[]> {
  return (await api.get<Envelope<Customer[]>>("/api/v1/customers", { params })).data.data;
}

export async function getCustomer(id: number, scope?: "all"): Promise<CustomerSummary> {
  return (await api.get<Envelope<CustomerSummary>>(`/api/v1/customers/${id}`, { params: { scope } })).data.data;
}

export async function saveCustomer(id: number | undefined, input: CustomerInput): Promise<Customer> {
  const response = id
    ? await api.put<Envelope<Customer>>(`/api/v1/customers/${id}`, input)
    : await api.post<Envelope<Customer>>("/api/v1/customers", input);
  return response.data.data;
}

export async function listCustomerDocuments(
  id: number,
  paymentStatus?: string,
  scope?: "all",
): Promise<CustomerDocument[]> {
  return (
    await api.get<Envelope<CustomerDocument[]>>(`/api/v1/customers/${id}/documents`, {
      params: { paymentStatus, scope },
    })
  ).data.data;
}

export async function listCustomerReminders(id: number, scope?: "all"): Promise<CustomerReminder[]> {
  return (
    await api.get<Envelope<CustomerReminder[]>>(`/api/v1/customers/${id}/reminders`, { params: { scope } })
  ).data.data;
}

export async function createCustomerReminder(
  id: number,
  input: { remindOn: string; documentId?: number; note?: string },
): Promise<CustomerReminder> {
  return (await api.post<Envelope<CustomerReminder>>(`/api/v1/customers/${id}/reminders`, input)).data.data;
}

export async function setReminderDone(customerId: number, reminderId: number, done: boolean): Promise<CustomerReminder> {
  return (
    await api.put<Envelope<CustomerReminder>>(`/api/v1/customers/${customerId}/reminders/${reminderId}`, { done })
  ).data.data;
}

export async function listDueReminders(scope?: "all"): Promise<CustomerReminder[]> {
  return (
    await api.get<Envelope<{ items: CustomerReminder[] }>>("/api/v1/customers/due-reminders", {
      params: { scope },
    })
  ).data.data.items;
}

export async function linkDocumentCustomer(documentId: number, customerId: number | null) {
  return (
    await api.put<Envelope<unknown>>(`/api/v1/stock-documents/${documentId}/customer`, { customerId })
  ).data.data;
}
