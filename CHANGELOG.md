# Changelog

## [Unreleased]
### Changed
- "Use system colors" is now off by default, so the app has its own look.
- The Tools card on the home is now a large illustrated highlight card. Settings opens on the first tap. The locked screen text is readable again (it was black on black). Entries linked to an installed app show that app's icon instead of a letter (read from the device, no network).
- Home redesigned: no bottom tab bar. A "Tools" card at the top of the vault opens the password generator and the check-up on one screen; Settings is now a floating button in the top-right corner (next to Lock). Screens slide in and out, with the predictive back gesture.
- The floating back button is back to 48 dp (it had grown to 56 dp).

### Added
- Website icons: entries show the site's own icon (found from the site's own home page, with apple-touch-icon / favicon.ico as fallback; downloaded once from the site itself and kept in the app's storage, not the cache). Can be turned off in Settings → Appearance; turning it off deletes them. Privacy policy updated.
- Settings → Appearance: theme (system default, light, dark, AMOLED pure black) and a switch to use the app's own colors instead of the Android system colors.
- Settings → "Autofill diagnostics" (off by default): records in memory only, and shows with a copy button, what Android reports on each fill request (field types, hints, roles, whether a field was empty). Never typed text, passwords or vault data; nothing leaves the device.
- Local encrypted vault (AES-256-GCM, PBKDF2-HMAC-SHA256 key derivation) stored only on the device; the master password is never stored.
- Create or unlock the vault with a master password (12 characters minimum) or with strong biometrics.
- Add, edit, search and delete passwords. Copy fields with a clipboard marked as sensitive and cleared after a chosen time.
- Password generator with a random mode and a memorable (words) mode, with an entropy estimate.
- Password check-up: finds reused and weak passwords on the device (it does not check for leaks).
- Backup to a location you choose (any Android document provider, such as a local folder or Google Drive): encrypted copy, automatic after each change or on demand. Restore from a backup file.
- Import from CSV (Google Password Manager, Chrome, Bitwarden and similar) and Bitwarden JSON. Export as unencrypted CSV with a warning.
- Change the master password; delete the vault.
- Automatic lock when the app stays in the background, and screenshot blocking (on by default).
- About screen and update notices from public GitHub Releases.
- Redesigned Updates and About: grouped cards with icon badges, version and build chips, developer card with the maintainer's GitHub photo, and a Licenses and credits screen.
- App picker on entries: choose an installed app (icon, name, search) instead of a website; the entry shows the app with a remove button and autofill suggests it in that app.
- Autofill service: appears in Android's preferred service list. With the vault unlocked, matching entries show up directly in the system suggestions and in keyboard inline suggestions; when locked it asks for unlock first, and "Other entries…" opens the full list (saving new passwords is not supported yet). Quick access in Settings.
- Autofill with the vault locked (optional "Suggestions while locked" in Settings): matching entries are listed and tapping one opens only the system fingerprint/device-PIN panel, then fills directly, without opening the app. It uses an encrypted index of titles, usernames and sites (never passwords). Biometric unlock now also accepts the device PIN/password (turn it on again once after updating).
- Material 3 Expressive interface, English and Brazilian Portuguese.

### Fixed
- About screen and shared UI now use the same sizes as the maintainer's other apps (Intruphon): bold type weights, 56 dp back button, 80 dp page title, 32 dp cards with 52 dp icon badges, sentence-case group labels, 120 dp header icon.
- Autofill suggestions no longer vanish after the first few typed characters: the open-vault entries now carry the same explicit prefix filter as the locked ones (a flat prefix list instead of nested groups).
- Autofill finds the username field even when the browser lists the password field first (seen on Firefox).
- Autofill now recognizes the Google sign-in identifier field (`identifier`), which has no label or e-mail type, so the system Google login screen gets suggestions.
- Crash when choosing a backup location, restoring a backup or importing passwords (an outdated transitive `fragment` library broke the system file pickers).
- Top of every screen: no more pinned top bar or floating back button covering titles. The title is now the first line of the page and scrolls with it; only the back button stays in the corner and the title starts after it (same pattern as BoomListener and Joule Arc).
- Settings: the last card no longer touches the bottom navigation bar.
- Update notices is now a bottom sheet with a choice (notify normally, not for a week, not for a month); before, tapping it did nothing when notices were on.
- Autofill: login fields on web pages whose inputs have no explicit type (or only a name/placeholder/aria-label) are now recognized, so the username is filled too. Autofill is only offered on screens that look like a login (a password field, or a field that is clearly a username/email); search boxes and other plain text fields no longer get "Fill with Babel Manager".
- Autofill suggestions that need unlocking now hide once you start typing (Android 13+): in the username field they stay only while the text is the beginning of that entry's username or title; elsewhere only while the field is empty.
- Autofill also reads what is already in the username field when the screen is opened (e.g. an e-mail the page pre-filled): it only suggests entries that start with that text, and offers nothing when a password is already typed.

### Known issues
- Not compiled or tested outside CI yet.
- No autofill service yet.
