import { Navigate, Route, Routes } from "react-router-dom";
import { useSession } from "./auth/useSession";
import { RequireRole } from "./auth/RequireRole";
import { ROLES } from "./auth/roles";
import { Layout } from "./components/Layout";
import { Loading } from "./components/Feedback";
import { Catalog } from "./pages/Catalog";
import { OrderDetail } from "./pages/OrderDetail";
import { Orders } from "./pages/Orders";
import { PlaceOrder } from "./pages/PlaceOrder";
import { ReceiveStock } from "./pages/ReceiveStock";
import { Shipments } from "./pages/Shipments";
import { SignIn } from "./pages/SignIn";

export function App() {
  const { isAuthenticated, isLoading } = useSession();

  // The SDK restores a session on reload, so rendering the sign-in screen before
  // that finishes would flash a login page at someone already signed in.
  if (isLoading) {
    return (
      <div className="signin">
        <Loading label="Restoring your session…" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <SignIn />;
  }

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/catalog" replace />} />
        <Route path="/catalog" element={<Catalog />} />
        <Route
          path="/order"
          element={
            <RequireRole anyOf={[ROLES.CUSTOMER]}>
              <PlaceOrder />
            </RequireRole>
          }
        />
        <Route
          path="/orders"
          element={
            <RequireRole anyOf={[ROLES.CUSTOMER]}>
              <Orders />
            </RequireRole>
          }
        />
        <Route
          path="/orders/:orderId"
          element={
            <RequireRole anyOf={[ROLES.CUSTOMER, ROLES.ADMIN]}>
              <OrderDetail />
            </RequireRole>
          }
        />
        <Route
          path="/shipments"
          element={
            <RequireRole anyOf={[ROLES.WAREHOUSE_OPERATOR, ROLES.ADMIN]}>
              <Shipments />
            </RequireRole>
          }
        />
        <Route
          path="/receive"
          element={
            <RequireRole anyOf={[ROLES.ADMIN]}>
              <ReceiveStock />
            </RequireRole>
          }
        />
        <Route path="*" element={<Navigate to="/catalog" replace />} />
      </Route>
    </Routes>
  );
}
