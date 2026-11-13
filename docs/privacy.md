# Privacy Policy — Babel Manager

Last updated: 2026-11-13

## Who is responsible

Babel Manager is an open-source app maintained by the project maintainer (GitHub user `rilclesaraiva`). Contact: [open an issue](https://github.com/rilclesaraiva/Babel-Manager/issues) or saraivarilcle@gmail.com.

## What the app does with your data

- **Your vault** (the passwords, usernames, sites and notes you add) is stored on your device, in the app's private storage, in an encrypted file. The key comes from your master password, which the app does not store. The master password cannot be recovered by anyone.
- **The app has no server of its own and no account system.** It does not send your vault, your entries or your master password anywhere.
- **Biometric unlock (optional):** if you turn it on, a key protected by the Android Keystore holds an encrypted copy of the vault key on your device. Your fingerprint or face data stays in Android and is never read by the app.
- **Settings and caches** stored on your device: your preferences, the location you chose for backup, and the cached answer of the update check.
- **Clipboard:** when you copy a field, the app puts it on the clipboard marked as sensitive and clears it after the time you choose, if Android allows.

## Backup

If you choose a backup location, the app writes an **encrypted** copy of your vault there, using Android's document picker. The app does not upload files itself. The place you chose (for example a local folder or Google Drive) receives that file and handles it under its own privacy policy. Without your master password the copy cannot be read.

## Network requests

The only network request made by the app is to ask GitHub (`api.github.com`) for the latest public release, when the app opens and when you return to it (at most every 10 minutes), or when you tap "Check for updates". GitHub receives your IP address and a user-agent that contains the app version, and handles them under [its own privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement). No vault data is part of this request. If you use "Open website" on an entry, or the project links in About, your browser opens that address.

## Permissions

- `INTERNET`: only for the update check above.
- `USE_BIOMETRIC`: only for biometric unlock.

## Third-party code

The app uses AndroidX, Jetpack Compose, Material 3 and Kotlin coroutines. It does not include analytics, advertising or crash-reporting libraries.

## Your choices and deleting your data

- Turn off biometric unlock, backup or screenshot blocking in Settings.
- Delete the vault in Settings, or uninstall the app, to erase the data on your device.
- Backup copies and exported files live where you saved them; delete them there.

## Laws

Where they apply (such as the Brazilian LGPD and the European GDPR), you have the rights those laws give you. Because the app keeps your vault only on your device and has no servers of its own, the controller for that data is you; for questions, open an issue.

## Changes

If the app changes what it collects or sends, this policy is updated in the same change, with a new date.
