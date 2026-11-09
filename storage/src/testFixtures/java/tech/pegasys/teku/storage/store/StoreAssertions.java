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

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import tech.pegasys.teku.infrastructure.ssz.SszData;

public class StoreAssertions {

  public static void assertStoresMatch(
      final UpdatableStore actualState, final UpdatableStore expectedState) {
    assertThat(actualState)
        .usingRecursiveComparison(
            RecursiveComparisonConfiguration.builder()
                // Preventing recursive comparator to fall inside Ssz's with its reflection
                // comparison of all fields including caches etc.
                .withComparatorForType((o1, o2) -> o1.equals(o2) ? 0 : 1, SszData.class)
                .build())
        .ignoringFields(
            "asyncRunner",
            "timeMillis",
            "stateCountGauge",
            "blockCountGauge",
            "executionPayloadCountGauge",
            "checkpointCountGauge",
            "votesLock",
            "readVotesLock",
            "lock",
            "readLock",
            "blockProvider",
            "blobSidecarsProvider",
            "earliestBlobSidecarSlotProvider",
            "blocks",
            "blockMetadata",
            "stateRequestCachedCounter",
            "stateRequestRegenerateCounter",
            "stateRequestMissCounter",
            "checkpointStateRequestCachedCounter",
            "checkpointStateRequestRegenerateCounter",
            "checkpointStateRequestMissCounter",
            "metricsSystem",
            "blockStates",
            "stateProvider",
            "checkpointStates",
            "forkChoiceStrategy",
            "maybeEpochStates",
            "epochStatesCountGauge",
            "blobSidecars",
            "blobSidecarsBlocksCountGauge",
            "executionPayloads",
            "executionPayloadProvider",
            "blindedExecutionPayloadProvider")
        .isEqualTo(expectedState);
    assertThat(actualState.getOrderedBlockRoots())
        .containsExactlyElementsOf(expectedState.getOrderedBlockRoots());
  }
}
