# Changelog

## [Unreleased]
### Added
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
- Material 3 Expressive interface, English and Brazilian Portuguese.

### Fixed
- Crash when choosing a backup location, restoring a backup or importing passwords (an outdated transitive `fragment` library broke the system file pickers).

### Known issues
- Not compiled or tested outside CI yet.
- No autofill service yet.
