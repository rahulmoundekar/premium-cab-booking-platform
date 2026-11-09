export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:58080/api/v1";

async function readBody<T>(response: Response): Promise<T> {
  const raw = await response.text();
  let body: unknown = null;

  if (raw) {
    try {
      body = JSON.parse(raw) as unknown;
    } catch {
      body = raw;
    }
  }

  if (!response.ok) {
    let message = "The request could not be completed. Please try again.";
    if (typeof body === "object" && body !== null) {
      if ("message" in body) message = String((body as { message: unknown }).message);
      else if ("detail" in body) message = String((body as { detail: unknown }).detail);
    }
    throw new Error(message);
  }

  return body as T;
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(API_BASE_URL + path, {
    method: "GET",
    credentials: "include",
    cache: "no-store",
    headers: { Accept: "application/json" },
  });
  return readBody<T>(response);
}

export async function apiWrite<T>(
  path: string,
  method: "POST" | "PATCH",
  payload?: unknown,
): Promise<T> {
  const csrf = await apiGet<{ token: string }>("/auth/csrf");
  const headers = new Headers({
    Accept: "application/json",
    "Content-Type": "application/json",
    "X-XSRF-TOKEN": csrf.token,
  });

  const response = await fetch(API_BASE_URL + path, {
    method,
    credentials: "include",
    cache: "no-store",
    headers,
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });

  if (response.status === 204) {
    return undefined as T;
  }
  return readBody<T>(response);
}

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  emailVerified: boolean;
  roles: string[];
}

export interface CustomerProfile {
  id: string;
  email: string;
  fullName: string;
  phoneE164: string | null;
  emailVerified: boolean;
  phoneVerified: boolean;
  createdAt: string;
}
