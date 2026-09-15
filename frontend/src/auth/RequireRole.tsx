import type { ReactNode } from "react";
import { useSession } from "./useSession";
import { displayRoles } from "./roles";

interface Props {
  anyOf: string[];
  children: ReactNode;
}

/**
 * Hides a route from someone whose token does not carry the role.
 *
 * Deliberately explains rather than redirecting: silently bouncing a warehouse
 * operator away from an admin page teaches them nothing, and the honest message
 * is that the server would refuse it anyway.
 */
export function RequireRole({ anyOf, children }: Props) {
  const { roles, isLoading } = useSession();

  if (isLoading) {
    return <p className="muted">Checking your permissions…</p>;
  }

  const permitted = anyOf.some((role) => roles.has(role));
  if (permitted) {
    return <>{children}</>;
  }

  return (
    <div className="card notice">
      <h2>Not available to your account</h2>
      <p>
        This page needs the <strong>{anyOf.join(" or ")}</strong> role.
      </p>
      <p className="muted">
        Your account holds: {displayRoles(roles).join(", ") || "no roles"}. Roles are assigned in
        Asgardeo, and the API enforces them independently of this page.
      </p>
    </div>
  );
}
