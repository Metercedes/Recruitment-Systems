# Recruitment Systems

**Short description**
A simple job/application application developed with IntelliJ IDEA, using Maven + Java 24. When run locally the entry page is: `http://localhost:8098/jobseeker-login.html`.

---

## Requirements

* JDK 24 (Java 24)
* Maven
* IntelliJ IDEA (Ultimate or **Community** — Community is free and likely sufficient)
* NOTE: IntelliJ can download JDK and Maven inside the IDE; no separate installation is required.

  * Download: [https://www.jetbrains.com/idea/download/?section=mac](https://www.jetbrains.com/idea/download/?section=mac)

---

## How to run

1. Clone the repo.
2. Open the project in IntelliJ as a Maven project (Import).
3. Select JDK 24 as the Project SDK.
4. Build from the command line or the IDE terminal:

   ```bash
   mvn clean package
   ```
5. Start the application via an IDE run configuration or, if a jar was produced:

   ```bash
   java -jar target/<artifact>.jar
   ```
6. Open in the browser:
   `http://localhost:8098/jobseeker-login.html`

> Note: Depending on the project structure you can use an embedded server (e.g., Jetty/Tomcat) or the IDE run configuration. Check the console logs.

---

## Desired (expected) features

* **Secure login**

  * Captcha (against bots)
  * Password strength checks (minimum length, complexity rules)
  * Hashing for passwords (bcrypt/argon2)
  * AES-256 encryption for sensitive user data (pay attention to key management)
* **Authorisation / Security**

  * Login should not be bypassable by link/URL manipulation — enforce server-side session and role checks
* **Roles**

  * `Admin`: Manages accounts; can delete/ban, etc.
  * `User`: Creates profile/CV; jobs are listed to them based on skills.
  * `Company`: Posts job ads; reviews applications, accepts/rejects.

---

## Current issues (areas where I need help)

1. **Login / Register buttons not working**

   * Clicking the button produces no reaction. (Frontend event not firing or backend endpoint not reachable.)
   * Checks: browser console (JS errors), Network tab (is the request sent?), button `type` and event listener.

2. **Login bypass via URL manipulation**

   * Protected pages can be accessed directly. Expected: server-side session/role checks and protection.

3. **Data not encrypted**

   * Sensitive data is stored in plain form. AES-256 encryption is desired; passwords should be hashed instead.

---

## Security notes (important)

* **Passwords are *not encrypted*; they are hashed.** (Use bcrypt or argon2.)
* AES-256 is suitable for other sensitive data (e.g., ID numbers, personal documents), not passwords.
* Do **not** store the AES key in code — use environment variables, a secrets manager, or a KMS.
* All validations must be repeated **server-side**; frontend validation is only for user experience.

---

## To-do / Recommended steps (in priority order)

1. Debug login/register buttons in the browser (console + network). Fix any errors; if none, check backend endpoints.
2. Add server-side access control (role-based access control).
3. Password storage: implement hashing with bcrypt/argon2.
4. AES-256 encryption for sensitive fields; decide on key management.
5. Integrate captcha and password strength validation — I tried this, it produced an error; afterwards I attempted to implement the feature myself but it didn't work.
6. Submit small, focused PRs (e.g., fix the button bug first, then authorisation, etc.).

---
