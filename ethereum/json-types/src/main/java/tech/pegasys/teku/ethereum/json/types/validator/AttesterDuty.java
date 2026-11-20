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

package tech.pegasys.teku.ethereum.json.types.validator;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * An attester duty. From PQ the validator is named by its 32-byte XMSS public key, and the duty
 * says whether the validator is in its committee's aggregation committee, as there are no selection
 * proofs.
 */
public class AttesterDuty {

  private final Bytes publicKey;
  private final int validatorIndex;
  private final int committeeLength;

  /** the committee index of the committee that includes the specified validator */
  private final int committeeIndex;

  private final int committeesAtSlot;
  // index of the validator in the committee
  private final int validatorCommitteeIndex;
  private final UInt64 slot;
  private final Optional<Boolean> isAggregator;

  public AttesterDuty(
      final BLSPublicKey publicKey,
      final int validatorIndex,
      final int committeeLength,
      final int committeeIndex,
      final int committeesAtSlot,
      final int validatorCommitteeIndex,
      final UInt64 slot) {
    this(
        publicKey.toBytesCompressed(),
        validatorIndex,
        committeeLength,
        committeeIndex,
        committeesAtSlot,
        validatorCommitteeIndex,
        slot,
        Optional.empty());
  }

  /**
   * @param publicKey the validator's signing key: a 48-byte BLS key, or from PQ its 32-byte XMSS
   *     key
   * @param isAggregator from PQ, whether the validator is in the aggregation committee
   */
  public AttesterDuty(
      final Bytes publicKey,
      final int validatorIndex,
      final int committeeLength,
      final int committeeIndex,
      final int committeesAtSlot,
      final int validatorCommitteeIndex,
      final UInt64 slot,
      final Optional<Boolean> isAggregator) {
    this.publicKey = publicKey;
    this.validatorIndex = validatorIndex;
    this.committeeLength = committeeLength;
    this.committeeIndex = committeeIndex;
    this.committeesAtSlot = committeesAtSlot;
    this.validatorCommitteeIndex = validatorCommitteeIndex;
    this.slot = slot;
    this.isAggregator = isAggregator;
  }

  /**
   * @throws IllegalArgumentException for a PQ validator, which is named by its XMSS key
   */
  public BLSPublicKey getPublicKey() {
    return BLSPublicKey.fromBytesCompressed(Bytes48.wrap(publicKey));
  }

  /** The validator's signing key: a BLS key, or from PQ an XMSS key. */
  public Bytes getPublicKeyBytes() {
    return publicKey;
  }

  /** From PQ, whether the validator aggregates for its committee. */
  public Optional<Boolean> getIsAggregator() {
    return isAggregator;
  }

  public int getValidatorIndex() {
    return validatorIndex;
  }

  public int getCommitteeLength() {
    return committeeLength;
  }

  public int getCommitteeIndex() {
    return committeeIndex;
  }

  public int getCommitteesAtSlot() {
    return committeesAtSlot;
  }

  public int getValidatorCommitteeIndex() {
    return validatorCommitteeIndex;
  }

  public UInt64 getSlot() {
    return slot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AttesterDuty that = (AttesterDuty) o;
    return validatorIndex == that.validatorIndex
        && committeeLength == that.committeeLength
        && committeeIndex == that.committeeIndex
        && committeesAtSlot == that.committeesAtSlot
        && validatorCommitteeIndex == that.validatorCommitteeIndex
        && Objects.equals(publicKey, that.publicKey)
        && Objects.equals(slot, that.slot)
        && Objects.equals(isAggregator, that.isAggregator);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        publicKey,
        validatorIndex,
        committeeLength,
        committeeIndex,
        committeesAtSlot,
        validatorCommitteeIndex,
        slot,
        isAggregator);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("publicKey", publicKey)
        .add("validatorIndex", validatorIndex)
        .add("committeeLength", committeeLength)
        .add("committeeIndex", committeeIndex)
        .add("committeesAtSlot", committeesAtSlot)
        .add("validatorCommitteeIndex", validatorCommitteeIndex)
        .add("slot", slot)
        .add("isAggregator", isAggregator)
        .toString();
  }
}
