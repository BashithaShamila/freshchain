import { useSession } from "../auth/useSession";
import { IS_CONFIGURED, ASGARDEO_BASE_URL } from "../config";

export function SignIn() {
  const { signIn, isLoading } = useSession();

  return (
    <div className="signin">
      <div className="card signin-card">
        <span className="mark large" aria-hidden="true" />
        <h1>FreshChain</h1>
        <p className="lead">Order and inventory platform for perishable goods.</p>

        {IS_CONFIGURED ? (
          <>
            <button type="button" className="primary wide" onClick={signIn} disabled={isLoading}>
              {isLoading ? "Signing in…" : "Sign in with Asgardeo"}
            </button>
            <p className="muted small">
              You will be redirected to {new URL(ASGARDEO_BASE_URL).host} to authenticate.
            </p>
          </>
        ) : (
          <div className="banner error">
            <strong>Not configured</strong>
            <p>
              This container has no Asgardeo client id, so there is nothing to sign in to.
            </p>
            <p className="small">
              If you have already set <code>ASGARDEO_SPA_CLIENT_ID</code> in <code>.env</code>, the
              container is still running with the old environment — Compose does not restart a
              container when <code>.env</code> changes. Recreate it:
            </p>
            <pre>docker compose --profile apps up -d frontend</pre>
            <p className="muted small">
              Configuration is read at container start, not baked in at build time, so no rebuild is
              needed. Setup steps are in docs/asgardeo-setup.md §2b.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
