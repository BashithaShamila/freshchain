import { useState } from "react";
import { apiRequest } from "../api/client";
import type { Lot } from "../api/types";
import { useSession } from "../auth/useSession";
import { ErrorBanner } from "../components/Feedback";
import { WAREHOUSE_ID } from "../config";

const SKUS = [
  "CHK-BRST-5LB",
  "CHK-THGH-5LB",
  "CHK-TNDR-4LB",
  "TKY-BRST-5LB",
  "BEF-GRND-10LB",
  "SLM-FLLT-4LB",
  "MLK-WHL-4GAL",
  "CHZ-MOZZ-5LB",
  "LET-ROM-24CT",
  "BRD-BRGR-96CT",
  "BRD-SUB-72CT",
];

function defaultExpiry(daysAhead: number): string {
  const date = new Date();
  date.setDate(date.getDate() + daysAhead);
  return date.toISOString().slice(0, 10);
}

export function ReceiveStock() {
  const { getToken } = useSession();
  const [sku, setSku] = useState(SKUS[0]);
  const [qty, setQty] = useState(50);
  const [expiryDate, setExpiryDate] = useState(defaultExpiry(14));
  const [received, setReceived] = useState<Lot[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const lot = await apiRequest<Lot>("/api/v1/lots", getToken, {
        method: "POST",
        body: { sku, warehouseId: WAREHOUSE_ID, qty, expiryDate },
      });
      setReceived((current) => [lot, ...current]);
    } catch (caught) {
      setError(caught);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section>
      <header className="page-head">
        <div>
          <h1>Receive stock</h1>
          <p className="muted">
            One receipt creates one lot. Stock is not fungible here: the expiry date travels with
            the lot and decides the order it goes out in.
          </p>
        </div>
      </header>

      <ErrorBanner error={error} />

      <form className="card receive" onSubmit={submit}>
        <label className="field">
          <span>Product</span>
          <select value={sku} onChange={(event) => setSku(event.target.value)}>
            {SKUS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>

        <label className="field">
          <span>Cases</span>
          <input
            type="number"
            min={1}
            value={qty}
            onChange={(event) => setQty(Math.max(1, Number(event.target.value) || 1))}
          />
        </label>

        <label className="field">
          <span>Expiry date</span>
          <input
            type="date"
            value={expiryDate}
            min={defaultExpiry(1)}
            onChange={(event) => setExpiryDate(event.target.value)}
          />
          <span className="muted small">
            Must be in the future — stock that is already out of date cannot be received.
          </span>
        </label>

        <div className="actions">
          <button type="submit" className="primary" disabled={busy}>
            {busy ? "Receiving…" : "Receive into stock"}
          </button>
        </div>
      </form>

      {received.length > 0 && (
        <div className="card">
          <h2>Received this session</h2>
          <table className="table">
            <thead>
              <tr>
                <th>Lot</th>
                <th>SKU</th>
                <th>Expires</th>
                <th className="num">Cases</th>
              </tr>
            </thead>
            <tbody>
              {received.map((lot) => (
                <tr key={lot.lotId}>
                  <td className="mono">{lot.lotId.slice(0, 8)}</td>
                  <td className="mono">{lot.sku}</td>
                  <td>{lot.expiryDate}</td>
                  <td className="num strong">{lot.qtyOnHand}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
