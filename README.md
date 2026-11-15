# Babel Manager

An Android password manager that keeps your vault encrypted on your device, and lets you choose where its backup copy goes.

## Features

- Encrypted local vault: AES-256-GCM, with the key derived from your master password (PBKDF2-HMAC-SHA256, 600,000 iterations).
- Unlock with the master password or with strong biometrics.
- Add, edit, search and delete passwords; sensitive clipboard that clears itself.
- Password generator (random or memorable words) and a check-up for reused and weak passwords.
- Backup to **a place you choose** (local folder, Google Drive or any Android document provider): an encrypted copy, saved after each change or on demand. Restore from a backup file.
- Link an entry to an **app**: pick it from your installed apps (icon and name, with search) and autofill suggests the entry in that app.
- Autofill service for apps and browsers: With the vault unlocked, matching entries appear right in Android's suggestions (and in the keyboard, when it supports it); when locked, Android asks you to unlock first (or, with the optional "Suggestions while locked" setting, lists the matching entries and asks for your fingerprint or device PIN in the system panel, without opening the app). Saving new passwords from other apps is not supported yet.
- Import from CSV (Google Password Manager, Chrome, Bitwarden and similar) and Bitwarden JSON.
- Auto-lock, screenshot blocking, Material 3 Expressive design, English and Brazilian Portuguese.

## Privacy and permissions

- Your vault is stored in the app's private storage, encrypted. There is no account and no server of its own.
- Permissions: `INTERNET`, used only to ask GitHub for the latest public release (update notice) and to show the maintainer's public photo on the About screen, and `USE_BIOMETRIC`. If you pick Babel Manager as your autofill service, Android lets it read the login fields of the screen you are on, only to fill them; nothing is stored or sent.
- The backup copy is encrypted before it leaves the app, and it is written to the location you pick through Android's document picker. What that provider does with the file follows its own policy.
- **There is no master password recovery.** If you forget it, nobody can open your vault.

Details in the [privacy policy](https://rilclesaraiva.github.io/Babel-Manager/privacy).

## 🤖 Built with AI ("vibe coding")

In the spirit of transparency: **Babel Manager is developed with heavy use of AI.**
Most of the code is written by [Claude](https://claude.ai) (Anthropic) under the
direction of the human maintainer, a workflow often called *vibe coding*.
We take care that it doesn't turn into AI slop:

- **The maintainer decides, the AI executes.** Every feature starts from a real
  need, and every build is tested on real devices before the next round.
- **Written rules the AI has to follow.** [`CLAUDE.md`](CLAUDE.md) sets the
  architecture, privacy limits and process, and is updated with every decision.
- **Tests and automated builds.** Every change goes through GitHub Actions,
  and a failing test means no build.
- **No release without approval.** Nothing is published without the
  maintainer's explicit approval.
- **Honest changelogs.** [`CHANGELOG.md`](CHANGELOG.md) says what changed,
  including what is still broken.

If AI-assisted code is a concern for you, now you know, and you are welcome to
read every line. Found something careless? Please [open an issue](../../issues).

## Install

Download the latest `.apk` from [Releases](../../releases) and open it on your Android 11+ device (you may need to allow installs from your browser or file manager).

To build from source you need JDK 17 and the Android SDK:

```
./gradlew testDebugUnitTest assembleRelease
```

## License

[GPL-3.0](LICENSE)

## Legal

[Privacy policy](https://rilclesaraiva.github.io/Babel-Manager/privacy) · [Terms of use](https://rilclesaraiva.github.io/Babel-Manager/terms)

## Contact

Questions, bugs and ideas: [open an issue](../../issues) or write to saraivarilcle@gmail.com.

## Support

Like the project? You can support it on [Buy Me a Coffee](https://buymeacoffee.com/saraivarilcle).
