# FR-006 — Jagex account authentication for the isolated developer client

**Status: prepared for Solis review. Live account testing (R3) stays on HOLD until Solis
clears this procedure.** Candidate `aefb1d79` and the retained official JAR (76,270 B,
`5eb3405a…`) are unchanged; only the playtest kit is touched.

## 1. Verified mechanism (pinned RuneLite 1.13.8 = `8397ec7131ec029bef30ab8ae835b3eb3ebffd9a`)

Source read from `runelite/runelite` at the pinned tag; binary facts pinned in the kit CI on the
exact client jars (`fr-windows-playtest`, "Jagex sign-in facts").

| Step | What happens | Source |
|---|---|---|
| Panel | Built-in plugin "Jagex Auth" (`developerPlugin = true`, hidden) adds a **Jagex Accounts** sidebar button, only in developer mode. The kit runs `--developer-mode` without a launcher, so it is enabled | `plugins/jagexauth/JagexAuthPlugin.java:41-42, 61`; `RuneLite.java:238` (developer mode only when `--developer-mode` and no launcher version) |
| Start-up | Loads saved sessions from `jagex-accounts.dat`; if the file does not exist nothing else is read (no key access) | `JagexAuthPlugin.java:69`; `rs/auth/SessionStore.java:46, 58` |
| Add Jagex Account | Copies the bundled helper `JagexAuth-Win-amd64.exe` (from `jagexauth-0.3.jar`, 573,440 B, SHA-256 `c864dcbe186e08a85e8cdbc372244aa7dfa184ccb8de509d07fb11b82c265b64`) to `java.io.tmpdir` (the kit's `tmp`), runs it with `--prompt=login`, reads `id_token:` from its output, deletes it | `rs/auth/JagexAuthService.java:143, 151, 163, 176, 254`; panel `JagexAuthPanel.java:65` |
| Sign-in window | The helper is an embedded **WebView2** window (`CreateCoreWebView2` ×12, `UserDataFolder`, `EBWebView`), loading `account.jagex.com` and handling a `http://localhost` redirect internally; no `ShellExecute` (the normal browser is not opened), no Credential Manager or registry calls | helper strings in CI |
| Session | `id_token` → `POST https://auth.jagex.com/game-session/v1/sessions` → session id; characters from `GET …/v1/accounts` | `rs/auth/JagexAuthClient.java:63, 81` |
| Persistence | Sessions saved encrypted to `<RUNELITE_DIR>\jagex-accounts.dat` (the kit's `home\.runelite`). **The encryption key** is a random 32-byte generic credential in **Windows Credential Manager**, target `RuneLite SessionStore`, machine persistence: outside the kit | `SessionStore.java:46, 103`; `rs/auth/WindowsCredentialStore.java:43, 45, 99, 105` |
| Play | Clicking a character switches the client to that Jagex session (`client.switchJagexAccount`); "Use Legacy Account" is for non-Jagex accounts | `JagexAuthService.java:216-218`; `JagexAuthPanel.java:81-83, 185` |

`SessionStore`/`WindowsCredentialStore` are used only by the developer `JagexAuthService` (CI
class scan), so the normal RuneLite installation never touches that key.

## 2. Previous TPT/Burrow procedure (records reviewed, read only)

Burrow (`feralenterprises/Burrow` @ `67b7d848`) TPT launchers start RuneLite's developer client
the same way the kit does (`BurrowPluginTest`: `ExternalPluginManager.loadBuiltin` +
`RuneLite.main --developer-mode`, "never handles credentials"; `packaging/LAUNCH-TPT.cmd`,
`QUICK-LAUNCH-WINDOWS.cmd`). No document describes the sign-in itself: 410 text files contain
no Jagex-account, credentials-file or `--insecure-write-credentials` procedure; owner playtests
just "sign in". The quick launcher did **not** redirect `user.home`, so its sign-ins used the
owner's normal `.runelite`. Reusable part: the developer client's own sign-in. Not reusable: the
non-isolated home.

## 3. Alternatives rejected

| Option | Why not |
|---|---|
| Jagex Launcher → RuneLite (`--insecure-write-credentials` writes `credentials.properties`) | Writes launcher tokens to a plain-text file in the normal `.runelite`; copying tokens is forbidden |
| Starting the kit client from the Jagex Launcher | Developer mode is disabled when a launcher version is present (`RuneLite.java:238`), and it would be the normal installation |
| TPT quick launch as-is | Uses the normal RuneLite profile |
| Patching RuneLite to avoid the Credential Manager key | Modifies the pinned client; not a minimal change |

## 4. Isolation compatibility and the kit change (red/green)

Before this change a Jagex sign-in would have created the Credential Manager key **without any
isolation failure** (the store was unwatched). Kit changes (`test/fr-windows-playtest`):

- The isolation snapshot now includes the **presence** of `RuneLite SessionStore` (via
  `cmdkey /list`, names only; the key is never read) and the **top-level folder names** of
  `%LOCALAPPDATA%` and `%APPDATA%` (a WebView2 data folder created outside the kit would show
  there). Unreadable state → preflight incomplete → step not run, kit latched.
- **Only R3** may see that key go from absent to present; the change is recorded
  (`isolation.json` `expectedChanges`, `KIT-CREATED-CREDENTIAL.txt`). Any other change, a
  removal, or the key appearing in R1/R2/R4 latches the kit.
- Menu option 7 removes the key **only if the kit recorded creating it** (`cmdkey /delete`,
  confirmed absent afterwards); a pre-existing key is never touched (the kit's session file is
  then encrypted with it).
- Session file `jagex-accounts.dat`, the helper copy and any WebView2 data next to it stay in the
  kit (`home`, `tmp`); option 7 deletes them. Receipts never include `client.log` (which can name
  the Jagex account); the R4 excerpt keeps only plugin lines.

Regressions (`playtest/tests/Launcher.Tests.ps1`, red at `9cbd3ea5`/`414c641a`, green at
`f4dddf81`): credential created in a no-login step latches; unreadable credential state stops
the step; R3 expected creation recorded; allowance never covers other changes or a removal;
only R3 gets the allowance; reset removes only a kit-created key; new top-level
`%LOCALAPPDATA%`/`%APPDATA%` folders latch even in R3. The Windows-runner R1, R2 and launch path
stay PASS with the new watches.

## 5. Procedure (for Solis to clear; not yet authorized)

1. Fresh kit in `C:\RaccoonPlaytest`; normal RuneLite and Jagex Launcher closed.
2. Menu 4 (R3). At the login screen click **Jagex Accounts** (sidebar) → **Add Jagex Account**.
3. A Jagex sign-in window opens inside the test client; the owner signs in there (the kit never
   sees or asks for the password).
4. The account appears in the panel; click the character; then play per the R3 checklist.
5. After R3, menu 6 (receipts), then menu 7 removes the kit's sign-in data and the key it created.

## 6. Remaining blockers

- Owner live sign-in and R3 remain HOLD until Solis clears this procedure.
- The WebView2 user-data location is inferred (default: beside the helper in the kit's `tmp`),
  not proven; the new AppData watch fails closed if it is elsewhere.
- The Edge WebView2 Runtime must be installed (standard on Windows 11; usually present on
  Windows 10 with Edge); without it the sign-in window cannot open.
- A genuine open-bank failed read cannot be produced on demand; it stays BLOCKED if not observed.
- R4/I03 owner receipt and the owner receipt ZIP review are outstanding.
