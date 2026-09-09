# secure-recruitment-platform

A job board — employers post vacancies, job seekers register skills and get matched — that was
built as a university project and then reviewed as if it were someone else's code.

The review found twelve issues. The worst of them was that authentication was decided by an
`X-Username` request header, so `curl -H "X-Username: admin"` was a full administrative session
against every endpoint. This branch fixes all twelve and adds a test for each one.

[docs/security-review.md](docs/security-review.md) is the substance of this repository. It has the
finding, the original code, the impact, the root cause, the fix, and the test that holds it, for
each issue. The vulnerable code is still in the history at `86281d6`, so every claim can be checked
against what it describes.

## The findings

| ID | Finding | CWE | Severity |
| --- | --- | --- | --- |
| F-01 | Authentication decided by a client-supplied header | CWE-287, CWE-290 | 9.8 Critical |
| F-02 | Administrative endpoints gated on that same header | CWE-285, CWE-863 | 9.1 Critical |
| F-03 | Debug endpoint returned all accounts with password hashes | CWE-200, CWE-489 | 7.5 High |
| F-04 | Administrator credentials compiled into the source | CWE-798 | 8.8 High |
| F-05 | Login responses enabled account enumeration | CWE-204 | 5.3 Medium |
| F-06 | Authentication flow written to standard output | CWE-532 | 5.3 Medium |
| F-07 | Encryption key generated at startup and printed | CWE-321, CWE-532 | 7.5 High |
| F-08 | No CSRF protection on state-changing endpoints | CWE-352 | 6.5 Medium |
| F-09 | Session cookie marked insecure | CWE-614 | 4.3 Medium |
| F-10 | Password change trusted a username from the body | CWE-639 | 8.1 High |
| F-11 | Build output committed to version control | CWE-1104 | Informational |
| F-12 | End-of-life framework with no dependency scanning | CWE-1104 | 5.9 Medium |

Two of the twelve are worth expanding on.

**F-07** is the one that would have been hardest to notice in production. When the encryption key
was unset, the service generated a fresh one *per call* and printed it to the console. So personal
data was encrypted with a key that was written to the log and then thrown away — it could never be
decrypted again. Nothing failed loudly. The data just quietly became unreadable.

**F-08** only became a real problem because of the fix for F-01. The original header-based scheme
happened to be CSRF-resistant, since a browser will not attach a custom header to a cross-site
request. Moving authentication to a session cookie removes that accident. Fixing the critical
finding without also enabling CSRF protection would have traded one vulnerability for another.

## Running it

Requires JDK 25.

```bash
export APP_ENCRYPTION_KEY="$(openssl rand -base64 32)"
export APP_ADMIN_USERNAME=admin
export APP_ADMIN_PASSWORD='choose-a-real-one'
mvn spring-boot:run
```

Then open <http://localhost:8098/>.

The application will not start without `APP_ENCRYPTION_KEY`, and will refuse a weak administrator
password. If the admin variables are unset it starts normally with no administrator account, which
is the correct default for a deployment that does not need one.

```bash
mvn test      # 26 tests
mvn package   # also writes a CycloneDX SBOM to target/
```

## What changed

Authentication is Spring Security with a server-side session. Identity reaches controllers through
`Principal` and nothing else — there is no code path where a caller can state who they are.
Authorisation is declared with `@PreAuthorize` and enforced again in the filter chain, so an
endpoint added without a role check is still covered by `anyRequest().authenticated()`.

Session cookies are `HttpOnly`, `SameSite=Strict`, and `Secure` where configured. The session id is
regenerated on login so a planted id is discarded rather than upgraded. CSRF protection uses a
cookie the page's own scripts can read and no other origin can.

Failed authentication returns one identical response whether the account is unknown, the password
is wrong, or the account is locked. The distinction goes to the security log, where the defender
can see it and the attacker cannot.

Passwords are BCrypt at cost 12 through Spring Security, replacing an unmaintained third-party
library. The policy requires twelve characters with mixed case, a digit and a symbol, and it now
applies to the administrator account too — which is where the original `admin123` came from.

## Testing

`SecurityRegressionTest` has one nested class per finding, each asserting the behaviour the
original code got wrong. A regression therefore re-opens a specific documented vulnerability, not just a red test:

- a request carrying `X-Username` is unauthenticated, and an authenticated user who also sends it
  still acts as themselves
- an ordinary user gets 403 from the administrative endpoints, an anonymous caller gets 401
- the debug paths return 404 even for an administrator, and no response body contains a BCrypt hash
- a wrong password and an unknown user produce byte-identical responses
- a state-changing POST without a CSRF token is rejected; with one it succeeds
- a password change naming another user in the body changes the caller's own password and leaves
  the named account's password working

`EncryptionServiceTest` covers F-07 directly, including the test that would have caught the
original bug: two instances sharing a configured key can read each other's ciphertext.

## Limitations

- **Storage is in memory.** The original entities were annotated for JPA but the service kept
  everything in `HashMap`s, so nothing was ever persisted. The annotations were removed rather than
  wired up, because leaving them implied a database that did not exist. All state is lost on
  restart and cannot be shared across instances.
- **No rate limiting.** Account lockout after five failures slows credential stuffing against one
  account. Nothing limits overall request volume, and a spray across many accounts is not stopped.
- **Lockout state is per-instance,** so it does not survive a restart or apply across replicas.
- **No email verification or password reset.** Both would need an email path this project does not
  have.
- **Reviewed statically.** Findings come from reading the code and are confirmed by tests, not by
  exploitation against a running instance.
