#!/usr/bin/env python3
"""Obtains Asgardeo access tokens for the FreshChain demo users.

Uses authorization code + PKCE, because that is what Asgardeo actually offers.
The resource owner password grant would be simpler for a script, but OAuth 2.1
deprecates it and Asgardeo no longer exposes it on the application templates, so
there is nothing to enable.

The cost of the browser round trip is paid once per user. Tokens are cached
under .tokens/ and refreshed silently afterwards, so `make seed` and `make demo`
stay non-interactive from the second run on.

    python3 scripts/asgardeo-login.py --all              log every demo user in
    python3 scripts/asgardeo-login.py --alias customer   log one user in
    python3 scripts/asgardeo-login.py --alias customer --print   print a token
"""
import argparse
import base64
import hashlib
import http.server
import json
import os
import pathlib
import secrets
import ssl
import sys
import threading
import time
import urllib.parse
import urllib.request
import webbrowser

ROOT = pathlib.Path(__file__).resolve().parent.parent
TOKEN_CACHE = ROOT / ".tokens"
CALLBACK_PORT = int(os.environ.get("ASGARDEO_CALLBACK_PORT", "8765"))
REDIRECT_URI = f"http://localhost:{CALLBACK_PORT}/callback"
SCOPE = os.environ.get("ASGARDEO_SCOPE", "openid profile roles groups")
# Refresh a little early rather than racing the expiry mid-run.
EXPIRY_SKEW_SECONDS = 60

# alias -> (env var naming the user, fallback label, the role that user must hold)
ALIASES = {
    "customer": ("FRESHCHAIN_CUSTOMER_USER", "alice", "CUSTOMER"),
    "customer2": ("FRESHCHAIN_SECOND_CUSTOMER_USER", "bob", "CUSTOMER"),
    "operator": ("FRESHCHAIN_OPERATOR_USER", "wanda", "WAREHOUSE_OPERATOR"),
    "admin": ("FRESHCHAIN_ADMIN_USER", "freshchain-admin", "ADMIN"),
}


def load_env():
    """Reads .env the same way the shell scripts do, without needing a shell."""
    env_file = ROOT / ".env"
    if env_file.exists():
        for line in env_file.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            os.environ.setdefault(key.strip(), value.strip())

    missing = [k for k in ("ASGARDEO_ORG", "ASGARDEO_CLIENT_ID") if not os.environ.get(k)]
    if missing:
        sys.exit(f"Missing config: {', '.join(missing)} — see docs/asgardeo-setup.md")

    org = os.environ["ASGARDEO_ORG"]
    base = os.environ.get("ASGARDEO_BASE", "https://api.asgardeo.io")
    return {
        "authorize": f"{base}/t/{org}/oauth2/authorize",
        "token": f"{base}/t/{org}/oauth2/token",
        "client_id": os.environ["ASGARDEO_CLIENT_ID"],
        "client_secret": os.environ.get("ASGARDEO_CLIENT_SECRET", ""),
    }


def tls_context():
    """An SSL context that actually has trust anchors.

    The python.org macOS installer ships a Python that trusts nothing until you
    run its "Install Certificates.command" — `ssl.get_default_verify_paths()`
    returns None for both cafile and capath. Every HTTPS call then fails with
    "self-signed certificate in certificate chain", which reads like a corporate
    proxy problem but is not: there are simply no roots to chain to. curl is
    unaffected because macOS curl uses the system keychain.

    Rather than make that the reader's problem, fall back to the certifi bundle
    that ships with pip when the interpreter has no store of its own.
    """
    paths = ssl.get_default_verify_paths()
    if paths.cafile or paths.capath:
        return ssl.create_default_context()
    try:
        import certifi
    except ImportError:
        return ssl.create_default_context()
    return ssl.create_default_context(cafile=certifi.where())


def post_token(cfg, form):
    """Token endpoint call. Confidential clients authenticate with Basic, public ones don't."""
    data = urllib.parse.urlencode(form).encode()
    request = urllib.request.Request(cfg["token"], data=data)
    request.add_header("Content-Type", "application/x-www-form-urlencoded")
    if cfg["client_secret"]:
        raw = f"{cfg['client_id']}:{cfg['client_secret']}".encode()
        request.add_header("Authorization", "Basic " + base64.b64encode(raw).decode())
    try:
        with urllib.request.urlopen(request, timeout=30, context=tls_context()) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as exc:
        body = exc.read().decode(errors="replace")
        raise SystemExit(f"token request failed ({exc.code}): {body}")
    except urllib.error.URLError as exc:
        if isinstance(exc.reason, ssl.SSLCertVerificationError):
            raise SystemExit(
                f"TLS verification failed talking to {cfg['token']}:\n"
                f"  {exc.reason}\n"
                "\n"
                "This interpreter has no CA bundle, and certifi was not available\n"
                "to fall back on. Fix it once with either:\n"
                "  open '/Applications/Python 3.13/Install Certificates.command'\n"
                "  python3 -m pip install --upgrade certifi\n")
        raise SystemExit(f"could not reach {cfg['token']}: {exc.reason}")


class CallbackHandler(http.server.BaseHTTPRequestHandler):
    result = {}

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path != "/callback":
            self.send_response(404)
            self.end_headers()
            return
        CallbackHandler.result = dict(urllib.parse.parse_qsl(parsed.query))
        ok = "code" in CallbackHandler.result
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        message = ("Signed in. You can close this tab and return to the terminal."
                   if ok else
                   f"Sign-in failed: {CallbackHandler.result.get('error_description', 'unknown error')}")
        self.wfile.write(
            f"<html><body style='font-family:system-ui;padding:3rem'>"
            f"<h2>FreshChain</h2><p>{message}</p></body></html>".encode())

    def log_message(self, *args):
        pass  # the handler's own logging would interleave with the CLI output


def authorize(cfg, alias, username_hint, role):
    verifier = base64.urlsafe_b64encode(secrets.token_bytes(64)).decode().rstrip("=")
    challenge = base64.urlsafe_b64encode(
        hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    state = secrets.token_urlsafe(16)

    params = {
        "response_type": "code",
        "client_id": cfg["client_id"],
        "redirect_uri": REDIRECT_URI,
        "scope": SCOPE,
        "state": state,
        "code_challenge": challenge,
        "code_challenge_method": "S256",
        # Each demo user is a different person, so the browser must not silently
        # reuse whoever signed in last.
        "prompt": "login",
    }
    # Deliberately no login_hint. The usernames in .env are placeholders unless
    # you changed them, and prefilling a wrong username into the form is worse
    # than leaving it empty — you end up entering a correct password against an
    # account that does not exist, which looks identical to a wrong password.
    url = f"{cfg['authorize']}?{urllib.parse.urlencode(params)}"

    try:
        server = http.server.HTTPServer(("localhost", CALLBACK_PORT), CallbackHandler)
    except OSError as exc:
        sys.exit(f"cannot listen on {REDIRECT_URI}: {exc}\n"
                 f"Set ASGARDEO_CALLBACK_PORT to a free port and register the matching "
                 f"redirect URL in Asgardeo.")

    CallbackHandler.result = {}

    def serve_until_callback():
        # Browsers request /favicon.ico unprompted. Serving a single request
        # would let that consume the listener and lose the real redirect, so
        # keep serving until /callback actually arrives.
        deadline = time.time() + 300
        while not CallbackHandler.result and time.time() < deadline:
            server.handle_request()

    server.timeout = 5
    thread = threading.Thread(target=serve_until_callback, daemon=True)
    thread.start()

    print(f"  Sign in as a user who holds the {role} role.")
    if username_hint:
        print(f"  (expected to be '{username_hint}', but use whatever username that")
        print(f"   account actually has in Asgardeo — an email address, commonly.)")
    print(f"  If the browser does not open, visit:\n    {url}\n")
    webbrowser.open(url)

    thread.join(timeout=300)
    server.server_close()

    result = CallbackHandler.result
    if not result:
        sys.exit(
            "timed out waiting for the browser callback.\n"
            "\n"
            "Asgardeo never redirected back, so the failure happened on its own\n"
            "login page — read the message shown there. Common causes:\n"
            "  - wrong username. The usernames in .env are placeholders; Asgardeo\n"
            "    often uses the email address as the username.\n"
            "  - the account is locked. Asgardeo locks after repeated failed\n"
            "    attempts; your tenant has reCAPTCHA on login, which is a sign\n"
            "    that protection is active. Unlock it in the console under\n"
            "    User Management -> Users -> the user -> Account security.\n"
            "  - the account must set or change its password before first use.\n"
            "    Sign in once at https://myaccount.asgardeo.io to clear that.\n"
            "  - the user is not assigned to the FreshChain application.")
    if "error" in result:
        sys.exit(f"sign-in failed: {result.get('error')}: {result.get('error_description', '')}")
    if result.get("state") != state:
        # A mismatch means the response did not originate from the request this
        # process made.
        sys.exit("state mismatch — discarding the response")

    tokens = post_token(cfg, {
        "grant_type": "authorization_code",
        "code": result["code"],
        "redirect_uri": REDIRECT_URI,
        "client_id": cfg["client_id"],
        "code_verifier": verifier,
    })
    save(alias, tokens)
    return tokens


def roles_in(access_token):
    """Reads role claims straight out of the JWT, mirroring JwtRoleConverter."""
    try:
        payload = access_token.split(".")[1]
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    except (IndexError, ValueError):
        return set()
    found = set()
    for name in ("roles", "groups", "application_roles"):
        value = claims.get(name)
        if not value:
            continue
        values = value if isinstance(value, list) else str(value).replace(",", " ").split()
        found.update(str(v).rsplit("/", 1)[-1].replace("-", "_").upper() for v in values)
    return found


def verify_role(alias, role, tokens):
    """Signing the wrong account into a slot is easy and the symptom is a 403 much later.

    Catch it here, where the cause is still obvious, rather than leaving the demo
    to fail on an authorisation rule that is working exactly as intended.
    """
    granted = roles_in(tokens.get("access_token", ""))
    if role in granted:
        return True
    print(f"  WARNING: this account does not hold {role}.")
    print(f"           roles found: {', '.join(sorted(granted)) or '(none)'}")
    print(f"           '{alias}' needs {role}. Sign in with a different account:")
    print(f"             rm .tokens/{alias}.json && python3 scripts/asgardeo-login.py --alias {alias}")
    return False


def save(alias, tokens):
    TOKEN_CACHE.mkdir(exist_ok=True)
    tokens = dict(tokens)
    tokens["expires_at"] = time.time() + int(tokens.get("expires_in", 3600))
    path = TOKEN_CACHE / f"{alias}.json"
    path.write_text(json.dumps(tokens, indent=2))
    path.chmod(0o600)  # these are bearer credentials
    return tokens


def cached(alias):
    path = TOKEN_CACHE / f"{alias}.json"
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text())
    except ValueError:
        return None


def usable_token(cfg, alias):
    """Returns a live access token from cache, refreshing if needed. None if a login is required."""
    tokens = cached(alias)
    if not tokens:
        return None
    if tokens.get("expires_at", 0) - EXPIRY_SKEW_SECONDS > time.time():
        return tokens["access_token"]
    if not tokens.get("refresh_token"):
        return None
    try:
        refreshed = post_token(cfg, {
            "grant_type": "refresh_token",
            "refresh_token": tokens["refresh_token"],
            "client_id": cfg["client_id"],
            "scope": SCOPE,
        })
    except SystemExit:
        return None  # refresh token expired or revoked; a fresh login is needed
    return save(alias, refreshed)["access_token"]


def main():
    parser = argparse.ArgumentParser(description="Asgardeo login for the FreshChain demo users")
    parser.add_argument("--alias", choices=sorted(ALIASES))
    parser.add_argument("--all", action="store_true", help="log every demo user in")
    parser.add_argument("--print", dest="print_token", action="store_true",
                        help="print a usable access token, without opening a browser")
    args = parser.parse_args()

    cfg = load_env()

    if args.all:
        interrupted = []
        for alias in ALIASES:
            env_key, default, role = ALIASES[alias]
            username = os.environ.get(env_key, default)
            print(f"\n[{alias}] needs the {role} role")
            if usable_token(cfg, alias):
                print("  already signed in")
                continue
            try:
                tokens = authorize(cfg, alias, username, role)
            except KeyboardInterrupt:
                # Skipping one user should not throw away the ones already done.
                print("  skipped")
                interrupted.append(alias)
                continue
            if verify_role(alias, role, tokens):
                print(f"  signed in, holds {role}")

        if interrupted:
            print(f"\nSkipped: {', '.join(interrupted)}. Re-run to finish them.")
        else:
            print("\nAll demo users signed in. Tokens cached in .tokens/")
        return 0

    if not args.alias:
        parser.error("give --alias or --all")

    env_key, default, role = ALIASES[args.alias]
    username = os.environ.get(env_key, default)

    token = usable_token(cfg, args.alias)
    if args.print_token:
        if not token:
            sys.exit(f"no usable token for '{args.alias}' — run: make login")
        print(token, end="")
        return 0

    if token:
        print(f"  already signed in for '{args.alias}'")
        return 0
    tokens = authorize(cfg, args.alias, username, role)
    if verify_role(args.alias, role, tokens):
        print(f"  signed in for '{args.alias}', holds {role}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
