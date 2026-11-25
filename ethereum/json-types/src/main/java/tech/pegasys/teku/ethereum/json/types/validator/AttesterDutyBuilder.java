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

import static tech.pegasys.teku.ethereum.json.types.beacon.ValidatorTypeDefinition.VALIDATOR_PUBKEY_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AttesterDutyBuilder {
  public static final DeserializableTypeDefinition<AttesterDuty> ATTESTER_DUTY_TYPE =
      DeserializableTypeDefinition.object(AttesterDuty.class, AttesterDutyBuilder.class)
          .name("AttesterDuty")
          .initializer(AttesterDutyBuilder::new)
          .finisher(AttesterDutyBuilder::build)
          .withField(
              "pubkey",
              VALIDATOR_PUBKEY_TYPE,
              AttesterDuty::getPublicKeyBytes,
              AttesterDutyBuilder::publicKey)
          .withField(
              "validator_index",
              INTEGER_TYPE,
              AttesterDuty::getValidatorIndex,
              AttesterDutyBuilder::validatorIndex)
          .withField(
              "committee_index",
              INTEGER_TYPE,
              AttesterDuty::getCommitteeIndex,
              AttesterDutyBuilder::committeeIndex)
          .withField(
              "committee_length",
              INTEGER_TYPE,
              AttesterDuty::getCommitteeLength,
              AttesterDutyBuilder::committeeLength)
          .withField(
              "committees_at_slot",
              INTEGER_TYPE,
              AttesterDuty::getCommitteesAtSlot,
              AttesterDutyBuilder::committeesAtSlot)
          .withField(
              "validator_committee_index",
              INTEGER_TYPE,
              AttesterDuty::getValidatorCommitteeIndex,
              AttesterDutyBuilder::validatorCommitteeIndex)
          .withField("slot", UINT64_TYPE, AttesterDuty::getSlot, AttesterDutyBuilder::slot)
          .withOptionalField(
              "is_aggregator",
              BOOLEAN_TYPE,
              AttesterDuty::getIsAggregator,
              AttesterDutyBuilder::isAggregator)
          .withOptionalField(
              "aggregator_position",
              INTEGER_TYPE,
              AttesterDuty::getAggregatorPosition,
              AttesterDutyBuilder::aggregatorPosition)
          .build();

  private Bytes publicKey;
  private int validatorIndex;
  private int committeeLength;
  private int committeeIndex;
  private int committeesAtSlot;
  private int validatorCommitteeIndex;
  private UInt64 slot;

  private Optional<Boolean> isAggregator = Optional.empty();
  private Optional<Integer> aggregatorPosition = Optional.empty();

  public AttesterDutyBuilder publicKey(final Bytes publicKey) {
    this.publicKey = publicKey;
    return this;
  }

  public AttesterDutyBuilder publicKey(final BLSPublicKey publicKey) {
    return publicKey(publicKey.toBytesCompressed());
  }

  public AttesterDutyBuilder isAggregator(final Optional<Boolean> isAggregator) {
    this.isAggregator = isAggregator;
    return this;
  }

  public AttesterDutyBuilder aggregatorPosition(final Optional<Integer> aggregatorPosition) {
    this.aggregatorPosition = aggregatorPosition;
    return this;
  }

  public AttesterDutyBuilder validatorIndex(final int validatorIndex) {
    this.validatorIndex = validatorIndex;
    return this;
  }

  public AttesterDutyBuilder committeeLength(final int committeeLength) {
    this.committeeLength = committeeLength;
    return this;
  }

  public AttesterDutyBuilder committeeIndex(final int committeeIndex) {
    this.committeeIndex = committeeIndex;
    return this;
  }

  public AttesterDutyBuilder committeesAtSlot(final int committeesAtSlot) {
    this.committeesAtSlot = committeesAtSlot;
    return this;
  }

  public AttesterDutyBuilder validatorCommitteeIndex(final int validatorCommitteeIndex) {
    this.validatorCommitteeIndex = validatorCommitteeIndex;
    return this;
  }

  public AttesterDutyBuilder slot(final UInt64 slot) {
    this.slot = slot;
    return this;
  }

  public AttesterDuty build() {
    return new AttesterDuty(
        publicKey,
        validatorIndex,
        committeeLength,
        committeeIndex,
        committeesAtSlot,
        validatorCommitteeIndex,
        slot,
        isAggregator,
        aggregatorPosition);
  }
}
