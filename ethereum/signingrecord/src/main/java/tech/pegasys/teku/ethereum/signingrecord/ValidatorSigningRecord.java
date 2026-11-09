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

package tech.pegasys.teku.ethereum.signingrecord;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Holds the key information required to prevent a validator from being slashed.
 *
 * <p>For blocks, the slot of the last signed block is recorded and signing is only allowed if the
 * slot is greater than the previous.
 *
 * <p>For attestations, the last source epoch and target epoch are recorded. An attestation may only
 * be signed if source >= previousSource AND target > previousTarget
 */
public record ValidatorSigningRecord(
    Optional<Bytes32> genesisValidatorsRoot,
    UInt64 blockSlot,
    UInt64 attestationSourceEpoch,
    UInt64 attestationTargetEpoch) {
  private static final Logger LOG = LogManager.getLogger();

  public static final UInt64 NEVER_SIGNED = null;

  public static ValidatorSigningRecord emptySigningRecord(final Bytes32 genesisValidatorsRoot) {
    return new ValidatorSigningRecord(
        Optional.ofNullable(genesisValidatorsRoot), UInt64.ZERO, NEVER_SIGNED, NEVER_SIGNED);
  }

  public static boolean isNeverSigned(final UInt64 value) {
    return Objects.equals(value, NEVER_SIGNED);
  }

  public static ValidatorSigningRecord fromBytes(final Bytes data) {
    return ValidatorSigningRecordSerialization.readRecord(data);
  }

  public Bytes toBytes() {
    return ValidatorSigningRecordSerialization.writeRecord(this);
  }

  /**
   * Determine if it is safe to sign a block at the specified slot.
   *
   * @param genesisValidatorsRoot the genesis validators root for the chain being signed
   * @param slot the slot of the block being signed
   * @return an Optional containing an updated {@link ValidatorSigningRecord} with the state after
   *     the block is signed or empty if it is not safe to sign the block.
   */
  public Optional<ValidatorSigningRecord> maySignBlock(
      final Bytes32 genesisValidatorsRoot, final UInt64 slot) {
    if (this.genesisValidatorsRoot.isPresent()
        && !this.genesisValidatorsRoot.get().equals(genesisValidatorsRoot)) {
      LOG.error(
          "Refusing to sign block because validator signing record is from the wrong chain. Expected genesis validators root {} but attempting to sign for {}",
          this.genesisValidatorsRoot.get(),
          genesisValidatorsRoot);
      return Optional.empty();
    }
    // We never allow signing a block at slot 0 because we shouldn't be signing the genesis block.
    if (blockSlot.compareTo(slot) < 0) {
      return Optional.of(
          new ValidatorSigningRecord(
              Optional.ofNullable(genesisValidatorsRoot),
              slot,
              attestationSourceEpoch,
              attestationTargetEpoch));
    }
    return Optional.empty();
  }

  /**
   * Determine if it is safe to sign an attestation with the specified source and target epochs.
   *
   * @param genesisValidatorsRoot the genesis validators root for the chain being signed
   * @param sourceEpoch the source epoch of the attestation to sign
   * @param targetEpoch the target epoch of the attestation to sign
   * @return an Optional containing an updated {@link ValidatorSigningRecord} with the state after
   *     the attestation is signed or empty if it is not safe to sign the attestation.
   */
  public Optional<ValidatorSigningRecord> maySignAttestation(
      final Bytes32 genesisValidatorsRoot, final UInt64 sourceEpoch, final UInt64 targetEpoch) {
    if (this.genesisValidatorsRoot.isPresent()
        && !this.genesisValidatorsRoot.get().equals(genesisValidatorsRoot)) {
      LOG.error(
          "Refusing to sign attestation because validator signing record is from the wrong chain. "
              + "Expected genesis validators root {}  but attempting to sign for {}",
          this.genesisValidatorsRoot.get(),
          genesisValidatorsRoot);
      return Optional.empty();
    }

    boolean sourceEpochIsSafe = isSafeSourceEpoch(sourceEpoch);
    boolean targetEpochIsSafe = isSafeTargetEpoch(targetEpoch);
    if (sourceEpochIsSafe && targetEpochIsSafe) {
      return Optional.of(
          new ValidatorSigningRecord(
              Optional.ofNullable(genesisValidatorsRoot), blockSlot, sourceEpoch, targetEpoch));
    } else {
      LOG.error(
          "Refusing to sign attestation:  source epoch ({}) is {}, target epoch ({}) is {}.  "
              + "Previously signed source epoch {}, target epoch {}",
          sourceEpoch,
          sourceEpochIsSafe ? "safe" : "unsafe",
          targetEpoch,
          targetEpochIsSafe ? "safe" : "unsafe",
          attestationSourceEpoch,
          attestationTargetEpoch);
    }
    return Optional.empty();
  }

  private boolean isSafeSourceEpoch(final UInt64 sourceEpoch) {
    return isNeverSigned(attestationSourceEpoch)
        || attestationSourceEpoch.isLessThanOrEqualTo(sourceEpoch);
  }

  private boolean isSafeTargetEpoch(final UInt64 targetEpoch) {
    return isNeverSigned(attestationTargetEpoch) || attestationTargetEpoch.isLessThan(targetEpoch);
  }

  @Override
  public String toString() {
    final String genesisValidatorsRootString =
        genesisValidatorsRoot.isPresent() ? genesisValidatorsRoot.get().toString() : "EMPTY";
    return MoreObjects.toStringHelper(this)
        .add("genesisValidatorsRoot", genesisValidatorsRootString)
        .add("blockSlot", blockSlot)
        .add("attestationSourceEpoch", attestationSourceEpoch)
        .add("attestationTargetEpoch", attestationTargetEpoch)
        .toString();
  }

  public boolean genesisValidatorsRootIsEqualTo(
      final Optional<Bytes32> maybeGenesisValidatorsRoot) {
    if (maybeGenesisValidatorsRoot.isPresent() && genesisValidatorsRoot.isPresent()) {
      return maybeGenesisValidatorsRoot.get().equals(genesisValidatorsRoot.get());
    } else if (maybeGenesisValidatorsRoot.isEmpty() && genesisValidatorsRoot.isEmpty()) {
      return true;
    }
    return false;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ValidatorSigningRecord that = (ValidatorSigningRecord) o;
    return genesisValidatorsRootIsEqualTo(that.genesisValidatorsRoot)
        && Objects.equals(blockSlot, that.blockSlot)
        && Objects.equals(attestationSourceEpoch, that.attestationSourceEpoch)
        && Objects.equals(attestationTargetEpoch, that.attestationTargetEpoch);
  }
}
