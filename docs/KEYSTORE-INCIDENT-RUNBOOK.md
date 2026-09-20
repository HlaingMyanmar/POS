# Keystore exposure incident runbook

## Scope and current evidence

Treat these private-key containers as compromised because they were committed:

- `src/main/resources/keystore.p12`
- `technician-app/app/keystore` (extensionless)

The server certificate is self-signed for `CN=192.168.20.253`, valid from
2026-05-19 through 2036-05-16, with SHA-256 fingerprint
`58:BD:EC:00:EA:0B:EF:11:5E:F7:FD:DB:86:EF:6D:8B:FF:9E:E4:7B:1B:BF:A1:AC:4B:96:70:04:4E:73:93:C6`.
Production documentation says Nginx terminates TLS and the JVM runs with
`SSL_ENABLED=false`. This workstation's ignored local configuration enables
SSL and points at the removed repository-root keystore, which is evidence of
active local use but not evidence about the live server. Confirm the live
service environment and any certificate pinning before deciding that this key
was not deployed.

The external Android release key at `D:\androids\keystore` has certificate
SHA-256 fingerprint
`44:A8:4D:B7:DC:3A:72:DF:57:70:59:8F:EF:1F:4A:76:01:18:2D:DF:0E:71:82:07:B2:87:62:67:17:47:61:A9`.
Known technician release APKs in repository history are signed by this
certificate. The checked-in extensionless keystore could not be opened with
credentials currently present in ignored local configuration, so its
certificate is still unknown. A different PKCS#12 file hash does not prove
that it contains a different private key.

## Immediate containment

1. Stop distributing artifacts built from an untrusted workstation.
2. Preserve an access-restricted, offline incident copy of each exposed
   container and record SHA-256 file hashes. Do not put backups in the
   repository or a synchronized/shared folder.
3. Remove the files from the current working tree, keep signing paths external,
   and ensure `.gitignore` and package exclusions cover PKCS#12, PFX, JKS, and
   Android keystores.
4. Inventory deployments, CI variables, release hosts, APK stores, and any TLS
   clients that pin the server certificate.
5. Rotate passwords as defense in depth, but do not mistake password rotation
   for private-key rotation: anyone who obtained the committed container and
   its password may retain the private key.

## Rotation decisions

### Server key

Rotate the server private key and certificate if the JVM keystore was ever
deployed, copied to a server, used for client TLS, or pinned by a client. For a
self-signed internal certificate, distribute the replacement trust anchor
before cutover. If production is conclusively Nginx-only and this key was
local-development-only, remove it and document that evidence; rotation is
still the conservative response.

### Android key

First obtain the certificate fingerprint of the checked-in keystore from a
known credential owner or signing service. Compare it with the active
fingerprint and every distribution channel:

- If it differs and no released APK/AAB uses it, do not rotate the active app
  key because of this file; retire the exposed unused key.
- If it matches an app-signing key, consider it compromised. For Google Play,
  use Play App Signing's supported app-signing key upgrade/reset procedure and
  verify the upgrade path for each Android version.
- For direct/sideloaded APK updates, changing the signer normally prevents an
  in-place update. Plan a migration app, uninstall/reinstall, managed-device
  rollout, or keep the existing signer only after an explicit risk decision.
- Do not publish a newly signed build until package name, signing lineage,
  installed-base continuity, and store ownership are verified.

## History purge procedure — approval required

Do not run this section until key rotation/containment is complete, repository
owners approve downtime, branch protection is prepared, and collaborators are
not pushing.

Run from a clean directory outside every working clone:

```powershell
gh auth status
git filter-repo --version
git clone --mirror https://github.com/HlaingMyanmar/POS.git POS-keystore-purge.git
Set-Location POS-keystore-purge.git
git bundle create ..\POS-before-keystore-purge.bundle --all
git filter-repo --force --invert-paths `
  --path src/main/resources/keystore.p12 `
  --path technician-app/app/keystore
git remote add origin https://github.com/HlaingMyanmar/POS.git
git rev-list --objects --all |
  Select-String -SimpleMatch 'src/main/resources/keystore.p12','technician-app/app/keystore'
git log --all --full-history -- `
  src/main/resources/keystore.p12 technician-app/app/keystore
git fsck --full --no-reflogs
git push --force --all origin
git push --force --tags origin
```

Both verification commands that search for the paths must return no matches
before pushing. Keep the bundle offline and access-restricted; it still
contains the exposed private material.

GitHub rulesets and protected branches may reject force-pushes. An owner must
temporarily allow force-pushes or grant a narrowly scoped ruleset bypass, then
restore protection immediately. Open pull requests, commit links, release
tags, caches, forks, and GitHub's retained objects may still reference old
objects; close/recreate affected pull requests and contact GitHub Support if
server-side purge guarantees are required.

## Collaborator recovery

The safest recovery is to archive uncommitted work as patches, delete the old
clone, and clone again. Existing clones retain the secret in object storage.
Do not merge or push pre-rewrite branches.

If recloning is impossible, first back up uncommitted work outside the clone,
then fetch rewritten refs, hard-reset each local branch to its corresponding
remote branch, delete and refetch tags, expire reflogs, prune unreachable
objects, and verify the two paths are absent from `git rev-list --objects
--all`. Coordinate exact branch names with the repository owner; these cleanup
steps are destructive to uncommitted and unpublished work.

## Post-incident verification

- Scan every branch and tag for the two paths and for private-key file types.
- Build the WAR and list its entries; no key container or local secrets file
  may be present.
- Verify each release APK/AAB signer with `apksigner`.
- Test update installation from the last production release before publishing.
- Restore branch protection and CI, then record rotation dates, certificate
  fingerprints, owners, and evidence without passwords or private material.
