import { API_BASE_URL } from "../config";
import type { ApiErrorBody } from "./types";

/**
 * An API failure carrying the server's own message.
 *
 * The services return a structured error body, and showing that to the user is
 * far more useful than "request failed" — a 409 from the allocator explains
 * exactly which invariant it refused to break.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly details: string[];

  constructor(status: number, message: string, details: string[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.details = details;
  }

  /** Turns the auth failures into something a person can act on. */
  get friendlyMessage(): string {
    if (this.status === 401) {
      return "Your session has expired. Sign in again.";
    }
    if (this.status === 403) {
      return "Your account does not have permission for this. " + this.message;
    }
    return this.message;
  }
}

export type TokenSource = () => Promise<string>;

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  signal?: AbortSignal;
}

export async function apiRequest<T>(
  path: string,
  getToken: TokenSource,
  options: RequestOptions = {},
): Promise<T> {
  const { method = "GET", body, signal } = options;

  let token: string;
  try {
    token = await getToken();
  } catch {
    throw new ApiError(401, "Could not read your session token.");
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    signal,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const payload: unknown = text.length > 0 ? safeParse(text) : undefined;

  if (!response.ok) {
    const error = payload as ApiErrorBody | undefined;
    throw new ApiError(
      response.status,
      error?.message ?? `${response.status} ${response.statusText}`,
      error?.details ?? [],
    );
  }

  return payload as T;
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    // A proxy or error page answered with something that is not JSON.
    return { message: text.slice(0, 300) };
  }
}
