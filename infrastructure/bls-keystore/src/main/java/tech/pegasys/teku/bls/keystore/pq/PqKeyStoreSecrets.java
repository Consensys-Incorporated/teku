/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.bls.keystore.pq;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;

/**
 * The secrets in a PQ validator keystore: the leanXMSS seed, which is the XMSS private key, and the
 * VRF seed that the validator's VRF tree is derived from. The two are independent.
 */
public record PqKeyStoreSecrets(Bytes32 xmssSeed, Bytes32 vrfSeed) {

  private static final Bytes XMSS_SEED_DOMAIN =
      Bytes.wrap("leanxmss-seed".getBytes(StandardCharsets.US_ASCII));
  private static final Bytes VRF_SEED_DOMAIN =
      Bytes.wrap("vrf-seed".getBytes(StandardCharsets.US_ASCII));

  /**
   * Derives a validator's PQ seeds from its BLS secret key, as the PQ devnet genesis tool
   * (ethpandaops/eth-beacon-genesis, pq branch) does: {@code sha256("leanxmss-seed" ‖ sk)} and
   * {@code sha256("vrf-seed" ‖ sk)}, where {@code sk} is the 32-byte big-endian BLS secret key.
   * This is a devnet convention, not part of the spec, and the PQ keys are only as secret as the
   * BLS key.
   */
  public static PqKeyStoreSecrets deriveFromBlsSecretKey(final Bytes32 blsSecretKey) {
    return new PqKeyStoreSecrets(
        Hash.sha256(XMSS_SEED_DOMAIN, blsSecretKey), Hash.sha256(VRF_SEED_DOMAIN, blsSecretKey));
  }

  /** The encrypted form: {@code xmss_seed ‖ vrf_seed}. */
  public Bytes toBytes() {
    return Bytes.concatenate(xmssSeed, vrfSeed);
  }

  public static PqKeyStoreSecrets fromBytes(final Bytes bytes) {
    checkArgument(
        bytes.size() == PqKeyStoreData.SECRET_SIZE,
        "Expected %s bytes of PQ keystore secrets but got %s",
        PqKeyStoreData.SECRET_SIZE,
        bytes.size());
    return new PqKeyStoreSecrets(
        Bytes32.wrap(bytes.slice(0, PqKeyStoreData.SEED_SIZE).toArray()),
        Bytes32.wrap(bytes.slice(PqKeyStoreData.SEED_SIZE).toArray()));
  }

  @Override
  public String toString() {
    return "PqKeyStoreSecrets{redacted}";
  }
}
