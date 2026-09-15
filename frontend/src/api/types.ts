/** Mirrors of the API's response shapes. Only the fields this app reads. */

export type OrderStatus =
  | "PENDING_ALLOCATION"
  | "ALLOCATED"
  | "PARTIALLY_ALLOCATED"
  | "REJECTED"
  | "CONFIRMED"
  | "SHIPPED"
  | "CANCELLED";

export type ShipmentStatus = "CREATED" | "PICKED" | "SHIPPED" | "CANCELLED";

export type ReservationStatus = "HELD" | "CONFIRMED" | "RELEASED" | "CONSUMED";

export interface Lot {
  lotId: string;
  sku: string;
  warehouseId: string;
  qtyOnHand: number;
  qtyReserved: number;
  available: number;
  expiryDate: string;
  receivedAt: string;
}

export interface Availability {
  sku: string;
  name: string;
  warehouseId: string | null;
  totalOnHand: number;
  totalReserved: number;
  available: number;
  nearestExpiry: string | null;
  lots: Lot[];
}

export interface Substitution {
  forSku: string;
  suggestedSku: string;
  suggestedName: string;
  availableQty: number;
  similarity: number;
  rationale: string;
}

export interface OrderLine {
  sku: string;
  qtyRequested: number;
  qtyAllocated: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  orderId: string;
  customerId: string;
  warehouseId: string;
  status: OrderStatus;
  statusReason: string | null;
  allowPartial: boolean;
  reservationId: string | null;
  reservationExpiresAt: string | null;
  total: number;
  placedAt: string;
  updatedAt: string;
  lines: OrderLine[];
  substitutions?: Substitution[];
}

export interface PlaceOrderResponse {
  order: Order;
  availabilityPreview: Record<string, number>;
}

export interface ReservationLine {
  lotId: string;
  sku: string;
  qty: number;
}

export interface Reservation {
  reservationId: string;
  orderId: string;
  warehouseId: string;
  status: ReservationStatus;
  expiresAt: string;
  createdAt: string;
  totalQty: number;
  lines: ReservationLine[];
}

export interface ShipmentLine {
  lotId: string;
  sku: string;
  qty: number;
}

export interface Shipment {
  shipmentId: string;
  orderId: string;
  warehouseId: string;
  status: ShipmentStatus;
  trackingRef: string | null;
  createdAt: string;
  pickedAt: string | null;
  shippedAt: string | null;
  totalQty: number;
  lines: ShipmentLine[];
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details: string[];
}
