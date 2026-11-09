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

package tech.pegasys.teku.beaconrestapi.schema;

import static tech.pegasys.teku.ethereum.execution.types.Eth1Address.ETH1ADDRESS_TYPE;
import static tech.pegasys.teku.ethereum.json.types.SharedApiTypes.PUBKEY_API_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.forkchoice.PreparedProposerInfo;
import tech.pegasys.teku.statetransition.forkchoice.RegisteredValidatorInfo;

public class ProposersData {
  private final Map<UInt64, PreparedProposerInfo> preparedProposers;
  private final Map<UInt64, RegisteredValidatorInfo> registeredValidators;

  public ProposersData(
      final Map<UInt64, PreparedProposerInfo> preparedProposers,
      final Map<UInt64, RegisteredValidatorInfo> registeredValidators) {
    this.preparedProposers = preparedProposers;
    this.registeredValidators = registeredValidators;
  }

  private List<Map.Entry<UInt64, PreparedProposerInfo>> getPreparedProposers() {
    return new ArrayList<>(preparedProposers.entrySet());
  }

  private List<Map.Entry<UInt64, RegisteredValidatorInfo>> getRegisteredValidators() {
    return new ArrayList<>(registeredValidators.entrySet());
  }

  public static SerializableTypeDefinition<ProposersData> getJsonTypeDefinition() {
    final SerializableTypeDefinition<Map.Entry<UInt64, PreparedProposerInfo>> proposersType =
        SerializableTypeDefinition.<Map.Entry<UInt64, PreparedProposerInfo>>object()
            .name("PreparedProposersType")
            .withField("proposer_index", UINT64_TYPE, Map.Entry::getKey)
            .withField(
                "fee_recipient", ETH1ADDRESS_TYPE, entry -> entry.getValue().getFeeRecipient())
            .withField("expiry_slot", UINT64_TYPE, entry -> entry.getValue().getExpirySlot())
            .build();

    final SerializableTypeDefinition<Map.Entry<UInt64, RegisteredValidatorInfo>> validatorsType =
        SerializableTypeDefinition.<Map.Entry<UInt64, RegisteredValidatorInfo>>object()
            .name("RegisteredValidatorsType")
            .withField("proposer_index", UINT64_TYPE, Map.Entry::getKey)
            .withField(
                "pubkey",
                PUBKEY_API_TYPE,
                entry ->
                    entry.getValue().getSignedValidatorRegistration().getMessage().getPublicKey())
            .withField(
                "fee_recipient",
                ETH1ADDRESS_TYPE,
                entry ->
                    entry
                        .getValue()
                        .getSignedValidatorRegistration()
                        .getMessage()
                        .getFeeRecipient())
            .withField(
                "gas_limit",
                UINT64_TYPE,
                entry ->
                    entry.getValue().getSignedValidatorRegistration().getMessage().getGasLimit())
            .withField(
                "timestamp",
                UINT64_TYPE,
                entry ->
                    entry.getValue().getSignedValidatorRegistration().getMessage().getTimestamp())
            .withField("expiry_slot", UINT64_TYPE, entry -> entry.getValue().getExpirySlot())
            .build();

    return SerializableTypeDefinition.object(ProposersData.class)
        .name("ProposersData")
        .withField("prepared_proposers", listOf(proposersType), ProposersData::getPreparedProposers)
        .withField(
            "registered_validators", listOf(validatorsType), ProposersData::getRegisteredValidators)
        .build();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProposersData that = (ProposersData) o;
    return Objects.equals(preparedProposers, that.preparedProposers)
        && Objects.equals(registeredValidators, that.registeredValidators);
  }

  @Override
  public int hashCode() {
    return Objects.hash(preparedProposers, registeredValidators);
  }

  @Override
  public String toString() {
    return "ProposersData{"
        + "preparedProposers="
        + preparedProposers
        + ", registeredValidators="
        + registeredValidators
        + '}';
  }
}
