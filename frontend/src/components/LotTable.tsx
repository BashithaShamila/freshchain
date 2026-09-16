import type { Lot } from "../api/types";

/**
 * Lots in the order stock will leave the warehouse: nearest expiry first.
 *
 * The expiry column is the point of the whole table. A single "available"
 * number hides that most of it goes out of date on Tuesday, which is exactly
 * what a buyer needs to see.
 */
export function LotTable({ lots }: { lots: Lot[] }) {
  const today = new Date().toISOString().slice(0, 10);
  const sorted = [...lots].sort((a, b) => a.expiryDate.localeCompare(b.expiryDate));

  return (
    <table className="table">
      <thead>
        <tr>
          <th>Lot</th>
          <th>Expires</th>
          <th className="num">On hand</th>
          <th className="num">Reserved</th>
          <th className="num">Available</th>
        </tr>
      </thead>
      <tbody>
        {sorted.map((lot) => {
          const expired = lot.expiryDate <= today;
          return (
            <tr key={lot.lotId} className={expired ? "expired" : undefined}>
              <td className="mono">{lot.lotId.slice(0, 8)}</td>
              <td>
                {lot.expiryDate}
                {expired && <span className="tag">expired</span>}
              </td>
              <td className="num">{lot.qtyOnHand}</td>
              <td className="num">{lot.qtyReserved}</td>
              <td className="num strong">{expired ? 0 : lot.available}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
