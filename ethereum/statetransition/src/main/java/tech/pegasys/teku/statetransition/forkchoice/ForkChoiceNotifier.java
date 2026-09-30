/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.statetransition.forkchoice;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;

public interface ForkChoiceNotifier {

  void onForkChoiceUpdated(
      ForkChoiceState forkChoiceState, Optional<UInt64> requestedBlockProductionSlot);

  void onAttestationsDue(UInt64 slot);

  void onPayloadAttestationsDue(UInt64 slot);

  void onSyncingStatusChanged(boolean inSync);

  SafeFuture<Optional<ExecutionPayloadContext>> getPayloadId(
      ForkChoiceNode parentBeaconBlock, UInt64 blockSlot);

  SafeFuture<Optional<ExecutionPayloadContext>> preparePayloadAttributes(
      ForkChoiceNode parentBeaconBlock, UInt64 blockSlot);

  void onTerminalBlockReached(Bytes32 executionBlockHash);

  void subscribeToForkChoiceUpdatedResult(ForkChoiceUpdatedResultSubscriber subscriber);
}
