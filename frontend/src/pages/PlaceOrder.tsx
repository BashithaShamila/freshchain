import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { apiRequest } from "../api/client";
import type { PlaceOrderResponse } from "../api/types";
import { useSession } from "../auth/useSession";
import { ErrorBanner } from "../components/Feedback";
import { WAREHOUSE_ID } from "../config";

interface DraftLine {
  sku: string;
  qty: number;
}

const SKUS = [
  "CHK-BRST-5LB",
  "CHK-THGH-5LB",
  "BEF-GRND-10LB",
  "SLM-FLLT-4LB",
  "MLK-WHL-4GAL",
  "LET-ROM-24CT",
  "BRD-BRGR-96CT",
  "BRD-SUB-72CT",
];

export function PlaceOrder() {
  const { getToken } = useSession();
  const navigate = useNavigate();

  const [lines, setLines] = useState<DraftLine[]>([{ sku: SKUS[0], qty: 40 }]);
  const [allowPartial, setAllowPartial] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (index: number, patch: Partial<DraftLine>) =>
    setLines((current) => current.map((line, i) => (i === index ? { ...line, ...patch } : line)));

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const response = await apiRequest<PlaceOrderResponse>("/api/v1/orders", getToken, {
        method: "POST",
        body: { warehouseId: WAREHOUSE_ID, allowPartial, lines },
      });
      // The API answers 202: the order exists but nothing is allocated yet, so
      // send the user somewhere that shows the outcome arriving.
      navigate(`/orders/${response.order.orderId}`);
    } catch (caught) {
      setError(caught);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section>
      <header className="page-head">
        <div>
          <h1>Place an order</h1>
          <p className="muted">
            Accepted immediately, allocated a moment later — the response is 202, not 201.
          </p>
        </div>
      </header>

      <ErrorBanner error={error} />

      <form className="card" onSubmit={submit}>
        <table className="table">
          <thead>
            <tr>
              <th>Product</th>
              <th className="num">Quantity</th>
              <th aria-label="Remove" />
            </tr>
          </thead>
          <tbody>
            {lines.map((line, index) => (
              <tr key={index}>
                <td>
                  <select
                    value={line.sku}
                    onChange={(event) => update(index, { sku: event.target.value })}
                  >
                    {SKUS.map((sku) => (
                      <option key={sku} value={sku}>
                        {sku}
                      </option>
                    ))}
                  </select>
                </td>
                <td className="num">
                  <input
                    type="number"
                    min={1}
                    value={line.qty}
                    onChange={(event) =>
                      update(index, { qty: Math.max(1, Number(event.target.value) || 1) })
                    }
                  />
                </td>
                <td className="num">
                  {lines.length > 1 && (
                    <button
                      type="button"
                      className="ghost small"
                      onClick={() => setLines((c) => c.filter((_, i) => i !== index))}
                    >
                      Remove
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <button
          type="button"
          className="ghost small"
          onClick={() => setLines((c) => [...c, { sku: SKUS[0], qty: 10 }])}
        >
          Add line
        </button>

        <label className="check">
          <input
            type="checkbox"
            checked={allowPartial}
            onChange={(event) => setAllowPartial(event.target.checked)}
          />
          <span>
            <strong>Accept a partial delivery</strong>
            <span className="muted small">
              With this off, an order that cannot be filled completely is rejected outright and
              holds no stock — half an order is of little use to a kitchen.
            </span>
          </span>
        </label>

        <div className="actions">
          <button type="submit" className="primary" disabled={submitting}>
            {submitting ? "Placing…" : "Place order"}
          </button>
        </div>
      </form>
    </section>
  );
}
