import { ApiError } from "../api/client";

export function ErrorBanner({ error }: { error: unknown }) {
  if (!error) {
    return null;
  }
  const isApi = error instanceof ApiError;
  const message = isApi ? error.friendlyMessage : String((error as Error)?.message ?? error);
  const details = isApi ? error.details : [];

  return (
    <div className="banner error" role="alert">
      <strong>{isApi ? `Request failed (${error.status})` : "Something went wrong"}</strong>
      <p>{message}</p>
      {details.length > 0 && (
        <ul>
          {details.map((detail) => (
            <li key={detail}>{detail}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function Loading({ label = "Loading…" }: { label?: string }) {
  return (
    <p className="muted" role="status">
      {label}
    </p>
  );
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <p className="muted empty">{children}</p>;
}
