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

package tech.pegasys.teku.storage.store;

import java.util.Optional;
import tech.pegasys.teku.dataproviders.generators.StateGenerationTask;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public abstract class EmptyStoreResults {

  public static final SafeFuture<Optional<SignedBeaconBlock>> EMPTY_SIGNED_BLOCK_FUTURE =
      SafeFuture.completedFuture(Optional.empty());

  public static final SafeFuture<Optional<BeaconBlock>> EMPTY_BLOCK_FUTURE =
      SafeFuture.completedFuture(Optional.empty());

  public static final SafeFuture<Optional<BeaconState>> EMPTY_STATE_FUTURE =
      SafeFuture.completedFuture(Optional.empty());

  public static final SafeFuture<Optional<SignedBlockAndState>>
      EMPTY_SIGNED_BLOCK_AND_STATE_FUTURE = SafeFuture.completedFuture(Optional.empty());

  public static final SafeFuture<Optional<StateAndBlockSummary>>
      EMPTY_STATE_AND_BLOCK_SUMMARY_FUTURE = SafeFuture.completedFuture(Optional.empty());

  public static final SafeFuture<Optional<StateGenerationTask>> EMPTY_STATE_GENERATION_TASK =
      SafeFuture.completedFuture(Optional.empty());

  public static final SafeFuture<Optional<UInt64>> NO_EARLIEST_BLOB_SIDECAR_SLOT_FUTURE =
      SafeFuture.completedFuture(Optional.empty());

  public static final SafeFuture<Optional<SignedExecutionPayloadEnvelope>>
      EMPTY_SIGNED_EXECUTION_PAYLOAD_ENVELOPE_FUTURE = SafeFuture.completedFuture(Optional.empty());

  public static final SafeFuture<Optional<SignedBlindedExecutionPayloadEnvelope>>
      EMPTY_SIGNED_BLINDED_EXECUTION_PAYLOAD_ENVELOPE_FUTURE =
          SafeFuture.completedFuture(Optional.empty());
}
