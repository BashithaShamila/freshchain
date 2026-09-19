# Setting up Asgardeo as the identity provider

FreshChain has no local identity provider. All four services are OAuth2 resource
servers that validate JWT access tokens issued by
[Asgardeo](https://asgardeo.io), WSO2's hosted IAM service.

This takes about fifteen minutes. At the end, `make check-idp` will tell you
whether it worked, and exactly what to fix if it did not.

---

## 1. Create an organisation

1. Sign up at <https://asgardeo.io> (the free tier is enough for this project).
2. Note your **organisation name** from the console URL —
   `https://console.asgardeo.io/t/<org>`. This is the *organisation* name, not
   the application name, and the two are rarely the same. Confirm it before
   going further:

   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' https://api.asgardeo.io/t/<org>/oauth2/token/.well-known/openid-configuration
   ```

   `200` means the organisation exists; `404` means it does not.

Your OIDC endpoints are then:

| Purpose   | URL |
|-----------|-----|
| Issuer    | `https://api.asgardeo.io/t/<org>/oauth2/token` |
| Discovery | `https://api.asgardeo.io/t/<org>/oauth2/token/.well-known/openid-configuration` |
| JWKS      | `https://api.asgardeo.io/t/<org>/oauth2/jwks` |

> The issuer is the **token endpoint URL**, not the console URL. Spring appends
> `/.well-known/openid-configuration` to it to discover the signing keys.

---

## 2. Create the application

**Applications → New Application → Standard-Based Application → OpenID Connect.**

> **The template matters, and it cannot be changed later.** Pick
> **Standard-Based Application**, not *Single-Page Application* and not
> *Traditional Web Application*. Those two templates only ever offer
> authorization code and refresh token — there is no Password checkbox on their
> Protocol tab to tick. If you have already created the wrong kind, create a new
> application; Asgardeo does not let you convert one template to another.
>
> `make check-idp` detects this specific case and says so.

- **Name:** `FreshChain`
- **Protocol:** OpenID Connect
- **Allowed grant types:** tick **Code** and **Refresh Token**
- **Public client:** leave *off* — the scripts hold a client secret
- **PKCE:** leave enabled (the login script uses S256)
- **Authorized redirect URLs:**
  ```
  http://localhost:8765/callback
  http://localhost:8081/swagger-ui/oauth2-redirect.html
  http://localhost:8082/swagger-ui/oauth2-redirect.html
  http://localhost:8083/swagger-ui/oauth2-redirect.html
  ```
  The first one matters most: it is where `make login` catches the authorization
  code. Without it, sign-in fails with `invalid_redirect_uri`.
- **Allowed origins:** `http://localhost:8080`

From the **Protocol** tab, copy the **Client ID** and **Client Secret**.

### Why authorization code and not the password grant

A command-line script has no browser, so the resource owner password grant would
be the convenient choice — one HTTP call, no redirect. Asgardeo does not offer
it: OAuth 2.1 deprecates ROPC, and current application templates have no
checkbox for it. The tenant's discovery document still advertises `password`
under `grant_types_supported`, which is misleading — that is tenant-level
metadata, not per-application permission.

So `make login` runs a proper **authorization code flow with PKCE**: it starts a
local listener on `http://localhost:8765/callback`, opens your browser, and
exchanges the returned code for tokens. The browser round trip is paid once per
user; tokens are cached under `.tokens/` and refreshed silently afterwards, so
`make seed` and `make demo` are non-interactive from then on.

Client credentials would not work here either. Those tokens identify the
*application*, not a person — no user subject, no user roles — and FreshChain
files every order under the token's subject and enforces that a customer can only
read their own orders.

---

## 2b. Create the frontend application

The React frontend needs its **own** Asgardeo application. A browser cannot keep
a client secret, so it must be a **public client** using PKCE, while the
command-line tooling stays confidential. Two clients, two client ids.

**Applications → New Application → Single-Page Application.**

- **Name:** `FreshChain Web`
- **Authorized redirect URLs:** `http://localhost:3001`
- **Allowed origins:** `http://localhost:3001`
  (add `http://localhost:5173` as well if you run the frontend with `npm run dev`)

Copy the **Client ID**. There is no secret, and that is correct — anything
shipped to a browser is public by definition.

Then repeat steps 3 to 5 below for this application too: token type **JWT**, the
same three roles, and the **Roles** attribute added so the claim reaches the
access token. Applications do not inherit each other's settings.

Put the client id in `.env`:

```bash
ASGARDEO_SPA_CLIENT_ID=<client id of FreshChain Web>
```

The services accept tokens from **either** application —
`FRESHCHAIN_OAUTH_AUDIENCE` is a list — so the CLI scripts and the browser both
work, while a token minted for some unrelated application is still rejected.

---

## 3. Make the access token a JWT

**Application → Protocol → Access Token.**

- **Token type:** `JWT`

The services validate the **access token**, so it has to be a JWT they can
verify against the JWKS. If it is opaque, `make check-idp` will say so.

---

## 4. Create the roles

**Application → Roles → New Role**, with audience **Application**.

Create exactly these three:

| Role | Can |
|------|-----|
| `CUSTOMER` | Place, view, confirm and cancel their own orders |
| `WAREHOUSE_OPERATOR` | Pick and ship shipments, view inventory |
| `ADMIN` | Receive stock, adjust lots, view everything |

Names are matched case-insensitively, and hyphens are treated as underscores, so
`warehouse-operator` also works. If you prefer entirely different names, keep
them and change the `@PreAuthorize` annotations instead — do not try to make the
two nearly match.

---

## 5. Put the roles into the access token

**This is the step most setups miss**, and the one where the console layout is
least predictable.

### The attributes

Two, and only two:

| Console attribute | OIDC claim | Underlying local claim |
|---|---|---|
| **Roles** | `roles` | `http://wso2.org/claims/roles` |
| **Groups** | `groups` | `http://wso2.org/claims/groups` |

Add **Roles** under **Application → User Attributes**. Add **Groups** as well if
you assign authorisation through groups rather than application roles — adding
both is harmless, and FreshChain reads whichever one arrives. If the console
offers a *Requested* or *Mandatory* checkbox next to the attribute, tick it.

### Getting them into the *access* token

FreshChain's services validate the **access token**. Asgardeo's attribute
selection primarily governs the **ID token** and the userinfo endpoint, so
selecting an attribute does not by itself guarantee it appears in the access
token. Two things have to be true as well:

- the access token is a **JWT**, not opaque (step 3); and
- the request asks for the matching scopes. The scripts here already send
  `scope=openid profile roles groups`.

Some Asgardeo versions expose an additional per-application control for which
attributes are written into the JWT access token, separate from the ID token
selection. Where it lives has moved between console revisions — look under the
application's **Protocol → Access Token** section and its **User Attributes**
tab. I am deliberately not giving you an exact click path here, because the one I
would have written is version-specific and would send you hunting for a control
that may be labelled differently in your tenant.

### Settle it empirically instead

```bash
make check-idp
```

That decodes a real access token and prints which claims are actually in it. It
is the authority here, not this document:

- **roles present** — you are done.
- **roles under a different claim name** — the script prints the exact
  `FRESHCHAIN_ROLE_CLAIMS=...` line to put in `.env`. No console change needed.
- **no role claim at all** — the attribute is not reaching the access token.
  Confirm the token type is JWT, then look for the access-token attribute control
  described above.

---

## 6. Create the demo users

**User Management → Users → Add User.** Create four, and assign each a role from
step 4:

| Alias in the scripts | Role it must hold | Used for |
|---|---|---|
| `customer` | `CUSTOMER` | places the orders in the demo |
| `customer2` | `CUSTOMER` | proves a customer cannot read another's order |
| `operator` | `WAREHOUSE_OPERATOR` | picks and ships |
| `admin` | `ADMIN` | receives stock |

**Note the exact username Asgardeo gives each account.** Asgardeo commonly uses
the **email address** as the username, so a user you think of as "alice" may
actually sign in as `alice@example.com`. `make login` asks you to sign in by
*role* rather than prefilling a username, precisely because guessing it wrong
produces a failure that looks identical to a wrong password.

Two things to get right when creating each user, both of which cause a
confusing sign-in failure later:

- **Set a password directly** rather than sending an email invitation, unless you
  intend to complete that invitation. An account still in the invited state
  cannot sign in.
- **Clear any "must reset password at first login" flag**, or sign in once at
  <https://myaccount.asgardeo.io> to complete it. The login script cannot answer
  a forced password-change prompt.

The `FRESHCHAIN_*_USER` values in `.env` are only labels printed to remind you
who to sign in as. They are not sent to Asgardeo and do not need to match.

## 7. Configure FreshChain

```bash
cp .env.example .env
```

Fill in:

```bash
ASGARDEO_ORG=freshchain
ASGARDEO_CLIENT_ID=<from the Protocol tab>
ASGARDEO_CLIENT_SECRET=<from the Protocol tab>

FRESHCHAIN_CUSTOMER_USER=alice
FRESHCHAIN_CUSTOMER_PASSWORD=<alice's password>
FRESHCHAIN_SECOND_CUSTOMER_USER=bob
FRESHCHAIN_SECOND_CUSTOMER_PASSWORD=<bob's password>
FRESHCHAIN_OPERATOR_USER=wanda
FRESHCHAIN_OPERATOR_PASSWORD=<wanda's password>
FRESHCHAIN_ADMIN_USER=freshchain-admin
FRESHCHAIN_ADMIN_PASSWORD=<admin's password>
```

`.env` is git-ignored. Do not commit it.

---

## 8. Sign in and verify

```bash
make login
```

This opens your browser once for each of the four demo users. Sign in as each in
turn — the prompt names who it wants. Tokens land in `.tokens/` (git-ignored,
owner-readable only) and refresh on their own afterwards.

```bash
make check-idp
```

This decodes a cached access token and reports what is actually inside it,
including which claim your roles landed in and whether FreshChain recognises
them. Then:

```bash
make infra
```

```bash
make up
```

```bash
make seed
```

```bash
make demo
```

---

## Troubleshooting

**`no roles or groups claim in the ACCESS token`**
Step 5 is incomplete. Attributes must be added in *both* User Attributes and
Protocol → Access Token.

**`the access token is opaque, not a JWT`**
Step 3 — set the token type to JWT.

**`invalid_redirect_uri` when the browser opens**
`http://localhost:8765/callback` is not in the application's Authorized redirect
URLs. Add it exactly, including the scheme and port.

**`make login` cannot bind port 8765**
Something else owns it. Set `ASGARDEO_CALLBACK_PORT` in `.env` and add the
matching redirect URL in Asgardeo.

**The browser signs you in as the wrong user**
The login request sends `prompt=login` to force re-authentication, but if your
browser has an active Asgardeo session it may still land on the wrong account.
Sign out at `https://myaccount.asgardeo.io/t/<org>`, or use a private window.

**`no usable token for '<alias>' — run: make login`**
The cache is empty or the refresh token has expired. Run `make login` again.

**`CERTIFICATE_VERIFY_FAILED: self-signed certificate in certificate chain`**
Despite how it reads, this is usually not a proxy. The python.org macOS
installer ships a Python that trusts nothing until you run its certificate
installer, so `ssl.get_default_verify_paths()` returns nothing and no chain can
be built. `curl` is unaffected because macOS curl uses the system keychain.

`make login` falls back to the `certifi` bundle automatically, so this should not
stop you. To fix the interpreter itself:

```bash
open "/Applications/Python 3.13/Install Certificates.command"
```

If it still fails after that, then something really is intercepting TLS.

**Roles are in a claim with a different name**
`make check-idp` prints the exact line to add to `.env`, for example
`FRESHCHAIN_ROLE_CLAIMS=groups`. The services read every claim listed there.

**401 from a service, with a token that looks fine**
Check `aud`. FreshChain validates the audience against `ASGARDEO_CLIENT_ID`, so a
token minted for a different application in the same organisation is rejected.
That is deliberate.

**Roles named differently from FreshChain's**
Names are normalised — case, hyphens and any group path prefix
(`Internal/admin` → `ADMIN`) are handled. Anything beyond that means renaming the
role in Asgardeo or changing the `@PreAuthorize` annotations.

---

## What this changed in the code

| | Before (Keycloak) | Now (Asgardeo) |
|---|---|---|
| Where it runs | A container in `docker-compose.yml` | Hosted; nothing local |
| Issuer URL | Two — `keycloak:8080` internally, `localhost:8180` externally | One public HTTPS URL |
| Realm/user setup | A committed realm-export JSON | Console setup, documented here |
| Roles claim | `realm_access.roles`, nested | `roles` / `groups`, flat |
| Audience | Not validated | Validated against the client id |

The internal/external issuer split is the interesting one. With a self-hosted
provider the containers and the host reach it at different addresses, so the
issuer in the token never matches one of them and the JWKS URL has to be
configured separately. A hosted provider has one address for everybody, and that
whole class of configuration disappears.
