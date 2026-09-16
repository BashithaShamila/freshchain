import { NavLink, Outlet } from "react-router-dom";
import { useSession } from "../auth/useSession";
import { ROLES, displayRoles } from "../auth/roles";

/** Nav entries appear only for roles the signed-in account actually holds. */
const NAV = [
  { to: "/catalog", label: "Catalog", roles: [ROLES.CUSTOMER, ROLES.WAREHOUSE_OPERATOR, ROLES.ADMIN] },
  { to: "/order", label: "Place order", roles: [ROLES.CUSTOMER] },
  { to: "/orders", label: "My orders", roles: [ROLES.CUSTOMER] },
  { to: "/shipments", label: "Warehouse", roles: [ROLES.WAREHOUSE_OPERATOR, ROLES.ADMIN] },
  { to: "/receive", label: "Receive stock", roles: [ROLES.ADMIN] },
];

export function Layout() {
  const { username, roles, signOut } = useSession();
  const visible = NAV.filter((item) => item.roles.some((role) => roles.has(role)));

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <span className="mark" aria-hidden="true" />
          <span>FreshChain</span>
        </div>

        <nav aria-label="Main">
          {visible.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => (isActive ? "navlink active" : "navlink")}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="account">
          <div className="who">
            <span className="username">{username}</span>
            <span className="roles">{displayRoles(roles).join(" · ") || "no roles"}</span>
          </div>
          <button type="button" className="ghost" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>

      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
