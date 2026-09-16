import { useCallback, useEffect, useState } from "react";
import { apiRequest } from "../api/client";
import type { Availability } from "../api/types";
import { useSession } from "../auth/useSession";
import { ErrorBanner, Loading } from "../components/Feedback";
import { LotTable } from "../components/LotTable";
import { WAREHOUSE_ID } from "../config";

const SKUS = [
  "CHK-BRST-5LB",
  "CHK-THGH-5LB",
  "CHK-TNDR-4LB",
  "TKY-BRST-5LB",
  "BEF-GRND-10LB",
  "BEF-SRLN-8LB",
  "SLM-FLLT-4LB",
  "SHR-1621-5LB",
  "MLK-WHL-4GAL",
  "CHZ-MOZZ-5LB",
  "CHZ-CHDR-5LB",
  "LET-ROM-24CT",
  "LET-ICE-24CT",
  "TOM-ROMA-25LB",
  "BRD-BRGR-96CT",
  "BRD-SUB-72CT",
];

export function Catalog() {
  const { getToken } = useSession();
  const [sku, setSku] = useState(SKUS[0]);
  const [data, setData] = useState<Availability | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(
    async (target: string) => {
      setLoading(true);
      setError(null);
      try {
        const result = await apiRequest<Availability>(
          `/api/v1/inventory/${encodeURIComponent(target)}/availability?warehouseId=${WAREHOUSE_ID}`,
          getToken,
        );
        setData(result);
      } catch (caught) {
        setError(caught);
        setData(null);
      } finally {
        setLoading(false);
      }
    },
    [getToken],
  );

  useEffect(() => {
    void load(sku);
  }, [sku, load]);

  return (
    <section>
      <header className="page-head">
        <div>
          <h1>Catalog</h1>
          <p className="muted">Availability by SKU, broken down by lot and expiry date.</p>
        </div>
        <label className="field inline">
          <span>Product</span>
          <select value={sku} onChange={(event) => setSku(event.target.value)}>
            {SKUS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
      </header>

      <ErrorBanner error={error} />
      {loading && <Loading />}

      {data && !loading && (
        <>
          <div className="stats">
            <div className="stat">
              <span className="stat-value">{data.available}</span>
              <span className="stat-label">available</span>
            </div>
            <div className="stat">
              <span className="stat-value">{data.totalOnHand}</span>
              <span className="stat-label">on hand</span>
            </div>
            <div className="stat">
              <span className="stat-value">{data.totalReserved}</span>
              <span className="stat-label">reserved</span>
            </div>
            <div className="stat">
              <span className="stat-value">{data.nearestExpiry ?? "—"}</span>
              <span className="stat-label">nearest expiry</span>
            </div>
          </div>

          <div className="card">
            <h2>{data.name}</h2>
            <p className="muted small">
              Allocation runs first-expired-first-out, so the top row here is what leaves first.
            </p>
            <LotTable lots={data.lots} />
          </div>
        </>
      )}
    </section>
  );
}
