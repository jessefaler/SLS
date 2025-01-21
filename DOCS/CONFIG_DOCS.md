### `allowed-client-versions`

**Description:**
This setting defines which Minecraft client versions are allowed to join the server. You can specify ranges, exclude versions, and combine multiple rules using commas.

**Usage:**

- **Version Ranges:**
  - `1.21..` – Allows clients using version 1.21 and newer.
  - `1.12..1.20` – Allows clients with versions between 1.12 and 1.20.
  - `..1.12` – Allows clients using version 1.12 and older.

- **Exclusions:**
  - `!1.12..1.15` – Excludes clients with versions from 1.12 to 1.15.

- **Combining Versions:**
  - `(1.12..1.15, !1.13, 1.20)` – Allows versions 1.12, 1.14, 1.15, and 1.20 while excluding version 1.13.

- **Default Value:**
  - If allowed-client-versions is not specified all client versions will be allowed.