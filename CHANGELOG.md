# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes
 - Teku no longer publishes the `-jdk26` Docker images, because Java 26 is end of life and no longer receives security fixes. Use the new `-jdk27` tags instead, or the default `-jdk25` ones.

### Additions and Improvements
 - Applying a batch of changes to an SSZ tree no longer wraps and boxes the list of changes at every tree level, which lowers the time of the epoch transition. [#11634](https://github.com/Consensys-Incorporated/teku/pull/11634)
 - Reduced the time of the epoch transition by about a third on mainnet: changes to SSZ lists are now tracked in a paged array instead of a sorted map, so reading and writing balances during epoch processing takes constant time. [#11633](https://github.com/Consensys-Incorporated/teku/pull/11633)
 - `Validator` views of the registry are now read straight from its packed SSZ bytes and are no longer cached. On mainnet this lowers the live heap of a running node by about 2 GB (about 7.2 million cached `Validator` views are no longer kept).

### Bug Fixes
 - Reduced retained memory by allowing the first beacon state in a sequence of derived states to be garbage collected while later states remain in use.
 - Importing slashing protection data (`teku slashing-protection import` or the key manager API) now rejects a validator whose history contains a block slot or attestation epoch of 18446744073709551615 (the largest unsigned 64-bit value), reporting an error for that validator instead of silently dropping the value. Teku's slashing protection records cannot store this value, so dropping it left the validator unprotected against signing a slashable attestation. [#11643](https://github.com/Consensys-Incorporated/teku/pull/11643)
 - Importing slashing protection data now rejects a validator with a signed attestation whose source epoch is after its target epoch. Importing such data could stop the validator from ever attesting again. [#11606](https://github.com/Consensys-Incorporated/teku/issues/11606)
 - Importing slashing protection data without a genesis validators root no longer removes the root from existing records, which disabled the check that stops a validator signing on the wrong chain.
 - `teku slashing-protection import` now exits with a non-zero status when any validator fails to import, and lists each failed validator with the reason.
 - The validator client now refuses to sign attestation data from the beacon node whose target epoch is not the epoch of the attestation slot, or whose source epoch is after its target epoch. Such data is invalid under the consensus spec, and signing a far-future target would have raised the slashing protection record and stopped the validator from attesting until that epoch.
 - Secrets Teku generates or imports are now written readable only by their owner (`rw-------`) instead of inheriting the umask, which usually made them readable by every local user: the Engine API JWT secret generated when `--ee-jwt-secret-file` is not set, the generated p2p private key, the validator API bearer token, and keystore passwords imported through the keymanager API. The JWT secret is regenerated on every start, so it is fixed by restarting. An execution client running as a different user can no longer read the generated JWT secret; instead, create a secret file both can read and pass it to Teku with `--ee-jwt-secret-file` and to the execution client with its own JWT secret option. Existing p2p key and bearer token files keep their permissions and should be restricted manually (e.g. `chmod 600`). Teku now also logs a warning when the file given with `--ee-jwt-secret-file` is readable by all local users. [#11617](https://github.com/Consensys-Incorporated/teku/pull/11617)
 - With `--validator-is-local-slashing-protection-synchronized-enabled=false`, a validator whose slashing protection file cannot be read now refuses to sign, as it does in the default mode, instead of signing as if it had never signed before and overwriting the file. The file is read again on the next signing attempt. [#11605](https://github.com/Consensys-Incorporated/teku/issues/11605)
 - Peer eviction no longer counts peers that are already being disconnected, which could evict one more peer than needed. [#11425](https://github.com/Consensys-Incorporated/teku/issues/11425)
