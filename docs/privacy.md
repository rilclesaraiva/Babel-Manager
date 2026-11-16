# Privacy Policy — Babel Manager

Last updated: 2026-11-13

## Who is responsible

Babel Manager is an open-source app maintained by the project maintainer (GitHub user `rilclesaraiva`). Contact: [open an issue](https://github.com/rilclesaraiva/Babel-Manager/issues) or saraivarilcle@gmail.com.

## What the app does with your data

- **Your vault** (the passwords, usernames, sites and notes you add) is stored on your device, in the app's private storage, in an encrypted file. The key comes from your master password, which the app does not store. The master password cannot be recovered by anyone.
- **The app has no server of its own and no account system.** It does not send your vault, your entries or your master password anywhere.
- **Biometric unlock (optional):** if you turn it on, a key protected by the Android Keystore holds an encrypted copy of the vault key on your device. It is released only with your fingerprint, face or the device PIN/password, which stay in Android and are never read by the app.
- **Suggestions while locked (optional, off by default, needs biometric unlock):** if you turn it on, the app keeps on your device an encrypted index with the title, username and site of your entries (never passwords), encrypted by an Android Keystore key that does not need your fingerprint, so autofill can list matching entries while the vault is locked. Picking one asks for fingerprint or device PIN. Turning the option or biometric unlock off, or deleting the vault, deletes the index.
- **Settings and caches** stored on your device: your preferences, the location you chose for backup, and the cached answer of the update check.
- **Autofill (optional):** if you choose Babel Manager as your autofill service in Android settings, Android sends the app the structure of a login screen (which fields exist, and the app or website name) when you tap a field. The app uses it only to fill the entry you pick. While the vault is locked, Android's suggestion list shows only "Fill with Babel Manager". While the vault is unlocked, it also lists the title and username of the entries that match the site or app (the password is never displayed in the list); those names are handed to Android and, for inline suggestions, to your keyboard app, which handles them under its own policy. The app never stores that screen information and never sends it anywhere.
- **App picker (optional):** when you link an entry to an app, the app reads the list of installed apps that have a launcher icon (name and icon), only on your device, only to show the picker and the chosen app. That list is not stored and is never sent anywhere. Only the package name you choose is saved, inside the encrypted vault.
- **Clipboard:** when you copy a field, the app puts it on the clipboard marked as sensitive and clears it after the time you choose, if Android allows.

## Backup

If you choose a backup location, the app writes an **encrypted** copy of your vault there, using Android's document picker. The app does not upload files itself. The place you chose (for example a local folder or Google Drive) receives that file and handles it under its own privacy policy. Without your master password the copy cannot be read.

## Network requests

The app makes up to three kinds of network request. The first two go to GitHub. (1) It asks GitHub (`api.github.com`) for the latest public release, when the app opens and when you return to it (at most every 10 minutes), or when you tap "Check for updates". (2) When you open the About screen, it downloads the public profile photo of the project maintainer from `github.com` (cached on your device for 7 days); the request contains only the maintainer's username. GitHub receives your IP address and a user-agent that contains the app version, and handles them under [its own privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement). (3) Unless you turn off Settings → Appearance → Website icons, the app reads the home page of each entry's website (`https://<domain of the entry>/`, only to find the address of its icon) and downloads the icon directly from the website, once per site, and keeps it on your device (in the app's private storage, not in the cache, so clearing the cache does not remove it; turning the option off deletes them). Each site receives your IP address and a browser-like user-agent (many sites refuse other clients), and the request contains only the domain you saved for that entry; it never contains usernames, passwords or other vault data. Sites with no icon are not asked again for 7 days. Requests (1) and (2) contain no vault data either. If you use "Open website" on an entry, or the project links in About, your browser opens that address.

## Permissions

- `INTERNET`: only for the update check, the maintainer's photo on the About screen and the website icons (which you can turn off).
- `USE_BIOMETRIC`: only for biometric unlock.
- Visibility of launcher apps (`<queries>` for apps with a launcher icon): only for the app picker. The app does not request `QUERY_ALL_PACKAGES`.
- Autofill service (`BIND_AUTOFILL_SERVICE`, granted by Android only when you select the app as your service): only to fill logins.

## Third-party code

The app uses AndroidX, Jetpack Compose, Material 3 and Kotlin coroutines. It does not include analytics, advertising or crash-reporting libraries.

## Your choices and deleting your data

- Turn off biometric unlock, backup or screenshot blocking in Settings, and change or remove the autofill service in Android settings.
- Delete the vault in Settings, or uninstall the app, to erase the data on your device.
- Backup copies and exported files live where you saved them; delete them there.

## Laws

Where they apply (such as the Brazilian LGPD and the European GDPR), you have the rights those laws give you. Because the app keeps your vault only on your device and has no servers of its own, the controller for that data is you; for questions, open an issue.

## Changes

If the app changes what it collects or sends, this policy is updated in the same change, with a new date.
