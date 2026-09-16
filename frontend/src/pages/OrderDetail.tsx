import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { apiRequest } from "../api/client";
import type { Order, Reservation } from "../api/types";
import { useSession } from "../auth/useSession";
import { ErrorBanner, Loading } from "../components/Feedback";
import { Status } from "../components/Status";

/** Statuses that mean allocation has finished, one way or another. */
const SETTLED = new Set(["ALLOCATED", "PARTIALLY_ALLOCATED", "REJECTED", "CONFIRMED", "SHIPPED", "CANCELLED"]);

export function OrderDetail() {
  const { orderId = "" } = useParams();
  const { getToken } = useSession();

  const [order, setOrder] = useState<Order | null>(null);
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const pollRef = useRef<number | null>(null);

  const load = useCallback(async () => {
    const fresh = await apiRequest<Order>(`/api/v1/orders/${orderId}`, getToken);
    setOrder(fresh);
    if (fresh.reservationId) {
      // Best effort: a rejected order has no reservation to show.
      try {
        setReservation(await apiRequest<Reservation>(`/api/v1/reservations/${orderId}`, getToken));
      } catch {
        setReservation(null);
      }
    }
    return fresh;
  }, [orderId, getToken]);

  // Allocation happens asynchronously over Kafka, so a freshly placed order
  // arrives here as PENDING_ALLOCATION. Poll until it settles, then stop —
  // an interval that never ends is how a demo page ends up hammering an API.
  useEffect(() => {
    let cancelled = false;

    const tick = async () => {
      try {
        const fresh = await load();
        if (!cancelled && SETTLED.has(fresh.status) && pollRef.current !== null) {
          window.clearInterval(pollRef.current);
          pollRef.current = null;
        }
      } catch (caught) {
        if (!cancelled) {
          setError(caught);
          if (pollRef.current !== null) {
            window.clearInterval(pollRef.current);
            pollRef.current = null;
          }
        }
      }
    };

    void tick();
    pollRef.current = window.setInterval(tick, 1500);
    return () => {
      cancelled = true;
      if (pollRef.current !== null) {
        window.clearInterval(pollRef.current);
      }
    };
  }, [load]);

  const act = async (action: "confirm" | "cancel") => {
    setBusy(true);
    setError(null);
    try {
      await apiRequest<Order>(`/api/v1/orders/${orderId}/${action}`, getToken, {
        method: "POST",
        body: action === "cancel" ? { reason: "cancelled from the FreshChain console" } : undefined,
      });
      await load();
    } catch (caught) {
      setError(caught);
    } finally {
      setBusy(false);
    }
  };

  if (!order) {
    return (
      <section>
        <ErrorBanner error={error} />
        {!error && <Loading label="Loading order…" />}
      </section>
    );
  }

  const canConfirm = order.status === "ALLOCATED" || order.status === "PARTIALLY_ALLOCATED";
  const canCancel = !["SHIPPED", "CANCELLED", "REJECTED"].includes(order.status);

  return (
    <section>
      <header className="page-head">
        <div>
          <h1>
            Order <span className="mono">{order.orderId.slice(0, 8)}</span>
          </h1>
          <p className="muted">
            <Status value={order.status} />
            {order.status === "PENDING_ALLOCATION" && (
              <span className="muted small"> · waiting for the allocator…</span>
            )}
            {order.statusReason && <span className="muted small"> · {order.statusReason}</span>}
          </p>
        </div>
        <div className="actions">
          {canConfirm && (
            <button type="button" className="primary" disabled={busy} onClick={() => act("confirm")}>
              Confirm
            </button>
          )}
          {canCancel && (
            <button type="button" className="ghost" disabled={busy} onClick={() => act("cancel")}>
              Cancel
            </button>
          )}
        </div>
      </header>

      <ErrorBanner error={error} />

      <div className="card">
        <h2>Lines</h2>
        <table className="table">
          <thead>
            <tr>
              <th>SKU</th>
              <th className="num">Requested</th>
              <th className="num">Allocated</th>
              <th className="num">Unit</th>
              <th className="num">Line total</th>
            </tr>
          </thead>
          <tbody>
            {order.lines.map((line) => (
              <tr key={line.sku} className={line.qtyAllocated < line.qtyRequested ? "short" : undefined}>
                <td className="mono">{line.sku}</td>
                <td className="num">{line.qtyRequested}</td>
                <td className="num strong">{line.qtyAllocated}</td>
                <td className="num">{line.unitPrice.toFixed(2)}</td>
                <td className="num">{line.lineTotal.toFixed(2)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={4}>Total (priced on what was allocated)</td>
              <td className="num strong">{order.total.toFixed(2)}</td>
            </tr>
          </tfoot>
        </table>
      </div>

      {reservation && (
        <div className="card">
          <h2>Which lots this drew from</h2>
          <p className="muted small">
            First-expired-first-out: nearest expiry is consumed first, spilling into later lots only
            once the earlier one is exhausted.
          </p>
          <table className="table">
            <thead>
              <tr>
                <th>Lot</th>
                <th>SKU</th>
                <th className="num">Quantity</th>
              </tr>
            </thead>
            <tbody>
              {reservation.lines.map((line) => (
                <tr key={line.lotId + line.sku}>
                  <td className="mono">{line.lotId.slice(0, 8)}</td>
                  <td className="mono">{line.sku}</td>
                  <td className="num strong">{line.qty}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="muted small">
            Reservation <Status value={reservation.status} /> · held until{" "}
            {new Date(reservation.expiresAt).toLocaleString()} — unconfirmed holds are swept back
            onto the shelf automatically.
          </p>
        </div>
      )}

      {order.substitutions && order.substitutions.length > 0 && (
        <div className="card">
          <h2>Suggested alternatives</h2>
          <p className="muted small">
            Proposed by similarity search, then filtered: anything introducing an allergen the
            original does not carry is removed before it ever reaches this page.
          </p>
          <ul className="suggestions">
            {order.substitutions.map((suggestion) => (
              <li key={suggestion.suggestedSku}>
                <div>
                  <span className="mono">{suggestion.suggestedSku}</span>{" "}
                  <strong>{suggestion.suggestedName}</strong>
                </div>
                <span className="muted small">{suggestion.rationale}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
