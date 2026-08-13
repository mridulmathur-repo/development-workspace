# Google SSO Demo

Spring Boot application demonstrating Google OAuth 2.0/OpenID Connect, automatic local user creation, and an HTTP session.

## Open in IntelliJ IDEA

1. Select **File > Open** and choose this `google-sso-demo` folder.
2. IntelliJ will detect `pom.xml`; choose **Load Maven Project** if prompted.
3. Use a Java 17 or newer SDK.

## Configure Google OAuth

1. In Google Cloud Console, create or select a project.
2. Configure the OAuth consent screen.
3. Create an **OAuth client ID** with application type **Web application**.
4. Add this authorized redirect URI exactly:

   `http://localhost:8080/login/oauth2/code/google`

5. Add these environment variables to the IntelliJ run configuration:

   `GOOGLE_CLIENT_ID=your-client-id;GOOGLE_CLIENT_SECRET=your-client-secret;BOOTSTRAP_ADMIN_EMAIL=admin@example.com`

Never commit the real client secret.

The client ID normally ends in `.apps.googleusercontent.com`. Restart the application after changing either environment variable. The authorization request includes Google's `prompt=select_account`, so Google displays its account chooser. Google may still reuse an active session for the selected account; only Google controls whether it asks for the password again.

## Roles and inventory

- Every new account receives `INVENTORY_USER` by default.
- Inventory users can view `/inventory`.
- Administrators can also add inventory at `/inventory/add` and manage roles at `/admin/users`.
- Only an administrator can call the role-change endpoint; hiding the UI is not the security boundary.
- Set `BOOTSTRAP_ADMIN_EMAIL` to the Google email that should be the bootstrap administrator. The matching account is promoted after any successful Google login, including when its local user already exists. Remove this environment variable after another administrator has been established if you no longer want automatic bootstrap promotion.
- The application prevents the final administrator from removing their own admin role.

## Run

Run `GoogleSsoApplication`, then open `http://localhost:8080/login`. The app redirects the user to Google's own sign-in page. After Google validates the identity, the app creates or finds the local user and redirects to the protected `/home` page.

The login page has two separate flows. **Log in with Google** accepts only an existing local account. **Create account with Google** inserts an `AppUser` after Google verifies the identity (and safely reuses it if it already exists). Later logins update the profile and last-login time. Spring Security stores the authenticated security context in the server-side HTTP session and the browser receives an HTTP-only `JSESSIONID` cookie.

The demo uses a file-backed H2 database under `data/`. For production, use PostgreSQL or another managed database, database migrations, HTTPS, secure cookies, and a persistent/distributed session store when running multiple application instances.

## Secure local file upload

- Every authenticated user can upload files and see/download only their own uploads.
- Administrators can see and download all uploaded files.
- File metadata is stored in H2; file contents are stored under `./uploads` by default.
- Override the directory with `FILE_STORAGE_LOCATION=C:\path\to\uploads`.
- The maximum file size is 25 MB.

## HTTPS through Tailscale Serve

Keep Spring Boot listening internally on `http://localhost:8080`, then run:

```text
tailscale serve --bg localhost:8080
```

Use the HTTPS URL printed by Tailscale. Add this exact Google OAuth callback in Google Cloud:

```text
https://YOUR-TAILSCALE-HOST/login/oauth2/code/google
```

For the HTTPS run configuration, set `SESSION_COOKIE_SECURE=true`. Google OAuth redirects must use the same public hostname used to open the application.
