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

package tech.pegasys.teku.spec;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.millisToSeconds;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.config.NetworkingSpecConfig;
import tech.pegasys.teku.spec.config.NetworkingSpecConfigDeneb;
import tech.pegasys.teku.spec.config.NetworkingSpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockInvariants;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockUnblinder;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelopeInvariants;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.MutableStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.BlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;
import tech.pegasys.teku.spec.genesis.GenesisGenerator;
import tech.pegasys.teku.spec.logic.StateTransition;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionPayloadVerifier;
import tech.pegasys.teku.spec.logic.common.execution.ExecutionRequestsProcessor;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.ExecutionPayloadProposalUtil.ExecutionPayloadProposalData;
import tech.pegasys.teku.spec.logic.common.util.LightClientUtil;
import tech.pegasys.teku.spec.logic.common.util.ProposerPreferencesUtil;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.util.ForkChoiceUtilDeneb;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BlobParameters;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.util.ForkChoiceUtilFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ForkChoiceUtilGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistryBuilder;

public class Spec {
  private static final Logger LOG = LogManager.getLogger();
  private final Map<SpecMilestone, SpecVersion> specVersions;
  private final ForkSchedule forkSchedule;
  private final StateTransition stateTransition;
  private final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent;
  private volatile boolean initialized = false;

  private Spec(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent,
      final Map<SpecMilestone, SpecVersion> specVersions,
      final ForkSchedule forkSchedule) {
    checkArgument(specVersions != null && !specVersions.isEmpty());
    checkArgument(forkSchedule != null);
    this.specConfigAndParent = specConfigAndParent;
    this.specVersions = specVersions;
    this.forkSchedule = forkSchedule;

    // Setup state transition
    this.stateTransition = new StateTransition(this::atSlot);
  }

  // This method must be called once after constructing the Spec to initialize any additional
  // dependencies lazily created during initialization in BeaconChainController
  public synchronized void initialize(
      final AvailabilityCheckerFactory<BlobSidecar> blobSidecarAvailabilityCheckerFactory,
      final AvailabilityCheckerFactory<UInt64> dataColumnSidecarAvailabilityCheckerFactory,
      final KZG kzg) {
    if (initialized) {
      throw new IllegalStateException("Spec already initialized");
    }
    initializeInternal(
        blobSidecarAvailabilityCheckerFactory, dataColumnSidecarAvailabilityCheckerFactory, kzg);
  }

  @VisibleForTesting
  public synchronized void reinitializeForTesting(
      final AvailabilityCheckerFactory<BlobSidecar> blobSidecarAvailabilityCheckerFactory,
      final AvailabilityCheckerFactory<UInt64> dataColumnSidecarAvailabilityCheckerFactory,
      final KZG kzg) {
    initializeInternal(
        blobSidecarAvailabilityCheckerFactory, dataColumnSidecarAvailabilityCheckerFactory, kzg);
  }

  private void initializeInternal(
      final AvailabilityCheckerFactory<BlobSidecar> blobSidecarAvailabilityCheckerFactory,
      final AvailabilityCheckerFactory<UInt64> dataColumnSidecarAvailabilityCheckerFactory,
      final KZG kzg) {
    initialized = true;

    specVersions
        .values()
        .forEach(
            specVersion -> {
              // inject ForkChoiceUtil dependencies
              switch (specVersion.getForkChoiceUtil()) {
                case ForkChoiceUtilGloas forkChoiceUtilGloas ->
                    forkChoiceUtilGloas.setDataColumnSidecarAvailabilityCheckerFactory(
                        dataColumnSidecarAvailabilityCheckerFactory);
                case ForkChoiceUtilFulu forkChoiceUtilFulu ->
                    forkChoiceUtilFulu.setDataColumnSidecarAvailabilityCheckerFactory(
                        dataColumnSidecarAvailabilityCheckerFactory);
                case ForkChoiceUtilDeneb forkChoiceUtilDeneb ->
                    forkChoiceUtilDeneb.setBlobSidecarAvailabilityCheckerFactory(
                        blobSidecarAvailabilityCheckerFactory);
                default -> {}
              }

              // inject KZG instance
              if (specVersion.miscHelpers() instanceof MiscHelpersDeneb miscHelpersDeneb) {
                miscHelpersDeneb.setKzg(kzg);
              }
            });
  }

  static Spec create(
      final SpecConfigAndParent<? extends SpecConfig> specConfigAndParent,
      final SpecMilestone highestMilestoneSupported) {
    final Map<SpecMilestone, SpecVersion> specVersions = new EnumMap<>(SpecMilestone.class);
    final ForkSchedule.Builder forkScheduleBuilder = ForkSchedule.builder();
    final SchemaRegistryBuilder schemaRegistryBuilder = SchemaRegistryBuilder.create();

    for (SpecMilestone milestone : SpecMilestone.getMilestonesUpTo(highestMilestoneSupported)) {
      SpecVersion.create(
              milestone, specConfigAndParent.forMilestone(milestone), schemaRegistryBuilder)
          .ifPresent(
              milestoneSpec -> {
                forkScheduleBuilder.addNextMilestone(milestoneSpec);
                specVersions.put(milestone, milestoneSpec);
              });
    }

    final ForkSchedule forkSchedule = forkScheduleBuilder.build();

    return new Spec(specConfigAndParent, specVersions, forkSchedule);
  }

  public Optional<KZG> getKzg() {
    if (!initialized) {
      throw new IllegalStateException("Spec must be initialized to access KZG");
    }
    return Optional.ofNullable(forMilestone(DENEB))
        .map(SpecVersion::miscHelpers)
        .flatMap(MiscHelpers::toVersionDeneb)
        .map(MiscHelpersDeneb::getKzg);
  }

  public SpecVersion forMilestone(final SpecMilestone milestone) {
    return specVersions.get(milestone);
  }

  public SpecVersion atEpoch(final UInt64 epoch) {
    return specVersions.get(forkSchedule.getSpecMilestoneAtEpoch(epoch));
  }

  public SpecVersion atSlot(final UInt64 slot) {
    return specVersions.get(forkSchedule.getSpecMilestoneAtSlot(slot));
  }

  public SpecVersion atTime(final UInt64 genesisTime, final UInt64 currentTime) {
    return specVersions.get(forkSchedule.getSpecMilestoneAtTime(genesisTime, currentTime));
  }

  private SpecVersion atTimeMillis(final UInt64 genesisTimeMillis, final UInt64 currentTimeMillis) {
    return atTime(millisToSeconds(genesisTimeMillis), millisToSeconds(currentTimeMillis));
  }

  public SpecConfigAndParent<? extends SpecConfig> getSpecConfigAndParent() {
    return specConfigAndParent;
  }

  public SpecConfig getSpecConfig(final UInt64 epoch) {
    return atEpoch(epoch).getConfig();
  }

  public BeaconStateUtil getBeaconStateUtil(final UInt64 slot) {
    return atSlot(slot).getBeaconStateUtil();
  }

  public Optional<SyncCommitteeUtil> getSyncCommitteeUtil(final UInt64 slot) {
    return atSlot(slot).getSyncCommitteeUtil();
  }

  public SyncCommitteeUtil getSyncCommitteeUtilRequired(final UInt64 slot) {
    return getSyncCommitteeUtil(slot)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Fork at slot " + slot + " does not support sync committees"));
  }

  public Optional<LightClientUtil> getLightClientUtil(final UInt64 slot) {
    return atSlot(slot).getLightClientUtil();
  }

  public LightClientUtil getLightClientUtilRequired(final UInt64 slot) {
    return getLightClientUtil(slot)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Fork at slot " + slot + " does not support light clients"));
  }

  public SpecVersion getGenesisSpec() {
    return atEpoch(UInt64.ZERO);
  }

  public SpecConfig getGenesisSpecConfig() {
    return getGenesisSpec().getConfig();
  }

  /**
   * Base networking constants
   *
   * <p>These constants are unified among forks and are not overridden, new constant name is used if
   * it's changed in the new fork
   */
  public NetworkingSpecConfig getNetworkingConfig() {
    // Networking config is constant along forks
    return getGenesisSpec().getConfig().getNetworkingConfig();
  }

  /**
   * Networking config with Deneb constants. Use {@link SpecConfigDeneb#required(SpecConfig)} when
   * you are sure that Deneb is available, otherwise use this method
   */
  public Optional<NetworkingSpecConfigDeneb> getNetworkingConfigDeneb() {
    return Optional.ofNullable(forMilestone(DENEB))
        .map(SpecVersion::getConfig)
        .map(specConfig -> (NetworkingSpecConfigDeneb) specConfig.getNetworkingConfig());
  }

  /**
   * Networking config with Gloas constants. Use {@link SpecConfigGloas#required(SpecConfig)} when
   * you are sure that Gloas is available, otherwise use this method
   */
  public Optional<NetworkingSpecConfigGloas> getNetworkingConfigGloas() {
    return Optional.ofNullable(forMilestone(GLOAS))
        .map(SpecVersion::getConfig)
        .map(specConfig -> (NetworkingSpecConfigGloas) specConfig.getNetworkingConfig());
  }

  public SchemaDefinitions getGenesisSchemaDefinitions() {
    return getGenesisSpec().getSchemaDefinitions();
  }

  public boolean supportsBlobSidecars() {
    return isMilestoneSupported(DENEB);
  }

  public boolean supportsDataColumnSidecars() {
    return isMilestoneSupported(FULU);
  }

  public boolean supportsExecutionPayloadEnvelopes() {
    return isMilestoneSupported(GLOAS);
  }

  public ForkSchedule getForkSchedule() {
    return forkSchedule;
  }

  /**
   * @return Milestones that are actively transitioned to. Does not include milestones that are
   *     immediately eclipsed by later milestones that activate at the same epoch.
   */
  public List<ForkAndSpecMilestone> getEnabledMilestones() {
    return forkSchedule.getActiveMilestones();
  }

  public Optional<BlobParameters> getBpoFork(final UInt64 epoch) {
    if (!isMilestoneSupported(FULU)) {
      return Optional.empty();
    }
    return MiscHelpersFulu.required(forMilestone(FULU).miscHelpers()).getBpoFork(epoch);
  }

  public Optional<BlobParameters> getNextBpoFork(final UInt64 epoch) {
    if (!isMilestoneSupported(FULU)) {
      return Optional.empty();
    }
    return MiscHelpersFulu.required(forMilestone(FULU).miscHelpers()).getNextBpoFork(epoch);
  }

  public Collection<BlobParameters> getBpoForks() {
    if (!isMilestoneSupported(FULU)) {
      return Collections.emptyList();
    }
    return MiscHelpersFulu.required(forMilestone(FULU).miscHelpers()).getBpoForks();
  }

  /**
   * Returns true if the given milestone is at or prior to our highest supported milestone
   *
   * @param milestone The milestone to be checked
   * @return True if the milestone is supported
   */
  public boolean isMilestoneSupported(final SpecMilestone milestone) {
    return forkSchedule.getSupportedMilestones().contains(milestone);
  }

  public Fork fork(final UInt64 epoch) {
    return forkSchedule.getFork(epoch);
  }

  // Config helpers
  public int slotsPerEpoch(final UInt64 epoch) {
    return atEpoch(epoch).getConfig().getSlotsPerEpoch();
  }

  public long getSlotsPerHistoricalRoot(final UInt64 slot) {
    return atSlot(slot).getConfig().getSlotsPerHistoricalRoot();
  }

  public int getSlotsPerEpoch(final UInt64 slot) {
    return atSlot(slot).getConfig().getSlotsPerEpoch();
  }

  public long getMaxDeposits(final BeaconState state) {
    return atState(state).getConfig().getMaxDeposits();
  }

  public long getEpochsPerEth1VotingPeriod(final UInt64 slot) {
    return atSlot(slot).getConfig().getEpochsPerEth1VotingPeriod();
  }

  public UInt64 getEth1FollowDistance(final UInt64 slot) {
    return atSlot(slot).getConfig().getEth1FollowDistance();
  }

  public int getSecondsPerEth1Block(final UInt64 slot) {
    return atSlot(slot).getConfig().getSecondsPerEth1Block();
  }

  public int getSyncCommitteeSize(final UInt64 slot) {
    return atSlot(slot)
        .getConfig()
        .toVersionAltair()
        .map(SpecConfigAltair::getSyncCommitteeSize)
        .orElse(0);
  }

  // Genesis
  public BeaconState initializeBeaconStateFromEth1(
      final Bytes32 eth1BlockHash,
      final UInt64 eth1Timestamp,
      final List<Deposit> deposits,
      final Optional<ExecutionPayloadHeader> payloadHeader) {
    final GenesisGenerator genesisGenerator = createGenesisGenerator();
    genesisGenerator.updateCandidateState(eth1BlockHash, eth1Timestamp, deposits);
    payloadHeader.ifPresent(genesisGenerator::updateExecutionPayloadHeader);
    return genesisGenerator.getGenesisState();
  }

  public GenesisGenerator createGenesisGenerator() {
    return new GenesisGenerator(getGenesisSpec(), forkSchedule.getGenesisFork());
  }

  // Serialization
  public BeaconState deserializeBeaconState(final Bytes serializedState) {
    final UInt64 slot = BeaconStateInvariants.extractSlot(serializedState);
    return atSlot(slot)
        .getSchemaDefinitions()
        .getBeaconStateSchema()
        .sszDeserialize(serializedState);
  }

  public SignedBeaconBlock deserializeSignedBeaconBlock(final Bytes serializedSignedBlock) {
    final UInt64 slot =
        BeaconBlockInvariants.extractSignedBlockContainerSlot(serializedSignedBlock);
    return atSlot(slot)
        .getSchemaDefinitions()
        .getSignedBeaconBlockSchema()
        .sszDeserialize(serializedSignedBlock);
  }

  public SignedBlindedExecutionPayloadEnvelope deserializeSignedBlindedExecutionPayloadEnvelope(
      final Bytes serializedSignedBlindedExecutionPayloadEnvelope) {
    final UInt64 slot =
        ExecutionPayloadEnvelopeInvariants.extractSignedBlindedExecutionPayloadEnvelopeSlot(
            serializedSignedBlindedExecutionPayloadEnvelope);
    return SchemaDefinitionsGloas.required(atSlot(slot).getSchemaDefinitions())
        .getSignedBlindedExecutionPayloadEnvelopeSchema()
        .sszDeserialize(serializedSignedBlindedExecutionPayloadEnvelope);
  }

  public SignedBlockContainer deserializeSignedBlockContainer(
      final Bytes serializedSignedBlockContainer, final Optional<String> milestone) {

    final SchemaDefinitions schemaDefinition =
        getSchemaDefinitionsForMilestone(milestone)
            .orElseGet(getSchemaDefinitionForSignedBlockSSZ(serializedSignedBlockContainer));

    return schemaDefinition
        .getSignedBlockContainerSchema()
        .sszDeserialize(serializedSignedBlockContainer);
  }

  public SignedBlockContainer deserializeSignedBlindedBlockContainer(
      final Bytes serializedSignedBlindedBlockContainer, final Optional<String> milestone) {

    final SchemaDefinitions schemaDefinition =
        getSchemaDefinitionsForMilestone(milestone)
            .orElseGet(getSchemaDefinitionForSignedBlockSSZ(serializedSignedBlindedBlockContainer));

    return schemaDefinition
        .getSignedBlindedBlockContainerSchema()
        .sszDeserialize(serializedSignedBlindedBlockContainer);
  }

  private Optional<SchemaDefinitions> getSchemaDefinitionsForMilestone(
      final Optional<String> milestone) {
    return milestone
        .map(SpecMilestone::forName)
        .map(specVersions::get)
        .map(SpecVersion::getSchemaDefinitions);
  }

  private Supplier<SchemaDefinitions> getSchemaDefinitionForSignedBlockSSZ(
      final Bytes serializedSignedBlindedBlockContainer) {
    return () -> {
      final UInt64 slot =
          BeaconBlockInvariants.extractSignedBlockContainerSlot(
              serializedSignedBlindedBlockContainer);
      return atSlot(slot).getSchemaDefinitions();
    };
  }

  public BeaconBlock deserializeBeaconBlock(final Bytes serializedBlock) {
    final UInt64 slot = BeaconBlockInvariants.extractBeaconBlockSlot(serializedBlock);
    return atSlot(slot)
        .getSchemaDefinitions()
        .getBeaconBlockSchema()
        .sszDeserialize(serializedBlock);
  }

  public SszList<Blob> deserializeBlobsInBlock(final Bytes serializedBlobs, final UInt64 slot) {
    return atSlot(slot)
        .getSchemaDefinitions()
        .toVersionDeneb()
        .orElseThrow(() -> new RuntimeException("Deneb milestone is required to deserialize blobs"))
        .getBlobsInBlockSchema()
        .sszDeserialize(serializedBlobs);
  }

  public BlobSidecar deserializeBlobSidecar(final Bytes serializedBlobSidecar, final UInt64 slot) {
    return atSlot(slot)
        .getSchemaDefinitions()
        .toVersionDeneb()
        .orElseThrow(
            () -> new RuntimeException("Deneb milestone is required to deserialize blob sidecar"))
        .getBlobSidecarSchema()
        .sszDeserialize(serializedBlobSidecar);
  }

  public ExecutionPayloadHeader deserializeJsonExecutionPayloadHeader(
      final ObjectMapper objectMapper, final File jsonFile, final UInt64 slot) throws IOException {
    return atSlot(slot)
        .getSchemaDefinitions()
        .toVersionBellatrix()
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Bellatrix milestone is required to deserialize execution payload header"))
        .getExecutionPayloadHeaderSchema()
        .jsonDeserialize(objectMapper.createParser(jsonFile));
  }

  public DataColumnSidecar deserializeSidecar(final Bytes serializedSidecar, final UInt64 slot) {
    return atSlot(slot)
        .getSchemaDefinitions()
        .toVersionFulu()
        .orElseThrow(
            () -> new RuntimeException("Fulu milestone is required to deserialize column sidecar"))
        .getDataColumnSidecarSchema()
        .sszDeserialize(serializedSidecar);
  }

  // BeaconState
  public UInt64 getCurrentEpoch(final BeaconState state) {
    return atState(state).beaconStateAccessors().getCurrentEpoch(state);
  }

  public UInt64 getPreviousEpoch(final BeaconState state) {
    return atState(state).beaconStateAccessors().getPreviousEpoch(state);
  }

  public Bytes32 getSeed(final BeaconState state, final UInt64 epoch, final Bytes4 domainType)
      throws IllegalArgumentException {
    return atState(state).beaconStateAccessors().getSeed(state, epoch, domainType);
  }

  public UInt64 computeStartSlotAtEpoch(final UInt64 epoch) {
    return atEpoch(epoch).miscHelpers().computeStartSlotAtEpoch(epoch);
  }

  public UInt64 computeEpochAtSlot(final UInt64 slot) {
    return atSlot(slot).miscHelpers().computeEpochAtSlot(slot);
  }

  // equivalent to compute_time_at_slot
  public UInt64 computeTimeAtSlot(final BeaconState state, final UInt64 slot) {
    return computeTimeAtSlot(slot, state.getGenesisTime());
  }

  public UInt64 computeTimeAtSlot(final UInt64 slot, final UInt64 genesisTime) {
    return atSlot(slot).miscHelpers().computeTimeAtSlot(genesisTime, slot);
  }

  public UInt64 computeTimeMillisAtSlot(final UInt64 slot, final UInt64 genesisTimeMillis) {
    return atSlot(slot).miscHelpers().computeTimeMillisAtSlot(genesisTimeMillis, slot);
  }

  public boolean isTimeReached(final UInt64 currentTimeMillis, final UInt64 earliestTimeMillis) {
    return currentTimeMillis.isGreaterThanOrEqualTo(earliestTimeMillis);
  }

  public boolean isBeforeTimeInSlot(
      final UInt64 slot,
      final UInt64 genesisTimeMillis,
      final UInt64 currentTimeMillis,
      final int timeInSlotMillisExclusive) {
    return !isTimeReached(
        currentTimeMillis,
        computeTimeMillisAtSlot(slot, genesisTimeMillis).plus(timeInSlotMillisExclusive));
  }

  public Bytes computeSigningRoot(final BeaconBlock block, final Bytes32 domain) {
    return atBlock(block).miscHelpers().computeSigningRoot(block, domain);
  }

  public Bytes computeSigningRoot(final BeaconBlockHeader blockHeader, final Bytes32 domain) {
    return atSlot(blockHeader.getSlot()).miscHelpers().computeSigningRoot(blockHeader, domain);
  }

  public Bytes computeSigningRoot(final AggregateAndProof proof, final Bytes32 domain) {
    return atSlot(proof.getAggregate().getData().getSlot())
        .miscHelpers()
        .computeSigningRoot(proof, domain);
  }

  public Bytes computeSigningRoot(
      final ExecutionPayloadEnvelope executionPayloadEnvelope, final Bytes32 domain) {
    return atSlot(executionPayloadEnvelope.getSlot())
        .miscHelpers()
        .computeSigningRoot(executionPayloadEnvelope, domain);
  }

  public Bytes computeSigningRoot(final UInt64 slot, final Bytes32 domain) {
    return atSlot(slot).miscHelpers().computeSigningRoot(slot, domain);
  }

  public Bytes computeBuilderApplicationSigningRoot(final UInt64 slot, final Merkleizable object) {
    final MiscHelpers miscHelpers = atSlot(slot).miscHelpers();
    return miscHelpers.computeSigningRoot(
        object, miscHelpers.computeDomain(Domain.APPLICATION_BUILDER));
  }

  /**
   * This method should NOT be used for milestones >= Fulu. Use {@link #computeForkDigest(Bytes32,
   * UInt64)} instead.
   */
  private Bytes4 computeForkDigest(
      final Bytes4 currentVersion, final Bytes32 genesisValidatorsRoot) {
    return atForkVersion(currentVersion)
        .miscHelpers()
        .computeForkDigest(currentVersion, genesisValidatorsRoot);
  }

  public Bytes4 computeForkDigest(final Bytes32 genesisValidatorsRoot, final UInt64 epoch) {
    return atEpoch(epoch)
        .miscHelpers()
        .toVersionFulu()
        .map(miscHelpersFulu -> miscHelpersFulu.computeForkDigest(genesisValidatorsRoot, epoch))
        // backwards compatibility for milestones before Fulu
        .orElseGet(() -> computeForkDigest(fork(epoch).getCurrentVersion(), genesisValidatorsRoot));
  }

  public int getBeaconProposerIndex(final BeaconState state, final UInt64 slot) {
    return atState(state).beaconStateAccessors().getBeaconProposerIndex(state, slot);
  }

  /**
   * Returns the proposer index for the given slot, advancing the state via slot processing if
   * necessary. On FULU+, the ProposerLookahead allows resolving the proposer without slot
   * processing for slots in the current or next epoch. On earlier forks, slot processing is avoided
   * only when the slot is in the same epoch as the state.
   *
   * @param state a beacon state at or before the requested slot
   * @param slot the slot to get the proposer index for (must be >= state.slot)
   */
  public int getProposerIndexAtSlot(final BeaconState state, final UInt64 slot)
      throws SlotProcessingException, EpochProcessingException {
    checkArgument(
        state.getSlot().isLessThanOrEqualTo(slot),
        "State slot %s must not be ahead of requested slot %s",
        state.getSlot(),
        slot);

    final BeaconState actualState;

    if (canCalculateProposerIndexAtSlot(state, slot)) {
      actualState = state;
    } else {
      actualState = processSlots(state, slot);
    }

    return getBeaconProposerIndex(actualState, slot);
  }

  public boolean canCalculateProposerIndexAtSlot(final BeaconState state, final UInt64 slot) {
    return atState(state).beaconStateAccessors().canCalculateProposerIndexAtSlot(state, slot);
  }

  public UInt64 getCommitteeCountPerSlot(final BeaconState state, final UInt64 epoch) {
    return atState(state).beaconStateAccessors().getCommitteeCountPerSlot(state, epoch);
  }

  public int getAttestationDueMillis(final UInt64 slot) {
    return atSlot(slot).getForkChoiceUtil().getAttestationDueMillis();
  }

  public int getAggregateDueMillis(final UInt64 slot) {
    return atSlot(slot).getForkChoiceUtil().getAggregateDueMillis();
  }

  public int getProposerReorgCutoffMillis(final UInt64 slot) {
    return atSlot(slot).getForkChoiceUtil().getProposerReorgCutoffMillis();
  }

  public int getSlotDurationMillis(final UInt64 slot) {
    return atSlot(slot).getConfig().getSlotDurationMillis();
  }

  public int getSyncMessageDueMillis(final UInt64 slot) {
    return atSlot(slot).getForkChoiceUtil().getSyncMessageDueMillis();
  }

  public int getContributionDueMillis(final UInt64 slot) {
    return atSlot(slot).getForkChoiceUtil().getContributionDueMillis();
  }

  public Optional<Integer> getPayloadAttestationDueMillis(final UInt64 slot) {
    return atSlot(slot).getForkChoiceUtil().getPayloadAttestationDueMillis();
  }

  public Optional<Integer> getPayloadDueMillis(final UInt64 slot) {
    return atSlot(slot).getForkChoiceUtil().getPayloadDueMillis();
  }

  public Bytes32 getBlockRoot(final BeaconState state, final UInt64 epoch) {
    return atState(state).beaconStateAccessors().getBlockRoot(state, epoch);
  }

  public Bytes32 getBlockRootAtSlot(final BeaconState state, final UInt64 slot) {
    return atState(state).beaconStateAccessors().getBlockRootAtSlot(state, slot);
  }

  public Bytes32 getDomain(
      final Bytes4 domainType,
      final UInt64 epoch,
      final Fork fork,
      final Bytes32 genesisValidatorsRoot) {
    return atEpoch(epoch)
        .beaconStateAccessors()
        .getDomain(domainType, epoch, fork, genesisValidatorsRoot);
  }

  /**
   * Resolved from the fork rather than the exit's epoch, to match how exits are validated (against
   * the state's fork). From Capella onwards EIP-7044 pins this domain, so an exit naming a past
   * epoch must still be signed under the current fork's domain to be valid. The epoch is still
   * passed through because pre-Deneb milestones legitimately use it to choose between the fork's
   * previous and current version.
   */
  public Bytes32 getVoluntaryExitDomain(
      final UInt64 epoch, final Fork fork, final Bytes32 genesisValidatorsRoot) {
    return atEpoch(fork.getEpoch())
        .beaconStateAccessors()
        .getVoluntaryExitDomain(epoch, fork, genesisValidatorsRoot);
  }

  public Bytes32 getRandaoMix(final BeaconState state, final UInt64 epoch) {
    return atState(state).beaconStateAccessors().getRandaoMix(state, epoch);
  }

  public boolean verifyProposerSlashingSignature(
      final BeaconState state,
      final ProposerSlashing proposerSlashing,
      final BLSSignatureVerifier signatureVerifier) {
    return atState(state)
        .operationSignatureVerifier()
        .verifyProposerSlashingSignature(
            state.getFork(), state, proposerSlashing, signatureVerifier);
  }

  public boolean verifyVoluntaryExitSignature(
      final BeaconState state,
      final SignedVoluntaryExit signedExit,
      final BLSSignatureVerifier signatureVerifier) {
    return atState(state)
        .operationSignatureVerifier()
        .verifyVoluntaryExitSignature(state, signedExit, signatureVerifier);
  }

  public Bytes32 getPreviousDutyDependentRoot(final BeaconState state) {
    return atState(state).getBeaconStateUtil().getPreviousDutyDependentRoot(state);
  }

  public Bytes32 getCurrentDutyDependentRoot(final BeaconState state) {
    return atState(state).getBeaconStateUtil().getCurrentDutyDependentRoot(state);
  }

  public UInt64 computeNextEpochBoundary(final UInt64 slot) {
    return atSlot(slot).getBeaconStateUtil().computeNextEpochBoundary(slot);
  }

  public int computeSubnetForAttestation(final BeaconState state, final Attestation attestation) {
    final UInt64 stateEpoch = getCurrentEpoch(state);
    final UInt64 attestationEpoch =
        atEpoch(stateEpoch).miscHelpers().computeEpochAtSlot(attestation.getData().getSlot());
    final UInt64 earliestEpochInRange =
        attestationEpoch.minusMinZero(atEpoch(attestationEpoch).getConfig().getMaxSeedLookahead());
    if (stateEpoch.isLessThan(earliestEpochInRange)) {
      final UInt64 earliestSlot =
          atEpoch(stateEpoch).miscHelpers().computeStartSlotAtEpoch(earliestEpochInRange);
      LOG.debug(
          "Processing empty slots for state at slot {}, target slot {}",
          state.getSlot(),
          earliestSlot);
      try {
        final BeaconState advancedState = processSlots(state, earliestSlot);
        return atState(advancedState)
            .getBeaconStateUtil()
            .computeSubnetForAttestation(advancedState, attestation);
      } catch (SlotProcessingException | EpochProcessingException e) {
        LOG.debug("Couldn't wind state at slot {} forward", state.getSlot(), e);
      }
    }

    return atState(state).getBeaconStateUtil().computeSubnetForAttestation(state, attestation);
  }

  public int computeSubnetForCommittee(
      final UInt64 attestationSlot, final UInt64 committeeIndex, final UInt64 committeesPerSlot) {
    return atSlot(attestationSlot)
        .getBeaconStateUtil()
        .computeSubnetForCommittee(attestationSlot, committeeIndex, committeesPerSlot);
  }

  public UInt64 getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(final UInt64 epoch) {
    return atEpoch(epoch)
        .miscHelpers()
        .getEarliestQueryableSlotForBeaconCommitteeInTargetEpoch(epoch);
  }

  // ForkChoice utils
  public UInt64 getCurrentSlot(final UInt64 currentTime, final UInt64 genesisTime) {
    return atTime(genesisTime, currentTime)
        .miscHelpers()
        .computeSlotAtTime(genesisTime, currentTime);
  }

  public UInt64 getCurrentSlotFromTimeMillis(
      final UInt64 currentTimeMillis, final UInt64 genesisTimeMillis) {
    return atTimeMillis(genesisTimeMillis, currentTimeMillis)
        .miscHelpers()
        .computeSlotAtTimeMillis(genesisTimeMillis, currentTimeMillis);
  }

  public UInt64 getCurrentSlot(final ReadOnlyStore store) {
    return atTime(store.getGenesisTime(), store.getTimeSeconds())
        .miscHelpers()
        .computeSlotAtTime(store.getGenesisTime(), store.getTimeSeconds());
  }

  public UInt64 getCurrentEpoch(final ReadOnlyStore store) {
    return computeEpochAtSlot(getCurrentSlot(store));
  }

  public Optional<Bytes32> getAncestor(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy, final Bytes32 root, final UInt64 slot) {
    return forGetAncestor(forkChoiceStrategy, root, slot)
        .getForkChoiceUtil()
        .getAncestor(forkChoiceStrategy, root, slot);
  }

  public NavigableMap<UInt64, Bytes32> getAncestors(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 startSlot,
      final UInt64 step,
      final UInt64 count) {
    return forGetAncestor(forkChoiceStrategy, root, startSlot)
        .getForkChoiceUtil()
        .getAncestors(forkChoiceStrategy, root, startSlot, step, count);
  }

  public NavigableMap<UInt64, Bytes32> getAncestorsOnFork(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 startSlot) {
    return forGetAncestor(forkChoiceStrategy, root, startSlot)
        .getForkChoiceUtil()
        .getAncestorsOnFork(forkChoiceStrategy, root, startSlot);
  }

  private SpecVersion forGetAncestor(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 root,
      final UInt64 startSlot) {
    return atSlot(forkChoiceStrategy.blockSlot(root).orElse(startSlot));
  }

  public void onTick(final MutableStore store, final UInt64 timeMillis) {
    atTimeMillis(store.getGenesisTimeMillis(), timeMillis)
        .getForkChoiceUtil()
        .onTick(store, timeMillis);
  }

  public SafeFuture<AttestationProcessingResult> validateAttestationAsync(
      final ReadOnlyStore store,
      final ValidatableAttestation validatableAttestation,
      final Optional<BeaconState> maybeState,
      final AsyncBLSSignatureVerifier asyncSignatureVerifier) {
    final UInt64 slot = validatableAttestation.getAttestation().getData().getSlot();
    final Fork fork = forkSchedule.getFork(computeEpochAtSlot(slot));
    return atSlot(slot)
        .getForkChoiceUtil()
        .validateAsync(fork, store, validatableAttestation, maybeState, asyncSignatureVerifier);
  }

  /**
   * Dispatched on the state's fork rather than an epoch taken from the slashing, so that pool and
   * gossip validation apply the same milestone's rules that block processing later will. The slots
   * inside a slashing are attacker controlled and unbounded, and from Gloas the attesting indices
   * bound is enforced in the validation logic rather than by the SSZ schema - so dispatching on
   * them would let a slashing naming a pre-Gloas epoch skip that bound.
   */
  public Optional<OperationInvalidReason> validateAttesterSlashing(
      final BeaconState state, final AttesterSlashing attesterSlashing) {
    return atState(state)
        .getOperationValidator()
        .validateAttesterSlashing(state.getFork(), state, attesterSlashing);
  }

  public Optional<OperationInvalidReason> validateProposerSlashing(
      final BeaconState state, final ProposerSlashing proposerSlashing) {
    return atState(state)
        .getOperationValidator()
        .validateProposerSlashing(state.getFork(), state, proposerSlashing);
  }

  public Optional<OperationInvalidReason> validateVoluntaryExit(
      final BeaconState state, final SignedVoluntaryExit signedExit) {
    return atState(state)
        .getOperationValidator()
        .validateVoluntaryExit(state.getFork(), state, signedExit);
  }

  public Optional<OperationInvalidReason> validateBlsToExecutionChange(
      final BeaconState state,
      final UInt64 currentTime,
      final BlsToExecutionChange blsToExecutionChange) {
    return atTime(state.getGenesisTime(), currentTime)
        .getOperationValidator()
        .validateBlsToExecutionChange(state.getFork(), state, blsToExecutionChange);
  }

  public boolean verifyBlsToExecutionChangeSignature(
      final BeaconState state,
      final SignedBlsToExecutionChange signedBlsToExecutionChange,
      final BLSSignatureVerifier signatureVerifier) {
    return atState(state)
        .operationSignatureVerifier()
        .verifyBlsToExecutionChangeSignature(state, signedBlsToExecutionChange, signatureVerifier);
  }

  public boolean isBlockProcessorOptimistic(final UInt64 slot) {
    return atSlot(slot).getBlockProcessor().isOptimistic();
  }

  public boolean blockDescendsFromLatestFinalizedBlock(
      final UInt64 blockSlot,
      final Bytes32 blockParentRoot,
      final ReadOnlyStore store,
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy) {
    return atSlot(blockSlot)
        .getForkChoiceUtil()
        .blockDescendsFromLatestFinalizedBlock(
            blockSlot, blockParentRoot, store, forkChoiceStrategy);
  }

  public BeaconState processSlots(final BeaconState preState, final UInt64 slot)
      throws SlotProcessingException, EpochProcessingException {
    return stateTransition.processSlots(preState, slot);
  }

  // Block Proposal
  public SafeFuture<BeaconBlockAndState> createNewUnsignedBlock(
      final UInt64 proposalSlot,
      final int proposerIndex,
      final BeaconState blockSlotState,
      final Bytes32 parentBlockSigningRoot,
      final Function<BeaconBlockBodyBuilder, SafeFuture<Void>> bodyBuilder,
      final BlockProductionPerformance blockProductionPerformance) {
    return atSlot(proposalSlot)
        .getBlockProposalUtil()
        .createNewUnsignedBlock(
            proposalSlot,
            proposerIndex,
            blockSlotState,
            parentBlockSigningRoot,
            bodyBuilder,
            blockProductionPerformance);
  }

  // Execution Payload Proposal
  public SafeFuture<ExecutionPayloadEnvelope> createNewUnsignedExecutionPayload(
      final UInt64 proposalSlot,
      final BeaconBlockAndState blockAndState,
      final SafeFuture<ExecutionPayloadProposalData> executionPayloadProposalDataFuture) {
    return atSlot(proposalSlot)
        .getExecutionPayloadProposalUtil()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to use execution payload proposal util when spec does not have execution payload proposal util"))
        .createNewUnsignedExecutionPayload(blockAndState, executionPayloadProposalDataFuture);
  }

  // Blind Block Utils

  public SafeFuture<Optional<SignedBeaconBlock>> unblindSignedBeaconBlock(
      final SignedBeaconBlock signedBlindedBeaconBlock,
      final Consumer<SignedBeaconBlockUnblinder> beaconBlockUnblinderConsumer) {
    return atSlot(signedBlindedBeaconBlock.getSlot())
        .getBlindBlockUtil()
        .map(
            converter ->
                converter.unblindSignedBeaconBlock(
                    signedBlindedBeaconBlock, beaconBlockUnblinderConsumer))
        .orElseGet(
            () -> {
              // this shouldn't happen: BlockFactory should skip unblinding when is not needed
              checkState(
                  !signedBlindedBeaconBlock.isBlinded(),
                  "Unblinder not available for the current spec but the given block was blinded");
              return SafeFuture.completedFuture(Optional.of(signedBlindedBeaconBlock));
            });
  }

  public SignedBeaconBlock blindSignedBeaconBlock(
      final SignedBeaconBlock unblindedSignedBeaconBlock) {
    return atSlot(unblindedSignedBeaconBlock.getSlot())
        .getBlindBlockUtil()
        .map(converter -> converter.blindSignedBeaconBlock(unblindedSignedBeaconBlock))
        .orElseGet(
            () -> {
              // this shouldn't happen: BlockFactory should skip blinding when is not needed
              checkState(
                  unblindedSignedBeaconBlock.getMessage().getBody().isBlinded(),
                  "Blinder not available for the current spec but the given block was unblinded");
              return unblindedSignedBeaconBlock;
            });
  }

  public Optional<List<Withdrawal>> getExpectedWithdrawals(final BeaconState state) {
    return atState(state)
        .getWithdrawalsHelpers()
        .map(withdrawalsHelpers -> withdrawalsHelpers.getExpectedWithdrawals(state).withdrawals());
  }

  /**
   * Returns the withdrawals to include in Engine API payload attributes for the supplied
   * post-slot-processing state.
   *
   * <p>For pre-GLOAS parents this is the state's expected withdrawals. For a GLOAS parent with an
   * empty payload, this is the state's payload_expected_withdrawals. For a GLOAS parent with a full
   * payload, the parent execution requests must be applied first so the withdrawals reflect the
   * effective state used for payload building.
   */
  public Optional<List<Withdrawal>> getPayloadAttributeWithdrawals(
      final BeaconState state,
      final ForkChoicePayloadStatus parentPayloadStatus,
      final Optional<ExecutionRequests> parentExecutionRequests) {
    return switch (parentPayloadStatus) {
      case PAYLOAD_STATUS_PENDING -> getExpectedWithdrawals(state);
      case PAYLOAD_STATUS_EMPTY ->
          Optional.of(BeaconStateGloas.required(state).getPayloadExpectedWithdrawals().asList());
      case PAYLOAD_STATUS_FULL -> {
        final ExecutionRequests executionRequests =
            parentExecutionRequests.orElseThrow(
                () ->
                    new IllegalStateException(
                        "Parent execution requests are required for GLOAS FULL parent payload attributes"));
        final ForkChoiceUtilGloas forkChoiceUtilGloas =
            ForkChoiceUtilGloas.required(atState(state).getForkChoiceUtil());
        yield Optional.of(
            forkChoiceUtilGloas.getPayloadAttributeWithdrawals(state, executionRequests).asList());
      }
    };
  }

  // Block Processor Utils

  public BlockProcessor getBlockProcessor(final UInt64 slot) {
    return atSlot(slot).getBlockProcessor();
  }

  public BeaconState processBlock(
      final BeaconState preState,
      final SignedBeaconBlock block,
      final BLSSignatureVerifier signatureVerifier,
      final Optional<OptimisticExecutionPayloadExecutor> payloadExecutor)
      throws StateTransitionException {
    try {
      final BeaconState blockSlotState = stateTransition.processSlots(preState, block.getSlot());
      return getBlockProcessor(block.getSlot())
          .processAndValidateBlock(
              block,
              blockSlotState,
              IndexedAttestationCache.NOOP,
              signatureVerifier,
              payloadExecutor);
    } catch (SlotProcessingException | EpochProcessingException e) {
      throw new StateTransitionException(e);
    }
  }

  public BeaconState replayValidatedBlock(final BeaconState preState, final SignedBeaconBlock block)
      throws StateTransitionException {
    try {
      final BeaconState blockSlotState = stateTransition.processSlots(preState, block.getSlot());
      return getBlockProcessor(block.getSlot())
          .processUnsignedBlock(
              blockSlotState,
              block.getMessage(),
              IndexedAttestationCache.NOOP,
              BLSSignatureVerifier.NOOP,
              Optional.empty());
    } catch (SlotProcessingException | EpochProcessingException | BlockProcessingException e) {
      throw new StateTransitionException(e);
    }
  }

  public BlockCheckpoints calculateBlockCheckpoints(final BeaconState state) {
    return atState(state).getEpochProcessor().calculateBlockCheckpoints(state);
  }

  @CheckReturnValue
  public Optional<OperationInvalidReason> validateAttestation(
      final BeaconState state, final AttestationData data) {
    return atState(state).getBlockProcessor().validateAttestation(state, data);
  }

  public UInt64 getSyncCommitteeParticipantReward(final BeaconState state) {
    final BeaconStateAltair beaconStateAltair = BeaconStateAltair.required(state);
    return atState(state).getBlockProcessor().computeParticipantReward(beaconStateAltair);
  }

  public boolean isEnoughVotesToUpdateEth1Data(
      final BeaconState state, final Eth1Data eth1Data, final int additionalVotes) {
    final BlockProcessor blockProcessor = atState(state).getBlockProcessor();
    final long existingVotes = blockProcessor.getVoteCount(state, eth1Data);
    return blockProcessor.isEnoughVotesToUpdateEth1Data(existingVotes + additionalVotes);
  }

  public UInt64 getMaxLookaheadEpoch(final BeaconState state) {
    return atState(state).beaconStateAccessors().getMaxLookaheadEpoch(state);
  }

  public IntList getActiveValidatorIndices(final BeaconState state, final UInt64 epoch) {
    return atEpoch(epoch).beaconStateAccessors().getActiveValidatorIndices(state, epoch);
  }

  public UInt64 getTotalActiveBalance(final BeaconState state) {
    return atState(state).beaconStateAccessors().getTotalActiveBalance(state);
  }

  public UInt64 getProposerBoostAmount(final BeaconState state) {
    return atState(state).beaconStateAccessors().getProposerBoostAmount(state);
  }

  public int getPreviousEpochAttestationCapacity(final BeaconState state) {
    return atState(state).beaconStateAccessors().getPreviousEpochAttestationCapacity(state);
  }

  public IntList getBeaconCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 index) {
    return atState(state).beaconStateAccessors().getBeaconCommittee(state, slot, index);
  }

  public Int2IntMap getBeaconCommitteesSize(final BeaconState state, final UInt64 slot) {
    return atState(state).beaconStateAccessors().getBeaconCommitteesSize(state, slot);
  }

  public Optional<BLSPublicKey> getValidatorPubKey(
      final BeaconState state, final UInt64 validatorIndex) {
    return atState(state).beaconStateAccessors().getValidatorPubKey(state, validatorIndex);
  }

  // Execution Requests Processor Utils

  public ExecutionRequestsProcessor getExecutionRequestsProcessor(final UInt64 slot) {
    return atSlot(slot)
        .getExecutionRequestsProcessor()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to use execution requests processor when spec does not have execution requests processor"));
  }

  public ExecutionRequestsDataCodec getExecutionRequestsDataCodec(final UInt64 slot) {
    return atSlot(slot)
        .getExecutionRequestsDataCodec()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to use execution requests data codec when spec does not have execution requests data codec"));
  }

  // Data Column Sidecar Util

  public DataColumnSidecarUtil getDataColumnSidecarUtil(final UInt64 slot) {
    return atSlot(slot)
        .getDataColumnSidecarUtil()
        .orElseThrow(
            () -> new IllegalStateException("DataColumnSidecarUtil not available at slot " + slot));
  }

  // Proposer Preferences Util

  public ProposerPreferencesUtil getProposerPreferencesUtil(final UInt64 epoch) {
    return atEpoch(epoch).getProposerPreferencesUtil();
  }

  // Execution Payload Verifier Utils

  public ExecutionPayloadVerifier getExecutionPayloadVerifier(final UInt64 slot) {
    return atSlot(slot)
        .getExecutionPayloadVerifier()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Attempting to use execution payload verifier when spec does not have execution payload verifier"));
  }

  // Validator Utils
  public int countActiveValidators(final BeaconState state, final UInt64 epoch) {
    return getActiveValidatorIndices(state, epoch).size();
  }

  public Optional<Integer> getValidatorIndex(
      final BeaconState state, final BLSPublicKey publicKey) {
    return atState(state).getValidatorsUtil().getValidatorIndex(state, publicKey);
  }

  public Optional<CommitteeAssignment> getCommitteeAssignment(
      final BeaconState state, final UInt64 epoch, final int validatorIndex) {
    return atEpoch(epoch).getValidatorsUtil().getCommitteeAssignment(state, epoch, validatorIndex);
  }

  public Int2ObjectMap<CommitteeAssignment> getValidatorIndexToCommitteeAssignmentMap(
      final BeaconState state, final UInt64 epoch) {
    return atEpoch(epoch)
        .getValidatorsUtil()
        .getValidatorIndexToCommitteeAssignmentMap(state, epoch);
  }

  // get_ptc_assignment
  public Optional<UInt64> getPtcAssignment(
      final BeaconState state, final UInt64 epoch, final int validatorIndex) {
    return atEpoch(epoch).getValidatorsUtil().getPtcAssignment(state, epoch, validatorIndex);
  }

  public Int2ObjectMap<UInt64> getValidatorIndexToPtcAssignmentMap(
      final BeaconState state, final UInt64 epoch) {
    return atEpoch(epoch).getValidatorsUtil().getValidatorIndexToPtcAssignmentMap(state, epoch);
  }

  // get_ptc
  public IntList getPtc(final BeaconState state, final UInt64 slot) {
    return atSlot(slot).beaconStateAccessors().getPtc(state, slot);
  }

  // Builder Utils
  public Optional<BLSPublicKey> getBuilderPubKey(
      final BeaconState state, final UInt64 builderIndex) {
    return atState(state).beaconStateAccessors().getBuilderPubKey(state, builderIndex);
  }

  public Optional<Integer> getBuilderIndex(final BeaconState state, final BLSPublicKey publicKey) {
    return atState(state).beaconStateAccessors().getBuilderIndex(state, publicKey);
  }

  // Attestation helpers
  public BeaconState getStateForAttestationRewardCalculation(
      final BeaconState state, final boolean parentPayloadAvailable) {
    return atState(state)
        .getAttestationUtil()
        .getStateForAttestationRewardCalculation(state, parentPayloadAvailable);
  }

  public List<UInt64> getAttestingIndices(final BeaconState state, final Attestation attestation) {
    return atSlot(attestation.getData().getSlot())
        .getAttestationUtil()
        .getAttestingIndices(state, attestation);
  }

  public AttestationData getGenericAttestationData(
      final UInt64 slot,
      final BeaconState state,
      final BeaconBlockSummary block,
      final UInt64 committeeIndex) {
    return atSlot(slot)
        .getAttestationUtil()
        .getGenericAttestationData(slot, state, block, committeeIndex);
  }

  public SafeFuture<AttestationProcessingResult> isValidIndexedAttestation(
      final BeaconState state,
      final ValidatableAttestation attestation,
      final AsyncBLSSignatureVerifier blsSignatureVerifier) {
    final UInt64 slot = attestation.getData().getSlot();
    return atSlot(slot)
        .getAttestationUtil()
        .isValidIndexedAttestationAsync(
            getForkAtSlot(slot), state, attestation, blsSignatureVerifier);
  }

  public boolean isMergeTransitionComplete(final BeaconState state) {
    return atState(state).miscHelpers().isMergeTransitionComplete(state);
  }

  // Deneb Utils
  public boolean isAvailabilityOfBlobSidecarsRequiredAtSlot(
      final ReadOnlyStore store, final UInt64 slot) {
    return isAvailabilityOfBlobSidecarsRequiredAtEpoch(store, computeEpochAtSlot(slot));
  }

  public boolean isAvailabilityOfBlobSidecarsRequiredAtEpoch(
      final ReadOnlyStore store, final UInt64 epoch) {
    return atEpoch(epoch)
        .miscHelpers()
        .toVersionDeneb()
        .map(
            denebMiscHelpers ->
                denebMiscHelpers.isAvailabilityOfBlobSidecarsRequiredAtEpoch(
                    getCurrentEpoch(store), epoch))
        .orElse(false);
  }

  public UInt64 blobSidecarsDeprecationSlot() {
    return getSpecConfigFulu()
        .map(maybeConfig -> computeStartSlotAtEpoch(maybeConfig.getFuluForkEpoch()))
        .orElse(UInt64.MAX_VALUE);
  }

  /**
   * This method is used to setup caches and limits during the initialization of the node. We
   * normally increase the blobs with each fork, but in case we will decrease them, let's consider
   * the last two forks.
   */
  public Optional<Integer> getMaxBlobsPerBlockForHighestMilestone() {
    final SpecMilestone highestSupportedMilestone =
        getForkSchedule().getHighestSupportedMilestone();

    // query the blob_schedule after FULU
    if (highestSupportedMilestone.isGreaterThanOrEqualTo(FULU)) {
      final Optional<Integer> maybeHighestMaxBlobsPerBlockFromBpoForkSchedule =
          forMilestone(FULU)
              .miscHelpers()
              .toVersionFulu()
              .flatMap(MiscHelpersFulu::getHighestMaxBlobsPerBlockFromBpoForkSchedule);
      // only use BPO fork max_blobs_per_block if it is present
      if (maybeHighestMaxBlobsPerBlockFromBpoForkSchedule.isPresent()) {
        return maybeHighestMaxBlobsPerBlockFromBpoForkSchedule;
      }
    }

    final Optional<Integer> maybeHighestMaxBlobsPerBlock =
        forMilestone(highestSupportedMilestone)
            .getConfig()
            .toVersionDeneb()
            .map(SpecConfigDeneb::getMaxBlobsPerBlock);

    final Optional<Integer> maybeSecondHighestMaxBlobsPerBlock =
        highestSupportedMilestone
            .getPreviousMilestoneIfExists()
            .map(this::forMilestone)
            .map(SpecVersion::getConfig)
            .flatMap(SpecConfig::toVersionDeneb)
            .map(SpecConfigDeneb::getMaxBlobsPerBlock);

    if (maybeHighestMaxBlobsPerBlock.isEmpty() && maybeSecondHighestMaxBlobsPerBlock.isEmpty()) {
      return Optional.empty();
    }

    final int highestMaxBlobsPerBlock = maybeHighestMaxBlobsPerBlock.orElse(0);
    final int secondHighestMaxBlobsPerBlock = maybeSecondHighestMaxBlobsPerBlock.orElse(0);
    final int max = Math.max(highestMaxBlobsPerBlock, secondHighestMaxBlobsPerBlock);

    return Optional.of(max);
  }

  public UInt64 computeSubnetForBlobSidecar(final BlobSidecar blobSidecar) {
    return blobSidecar
        .getIndex()
        .mod(
            SpecConfigDeneb.required(atSlot(blobSidecar.getSlot()).getConfig())
                .getBlobSidecarSubnetCount());
  }

  public Optional<Integer> getNumberOfDataColumns() {
    return getSpecConfigFulu().map(SpecConfigFulu::getNumberOfColumns);
  }

  public Optional<Integer> getNumberOfDataColumnSubnets() {
    return getSpecConfigFulu().map(SpecConfigFulu::getDataColumnSidecarSubnetCount);
  }

  public Optional<Integer> getNumberOfCustodyGroups() {
    return getSpecConfigFulu().map(SpecConfigFulu::getNumberOfCustodyGroups);
  }

  public int getNumberOfCustodyGroups(final UInt64 slot) {
    return SpecConfigFulu.required(atSlot(slot).getConfig()).getNumberOfCustodyGroups();
  }

  public boolean isAvailabilityOfDataColumnSidecarsRequiredAtSlot(
      final ReadOnlyStore store, final UInt64 slot) {
    return isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(store, computeEpochAtSlot(slot));
  }

  public boolean isAvailabilityOfDataColumnSidecarsRequiredAtEpoch(
      final ReadOnlyStore store, final UInt64 epoch) {
    final Optional<SpecConfigFulu> maybeSpecConfigFulu = getSpecConfigFulu();
    if (maybeSpecConfigFulu.isEmpty()) {
      return false;
    }
    final SpecConfigFulu specConfigFulu = maybeSpecConfigFulu.get();
    if (epoch.isLessThan(specConfigFulu.getFuluForkEpoch())) {
      return false;
    }
    return getCurrentEpoch(store)
        .minusMinZero(epoch)
        .isLessThanOrEqualTo(specConfigFulu.getMinEpochsForDataColumnSidecarsRequests());
  }

  public Optional<UInt64> computeFirstSlotWithBlobSupport() {
    return getSpecConfigDeneb()
        .map(SpecConfigDeneb::getDenebForkEpoch)
        .map(this::computeStartSlotAtEpoch);
  }

  public Optional<UInt64> computeFirstSlotWithDataColumnSidecarSupport() {
    if (!supportsDataColumnSidecars()) {
      return Optional.empty();
    }
    return getSpecConfigFulu()
        .map(SpecConfigFulu::getFuluForkEpoch)
        .map(this::computeStartSlotAtEpoch);
  }

  // Gloas Utils
  public boolean isExecutionPayloadEnvelopeAvailableAtSlot(final UInt64 slot) {
    return atSlot(slot)
        .miscHelpers()
        .toVersionGloas()
        .map(MiscHelpersGloas::isExecutionPayloadEnvelopeAvailable)
        .orElse(false);
  }

  // Electra Utils
  public boolean isFormerDepositMechanismDisabled(final BeaconState state) {
    return atState(state).miscHelpers().isFormerDepositMechanismDisabled(state);
  }

  // Gloas Utils
  public boolean isProposerPreferencesAvailableAtEpoch(final UInt64 epoch) {
    return atEpoch(epoch).miscHelpers().toVersionGloas().isPresent();
  }

  /**
   * EIP-8261: the gas limit scheduled for the given epoch, empty when the epoch is pre-Gloas or the
   * network has no gas limit schedule defined for it.
   */
  public Optional<UInt64> getScheduledGasLimit(final UInt64 epoch) {
    return atEpoch(epoch)
        .miscHelpers()
        .toVersionGloas()
        .flatMap(miscHelpers -> miscHelpers.getScheduledGasLimit(epoch));
  }

  // Deneb private helpers
  private Optional<SpecConfigDeneb> getSpecConfigDeneb() {
    final SpecMilestone highestSupportedMilestone =
        getForkSchedule().getHighestSupportedMilestone();
    return Optional.ofNullable(forMilestone(highestSupportedMilestone))
        .map(SpecVersion::getConfig)
        .flatMap(SpecConfig::toVersionDeneb);
  }

  // Fulu private helpers
  private Optional<SpecConfigFulu> getSpecConfigFulu() {
    final SpecMilestone highestSupportedMilestone =
        getForkSchedule().getHighestSupportedMilestone();
    return Optional.ofNullable(forMilestone(highestSupportedMilestone))
        .map(SpecVersion::getConfig)
        .flatMap(SpecConfig::toVersionFulu);
  }

  // Private helpers
  private SpecVersion atState(final BeaconState state) {
    return atSlot(state.getSlot());
  }

  private SpecVersion atBlock(final BeaconBlockSummary blockSummary) {
    return atSlot(blockSummary.getSlot());
  }

  private SpecVersion atForkVersion(final Bytes4 forkVersion) {
    final SpecMilestone milestone =
        forkSchedule
            .getSpecMilestoneAtForkVersion(forkVersion)
            .orElseThrow(
                () -> new IllegalArgumentException("Unknown fork version: " + forkVersion));

    return forMilestone(milestone);
  }

  private Fork getForkAtSlot(final UInt64 slot) {
    return forkSchedule.getFork(computeEpochAtSlot(slot));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Spec spec = (Spec) o;
    return Objects.equals(forkSchedule, spec.forkSchedule);
  }

  @Override
  public int hashCode() {
    return Objects.hash(forkSchedule);
  }

  public Optional<Integer> getMaxBlobsPerBlockAtSlot(final UInt64 slot) {
    final SpecVersion specVersion = atSlot(slot);
    return switch (specVersion.getMilestone()) {
      case PHASE0, ALTAIR, BELLATRIX, CAPELLA -> Optional.empty();
      case DENEB, ELECTRA ->
          Optional.of(SpecConfigDeneb.required(specVersion.getConfig()).getMaxBlobsPerBlock());
      case FULU, GLOAS, HEZE -> {
        final UInt64 epoch = specVersion.miscHelpers().computeEpochAtSlot(slot);
        yield Optional.of(
            MiscHelpersFulu.required(specVersion.miscHelpers())
                .getBlobParameters(epoch)
                .maxBlobsPerBlock());
      }
    };
  }
}
