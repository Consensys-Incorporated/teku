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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.builder.rest.StakedBuilderClient;
import tech.pegasys.teku.builder.rest.StakedBuilderClientProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBid;

public class BuilderBidFetcherTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final StakedBuilderClientProvider stakedBuilderClientProvider =
      mock(StakedBuilderClientProvider.class);
  private final StakedBuilderClient builderClient = mock(StakedBuilderClient.class);

  private final BuilderBidFetcher fetcher =
      new BuilderBidFetcher(spec, stakedBuilderClientProvider);

  @Test
  void returnsEmptyListWhenNoBuildersDefined() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    final List<RemoteBid> result =
        SafeFutureAssert.safeJoin(
            fetcher.getBuilderBids(
                state,
                state.getSlot(),
                BuilderConfig.NO_OP,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32()));

    assertThat(result).isEmpty();
  }

  @Test
  void includesBidsFromConfiguredBuilders() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final SignedExecutionPayloadBid firstBid = dataStructureUtil.randomSignedExecutionPayloadBid();
    final SignedExecutionPayloadBid secondBid = dataStructureUtil.randomSignedExecutionPayloadBid();
    final BuilderConfig builderConfig = dataStructureUtil.randomBuilderConfig(2);
    when(stakedBuilderClientProvider.getClient(any())).thenReturn(builderClient);
    when(builderClient.getExecutionPayloadBid(any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(firstBid)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(secondBid)));

    final List<RemoteBid> result =
        SafeFutureAssert.safeJoin(
            fetcher.getBuilderBids(
                state,
                state.getSlot(),
                builderConfig,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32()));

    assertThat(result).map(RemoteBid::bid).containsExactly(firstBid, secondBid);
    assertThat(result)
        .map(RemoteBid::builderUrl)
        .containsExactly(
            Optional.of(builderConfig.getBuilders().get(0).getUrl()),
            Optional.of(builderConfig.getBuilders().get(1).getUrl()));
  }

  @Test
  void excludesBuilderBidWhenRequestFails() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final SignedExecutionPayloadBid successfulBid =
        dataStructureUtil.randomSignedExecutionPayloadBid();
    final BuilderConfig builderConfig = dataStructureUtil.randomBuilderConfig(2);

    when(stakedBuilderClientProvider.getClient(any())).thenReturn(builderClient);
    when(builderClient.getExecutionPayloadBid(any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(successfulBid)))
        // second request fails
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("oopsy, builder is bad")));

    final List<RemoteBid> result =
        SafeFutureAssert.safeJoin(
            fetcher.getBuilderBids(
                state,
                state.getSlot(),
                builderConfig,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32()));

    assertThat(result).map(RemoteBid::bid).containsExactly(successfulBid);
  }
}
