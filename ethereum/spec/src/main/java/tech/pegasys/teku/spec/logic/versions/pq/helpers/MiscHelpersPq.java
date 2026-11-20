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

package tech.pegasys.teku.spec.logic.versions.pq.helpers;

import static tech.pegasys.teku.spec.constants.PqConstants.LEGACY_VRF_ROOT_DOMAIN;
import static tech.pegasys.teku.spec.constants.PqConstants.LEGACY_XMSS_PUBKEY_DOMAIN;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_ATTESTATION_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_BUILDER_BID_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_DEPOSIT_PREFIX;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_LEAVES_PER_SLOT;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_RESERVED_LEAVES;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

public class MiscHelpersPq extends MiscHelpersGloas {

  private final PredicatesPq predicatesPq;
  private final int attestationSubnetCount;
  private final int attestationSubnetPrefixBits;

  public static MiscHelpersPq required(final MiscHelpers miscHelpers) {
    if (miscHelpers instanceof MiscHelpersPq miscHelpersPq) {
      return miscHelpersPq;
    }
    throw new IllegalArgumentException(
        "Expected PQ misc helpers but got: " + miscHelpers.getClass().getSimpleName());
  }

  public MiscHelpersPq(
      final SpecConfigPq specConfig,
      final PredicatesPq predicates,
      final SchemaDefinitionsPq schemaDefinitions) {
    super(specConfig, predicates, schemaDefinitions);
    this.predicatesPq = predicates;
    this.attestationSubnetCount = specConfig.getAttestationSubnetCountPq();
    this.attestationSubnetPrefixBits =
        SpecConfigBuilder.computeAttestationSubnetPrefixBits(
            attestationSubnetCount,
            specConfig.getNetworkingConfig().getAttestationSubnetExtraBits());
  }

  /** ATTESTATION_SUBNET_COUNT_PQ */
  @Override
  public int getAttestationSubnetCount() {
    return attestationSubnetCount;
  }

  /** compute_attestation_subnet_prefix_bits, modified in PQ to use ATTESTATION_SUBNET_COUNT_PQ. */
  @Override
  protected int getAttestationSubnetPrefixBits() {
    return attestationSubnetPrefixBits;
  }

  /** compute_legacy_xmss_pubkey: the XMSS public key derived from a BLS public key. */
  public Bytes32 computeLegacyXmssPubkey(final Bytes48 pubkey) {
    return Hash.sha256(Bytes.concatenate(pubkey, LEGACY_XMSS_PUBKEY_DOMAIN));
  }

  /** compute_legacy_vrf_root: the VRF root derived from a BLS public key. */
  public Bytes32 computeLegacyVrfRoot(final Bytes48 pubkey) {
    return Hash.sha256(Bytes.concatenate(pubkey, LEGACY_VRF_ROOT_DOMAIN));
  }

  /** compute_deposit_pubkey: the XMSS public key a deposit naming {@code pubkey} is for. */
  public Bytes32 computeDepositPubkey(final Bytes48 pubkey) {
    if (predicatesPq.isLegacyPubkey(pubkey)) {
      return computeLegacyXmssPubkey(pubkey);
    }
    return Bytes32.wrap(pubkey.slice(XMSS_DEPOSIT_PREFIX.size()));
  }

  /** compute_deposit_vrf_root: the VRF root of the validator a deposit creates. */
  public Bytes32 computeDepositVrfRoot(final PendingDeposit deposit) {
    final Bytes48 pubkey = deposit.getPubkeyBytes();
    if (predicatesPq.isLegacyPubkey(pubkey)) {
      return computeLegacyVrfRoot(pubkey);
    }
    return Bytes32.wrap(deposit.getSignatureBytes().slice(0, Bytes32.SIZE));
  }

  /** compute_xmss_attestation_leaf_index: one leaf per target epoch, after the reserved leaves. */
  public UInt64 computeXmssAttestationLeafIndex(final UInt64 epoch) {
    return XMSS_RESERVED_LEAVES.plus(epoch);
  }

  /** compute_xmss_leaf_index: the leaf of a message of the type given by {@code leafOffset}. */
  public UInt64 computeXmssLeafIndex(final UInt64 slot, final UInt64 leafOffset) {
    return XMSS_RESERVED_LEAVES
        .plus(XMSS_ATTESTATION_LEAVES)
        .plus(XMSS_BUILDER_BID_LEAVES)
        .plus(slot.times(XMSS_LEAVES_PER_SLOT))
        .plus(leafOffset);
  }

  /**
   * is_pending_validator, modified in PQ: deposits carry no signature, so any pending deposit for
   * the key counts.
   */
  public boolean isPendingValidator(
      final List<PendingDeposit> pendingDeposits, final Bytes32 xmssPubkey) {
    return pendingDeposits.stream()
        .anyMatch(deposit -> computeDepositPubkey(deposit.getPubkeyBytes()).equals(xmssPubkey));
  }

  /** PQ matches pending deposits by XMSS public key. */
  @Override
  public boolean isPendingValidator(
      final List<PendingDeposit> pendingDeposits, final BLSPublicKey pubkey) {
    throw new UnsupportedOperationException("PQ matches pending deposits by XMSS public key");
  }
}
