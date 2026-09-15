import { useAuthContext } from "@asgardeo/auth-react";
import { useCallback, useEffect, useState } from "react";
import { rolesFromAccessToken } from "./roles";
import type { TokenSource } from "../api/client";

export interface Session {
  isAuthenticated: boolean;
  isLoading: boolean;
  username: string;
  roles: Set<string>;
  getToken: TokenSource;
  signIn: () => void;
  signOut: () => void;
}

/**
 * Wraps the Asgardeo SDK in the shape this app actually uses.
 *
 * Keeping the SDK behind one hook means the rest of the code never imports it
 * directly, so swapping providers later is a change in one file rather than in
 * every page.
 */
export function useSession(): Session {
  const { state, signIn, signOut, getAccessToken } = useAuthContext();
  const [roles, setRoles] = useState<Set<string>>(new Set());

  // getAccessToken is async, so roles arrive a tick after authentication does.
  useEffect(() => {
    let cancelled = false;
    if (!state.isAuthenticated) {
      setRoles(new Set());
      return;
    }
    getAccessToken()
      .then((token) => {
        if (!cancelled) {
          setRoles(rolesFromAccessToken(token));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRoles(new Set());
        }
      });
    return () => {
      cancelled = true;
    };
  }, [state.isAuthenticated, getAccessToken]);

  const getToken = useCallback<TokenSource>(() => getAccessToken(), [getAccessToken]);

  return {
    isAuthenticated: Boolean(state.isAuthenticated),
    isLoading: Boolean(state.isLoading),
    username: state.username ?? state.displayName ?? "",
    roles,
    getToken,
    signIn: () => void signIn(),
    signOut: () => void signOut(),
  };
}
