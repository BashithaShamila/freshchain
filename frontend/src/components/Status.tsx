import type { OrderStatus, ReservationStatus, ShipmentStatus } from "../api/types";

type AnyStatus = OrderStatus | ShipmentStatus | ReservationStatus;

/** Colour carries meaning here: settled, in flight, or refused. */
const TONE: Record<string, "good" | "wait" | "bad" | "warn"> = {
  PENDING_ALLOCATION: "wait",
  ALLOCATED: "good",
  PARTIALLY_ALLOCATED: "warn",
  REJECTED: "bad",
  CONFIRMED: "good",
  SHIPPED: "good",
  CANCELLED: "bad",
  CREATED: "wait",
  PICKED: "wait",
  HELD: "wait",
  RELEASED: "bad",
  CONSUMED: "good",
};

export function Status({ value }: { value: AnyStatus }) {
  const tone = TONE[value] ?? "wait";
  return <span className={`pill ${tone}`}>{value.replace(/_/g, " ").toLowerCase()}</span>;
}
