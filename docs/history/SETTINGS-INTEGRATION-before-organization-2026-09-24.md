# Settings, backup, activation, and updates

`MainActivity` retains one `SettingsUi` instance, calls `open()` from the avatar/settings action, calls `checkDailyUpdate()` after the first render, forwards request codes 330–399 to `onActivityResult`, and initializes `prefs = new AppPrefs(this)` before constructing the controller.

The host APIs used by the controller are those in `work/integration-contract.md`, plus `section` is implemented locally. Media integration calls `media.showBackgroundPicker()` and `pickImage`. Restoring reloads `store.read()` only after the validated archive has been saved.

The manifest needs `android.permission.INTERNET`, `android.permission.REQUEST_INSTALL_PACKAGES`, and the existing non-exported `AppFileProvider` with URI grants. `AppFileProvider.uriFor` must allow `cacheDir/updates`; Android always displays its package installer confirmation.

## Offline activation setup

`ActivationService` verifies `LVX1.<base64url-json>.<base64url-signature>` using RSA SHA-256. The JSON contains `product: "maisui-travel"`, `subject`, and optional ISO `expires`. The checked-in RSA-3072 public key matches the owner key generated for this build. Its private key is kept under the workspace `work/activation` folder, outside this repository and APK. Preserve that private key offline; losing it means future builds must switch to a new signing public key and old activation codes will stop working.

Example key setup with OpenSSL:

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -aes-256-cbc -out maisui-activation-private.pem
openssl pkey -in maisui-activation-private.pem -pubout -outform DER | openssl base64 -A
```

The standalone `tools/IssueActivationCode.java` utility creates codes from a PKCS#8 DER private key, and `ActivationService.issue(payload, privateKey)` is available for a custom issuer. Load the encrypted private key only on the offline issuing machine. No private activation key or GitHub token belongs in this project.

## Release metadata

The default release endpoint is the public GitHub API for `DualSpirits-Light/maisui-travel`. A private or missing repository reports that no public release is available. Each release must have a `maisui-update.json` asset:

```json
{"versionCode":4,"versionName":"0.2.1","packageName":"cn.lvxu.travel","apkUrl":"https://trusted.example/maisui.apk","sha256":"64 lowercase hex characters","notes":"Release notes"}
```

The updater checks `versionCode`, SHA-256, package ID, and that the APK signer matches the installed app before opening Android's installer.
