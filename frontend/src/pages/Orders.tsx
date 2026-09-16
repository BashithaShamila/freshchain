import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { apiRequest } from "../api/client";
import type { Order } from "../api/types";
import { useSession } from "../auth/useSession";
import { Empty, ErrorBanner, Loading } from "../components/Feedback";
import { Status } from "../components/Status";

export function Orders() {
  const { getToken } = useSession();
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    const controller = new AbortController();
    apiRequest<Order[]>("/api/v1/orders?limit=50", getToken, { signal: controller.signal })
      .then(setOrders)
      .catch((caught) => {
        if (!controller.signal.aborted) {
          setError(caught);
        }
      });
    return () => controller.abort();
  }, [getToken]);

  return (
    <section>
      <header className="page-head">
        <div>
          <h1>My orders</h1>
          <p className="muted">Only your own orders — the API refuses anyone else's.</p>
        </div>
      </header>

      <ErrorBanner error={error} />
      {!orders && !error && <Loading />}
      {orders?.length === 0 && <Empty>No orders yet. Place one from the Place order tab.</Empty>}

      {orders && orders.length > 0 && (
        <div className="card">
          <table className="table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Status</th>
                <th>Placed</th>
                <th className="num">Lines</th>
                <th className="num">Total</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.orderId}>
                  <td className="mono">
                    <Link to={`/orders/${order.orderId}`}>{order.orderId.slice(0, 8)}</Link>
                  </td>
                  <td>
                    <Status value={order.status} />
                  </td>
                  <td>{new Date(order.placedAt).toLocaleString()}</td>
                  <td className="num">{order.lines.length}</td>
                  <td className="num">{order.total.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
