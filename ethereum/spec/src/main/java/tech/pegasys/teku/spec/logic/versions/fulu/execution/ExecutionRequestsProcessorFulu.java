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

package tech.pegasys.teku.spec.logic.versions.fulu.execution;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.electra.execution.ExecutionRequestsProcessorElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class ExecutionRequestsProcessorFulu extends ExecutionRequestsProcessorElectra {

  public ExecutionRequestsProcessorFulu(
      final SchemaDefinitionsFulu schemaDefinitions,
      final MiscHelpersFulu miscHelpers,
      final SpecConfigFulu specConfig,
      final PredicatesElectra predicates,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateMutatorsElectra beaconStateMutators,
      final BeaconStateAccessorsFulu beaconStateAccessors) {
    super(
        schemaDefinitions,
        miscHelpers,
        specConfig,
        predicates,
        validatorsUtil,
        beaconStateMutators,
        beaconStateAccessors);
  }

  /*
   The function `process_deposit_request` is modified to remove support for the former deposit mechanism.
  */
  @Override
  public void processDepositRequests(
      final MutableBeaconState state, final List<DepositRequest> depositRequests) {
    final MutableBeaconStateElectra stateElectra = MutableBeaconStateElectra.required(state);
    final SszMutableList<PendingDeposit> pendingDeposits = stateElectra.getPendingDeposits();
    final PendingDeposit.PendingDepositSchema pendingDepositSchema =
        schemaDefinitions.getPendingDepositSchema();
    for (DepositRequest depositRequest : depositRequests) {
      pendingDeposits.append(
          pendingDepositSchema.create(
              new SszPublicKey(depositRequest.getPubkey()),
              SszBytes32.of(depositRequest.getWithdrawalCredentials()),
              SszUInt64.of(depositRequest.getAmount()),
              new SszSignature(depositRequest.getSignature()),
              SszUInt64.of(state.getSlot())));
    }
  }
}
