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

package tech.pegasys.teku.statetransition.validatorcache;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public interface ActiveValidatorChannel extends ChannelInterface {
  /**
   * Called when a block is received, this will add the block producer to the list of seen
   * validators for the epoch the block was produced in
   *
   * @param block the SignedBeaconBlock that was produced
   */
  void onBlockImported(SignedBeaconBlock block);

  /**
   * Called when an attestation or aggregate is received, this will add any validators in the
   * attestation or aggregate to the seen validators for the epoch that the attestation was produced
   * for
   *
   * @param attestation the attestation that was produced
   */
  void onAttestation(ValidatableAttestation attestation);

  /**
   * Query the validator liveness cache to check if validators were seen in a recent epoch If N is
   * current epoch, can detect (N, N-1, N-2)
   *
   * @param validators the validator indices
   * @param epoch an epoch within current >= epoch >= current-2
   */
  SafeFuture<Object2BooleanMap<UInt64>> validatorsLiveAtEpoch(
      List<UInt64> validators, UInt64 epoch);
}
