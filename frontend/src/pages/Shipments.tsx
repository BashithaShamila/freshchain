import { useState } from "react";
import { apiRequest } from "../api/client";
import type { Shipment } from "../api/types";
import { useSession } from "../auth/useSession";
import { ErrorBanner } from "../components/Feedback";
import { Status } from "../components/Status";

export function Shipments() {
  const { getToken } = useSession();
  const [orderId, setOrderId] = useState("");
  const [shipment, setShipment] = useState<Shipment | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  const run = async (path: string, method: "GET" | "POST") => {
    setBusy(true);
    setError(null);
    try {
      setShipment(await apiRequest<Shipment>(path, getToken, { method }));
    } catch (caught) {
      setError(caught);
      if (method === "GET") {
        setShipment(null);
      }
    } finally {
      setBusy(false);
    }
  };

  const id = orderId.trim();

  return (
    <section>
      <header className="page-head">
        <div>
          <h1>Warehouse</h1>
          <p className="muted">
            A shipment appears once inventory has allocated the stock and the customer has
            confirmed — the two arrive on separate topics, in either order.
          </p>
        </div>
      </header>

      <form
        className="card lookup"
        onSubmit={(event) => {
          event.preventDefault();
          if (id) {
            void run(`/api/v1/shipments/${id}`, "GET");
          }
        }}
      >
        <label className="field">
          <span>Order ID</span>
          <input
            value={orderId}
            onChange={(event) => setOrderId(event.target.value)}
            placeholder="paste an order id"
            spellCheck={false}
          />
        </label>
        <button type="submit" className="primary" disabled={!id || busy}>
          Find shipment
        </button>
      </form>

      <ErrorBanner error={error} />

      {shipment && (
        <div className="card">
          <div className="page-head">
            <div>
              <h2>
                Shipment <span className="mono">{shipment.shipmentId.slice(0, 8)}</span>
              </h2>
              <p className="muted">
                <Status value={shipment.status} /> · {shipment.totalQty} units
                {shipment.trackingRef && <span className="mono"> · {shipment.trackingRef}</span>}
              </p>
            </div>
            <div className="actions">
              <button
                type="button"
                className="ghost"
                disabled={busy || shipment.status !== "CREATED"}
                onClick={() => void run(`/api/v1/shipments/${shipment.orderId}/pick`, "POST")}
              >
                Mark picked
              </button>
              <button
                type="button"
                className="primary"
                disabled={busy || shipment.status !== "PICKED"}
                onClick={() => void run(`/api/v1/shipments/${shipment.orderId}/ship`, "POST")}
              >
                Dispatch
              </button>
            </div>
          </div>

          <p className="muted small">
            Dispatch is the point of no return: it publishes the event that finally decrements
            on-hand stock, and nothing puts a delivered pallet back on the rack.
          </p>

          <table className="table">
            <thead>
              <tr>
                <th>Lot to pick</th>
                <th>SKU</th>
                <th className="num">Quantity</th>
              </tr>
            </thead>
            <tbody>
              {shipment.lines.map((line) => (
                <tr key={line.lotId + line.sku}>
                  <td className="mono">{line.lotId.slice(0, 8)}</td>
                  <td className="mono">{line.sku}</td>
                  <td className="num strong">{line.qty}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
