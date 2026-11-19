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

import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_DEPOSIT_PREFIX;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.MutableBeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPreregistration;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

public class BeaconStateMutatorsPq extends BeaconStateMutatorsGloas {

  private static final Bytes EMPTY_DEPOSIT_SIGNATURE = Bytes.wrap(new byte[96]);

  private final SpecConfigPq specConfigPq;
  private final SchemaDefinitionsPq schemaDefinitionsPq;
  private final BeaconStateAccessorsPq beaconStateAccessorsPq;

  public BeaconStateMutatorsPq(
      final SpecConfigPq specConfig,
      final MiscHelpersPq miscHelpers,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final SchemaDefinitionsPq schemaDefinitions) {
    super(specConfig, miscHelpers, beaconStateAccessors, schemaDefinitions);
    this.specConfigPq = specConfig;
    this.schemaDefinitionsPq = schemaDefinitions;
    this.beaconStateAccessorsPq = beaconStateAccessors;
  }

  /**
   * queue_excess_active_balance, modified in PQ: the pending deposit names {@code 0^16 ‖
   * xmss_pubkey} and has an empty signature.
   */
  @Override
  public void queueExcessActiveBalance(
      final MutableBeaconStateElectra state, final int validatorIndex) {
    final UInt64 balance = state.getBalances().get(validatorIndex).get();
    final UInt64 minActivationBalance = specConfigPq.getMinActivationBalance();
    if (!balance.isGreaterThan(minActivationBalance)) {
      return;
    }
    final UInt64 excessBalance = balance.minus(minActivationBalance);
    state.getBalances().set(validatorIndex, SszUInt64.of(minActivationBalance));
    final ValidatorPq validator = (ValidatorPq) state.getValidators().get(validatorIndex);
    final PendingDeposit deposit =
        schemaDefinitionsPq
            .getPendingDepositSchema()
            .create(
                new SszPublicKey(
                    Bytes48.wrap(
                        Bytes.concatenate(XMSS_DEPOSIT_PREFIX, validator.getXmssPublicKey()))),
                SszBytes32.of(validator.getWithdrawalCredentials()),
                SszUInt64.of(excessBalance),
                new SszSignature(EMPTY_DEPOSIT_SIGNATURE),
                SszUInt64.of(SpecConfig.GENESIS_SLOT));
    state.getPendingDeposits().append(deposit);
  }

  /** add_builder_to_registry, modified in PQ: the builder is keyed by its XMSS public key. */
  public void addBuilderToRegistry(
      final MutableBeaconState state,
      final Bytes32 pubkey,
      final int version,
      final Eth1Address executionAddress,
      final UInt64 amount,
      final UInt64 slot) {
    final int index = beaconStateAccessorsPq.getIndexForNewBuilder(state).intValue();
    final BuilderPq builder =
        new BuilderPq(
            pubkey,
            version,
            executionAddress,
            amount,
            miscHelpers.computeEpochAtSlot(slot),
            SpecConfig.FAR_FUTURE_EPOCH);
    final SszMutableList<Builder> builders = MutableBeaconStateGloas.required(state).getBuilders();
    if (index >= builders.size()) {
      builders.append(builder);
    } else {
      builders.set(index, builder);
    }
  }

  /** remove_validator_preregistration: keeps the order of the remaining records. */
  public void removeValidatorPreregistration(
      final MutableBeaconStatePq state, final Bytes32 pubkey) {
    final SszList<ValidatorPreregistration> preregistrations = state.getValidatorPreregistrations();
    state.setValidatorPreregistrations(
        preregistrations
            .getSchema()
            .createFromElements(
                preregistrations.stream()
                    .filter(preregistration -> !preregistration.getXmssPublicKey().equals(pubkey))
                    .toList()));
  }

  /** remove_builder_preregistration: keeps the order of the remaining records. */
  public void removeBuilderPreregistration(final MutableBeaconStatePq state, final Bytes32 pubkey) {
    final SszList<BuilderPreregistration> preregistrations = state.getBuilderPreregistrations();
    state.setBuilderPreregistrations(
        preregistrations
            .getSchema()
            .createFromElements(
                preregistrations.stream()
                    .filter(preregistration -> !preregistration.getXmssPublicKey().equals(pubkey))
                    .toList()));
  }
}
