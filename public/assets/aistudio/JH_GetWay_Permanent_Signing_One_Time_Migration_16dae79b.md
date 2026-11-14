# JH GetWay --- One-Time Migration to Permanent Signing Identity

**Decision:** Do not recover the old ephemeral signing key. Establish a
new permanent production keystore.\
**Installed build:** v1.0.11, versionCode 111, reported `ci-ephemeral`.\
**Goal:** Perform one controlled migration, then make every future
release an in-place Android update without repeated uninstall/reinstall.

## Critical Android constraint

The new permanent key has a different signing certificate from the old
ephemeral key. Android will reject an APK signed by the new key as an
update to the currently installed app.

Therefore: - A one-time uninstall/reinstall may be unavoidable. - Before
that, export and verify an application backup. - Keep the same
`applicationId` if the existing app identity is to continue. After the
old app is uninstalled, the new certificate can be used for a fresh
installation under that same application ID. - Once the new permanent
key is established, never change it again. - Future updates must use the
same application ID, same permanent signing certificate and a strictly
higher `versionCode`.

Do not promise that private app data automatically survives uninstall.
Only data included in a tested export/backup can be restored.

## Non-negotiable preservation rules

1.  Do not uninstall v1.0.11 until backup export and restore validation
    are complete.
2.  Do not clear app data.
3.  Do not change the current `applicationId` unless a separately
    documented technical blocker makes it unavoidable and the user
    explicitly approves.
4.  Do not modify the completed Playground or unrelated features.
5.  Do not remove existing settings or features.
6.  Do not use destructive Room migrations.
7.  Do not publish a release automatically.
8.  Do not use ephemeral signing or a temporary fallback key for
    production ever again.
9.  Do not expose keystore/passwords in source code, Git, build logs or
    generated reports.
10. Do not claim successful migration until the new signed APK is
    installed and the restored data has been checked on the user's
    device.

## Phase 1 --- Inspect and freeze the current app identity

Inspect the current source and report: - actual `applicationId` -
namespace - current version name/code - current release workflow -
expected key alias and signing configuration - update mechanism and
GitHub repository configuration - all user data/configuration that can
be exported and restored

Keep the current `applicationId` for continuity. Do not silently change
it.

The next production build must have a `versionCode` greater than 111.
Choose and document the next version name; do not reuse a previously
published version tag.

## Phase 2 --- Implement and verify a complete backup/export

Before creating a migration package: - Inspect the existing encrypted
backup implementation and supported backup versions. - Ensure export
includes every supported user-owned record: - provider configurations
(never export plaintext secrets) - encrypted API credentials only if
they are safely portable; otherwise explain re-entry is required - model
aliases and selections - routing policies and provider priorities -
local AI server configuration - coding-agent configuration - MCP server
configuration and permissions - workflows and relevant history - device
records only where safe and meaningful - user preferences, language and
theme - app-lock configuration only if safely portable; otherwise
require setup again - Playground conversations and history -
analytics/history if supported by the backup format - Include a manifest
with backup format version, creation time, app version and included data
categories. - Encrypt the backup using the existing secure backup
design. - Do not include Keystore master keys or Android-bound key
material that cannot be restored on a fresh installation. - Clearly
identify data that cannot be migrated, such as Android Keystore-bound
secrets or device-specific tokens. - Add integrity validation and
wrong-password/corrupt-backup tests. - Provide a restore preview and
transactional restore. Do not overwrite existing records without an
explicit conflict policy.

If existing backup support cannot safely export a category, report it
honestly. Do not fabricate a successful migration.

## Phase 3 --- Create the new permanent keystore

Create exactly one production keystore using a secure, supported Android
signing workflow.

Requirements: - Use a strong unique keystore password and key
password. - Use a documented stable key alias. - Keep at least two
secure offline backups of the `.jks` file. - Store passwords in a
password manager. - Never commit the `.jks` or passwords to the
repository. - Convert the keystore file to Base64 only for transferring
it into the GitHub Actions secret. - Do not print the Base64 content or
passwords in logs or chat. - Verify and record the new certificate
SHA-256 fingerprint. - Treat this keystore as permanent. Losing it can
prevent future updates.

Configure GitHub repository Actions secrets: - `KEYSTORE_BASE64` -
`STORE_PASSWORD` - `KEY_PASSWORD`

Use the exact alias configured in Gradle. Do not assume an alias without
checking.

## Phase 4 --- Make release signing fail closed

Update the release workflow: - Production release must use the
configured permanent keystore. - If any signing secret is missing or
invalid, fail before publishing. - Remove all `ci-ephemeral`, generated
temporary production key and fallback signing logic. - Debug builds may
use the normal Android debug key, but debug APKs must never be published
as production update assets. - Do not print secrets. - Verify the APK
signer certificate SHA-256 against the expected permanent fingerprint. -
Verify package/application ID. - Verify `versionCode` is strictly higher
than the prior production version. - Publish only on explicit version
tags such as `v1.0.12`, after all tests pass. - Do not publish on
ordinary pushes to `main` or `master`.

## Phase 5 --- Build and test the migration candidate

Build a candidate signed with the new permanent key. Do not publish it
yet.

Verify: - correct application ID - expected version name - versionCode
greater than 111 - signer certificate matches the new permanent
fingerprint - release APK checksum - app launches - backup import
succeeds - restored data categories are correct - no unrelated
feature/settings regressions

Install the candidate only after the backup is exported and verified.
Because the signature differs, the old v1.0.11 app may need to be
uninstalled once before installing the new signed APK under the same
application ID. Explain this clearly and obtain the user's approval
before the one-time uninstall. Never automate this destructive step.

After installation, restore the verified backup and test: - providers
and model catalogue - routing rules - credentials that are portable -
local AI and coding-agent settings - MCP servers and permissions -
workflows - Playground conversations - language and theme -
analytics/history where supported - app lock (reconfigure if not
portable)

If the backup does not include a data category, explain the manual
reconfiguration needed before the old app is removed.

## Phase 6 --- Permanent future update policy

For every future production release: 1. Create a unique tag,
e.g. `v1.0.12`, `v1.0.13`, `v1.1.0`. 2. Run the complete CI test suite.
3. Build with the same permanent keystore. 4. Verify application ID,
version code and certificate fingerprint. 5. Generate SHA-256 checksum
and update manifest. 6. Publish APK, manifest, checksum and release
notes to the GitHub Release for that exact tag. 7. Let the app updater
detect and verify the release. 8. Install through Android's normal
in-place update flow (`Package Installer` confirmation may be required).
9. Preserve Room database, DataStore, credentials, settings and user
content.

Release gate must reject: - missing signing secrets - changed signing
certificate - changed application ID - reused tag/version -
non-increasing versionCode - missing/invalid APK asset - failed tests -
checksum mismatch

## Required GitHub release assets

Use consistent names, for example: - `JH-GetWay-v1.0.12-release.apk` -
`update-manifest.json` - `SHA256SUMS.txt` - release notes

The manifest must identify the exact tag, version name, version code,
package ID, APK asset URL and SHA-256 checksum. The updater must verify
the downloaded artifact before handing it to Android Package Installer.

## Final acceptance checklist

-   [ ] Current app identity inspected and documented.
-   [ ] Current user data export completed.
-   [ ] Backup integrity verified.
-   [ ] Backup restore tested before uninstall.
-   [ ] New permanent keystore created once.
-   [ ] Keystore securely backed up twice.
-   [ ] GitHub Actions secrets configured.
-   [ ] Workflow has no ephemeral production signing fallback.
-   [ ] Release workflow fails closed when secrets are absent.
-   [ ] Candidate APK has expected application ID.
-   [ ] Candidate APK has new permanent certificate fingerprint.
-   [ ] Candidate versionCode is greater than 111.
-   [ ] One-time migration performed only after user approval.
-   [ ] Backup restored and data verified.
-   [ ] Future tag-based release workflow tested.
-   [ ] In-app updater verifies checksum and signing compatibility.
-   [ ] No Playground or unrelated feature regression.
-   [ ] No production release published before explicit approval.

## Google AI Studio execution instruction

Work in the existing JH GetWay project. Do not create a new app or
redesign the UI.

First inspect the source and implement backup/export validation,
permanent signing configuration and fail-closed GitHub release workflow.
Do not uninstall the current app, do not publish a release, and do not
create a new application ID without approval.

Prepare the exact steps and commands for generating the permanent
keystore and adding the three GitHub Actions secrets. Never output the
keystore, Base64 value or passwords into the chat or repository.

When the candidate is ready, report: - exact application ID - new
signing certificate fingerprint - next version name/code - backup
coverage and limitations - files changed - tests actually run - exact
one-time migration steps

Stop before the one-time uninstall/install migration and wait for user
approval.

**JH GetWay --- JH Soft Corporation**
