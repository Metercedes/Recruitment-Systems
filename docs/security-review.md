# Security review

Review of the recruitment platform as it stood at commit `86281d6`, before the changes on this
branch. Twelve findings, each with the evidence that established it, the fix, and the test that
holds the fix in place.

The original code is still in the history, so every finding can be checked against what it
describes rather than taken on trust.

Severity uses CVSS v3.1 base scores. The application was never deployed and holds no real data, so
these describe the code, not a live exposure.

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

---

## F-01 Authentication decided by a client-supplied header

**Severity** 9.8 Critical (`AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H`) &middot; CWE-287, CWE-290

**Finding.** `AuthenticationInterceptor` established the caller's identity by reading the
`X-Username` request header. There was no token, no signature and no session:

```java
String username = request.getHeader("X-Username");
if (username == null || username.trim().isEmpty()) { /* 401 */ }
if (!recruitmentService.isSessionValid(username)) { /* 401 */ }
return true;
```

`isSessionValid` checked whether that username had an entry in a `Map<String, LocalDateTime>`. The
key is the value the attacker just supplied, so the check confirms only that *somebody* with that
name has logged in at some point, not that the caller is them.

**Impact.** Every authenticated endpoint was reachable as any user by setting one header. Once any
account had logged in once, `curl -H "X-Username: admin"` was a full administrative session. This
is not privilege escalation from a foothold; it requires no credentials at all.

**Root cause.** Identity was treated as a request parameter. A request header is attacker-supplied
input in exactly the way a query string is, and the code applied a trust boundary that does not
exist.

**Remediation.** The interceptor was deleted. Spring Security now establishes identity through an
authentication step and carries it in a server-side session. Every controller method takes the
account from `Principal`, which the framework populates from the security context, so no method
can be told who the caller is.

**Verification.** `SecurityRegressionTest.AuthenticationBypass` — a request carrying `X-Username`
is unauthenticated; `X-Username: admin` does not grant administrative access; and an authenticated
user who also sends the header still acts as themselves.

---

## F-02 Administrative endpoints gated on that same header

**Severity** 9.1 Critical (`AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:N`) &middot; CWE-285, CWE-863

**Finding.** `DELETE /api/admin/users/{username}` and `POST /api/admin/users/{username}/ban`
resolved the administrator from the spoofable header and then checked the flag in application code:

```java
User admin = service.getUserByUsername(username);
if (admin == null || !admin.isAdmin()) { return ResponseEntity.status(403)... }
```

The check is correct in isolation. It is worthless because `username` came from F-01.

**Impact.** Unauthenticated account deletion and banning of any user.

**Root cause.** Two problems compounded. The identity was untrusted, and the authorisation
decision lived in a method body where it is easy to omit on the next endpoint someone adds.

**Remediation.** `@EnableMethodSecurity` with `@PreAuthorize("hasRole('ADMIN')")` on the
administrative methods, and `.requestMatchers("/api/admin/**").hasRole("ADMIN")` in the filter
chain. Authorisation is now declared and enforced by the framework before the method runs, and it
is enforced twice. Roles come from the authenticated principal's authorities. An administrator can
no longer delete their own account, which was previously possible and left the system with none.

**Verification.** `SecurityRegressionTest.AdministrativeAccess` — an ordinary authenticated user
receives 403 from both endpoints, and an anonymous caller receives 401.

---

## F-03 Debug endpoint returned all accounts with password hashes

**Severity** 7.5 High (`AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N`) &middot; CWE-200, CWE-489

**Finding.** `GET /api/debug/users` returned `service.getAllUsers()`, which returned the live
`Map<String, User>`. `User` carried the BCrypt hash, so it was serialised into the response.
`GET /api/debug/lockout-status` exposed failed-attempt counters and lockout state. Both were
reachable with the header from F-01.

**Impact.** Disclosure of every username and password hash to an unauthenticated caller. BCrypt
resists cracking but does not prevent it; weak and reused passwords fall to an offline attack, and
the hashes also confirm which accounts exist.

**Root cause.** Diagnostic endpoints added during development and never removed, returning the
persistence object directly rather than a deliberately chosen projection.

**Remediation.** Both endpoints and their service methods were deleted. No endpoint returns the
`User` object; the profile endpoint builds an explicit map of fields.

**Verification.** `SecurityRegressionTest.DebugEndpoints` — both paths return 404 even for an
administrator, and no response body contains a BCrypt prefix.

---

## F-04 Administrator credentials compiled into the source

**Severity** 8.8 High (`AV:N/AC:L/PR:N/UI:R/S:U/C:H/I:H/A:H`) &middot; CWE-798

**Finding.** The service constructor seeded an administrator on every start:

```java
admin.setUsername("admin");
admin.setPassword(BCrypt.hashpw("admin123", BCrypt.gensalt()));
admin.setAdmin(true);
```

**Impact.** Every deployment shared a known administrator password, present in a public
repository. `admin123` also fails the application's own password policy, which registration
enforced on users but not on this account.

**Root cause.** Convenience seeding for local development, with no separation between a developer
fixture and a deployed configuration.

**Remediation.** The account is created only when `app.admin.username` and `app.admin.password`
are configured, and the configured password is checked against the password policy at startup —
a weak one aborts the boot rather than being silently accepted. Without configuration the
application has no administrator at all, which is the correct default.

**Verification.** `SecurityRegressionTest.SeededAdministrator` — the `admin` account does not
exist, and `admin123` is rejected by the policy.

---

## F-05 Login responses enabled account enumeration

**Severity** 5.3 Medium (`AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N`) &middot; CWE-204

**Finding.** Failed logins returned distinguishable messages:

```java
return Map.of("success", false, "message", "Invalid credentials. Attempt " + attempts + "/" + MAX_ATTEMPTS);
...
return Map.of("success", false, "message", "Account locked. Try again in " + secondsRemaining + " seconds");
```

An unknown username produced attempt counting against a name with no account, while a real one
eventually produced a lockout message. The two are distinguishable.

**Impact.** An attacker can confirm which usernames exist before spending guesses, and the attempt
counter reports exactly how many remain before lockout — which is the information needed to stay
below the threshold indefinitely.

**Root cause.** Error messages written for the convenience of a legitimate user, without
considering what they tell someone who is not one.

**Remediation.** Every failed authentication returns an identical 401 and the body
`{"error":"Invalid username or password"}`, whether the account is unknown, the password is wrong,
or the account is locked. The distinction is recorded in the security log, where the defender can
see it and the attacker cannot. `hideUserNotFoundExceptions` is enabled on the provider so the
framework does not reintroduce the distinction. Registration was fixed the same way: it no longer
reports that a username is taken.

**Verification.** `SecurityRegressionTest.AccountEnumeration` — a wrong password and an unknown
user produce byte-identical responses, and no response contains an attempt count.

---

## F-06 Authentication flow written to standard output

**Severity** 5.3 Medium (`AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N`) &middot; CWE-532

**Finding.** Roughly thirty `System.out.println` calls traced the authentication path, including
usernames, attempt counts, lockout transitions and password-change attempts.

**Impact.** Security-relevant events went to a stream with no level, no retention policy and no
access control, mixed into ordinary output. Anyone who could read the console could reconstruct
who logged in and when.

**Root cause.** Debugging statements left in place, and no logging framework in use despite one
being on the classpath.

**Remediation.** Replaced with SLF4J. Authentication, lockout, role changes and account deletion
go to a dedicated `SECURITY` logger, which can be routed and retained separately from application
noise. No credential, hash, token or session identifier is logged.

---

## F-07 Encryption key generated at startup and printed

**Severity** 7.5 High (`AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:H`) &middot; CWE-321, CWE-532

**Finding.** When `app.encryption.key` was unset — which it was, the property was committed
empty — `EncryptionService` generated a key per call and printed it:

```java
SecretKey key = keyGenerator.generateKey();
System.out.println("Generated key (base64): " + Base64.getEncoder().encodeToString(key.getEncoded()));
```

**Impact.** Two failures, neither of which announced itself. The key was written to the console, so
anything encrypted with it was readable by anyone with log access. And because `getEncryptionKey()`
was called per operation, a *different* key was generated for every encrypt and every decrypt, so
stored personal data — national identifiers, phone numbers, addresses — could never be read back.
The encryption was decorative.

**Root cause.** A development fallback that made a missing security control invisible instead of
loud. The AES-GCM implementation itself is correct: random 12-byte IV per operation, 128-bit tag,
IV prefixed to the ciphertext. Only the key management was wrong.

**Remediation.** The key is required, resolved once in the constructor, and validated as Base64
decoding to exactly 32 bytes. A missing or malformed key aborts startup with a message naming the
`openssl` command that produces a valid one. Nothing about the key is ever logged.

**Verification.** `EncryptionServiceTest` — construction fails without a key, with a short key and
with non-Base64; ciphertext round-trips; the same plaintext encrypts differently each time,
confirming the IV is not reused; and two instances sharing a configured key can read each other's
ciphertext, which is the property that was broken.

---

## F-08 No CSRF protection on state-changing endpoints

**Severity** 6.5 Medium (`AV:N/AC:L/PR:N/UI:R/S:U/C:N/I:H/A:N`) &middot; CWE-352

**Finding.** The application had no Spring Security on the classpath, so no CSRF protection
existed. The class named `SecurityConfig` implemented `WebMvcConfigurer` and only registered the
interceptor — the name suggested a control that was not there.

**Impact.** Limited in the original design, because the `X-Username` header is not attached
automatically by a browser and a cross-site form post cannot set it. That is an accident rather
than a defence, and it stopped being true the moment authentication moved to a session cookie —
which is exactly what the remediation for F-01 does. Fixing F-01 without this would have replaced
one vulnerability with another.

**Remediation.** CSRF protection is enabled with `CookieCsrfTokenRepository.withHttpOnlyFalse()`,
so the page's own scripts can read the token and echo it back, while another origin cannot read it
at all. `GET /api/auth/csrf` issues the cookie before the first state-changing request. The front
end was updated to send the token and to use `credentials: 'include'`.

**Verification.** `SecurityRegressionTest.CsrfProtection` — a state-changing POST without a token
is rejected with 403, the same request with a token succeeds, and reads are unaffected.

---

## F-09 Session cookie marked insecure

**Severity** 4.3 Medium (`AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:N/A:N`) &middot; CWE-614

**Finding.** `server.servlet.session.cookie.secure=false` was committed. `http-only` and
`same-site=strict` were set correctly.

**Impact.** The session cookie would be transmitted over plain HTTP, exposing it to anyone on the
network path.

**Remediation.** The setting is now `${SESSION_COOKIE_SECURE:false}` — still false for a local
plain-HTTP run, but set per environment rather than compiled in, and documented in `.env.example`
as required wherever the application is served over HTTPS. HSTS is also sent.

---

## F-10 Password change trusted a username from the body

**Severity** 8.1 High (`AV:N/AC:H/PR:L/UI:N/S:U/C:H/I:H/A:H`) &middot; CWE-639

**Finding.** `POST /api/change-password` read the target account from the request body:

```java
String username = request.get("username");
String oldPassword = request.get("oldPassword");
```

The endpoint was also on the interceptor's exclude list, so it required no authentication at all.
It did verify the old password, which is the only reason this was not trivially exploitable.

**Impact.** An attacker who obtained one account's current password could change it from an
unauthenticated request, and the endpoint accepted any username, making it a usable oracle for
testing credentials from a breach corpus against every account.

**Root cause.** The account to act on was taken from the request rather than from the session —
the same mistake as F-01, in a different place.

**Remediation.** Moved to `POST /api/auth/change-password`, which requires authentication and
takes the account from `Principal`. The request body carries only the current and new password. The
new password must satisfy the policy and must differ from the current one.

**Verification.** `SecurityRegressionTest.PasswordChange` — a request naming a different user in
the body changes the caller's own password and leaves the named account's password working, and an
unauthenticated request is rejected.

---

## F-11 Build output committed to version control

**Severity** Informational &middot; CWE-1104

**Finding.** `RecruitmentSystem/target/` was tracked, including compiled `.class` files and a copy
of `application.properties`.

**Impact.** No direct vulnerability here, since nothing sensitive was in the properties file. It
matters because compiled artifacts in a repository diverge from source without anyone noticing,
and a reviewer reading the tracked configuration may be reading a stale copy.

**Remediation.** `target/` removed from tracking and added to `.gitignore`, along with IDE
metadata, logs and `.env`.

---

## F-12 End-of-life framework with no dependency scanning

**Severity** 5.9 Medium &middot; CWE-1104

**Finding.** Spring Boot 3.2.0, whose OSS support ended in December 2024, with no dependency
scanning in the project. The README also claimed Java 24 while the POM declared 17.

**Impact.** Known vulnerabilities in the framework and its transitive dependencies would go
unnoticed and unpatched.

**Remediation.** Upgraded to Spring Boot 4.1.1 on Java 25. `jbcrypt`, an unmaintained third-party
BCrypt implementation, was dropped in favour of Spring Security's `BCryptPasswordEncoder`. A
CycloneDX SBOM is produced at package time, and CI runs dependency scanning, CodeQL and the test
suite on every push.

The JPA and H2 dependencies were removed rather than upgraded. The entities were annotated for
persistence but the service kept everything in `HashMap`s, so nothing was ever written to a
database. Removing the annotations makes the storage model honest; a persistent store is listed as
a limitation in the README rather than implied by unused configuration.

---

## Notes on scope

Two things were reviewed and left alone.

The AES-GCM implementation in `EncryptionService` is correct: a fresh 12-byte IV per operation from
`SecureRandom`, a 128-bit authentication tag, and the IV prefixed to the ciphertext. Only the key
management was wrong, and that is F-07.

`CaptchaService` accepts a fixed string `mock-recaptcha-token` when reCAPTCHA is disabled. That is
a test seam rather than a bypass, since with reCAPTCHA disabled there is nothing to bypass, but it
would become one if the flag were ever wrong in a deployed environment. It is noted here rather
than filed as a finding, and the safer shape would be to reject the request outright when the
feature is disabled.

## What this review does not cover

- No dynamic testing was performed against a running instance. Findings come from reading the code
  and are confirmed by tests, not by exploitation.
- Rate limiting is not implemented. Account lockout slows credential stuffing against one account
  but nothing limits request volume overall.
- The in-memory store means all state is lost on restart and cannot be shared across instances.
- File upload does not exist in this application, so the upload-handling controls that a real
  recruitment platform would need are out of scope entirely.
