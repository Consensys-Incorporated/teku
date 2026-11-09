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

package tech.pegasys.teku.statetransition.attestation.utils;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.PooledAttestationWithRewardInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public interface AggregatingAttestationPoolProfiler {

  AggregatingAttestationPoolProfiler NOOP =
      new AggregatingAttestationPoolProfiler() {
        @Override
        public void execute(
            final Spec spec,
            final UInt64 slot,
            final RecentChainData recentChainData,
            final AggregatingAttestationPool aggregatingAttestationPool) {
          // No-op
        }

        @Override
        public void onPreFillUp(
            final BeaconState stateAtBlockSlot, final PooledAttestationWithRewardInfo attestation) {
          // No-op
        }

        @Override
        public void onPostFillUp(
            final BeaconState stateAtBlockSlot, final PooledAttestationWithRewardInfo attestation) {
          // No-op
        }
      };

  void execute(
      Spec spec,
      UInt64 slot,
      RecentChainData recentChainData,
      AggregatingAttestationPool aggregatingAttestationPool);

  void onPreFillUp(BeaconState stateAtBlockSlot, PooledAttestationWithRewardInfo attestation);

  void onPostFillUp(BeaconState stateAtBlockSlot, PooledAttestationWithRewardInfo attestation);
}
