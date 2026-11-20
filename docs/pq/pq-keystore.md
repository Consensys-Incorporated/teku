# PQ validator keys: keystore format, generation and loading

Status: Teku-specific, version 1. No cross-client standard exists yet (see `pq-fork-plan.md` §5.7 and §5.8). Use it on devnets only.

A PQ validator has two independent 32-byte secrets (consensus-specs `pq` validator.md, "Initialization"):

- the **XMSS seed**, which is the leanXMSS private key; the 32-byte **XMSS public key** is derived from it;
- the **VRF seed**, from which the validator's VRF tree is derived; the tree's root is the **VRF root**.

The validator registers both public values. EIP-8483 `pq_data` is `xmss_pubkey ‖ vrf_root` (64 bytes). A post-fork deposit has `pubkey = XMSS_DEPOSIT_PREFIX ‖ xmss_pubkey` (16 zero bytes, then the key) and `signature = vrf_root ‖ 64 zero bytes`.

## Files

Each validator has three files:

| File | Contents | Where |
|---|---|---|
| `<name>.pq-keystore` | The encrypted seeds and their public values (JSON, below) | Next to the validator's BLS keystore `<name>.json` |
| `<name>.vrf-cache` | Saved VRF tree nodes (binary, below) | Next to the BLS keystore |
| `<xmss pubkey hex>.xmss-leaves` | The leaf record: which leaves have been signed, and with what | The slashing-protection directory (`LocalXmssLeafProtector`, phase 9b) |

Neither PQ file name ends in `.json`, so `--validator-keys` directory scans do not mistake them for BLS keystores. The PQ keystore is encrypted with **the same password as its BLS keystore**, so no extra password file is needed.

Keys generated for new validators without a BLS key are named `pq_<xmss pubkey hex>`. The validator client cannot load them yet: that needs index-first validator identity (phase 9a).

## Keystore (`.pq-keystore`), version 1

```json
{
  "format": "teku-pq-validator-keystore",
  "version": 1,
  "xmss_pubkey": "<32 bytes, hex, no 0x>",
  "vrf_root": "<32 bytes, hex, no 0x>",
  "bls_pubkey": "<48 bytes, hex, no 0x; optional>",
  "crypto": { "kdf": {...}, "checksum": {...}, "cipher": {...} },
  "uuid": "<random UUID>"
}
```

- `crypto` is an EIP-2335 `crypto` object, produced and checked by `infrastructure/bls-keystore` `KeyStore`. Its secret is the **64 bytes `xmss_seed ‖ vrf_seed`**. AES-128-CTR is a stream cipher, so the ciphertext is 64 bytes as well. The generator uses PBKDF2-HMAC-SHA256 with `c = 262144`. Loaders accept any KDF that EIP-2335 allows.
- `bls_pubkey` names the BLS validator these keys belong to. Validators that exist before the PQ fork carry it. The loader refuses a PQ keystore whose `bls_pubkey` differs from its BLS keystore's.
- `format` and `version` identify the format. A loader rejects any other `format`, and any `version` it does not know. A change that older loaders must not read gets a new `version`.
- The public fields are **not authenticated**, as with `pubkey` in EIP-2335. The loader checks each one against the decrypted seeds (below).

Code: `tech.pegasys.teku.bls.keystore.pq.PqKeyStore` and `PqKeyStoreData`.

## VRF cache (`.vrf-cache`)

The cache holds the 2^15 nodes at height 16 of the depth-31 VRF tree, concatenated left to right: 32 bytes each, 1 MiB in all. It is `VrfTree.getCachedNodes()` at `VrfTree.DEFAULT_CACHED_HEIGHT`. It has no header, because it is derived data. With it, a VRF proof costs one 2^16-leaf subtree instead of the whole tree. Deriving it from the seed hashes about 2^32 leaves.

If the cache is missing, the loader rebuilds it from the seed and saves it. That takes minutes, and the loader logs a warning first. A cache of the wrong size is rejected.

## Generation

```
teku internal-tools validator-tools generate-pq-keys \
  --bls-keystores=<KEY_DIR>:<PASS_DIR> \
  --registration-data-file=pq-registrations.json
```

- `--bls-keystores` takes the same `<keys>:<passwords>` pairs as `--validator-keys`. For each BLS keystore it writes `<name>.pq-keystore` and `<name>.vrf-cache` next to it, encrypted with that keystore's password.
- Before generating anything, it checks every password and that no PQ file exists already. It never replaces a PQ keystore, since one that exists may hold seeds that are already registered.
- `--number-of-validators=N --keys-output-path=<DIR> --encrypted-keystore-validator-password-file=<FILE>` instead generates keys for new validators without a BLS key.
- `--registration-data-file` writes, for each validator, `bls_pubkey` (if any), `xmss_pubkey`, `vrf_root`, `pq_data`, `deposit_pubkey`, `deposit_signature` and the keystore path.
- Both seeds come from `SecureRandom`. The command prints progress while deriving each VRF tree. Deriving one tree uses every core: one key took 131 s of wall time and about 22 CPU-minutes on a 16-core Apple Silicon machine (2026-11-20, partly overlapped with other work), so budget minutes per validator.

The command is hidden and marked not for production, like `generate-keys`.

## Loading

When the network schedules the PQ fork, `ValidatorClientService` creates a `LocalXmssLeafProtector` in the slashing-protection directory. `LocalValidatorSource` then looks for a `.pq-keystore` next to each local BLS keystore, for both `--validator-keys` and keymanager-managed keystores. If it finds one, `PqValidatorKeyLoader`:

1. reads the keystore and checks that its `bls_pubkey`, if present, is the BLS keystore's;
2. decrypts it with the BLS keystore's password;
3. reads the VRF cache, recomputes one random cached node from the VRF seed (2^16 hashes), and checks that the cached nodes open to the keystore's `vrf_root`. If the cache is missing, it rebuilds it instead;
4. loads the leanXMSS key, which builds its top tree and takes seconds, and checks that its public key is `xmss_pubkey`;
5. builds a `LocalXmssSigner` from the key, the VRF tree and the shared leaf protector.

The signer is available as `Validator.getXmssSigner()`. Any mismatch fails validator loading with an `InvalidConfigurationException`, just as a bad BLS keystore does.

On networks without a PQ fork, PQ files are ignored and nothing touches the native leanXMSS library.

## Not covered yet

- Checking `vrf_root` and `xmss_pubkey` against the validator's registration in the beacon state (`validator_pq_registrations`, or the PQ `Validator` after the fork). That belongs with the duties work (9c) and the pre-fork registration warning.
- Importing, listing or deleting PQ keys through the keymanager API, and deleting the PQ files when a keymanager-managed BLS keystore is deleted.
- Moving the leaf record with the key (no interchange format).
- External signers.
