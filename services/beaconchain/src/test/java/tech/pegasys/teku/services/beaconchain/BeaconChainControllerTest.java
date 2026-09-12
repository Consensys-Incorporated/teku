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

package tech.pegasys.teku.services.beaconchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.execution.ReceivedExecutionPayloadEventsChannel;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.storage.client.RecentChainData;

class BeaconChainControllerTest {

  private final RecentChainData recentChainData = mock(RecentChainData.class);

  @Test
  void isSafeToDeactivateDenebFeatures_returnsFalseWhenFuluNotSupported() {
    final Spec spec = TestSpecFactory.createMinimalDeneb();
    final BeaconChainController controller = createController(spec);

    assertThat(controller.isSafeToDeactivateDenebFeatures()).isFalse();
  }

  @Test
  void isSafeToDeactivateDenebFeatures_returnsFalseWhenCurrentEpochIsEmpty() {
    final Spec spec = createFuluSpecWithRetentionPeriod(UInt64.valueOf(2), 10);
    final BeaconChainController controller = createController(spec);

    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.empty());

    assertThat(controller.isSafeToDeactivateDenebFeatures()).isFalse();
  }

  @Test
  void isSafeToDeactivateDenebFeatures_returnsFalseWhenWithinRetentionPeriod() {
    final UInt64 fuluForkEpoch = UInt64.valueOf(10);
    final int retentionPeriod = 100;
    final Spec spec = createFuluSpecWithRetentionPeriod(fuluForkEpoch, retentionPeriod);
    final BeaconChainController controller = createController(spec);

    // current epoch is fuluForkEpoch + retentionPeriod (exactly at boundary, not past it)
    final UInt64 currentEpoch = fuluForkEpoch.plus(retentionPeriod);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    assertThat(controller.isSafeToDeactivateDenebFeatures()).isFalse();
  }

  @Test
  void isSafeToDeactivateDenebFeatures_returnsFalseWhenBeforeFuluForkEpoch() {
    final UInt64 fuluForkEpoch = UInt64.valueOf(10);
    final int retentionPeriod = 100;
    final Spec spec = createFuluSpecWithRetentionPeriod(fuluForkEpoch, retentionPeriod);
    final BeaconChainController controller = createController(spec);

    // current epoch is before fulu fork
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(UInt64.valueOf(5)));

    assertThat(controller.isSafeToDeactivateDenebFeatures()).isFalse();
  }

  @Test
  void isSafeToDeactivateDenebFeatures_returnsTrueWhenPastRetentionPeriod() {
    final UInt64 fuluForkEpoch = UInt64.valueOf(10);
    final int retentionPeriod = 100;
    final Spec spec = createFuluSpecWithRetentionPeriod(fuluForkEpoch, retentionPeriod);
    final BeaconChainController controller = createController(spec);

    // current epoch is fuluForkEpoch + retentionPeriod + 1 (past the boundary)
    final UInt64 currentEpoch = fuluForkEpoch.plus(retentionPeriod + 1);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    assertThat(controller.isSafeToDeactivateDenebFeatures()).isTrue();
  }

  @Test
  void isSafeToDeactivateDenebFeatures_returnsTrueWhenWellPastRetentionPeriod() {
    final UInt64 fuluForkEpoch = UInt64.valueOf(10);
    final int retentionPeriod = 100;
    final Spec spec = createFuluSpecWithRetentionPeriod(fuluForkEpoch, retentionPeriod);
    final BeaconChainController controller = createController(spec);

    // current epoch is far past the retention period
    final UInt64 currentEpoch = fuluForkEpoch.plus(retentionPeriod * 2);
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    assertThat(controller.isSafeToDeactivateDenebFeatures()).isTrue();
  }

  @Test
  void initExecutionPayloadBidManager_subscribesToEventChannelsWhenGloasSupported() {
    final BeaconChainController controller = createController(TestSpecFactory.createMinimalGloas());
    final EventChannels eventChannels = mock(EventChannels.class);

    controller.gossipValidationHelper = mock(GossipValidationHelper.class);
    controller.proposerPreferencesManager = mock(ProposerPreferencesManager.class);
    controller.beaconConfig = mock(BeaconChainConfiguration.class, RETURNS_DEEP_STUBS);
    controller.poolFactory = mock(PoolFactory.class);
    controller.eventChannels = eventChannels;

    controller.initExecutionPayloadBidManager();

    verify(eventChannels).subscribe(eq(SlotEventsChannel.class), any());
    verify(eventChannels).subscribe(eq(ReceivedBlockEventsChannel.class), any());
    verify(eventChannels).subscribe(eq(ReceivedExecutionPayloadEventsChannel.class), any());
    verify(controller.proposerPreferencesManager).subscribeOperationAdded(any());
    assertThat(controller.executionPayloadBidManager).isNotEqualTo(ExecutionPayloadBidManager.NOOP);
  }

  @Test
  void initExecutionPayloadBidManager_doesNothingWhenGloasNotSupported() {
    final BeaconChainController controller = createController(TestSpecFactory.createMinimalFulu());
    final EventChannels eventChannels = mock(EventChannels.class);
    controller.eventChannels = eventChannels;

    controller.initExecutionPayloadBidManager();

    verifyNoInteractions(eventChannels);
    assertThat(controller.executionPayloadBidManager).isEqualTo(ExecutionPayloadBidManager.NOOP);
  }

  @Test
  void initAll_initialisesExecutionPayloadBidManagerBeforeValidatorApiHandlerConsumesIt() {
    // initValidatorApiHandler() builds the block factory (BlockOperationSelectorFactory) and the
    // ValidatorApiHandler, both consuming executionPayloadBidManager. It must therefore run after
    // initExecutionPayloadBidManager(), otherwise those consumers capture a null manager. This
    // pins that ordering in initAll(): reordering the two calls fails the test.
    final BeaconChainController controller = mock(BeaconChainController.class);
    doCallRealMethod().when(controller).initAll();

    controller.initAll();

    final InOrder inOrder = inOrder(controller);
    inOrder.verify(controller).initExecutionPayloadBidManager();
    inOrder.verify(controller).initValidatorApiHandler();
  }

  private Spec createFuluSpecWithRetentionPeriod(
      final UInt64 fuluForkEpoch, final int minEpochsForBlobSidecarsRequests) {
    return TestSpecFactory.createMinimalFulu(
        builder ->
            builder
                .fuluForkEpoch(fuluForkEpoch)
                .denebBuilder(
                    deneb ->
                        deneb.minEpochsForBlobSidecarsRequests(minEpochsForBlobSidecarsRequests)));
  }

  private BeaconChainController createController(final Spec spec) {
    final BeaconChainController controller = mock(BeaconChainController.class, CALLS_REAL_METHODS);
    controller.spec = spec;
    controller.recentChainData = recentChainData;
    return controller;
  }
}
