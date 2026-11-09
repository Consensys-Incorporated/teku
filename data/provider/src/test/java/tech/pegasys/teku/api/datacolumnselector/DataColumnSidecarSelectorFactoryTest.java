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

package tech.pegasys.teku.api.datacolumnselector;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.metadata.DataColumnSidecarsAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

@TestSpecContext(
    signatureVerifierNoop = true,
    milestone = {SpecMilestone.ELECTRA, SpecMilestone.FULU, SpecMilestone.GLOAS})
public class DataColumnSidecarSelectorFactoryTest {

  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private final List<UInt64> indices = List.of(UInt64.ZERO, UInt64.ONE);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private SignedBeaconBlock block;
  private List<DataColumnSidecar> dataColumnSidecars;
  private DataColumnSidecarSelectorFactory dataColumnSidecarSelectorFactory;
  private SpecContext specContext;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    this.specContext = specContext;
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    block = dataStructureUtil.randomSignedBeaconBlock();
    dataColumnSidecarSelectorFactory = new DataColumnSidecarSelectorFactory(spec, client);

    // Post-FULU initialization part
    if (specContext.getSpecMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      dataColumnSidecars =
          IntStream.range(0, 5)
              .mapToObj(
                  index ->
                      dataStructureUtil.randomDataColumnSidecar(
                          block.asHeader(), UInt64.valueOf(index)))
              .toList();
    }
  }

  @TestTemplate
  public void shouldLookForDataColumnSidecarsBySlotOnlyAfterFulu()
      throws ExecutionException, InterruptedException {
    final DataColumnSidecarSelectorFactory dataColumnSidecarSelectorFactory =
        new DataColumnSidecarSelectorFactory(spec, client);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    when(client.isFinalized(block.getSlot())).thenReturn(false);
    when(client.getBlockAtSlotExact(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(any(UInt64.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));
    dataColumnSidecarSelectorFactory
        .slotSelector(block.getSlot())
        .getDataColumnSidecars(indices)
        .get();

    validateDataClientGetDataColumnSidecarsCalled();
  }

  @TestTemplate
  public void shouldLookForDataColumnSidecarsByHeadOnlyAfterFulu()
      throws ExecutionException, InterruptedException {
    final DataColumnSidecarSelectorFactory dataColumnSidecarSelectorFactory =
        new DataColumnSidecarSelectorFactory(spec, client);

    final SignedBlockAndState blockAndState = dataStructureUtil.randomSignedBlockAndState(100);

    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    when(client.getDataColumnSidecars(blockAndState.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    dataColumnSidecarSelectorFactory.headSelector().getDataColumnSidecars(indices).get();

    validateDataClientGetDataColumnSidecarsCalled();
  }

  @TestTemplate
  public void headSelector_shouldGetHeadDataColumnSidecars()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();
    final SignedBlockAndState blockAndState = dataStructureUtil.randomSignedBlockAndState(100);

    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    when(client.getDataColumnSidecars(blockAndState.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory.headSelector().getDataColumnSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @TestTemplate
  public void finalizedSelector_shouldGetFinalizedDataColumnSidecars()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();
    final AnchorPoint anchorPoint = dataStructureUtil.randomAnchorPoint(UInt64.ONE);

    when(client.getLatestFinalized()).thenReturn(Optional.of(anchorPoint));
    when(client.getDataColumnSidecars(anchorPoint.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory.finalizedSelector().getDataColumnSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @TestTemplate
  public void genesisSelector_shouldGetGenesisDataColumnSidecars()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory.genesisSelector().getDataColumnSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @TestTemplate
  public void blockRootSelector_shouldGetDataColumnSidecarsForFinalizedSlot()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();
    final UInt64 finalizedSlot = UInt64.valueOf(42);
    final SignedBlockAndState blockAndState = dataStructureUtil.randomSignedBlockAndState(100);
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));

    when(client.getFinalizedSlotByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(finalizedSlot)));
    when(client.getDataColumnSidecars(finalizedSlot, indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .blockRootSelector(block.getRoot())
            .getDataColumnSidecars(indices)
            .get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @TestTemplate
  public void blockRootSelector_shouldGetDataColumnSidecarsByRetrievingBlock()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();
    final SignedBlockAndState blockAndState = dataStructureUtil.randomSignedBlockAndState(100);
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));

    when(client.getFinalizedSlotByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(client.getBlockByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .blockRootSelector(block.getRoot())
            .getDataColumnSidecars(indices)
            .get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @TestTemplate
  public void slotSelector_shouldGetDataColumnSidecarsFromFinalizedSlot()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();
    when(client.isFinalized(block.getSlot())).thenReturn(true);
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));
    when(client.isChainHeadOptimistic()).thenReturn(false);

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .slotSelector(block.getSlot())
            .getDataColumnSidecars(indices)
            .get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @TestTemplate
  public void slotSelector_shouldGetDataColumnSidecarsByRetrievingBlockWhenSlotNotFinalized()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();

    when(client.isFinalized(block.getSlot())).thenReturn(false);
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .slotSelector(block.getSlot())
            .getDataColumnSidecars(indices)
            .get();
    assertThat(result.get().getData()).isEqualTo(dataColumnSidecars);
  }

  @TestTemplate
  public void createSelectorForBlockId_shouldThrowBadRequestException() {
    assertThrows(
        BadRequestException.class,
        () -> dataColumnSidecarSelectorFactory.createSelectorForBlockId("a"));
  }

  @TestTemplate
  public void stateRootSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            dataColumnSidecarSelectorFactory.stateRootSelector(dataStructureUtil.randomBytes32()));
  }

  @TestTemplate
  public void createSelectorForBlockId_shouldThrowBadRequestExceptionOnJustifiedKeyword() {
    assertThrows(
        BadRequestException.class,
        () -> dataColumnSidecarSelectorFactory.createSelectorForBlockId("justified"));
  }

  @TestTemplate
  public void justifiedSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class, dataColumnSidecarSelectorFactory::justifiedSelector);
  }

  @TestTemplate
  public void shouldNotLookForDataColumnSidecarsWhenNoKzgCommitments()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();
    final SignedBlockAndState blockAndState = dataStructureUtil.randomSignedBlockAndState(100);

    final SignedBeaconBlock blockWithEmptyCommitments =
        dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments();
    when(client.isFinalized(blockWithEmptyCommitments.getSlot())).thenReturn(false);
    when(client.getFinalizedSlotByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(client.getBlockByBlockRoot(blockWithEmptyCommitments.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockWithEmptyCommitments)));
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    Optional<DataColumnSidecarsAndMetaData> maybeDataColumnSidecars =
        dataColumnSidecarSelectorFactory
            .blockRootSelector(blockWithEmptyCommitments.getRoot())
            .getDataColumnSidecars(indices)
            .get();
    verify(client, never()).getDataColumnSidecars(any(UInt64.class), anyList());
    assertThat(maybeDataColumnSidecars).isPresent();
    assertThat(maybeDataColumnSidecars.get().getData()).isEmpty();
  }

  @TestTemplate
  public void genesisSelector_shouldAlwaysReturnOptimisticMetadataFieldFalse()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory.genesisSelector().getDataColumnSidecars(indices).get();
    assertThat(result).isNotEmpty();
    final DataColumnSidecarsAndMetaData dataColumnSidecarsAndMetaData = result.get();
    assertThat(dataColumnSidecarsAndMetaData.isExecutionOptimistic()).isFalse();
  }

  @TestTemplate
  public void slotSelector_whenSelectingFinalizedBlockMetadataReturnsFinalizedTrue()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();
    when(client.isFinalized(block.getSlot())).thenReturn(true);
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));
    when(client.isChainHeadOptimistic()).thenReturn(false);

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .slotSelector(block.getSlot())
            .getDataColumnSidecars(indices)
            .get();

    assertThat(result).isNotEmpty();
    final DataColumnSidecarsAndMetaData dataColumnSidecarsAndMetaData = result.get();
    assertThat(dataColumnSidecarsAndMetaData.isFinalized()).isTrue();
  }

  @TestTemplate
  public void slotSelector_whenSelectingNonFinalizedBlockMetadataReturnsFinalizedFalse()
      throws ExecutionException, InterruptedException {
    specContext.assumeFuluActive();
    when(client.isFinalized(block.getSlot())).thenReturn(false);
    when(client.getBlockAtSlotExact(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getDataColumnSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(dataColumnSidecars));
    when(client.isChainHeadOptimistic()).thenReturn(false);

    final Optional<DataColumnSidecarsAndMetaData> result =
        dataColumnSidecarSelectorFactory
            .slotSelector(block.getSlot())
            .getDataColumnSidecars(indices)
            .get();

    assertThat(result).isNotEmpty();
    final DataColumnSidecarsAndMetaData dataColumnSidecarsAndMetaData = result.get();
    assertThat(dataColumnSidecarsAndMetaData.isFinalized()).isFalse();
  }

  private void validateDataClientGetDataColumnSidecarsCalled() {
    if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
      verify(client, never()).getDataColumnSidecars(any(UInt64.class), anyList());
      return;
    }

    verify(client).getDataColumnSidecars(any(UInt64.class), anyList());
  }
}
