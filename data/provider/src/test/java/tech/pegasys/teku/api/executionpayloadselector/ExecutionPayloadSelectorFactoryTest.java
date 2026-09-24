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

package tech.pegasys.teku.api.executionpayloadselector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.metadata.ExecutionPayloadAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class ExecutionPayloadSelectorFactoryTest {
  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil data = new DataStructureUtil(spec);
  private final SpecMilestone milestone = spec.getGenesisSpec().getMilestone();

  private final ExecutionPayloadSelectorFactory executionPayloadSelectorFactory =
      new ExecutionPayloadSelectorFactory(spec, client);

  @Test
  public void blockRootSelector_shouldGetExecutionPayloadByBlockRoot()
      throws ExecutionException, InterruptedException {
    final Bytes32 blockRoot = data.randomBytes32();
    final ChainHead chainHead = ChainHead.create(data.randomSignedBlockAndState(10));
    final SignedExecutionPayloadEnvelope executionPayload =
        data.randomSignedExecutionPayloadEnvelope(10);
    when(client.getExecutionPayloadByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayload)));
    when(client.getChainHead()).thenReturn(Optional.of(chainHead));
    when(client.isOptimisticBlock(executionPayload.getBeaconBlockRoot())).thenReturn(false);
    when(client.isFinalized(executionPayload.getSlot())).thenReturn(true);

    final Optional<ExecutionPayloadAndMetaData> result =
        executionPayloadSelectorFactory.blockRootSelector(blockRoot).getExecutionPayload().get();

    verify(client).getExecutionPayloadByBlockRoot(blockRoot);
    assertThat(result)
        .contains(new ExecutionPayloadAndMetaData(executionPayload, milestone, false, true));
  }

  @Test
  public void slotSelector_shouldGetExecutionPayloadAtSlotExact()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState head = data.randomSignedBlockAndState(100);
    final ChainHead chainHead = ChainHead.create(head);
    final UInt64 slot = UInt64.valueOf(10);
    final SignedExecutionPayloadEnvelope executionPayload =
        data.randomSignedExecutionPayloadEnvelope(10);
    when(client.getChainHead()).thenReturn(Optional.of(chainHead));
    when(client.getExecutionPayloadAtSlotExact(slot, chainHead.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayload)));
    when(client.isOptimisticBlock(executionPayload.getBeaconBlockRoot())).thenReturn(false);
    when(client.isFinalized(slot)).thenReturn(true);

    final Optional<ExecutionPayloadAndMetaData> result =
        executionPayloadSelectorFactory.slotSelector(slot).getExecutionPayload().get();

    verify(client).getExecutionPayloadAtSlotExact(slot, chainHead.getRoot());
    assertThat(result)
        .contains(new ExecutionPayloadAndMetaData(executionPayload, milestone, false, true));
  }

  @Test
  public void genesisSelector_shouldGetExecutionPayloadAtSlotZero()
      throws ExecutionException, InterruptedException {
    final SignedBeaconBlock genesisBlock = data.randomSignedBeaconBlock(0);
    final SignedExecutionPayloadEnvelope executionPayload =
        data.randomSignedExecutionPayloadEnvelope(0);
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(genesisBlock)));
    when(client.getExecutionPayloadByBlockRoot(genesisBlock.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayload)));
    when(client.isFinalized(UInt64.ZERO)).thenReturn(true);

    final Optional<ExecutionPayloadAndMetaData> result =
        executionPayloadSelectorFactory.genesisSelector().getExecutionPayload().get();

    verify(client).getBlockAtSlotExact(UInt64.ZERO);
    verify(client).getExecutionPayloadByBlockRoot(genesisBlock.getRoot());
    assertThat(result)
        .contains(new ExecutionPayloadAndMetaData(executionPayload, milestone, false, true));
  }

  @Test
  public void finalizedSelector_shouldGetFinalizedExecutionPayload()
      throws ExecutionException, InterruptedException {
    final AnchorPoint anchorPoint = data.randomAnchorPoint(UInt64.ONE);
    final SignedExecutionPayloadEnvelope executionPayload =
        data.randomSignedExecutionPayloadEnvelope(anchorPoint.getSlot().longValue());
    when(client.getLatestFinalized()).thenReturn(Optional.of(anchorPoint));
    when(client.getExecutionPayloadByBlockRoot(anchorPoint.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(executionPayload)));
    when(client.isOptimisticBlock(anchorPoint.getRoot())).thenReturn(false);

    final Optional<ExecutionPayloadAndMetaData> result =
        executionPayloadSelectorFactory.finalizedSelector().getExecutionPayload().get();

    verify(client).getLatestFinalized();
    verify(client).getExecutionPayloadByBlockRoot(anchorPoint.getRoot());
    assertThat(result)
        .contains(new ExecutionPayloadAndMetaData(executionPayload, milestone, false, true));
  }

  @Test
  public void finalizedSelector_shouldReturnEmptyWhenNoFinalizedCheckpoint()
      throws ExecutionException, InterruptedException {
    when(client.getLatestFinalized()).thenReturn(Optional.empty());

    final Optional<ExecutionPayloadAndMetaData> result =
        executionPayloadSelectorFactory.finalizedSelector().getExecutionPayload().get();

    assertThat(result).isEmpty();
  }
}
