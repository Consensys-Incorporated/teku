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

package tech.pegasys.teku.validator.client.duties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.failedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricsSteps;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContentsDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.SignedBlockContentsDeneb;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.BuilderConfigProvider;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.execution.ExecutionPayloadBidEventsChannel;
import tech.pegasys.teku.validator.client.signer.BlockContainerSigner;
import tech.pegasys.teku.validator.client.signer.MilestoneBasedBlockContainerSigner;

class BlockProductionDutyTest {
  private static final String TYPE = "block";
  private static final UInt64 CAPELLA_SLOT = UInt64.valueOf(498294);
  private static final UInt64 DENEB_FORK_EPOCH = UInt64.valueOf(500000);
  private final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(DENEB_FORK_EPOCH);
  private final UInt64 denebSlot = DENEB_FORK_EPOCH.times(spec.getSlotsPerEpoch(ZERO)).plus(42);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final Signer signer = mock(Signer.class);
  private final Bytes32 graffiti = dataStructureUtil.randomBytes32();
  private final Validator validator =
      new Validator(
          dataStructureUtil.randomPublicKey(),
          signer,
          new FileBackedGraffitiProvider(Optional.of(graffiti), Optional.empty()));
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);
  private final BlockContainerSigner blockContainerSigner =
      new MilestoneBasedBlockContainerSigner(spec);
  private final ValidatorDutyMetrics validatorDutyMetrics =
      spy(ValidatorDutyMetrics.create(new StubMetricsSystem()));
  private final ExecutionPayloadBidEventsChannel executionPayloadBidEventsChannelPublisher =
      mock(ExecutionPayloadBidEventsChannel.class);
  private final BuilderConfigProvider builderConfigProvider = mock(BuilderConfigProvider.class);
  private BlockProductionDuty duty;

  @BeforeEach
  public void setUp() {
    duty =
        new BlockProductionDuty(
            validator,
            CAPELLA_SLOT,
            forkProvider,
            validatorApiChannel,
            blockContainerSigner,
            spec,
            validatorDutyMetrics,
            executionPayloadBidEventsChannelPublisher,
            builderConfigProvider);
    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
    when(builderConfigProvider.getBuilderConfig(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  public void shouldFailWhenCreateRandaoFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(CAPELLA_SLOT), fork))
        .thenReturn(failedFuture(error));

    assertDutyFails(error);
    verifyNoInteractions(validatorDutyMetrics);
  }

  @Test
  public void shouldFailWhenCreateUnsignedBlockFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(CAPELLA_SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(
            CAPELLA_SLOT, randaoReveal, Optional.of(graffiti), false, Optional.empty()))
        .thenReturn(failedFuture(error));

    assertDutyFails(error);

    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    verifyNoMoreInteractions(validatorDutyMetrics);
  }

  @Test
  public void shouldFailWhenCreateUnsignedBlockReturnsEmpty() {
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(CAPELLA_SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(
            CAPELLA_SLOT, randaoReveal, Optional.of(graffiti), false, Optional.empty()))
        .thenReturn(completedFuture(Optional.empty()));

    performAndReportDuty();

    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(CAPELLA_SLOT),
            eq(Set.of(validator.getPublicKey().toAbbreviatedString())),
            any(IllegalStateException.class));
    verifyNoMoreInteractions(validatorLogger);

    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    verifyNoMoreInteractions(validatorDutyMetrics);
  }

  @Test
  public void shouldFailWhenSigningBlockFails() {
    final RuntimeException error = new RuntimeException("Sorry!");
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(CAPELLA_SLOT);
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(CAPELLA_SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(
            CAPELLA_SLOT, randaoReveal, Optional.of(graffiti), false, Optional.empty()))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaData)));
    when(signer.signBlock(blockContainerAndMetaData.blockContainer().getBlock(), fork))
        .thenReturn(failedFuture(error));

    assertDutyFails(error);

    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.SIGN));
    verifyNoMoreInteractions(validatorDutyMetrics);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCreateAndPublishBlock(final boolean isBlindedBlocksEnabled) {
    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();
    final BlockContainerAndMetaData blockContainerAndMetaData;

    if (isBlindedBlocksEnabled) {
      blockContainerAndMetaData =
          dataStructureUtil.randomBlindedBlockContainerAndMetaData(CAPELLA_SLOT);
    } else {
      blockContainerAndMetaData = dataStructureUtil.randomBlockContainerAndMetaData(CAPELLA_SLOT);
    }

    final BeaconBlock unsignedBlock = blockContainerAndMetaData.blockContainer().getBlock();

    when(signer.createRandaoReveal(spec.computeEpochAtSlot(CAPELLA_SLOT), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(validatorApiChannel.createUnsignedBlock(
            CAPELLA_SLOT, randaoReveal, Optional.of(graffiti), false, Optional.empty()))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaData)));
    when(signer.signBlock(unsignedBlock, fork)).thenReturn(completedFuture(blockSignature));
    final SignedBeaconBlock signedBlock =
        dataStructureUtil.signedBlock(unsignedBlock, blockSignature);
    when(validatorApiChannel.sendSignedBlock(signedBlock, BroadcastValidationLevel.GOSSIP))
        .thenReturn(completedFuture(SendSignedBlockResult.success(signedBlock.getRoot())));

    performAndReportDuty();

    verify(validatorApiChannel).sendSignedBlock(signedBlock, BroadcastValidationLevel.GOSSIP);
    verify(validatorLogger)
        .dutyCompleted(
            eq(TYPE),
            eq(CAPELLA_SLOT),
            eq(1),
            eq(Set.of(unsignedBlock.hashTreeRoot())),
            ArgumentMatchers.argThat(Optional::isPresent));
    verifyNoMoreInteractions(validatorLogger);

    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.SIGN));
    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.SEND));
  }

  @Test
  public void forDeneb_shouldCreateAndPublishBlockContents() {
    duty = createBlockProductionDutyForDeneb();

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();
    // can create BlockContents only post-Deneb
    final BlockContainer unsignedBlockContents = dataStructureUtil.randomBlockContents(denebSlot);
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(unsignedBlockContents, denebSlot);
    final BeaconBlock unsignedBlock = unsignedBlockContents.getBlock();
    final List<Blob> blobsFromUnsignedBlockContents =
        unsignedBlockContents.getBlobs().orElseThrow().asList();
    final List<SszKZGProof> kzgProofsFromUnsignedBlockContents =
        unsignedBlockContents.getKzgProofs().orElseThrow().asList();
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(denebSlot), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(signer.signBlock(unsignedBlockContents.getBlock(), fork))
        .thenReturn(completedFuture(blockSignature));
    when(validatorApiChannel.createUnsignedBlock(
            denebSlot, randaoReveal, Optional.of(graffiti), false, Optional.empty()))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaData)));
    when(validatorApiChannel.sendSignedBlock(any(), any()))
        .thenReturn(completedFuture(SendSignedBlockResult.success(unsignedBlock.getRoot())));

    performAndReportDuty(denebSlot);

    final ArgumentCaptor<SignedBlockContainer> signedBlockContainerArgumentCaptor =
        ArgumentCaptor.forClass(SignedBlockContainer.class);

    verify(validatorApiChannel)
        .sendSignedBlock(signedBlockContainerArgumentCaptor.capture(), any());
    verify(validatorLogger)
        .dutyCompleted(
            eq(TYPE),
            eq(denebSlot),
            eq(1),
            eq(Set.of(unsignedBlock.hashTreeRoot())),
            ArgumentMatchers.argThat(Optional::isPresent));
    verifyNoMoreInteractions(validatorLogger);

    final SignedBlockContainer signedBlockContainer = signedBlockContainerArgumentCaptor.getValue();

    assertThat(signedBlockContainer).isInstanceOf(SignedBlockContentsDeneb.class);

    final SignedBlockContentsDeneb signedBlockContents =
        (SignedBlockContentsDeneb) signedBlockContainer;

    assertThat(signedBlockContents.isBlinded()).isFalse();

    final SignedBeaconBlock signedBlock = signedBlockContents.getSignedBlock();
    assertThat(signedBlock.getMessage()).isEqualTo(unsignedBlock);
    assertThat(signedBlock.getSignature()).isEqualTo(blockSignature);

    assertThat(signedBlockContents.getKzgProofs()).isPresent();

    final SszList<SszKZGProof> kzgProofsFromSignedBlockContent =
        signedBlockContents.getKzgProofs().get();

    assertThat(kzgProofsFromSignedBlockContent).isNotEmpty();

    assertThat(kzgProofsFromUnsignedBlockContents)
        .isEqualTo(kzgProofsFromSignedBlockContent.asList());

    assertThat(signedBlockContents.getBlobs()).isPresent();

    final SszList<Blob> blobsFromSignedBlockContents = signedBlockContents.getBlobs().get();

    assertThat(blobsFromSignedBlockContents).isNotEmpty();

    assertThat(blobsFromUnsignedBlockContents).isEqualTo(blobsFromSignedBlockContents.asList());

    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.SIGN));
    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.SEND));
  }

  @Test
  public void forDeneb_shouldCreateAndPublishBlindedBlock() {
    duty = createBlockProductionDutyForDeneb();

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlindedBlockContainerAndMetaData(denebSlot);
    final BeaconBlock unsignedBlindedBlock = blockContainerAndMetaData.blockContainer().getBlock();

    when(signer.createRandaoReveal(spec.computeEpochAtSlot(denebSlot), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(signer.signBlock(unsignedBlindedBlock.getBlock(), fork))
        .thenReturn(completedFuture(blockSignature));
    when(validatorApiChannel.createUnsignedBlock(
            denebSlot, randaoReveal, Optional.of(graffiti), false, Optional.empty()))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaData)));
    when(validatorApiChannel.sendSignedBlock(any(), any()))
        .thenReturn(completedFuture(SendSignedBlockResult.success(unsignedBlindedBlock.getRoot())));

    performAndReportDuty(denebSlot);

    final ArgumentCaptor<SignedBlockContainer> signedBlindedBlockContainerArgumentCaptor =
        ArgumentCaptor.forClass(SignedBlockContainer.class);

    verify(validatorApiChannel)
        .sendSignedBlock(signedBlindedBlockContainerArgumentCaptor.capture(), any());
    verify(validatorLogger)
        .dutyCompleted(
            eq(TYPE),
            eq(denebSlot),
            eq(1),
            eq(Set.of(unsignedBlindedBlock.hashTreeRoot())),
            ArgumentMatchers.argThat(Optional::isPresent));
    verifyNoMoreInteractions(validatorLogger);

    final SignedBlockContainer signedBlindedBlockContainer =
        signedBlindedBlockContainerArgumentCaptor.getValue();

    assertThat(signedBlindedBlockContainer).isInstanceOf(SignedBeaconBlock.class);

    final SignedBeaconBlock signedBlock = signedBlindedBlockContainer.getSignedBlock();

    assertThat(signedBlock.getMessage()).isEqualTo(unsignedBlindedBlock);
    assertThat(signedBlock.isBlinded()).isTrue();
    assertThat(signedBlock.getSignature()).isEqualTo(blockSignature);

    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.CREATE));
    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.SIGN));
    verify(validatorDutyMetrics)
        .record(any(), any(BlockProductionDuty.class), eq(ValidatorDutyMetricsSteps.SEND));
  }

  @Test
  public void forDeneb_shouldFailWhenNoKzgProofs() {
    duty = createBlockProductionDutyForDeneb();

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();
    // can create BlockContents only post-Deneb
    final BlockContainer unsignedBlockContents = dataStructureUtil.randomBlockContents(denebSlot);
    final BlockContentsDeneb unsignedBlockContentsMock = mock(BlockContentsDeneb.class);
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(unsignedBlockContentsMock, denebSlot);

    when(unsignedBlockContentsMock.getKzgProofs()).thenReturn(Optional.empty());
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(unsignedBlockContentsMock.getSlot()).thenReturn(unsignedBlockContents.getSlot());
    when(unsignedBlockContentsMock.getBlock()).thenReturn(unsignedBlockContents.getBlock());
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(denebSlot), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(signer.signBlock(unsignedBlockContentsMock.getBlock(), fork))
        .thenReturn(completedFuture(blockSignature));
    when(validatorApiChannel.createUnsignedBlock(
            denebSlot, randaoReveal, Optional.of(graffiti), false, Optional.empty()))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaData)));
    when(validatorApiChannel.sendSignedBlock(any(), any()))
        .thenReturn(completedFuture(SendSignedBlockResult.success(blockRoot)));

    final RuntimeException error =
        new RuntimeException(
            String.format(
                "Unable to get KZG Proofs when signing Deneb block at slot %d",
                denebSlot.longValue()));

    assertDutyFails(error, denebSlot);
  }

  @Test
  public void forDeneb_shouldFailWhenNoBlobs() {
    duty = createBlockProductionDutyForDeneb();

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();
    // can create BlockContents only post-Deneb
    final BlockContainer unsignedBlockContents = dataStructureUtil.randomBlockContents(denebSlot);
    final BlockContentsDeneb unsignedBlockContentsMock = mock(BlockContentsDeneb.class);
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(unsignedBlockContentsMock, denebSlot);

    when(unsignedBlockContentsMock.getBlobs()).thenReturn(Optional.empty());
    when(unsignedBlockContentsMock.getKzgProofs()).thenReturn(unsignedBlockContents.getKzgProofs());
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(unsignedBlockContentsMock.getSlot()).thenReturn(unsignedBlockContents.getSlot());
    when(unsignedBlockContentsMock.getBlock()).thenReturn(unsignedBlockContents.getBlock());
    when(signer.createRandaoReveal(spec.computeEpochAtSlot(denebSlot), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(signer.signBlock(unsignedBlockContentsMock.getBlock(), fork))
        .thenReturn(completedFuture(blockSignature));
    when(validatorApiChannel.createUnsignedBlock(
            denebSlot, randaoReveal, Optional.of(graffiti), false, Optional.empty()))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaData)));
    when(validatorApiChannel.sendSignedBlock(any(), any()))
        .thenReturn(completedFuture(SendSignedBlockResult.success(blockRoot)));

    final RuntimeException error =
        new RuntimeException(
            String.format(
                "Unable to get blobs when signing Deneb block at slot %d", denebSlot.longValue()));

    assertDutyFails(error, denebSlot);
  }

  @Test
  public void forGloas_shouldCreateAndPublishBlockAndNotifyBidEventsSubscribersWhenSelfBuiltBid() {
    final UInt64 slot = UInt64.valueOf(42);
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BlockContainerSigner blockContainerSigner = new MilestoneBasedBlockContainerSigner(spec);
    duty =
        new BlockProductionDuty(
            validator,
            UInt64.valueOf(42),
            forkProvider,
            validatorApiChannel,
            blockContainerSigner,
            spec,
            validatorDutyMetrics,
            executionPayloadBidEventsChannelPublisher,
            builderConfigProvider);

    final BLSSignature randaoReveal = dataStructureUtil.randomSignature();
    final BLSSignature blockSignature = dataStructureUtil.randomSignature();

    final ExecutionPayloadBid bid =
        dataStructureUtil.randomExecutionPayloadBid(slot, BUILDER_INDEX_SELF_BUILD);
    final BeaconBlockBody blockBody =
        dataStructureUtil.randomBeaconBlockBody(
            builder ->
                builder.signedExecutionPayloadBid(
                    SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
                        .getSignedExecutionPayloadBidSchema()
                        .create(bid, dataStructureUtil.randomSignature())));

    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(
            dataStructureUtil.randomBeaconBlock(slot, blockBody), slot);

    final BeaconBlock unsignedBlock = blockContainerAndMetaData.blockContainer().getBlock();

    when(signer.createRandaoReveal(spec.computeEpochAtSlot(slot), fork))
        .thenReturn(completedFuture(randaoReveal));
    when(builderConfigProvider.getBuilderConfig(validator, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(BuilderConfig.NO_OP)));
    when(validatorApiChannel.createUnsignedBlock(
            slot, randaoReveal, Optional.of(graffiti), false, Optional.of(BuilderConfig.NO_OP)))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaData)));
    when(signer.signBlock(unsignedBlock, fork)).thenReturn(completedFuture(blockSignature));
    final SignedBeaconBlock signedBlock =
        dataStructureUtil.signedBlock(unsignedBlock, blockSignature);
    when(validatorApiChannel.sendSignedBlock(signedBlock, BroadcastValidationLevel.GOSSIP))
        .thenReturn(completedFuture(SendSignedBlockResult.success(signedBlock.getRoot())));

    performAndReportDuty(slot);

    verify(executionPayloadBidEventsChannelPublisher)
        .onSelfBuiltBidIncludedInBlock(validator, fork, bid, signedBlock.getRoot());
  }

  @Test
  public void bellatrixBlockSummary() {
    final BeaconBlockBody block = mock(BeaconBlockBody.class);
    when(block.getOptionalExecutionPayloadSummary())
        .thenReturn(
            Optional.of(
                new PayloadSummary(
                    UInt64.valueOf(1024000),
                    UInt64.valueOf(102400),
                    dataStructureUtil.randomBytes32(),
                    dataStructureUtil.randomUInt64())));
    assertThat(duty.getBlockSummary(block))
        .containsExactly(
            "102400 (10%) gas, EL block: 499db7404cbff78670f0209f1932346fef68d985cb55a8d27472742bdf54d379 (4661716390776343276)");
  }

  @Test
  public void denebBlockSummary() {
    final BeaconBlockBody block = dataStructureUtil.randomBeaconBlockBody(denebSlot);
    assertThat(duty.getBlockSummary(block))
        .containsExactly(
            "Blobs: 1",
            "4491510546443434056 (0%) gas, EL block: 58913d3ec8a62b95e52fb1ee60ebddf392af6e1db902dd5bc3f1eea7003130ff (4488205580010065800)");
  }

  @Test
  public void gloasBlockSummary() {
    final Spec spec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BeaconBlockBody block = dataStructureUtil.randomBeaconBlockBody();
    assertThat(duty.getBlockSummary(block))
        .containsExactly(
            "Blobs: 7",
            "Builder: 1125033, Bid gas limit: 4759212943510379790, Bid EL block: 5999d9..3515");
  }

  public void assertDutyFails(final RuntimeException error) {
    assertDutyFails(error, CAPELLA_SLOT);
  }

  public void assertDutyFails(final RuntimeException expectedError, final UInt64 slot) {
    performAndReportDuty(slot);
    final ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(slot),
            eq(Set.of(validator.getPublicKey().toAbbreviatedString())),
            errorCaptor.capture());
    final Throwable actualError = errorCaptor.getValue();
    assertThat(expectedError.getCause()).isEqualTo(actualError.getCause());
    assertThat(expectedError.getMessage()).isEqualTo(actualError.getMessage());
    verifyNoMoreInteractions(validatorLogger);
  }

  private BlockProductionDuty createBlockProductionDutyForDeneb() {
    return new BlockProductionDuty(
        validator,
        denebSlot,
        forkProvider,
        validatorApiChannel,
        blockContainerSigner,
        spec,
        validatorDutyMetrics,
        executionPayloadBidEventsChannelPublisher,
        builderConfigProvider);
  }

  private void performAndReportDuty() {
    performAndReportDuty(CAPELLA_SLOT);
  }

  private void performAndReportDuty(final UInt64 slot) {
    final SafeFuture<DutyResult> result = duty.performDuty();
    assertThat(result).isCompleted();
    safeJoin(result).report(TYPE, slot, validatorLogger);
  }

  static class PayloadSummary implements ExecutionPayloadSummary {
    private final UInt64 gasLimit;
    private final UInt64 gasUsed;
    private final Bytes32 blockHash;
    private final UInt64 blockNumber;

    public PayloadSummary(
        final UInt64 gasLimit,
        final UInt64 gasUsed,
        final Bytes32 blockHash,
        final UInt64 blockNumber) {
      this.gasLimit = gasLimit;
      this.gasUsed = gasUsed;
      this.blockHash = blockHash;
      this.blockNumber = blockNumber;
    }

    @Override
    public UInt64 getGasLimit() {
      return gasLimit;
    }

    @Override
    public UInt64 getGasUsed() {
      return gasUsed;
    }

    @Override
    public UInt64 getBlockNumber() {
      return blockNumber;
    }

    @Override
    public Bytes32 getBlockHash() {
      return blockHash;
    }

    @Override
    public UInt256 getBaseFeePerGas() {
      return null;
    }

    @Override
    public UInt64 getTimestamp() {
      return null;
    }

    @Override
    public Bytes32 getPrevRandao() {
      return null;
    }

    @Override
    public Bytes32 getReceiptsRoot() {
      return null;
    }

    @Override
    public Bytes32 getStateRoot() {
      return null;
    }

    @Override
    public Bytes20 getFeeRecipient() {
      return null;
    }

    @Override
    public Bytes32 getParentHash() {
      return null;
    }

    @Override
    public Bytes getLogsBloom() {
      return null;
    }

    @Override
    public Bytes getExtraData() {
      return null;
    }

    @Override
    public boolean isDefaultPayload() {
      return false;
    }

    @Override
    public Optional<Bytes32> getOptionalWithdrawalsRoot() {
      return Optional.empty();
    }
  }
}
