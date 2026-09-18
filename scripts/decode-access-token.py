#!/usr/bin/env python3
"""Decodes an Asgardeo JWT access token and reports whether FreshChain can use it.

Reads the token on stdin. Expected role names are passed as arguments.

Exit codes are what asgardeo-check.sh branches on:
    0  usable — a role claim is present
    2  the token is opaque, not a JWT
    3  no role claim in the access token

This lives in its own file rather than inside the shell script on purpose: the
same logic embedded in a single-quoted shell string is a quoting minefield that
neither `bash -n` nor a Python syntax check can see through.
"""
import base64
import json
import sys
import time

GREEN, YELLOW, RED, RESET = "\033[32m", "\033[33m", "\033[31m", "\033[0m"
ROLE_CLAIMS = ("roles", "groups", "application_roles")


def decode_segment(segment):
    return base64.urlsafe_b64decode(segment + "=" * (-len(segment) % 4))


def normalise(value):
    """Mirrors JwtRoleConverter: Internal/admin and warehouse-operator both land on a role name."""
    return str(value).rsplit("/", 1)[-1].replace("-", "_").upper()


def main():
    expected = set(sys.argv[1:])
    raw = sys.stdin.read().strip()

    parts = raw.split(".")
    if len(parts) != 3:
        print(f"  {YELLOW}!{RESET} the access token is opaque, not a JWT.")
        print("    Set the token type to JWT: Application -> Protocol -> Access Token.")
        return 2

    try:
        claims = json.loads(decode_segment(parts[1]))
    except (ValueError, TypeError) as exc:
        print(f"  {RED}x{RESET} could not decode the token payload: {exc}")
        return 2

    audience = claims.get("aud")
    print("    iss  :", claims.get("iss"))
    print("    sub  :", claims.get("sub"))
    print("    aud  :", ", ".join(audience) if isinstance(audience, list) else audience)
    if claims.get("exp"):
        print("    exp  :", time.strftime("%Y-%m-%d %H:%M:%SZ", time.gmtime(claims["exp"])))
    print("    scope:", claims.get("scope", "(none)"))
    print()

    present = {name: claims[name] for name in ROLE_CLAIMS if name in claims}
    if not present:
        print(f"  {RED}x{RESET} no roles or groups claim in the ACCESS token.")
        print()
        print("    The two attributes you need are Roles and Groups:")
        print('      Roles  -> claim "roles"   (local claim http://wso2.org/claims/roles)')
        print('      Groups -> claim "groups"  (local claim http://wso2.org/claims/groups)')
        print()
        print("    Check, in order:")
        print("      1. Application -> User Attributes: is Roles selected, and marked requested?")
        print("      2. Application -> Protocol -> Access Token: is the token type JWT?")
        print("      3. Is there a separate control for attributes in the ACCESS token, as")
        print("         opposed to the ID token? Its location varies between console versions.")
        print("         Attribute selection governs the ID token first, and the services here")
        print("         validate the access token.")
        print()
        print("    Scopes that were requested:", claims.get("scope", "(none)"))
        return 3

    found = set()
    for name, value in present.items():
        print(f"  {GREEN}v{RESET} claim \"{name}\" = {json.dumps(value)}")
        values = value if isinstance(value, list) else str(value).replace(",", " ").split()
        found.update(normalise(v) for v in values)

    print()
    print("    maps to roles:", ", ".join(sorted(found)) or "(none)")

    matched = expected & found
    if matched:
        print(f"  {GREEN}v{RESET} recognised by FreshChain: {', '.join(sorted(matched))}")
    else:
        print(f"  {YELLOW}!{RESET} none of these match FreshChain's roles: {', '.join(sorted(expected))}")
        print("    Rename the roles in Asgardeo, or change the @PreAuthorize annotations.")

    print()
    print("    Set in .env:  FRESHCHAIN_ROLE_CLAIMS=" + ",".join(sorted(present)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
