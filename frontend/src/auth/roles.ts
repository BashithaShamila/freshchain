/**
 * Reads roles out of the Asgardeo access token.
 *
 * The services read roles from the access token, not the ID token, so the UI
 * reads the same place — otherwise the menu and the API could disagree about
 * what you are allowed to do.
 *
 * These checks are for presentation only. Every rule they express is enforced
 * again server-side, and must be: anything decided in a browser can be edited in
 * a browser. Hiding a button the API would reject is a courtesy, not a control.
 */
export const ROLES = {
  CUSTOMER: "CUSTOMER",
  WAREHOUSE_OPERATOR: "WAREHOUSE_OPERATOR",
  ADMIN: "ADMIN",
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

/** Mirrors the server's JwtRoleConverter: strip any group path, normalise separators and case. */
function normalise(value: string): string {
  const lastSegment = value.includes("/") ? value.slice(value.lastIndexOf("/") + 1) : value;
  return lastSegment.replace(/-/g, "_").replace(/ /g, "_").toUpperCase();
}

export function rolesFromAccessToken(accessToken: string | undefined): Set<string> {
  if (!accessToken) {
    return new Set();
  }
  const segments = accessToken.split(".");
  if (segments.length !== 3) {
    return new Set(); // opaque token; roles are not readable here
  }

  let claims: Record<string, unknown>;
  try {
    const padded = segments[1].replace(/-/g, "+").replace(/_/g, "/");
    claims = JSON.parse(atob(padded + "=".repeat((4 - (padded.length % 4)) % 4)));
  } catch {
    return new Set();
  }

  const found = new Set<string>();
  for (const claimName of ["roles", "groups", "application_roles"]) {
    const claim = claims[claimName];
    const values = Array.isArray(claim)
      ? claim
      : typeof claim === "string"
        ? claim.split(/[,\s]+/)
        : [];
    for (const value of values) {
      if (typeof value === "string" && value.length > 0) {
        found.add(normalise(value));
      }
    }
  }
  return found;
}

export function displayRoles(roles: Set<string>): string[] {
  // "everyone" is Asgardeo's default group and means nothing here.
  return [...roles].filter((role) => role !== "EVERYONE").sort();
}
