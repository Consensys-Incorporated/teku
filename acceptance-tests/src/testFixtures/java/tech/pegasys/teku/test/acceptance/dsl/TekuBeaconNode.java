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

package tech.pegasys.teku.test.acceptance.dsl;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withLabelValueSubstring;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withNameEqualsTo;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withValueGreaterThan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.ThrowingConsumer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.ethereum.json.types.SharedApiTypes;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessageSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.generator.BlsToExecutionChangeGenerator;
import tech.pegasys.teku.spec.logic.versions.capella.block.BlockProcessorCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.test.acceptance.dsl.Eth2EventHandler.PackedMessage;
import tech.pegasys.teku.test.acceptance.dsl.tools.deposits.ValidatorKeystores;

public class TekuBeaconNode extends TekuNode {

  private static final Logger LOG = LogManager.getLogger();
  public static final String LOCAL_VALIDATOR_LIVENESS_URL = "/eth/v1/validator/liveness/{epoch}";
  public static final String POST_PROPOSER_SLASHING_URL = "/eth/v1/beacon/pool/proposer_slashings";
  public static final String POST_ATTESTER_SLASHING_URL = "/eth/v1/beacon/pool/attester_slashings";
  public static final String PUT_LOG_LEVEL_URL = "/teku/v1/admin/log_level";
  private final TekuNodeConfig config;
  private final Spec spec;

  private TekuBeaconNode(
      final Network network, final TekuDockerVersion version, final TekuNodeConfig tekuNodeConfig) {
    super(network, TEKU_DOCKER_IMAGE_NAME, version, LOG);
    this.config = tekuNodeConfig;
    if (config.getConfigFileMap().containsValue(NETWORK_FILE_PATH)) {
      final String tmpConfigFilePath =
          config.getConfigFileMap().entrySet().stream()
              .filter(entry -> entry.getValue().equals(NETWORK_FILE_PATH))
              .findFirst()
              .orElseThrow()
              .getKey()
              .getAbsolutePath();
      this.spec = SpecFactory.create(tmpConfigFilePath, true, config.getSpecConfigModifier());
    } else {
      this.spec = SpecFactory.create(config.getNetworkName(), true, config.getSpecConfigModifier());
    }
    if (config.getConfigMap().containsKey("validator-api-enabled")) {
      container.addExposedPort(VALIDATOR_API_PORT);
    }

    container.addExposedPorts(METRICS_PORT, REST_API_PORT);
    container
        .withWorkingDirectory(WORKING_DIRECTORY)
        .waitingFor(
            new HttpWaitStrategy()
                .forPort(REST_API_PORT)
                .forPath("/eth/v1/node/identity")
                .withStartupTimeout(Duration.ofMinutes(2)))
        .withCommand("--config-file", CONFIG_FILE_PATH);
  }

  public static TekuBeaconNode create(
      final Network network, final TekuDockerVersion version, final TekuNodeConfig tekuNodeConfig) {
    return new TekuBeaconNode(network, version, tekuNodeConfig);
  }

  public Spec getSpec() {
    return spec;
  }

  public void waitForContributionAndProofEvent() {
    waitFor(
        () ->
            assertThat(countContributionAndProofEvents() > 0L)
                .describedAs("Did not find contribution and proof events"));
  }

  private long countContributionAndProofEvents() {
    return maybeEventStreamListener
        .map(
            eventStreamListener ->
                eventStreamListener.getMessages().stream()
                    .filter(
                        packedMessage ->
                            packedMessage
                                .getEvent()
                                .equals(EventType.contribution_and_proof.name()))
                    .count())
        .orElse(0L);
  }

  /**
   * Returns the most recent {@code fast_confirmation} SSE event, or empty if none received yet.
   * Requires {@code startEventListener(EventType.fast_confirmation)} to have been called.
   */
  public Optional<FastConfirmationEventData> getLatestFastConfirmationEvent() throws IOException {
    final Optional<Eth2EventHandler.PackedMessage> maybeMessage =
        maybeEventStreamListener.flatMap(
            listener ->
                listener.getMessages().stream()
                    .filter(
                        packedMessage ->
                            packedMessage.getEvent().equals(EventType.fast_confirmation.name()))
                    .reduce((ignored, latest) -> latest));
    if (maybeMessage.isEmpty()) {
      return Optional.empty();
    }
    final String rawData = maybeMessage.get().getMessageEvent().getData();
    final JsonNode data = OBJECT_MAPPER.readTree(rawData);
    return Optional.of(
        new FastConfirmationEventData(
            Bytes32.fromHexString(getRequiredTextField(data, "block", rawData)),
            UInt64.valueOf(getRequiredTextField(data, "slot", rawData)),
            UInt64.valueOf(getRequiredTextField(data, "current_slot", rawData))));
  }

  private static String getRequiredTextField(
      final JsonNode data, final String fieldName, final String rawData) {
    final JsonNode field = data.get(fieldName);
    if (field == null || field.isNull() || field.asText().isEmpty()) {
      throw new IllegalStateException(
          "fast_confirmation event is missing field '" + fieldName + "': " + rawData);
    }
    return field.asText();
  }

  public record FastConfirmationEventData(Bytes32 block, UInt64 slot, UInt64 currentSlot) {}

  public void waitForGenesis() {
    LOG.debug("Wait for genesis");
    waitFor(this::fetchGenesisTime);
  }

  public void waitForBlockAtOrAfterSlot(final long slot) {
    if (maybeEventStreamListener.isEmpty()) {
      startEventListener(EventType.head);
    }
    waitFor(
        () ->
            assertThat(
                    getSlotsFromHeadEvents().stream()
                        .filter(v -> v.isGreaterThanOrEqualTo(slot))
                        .count())
                .isGreaterThan(0));
  }

  private List<UInt64> getSlotsFromHeadEvents() {
    return maybeEventStreamListener.orElseThrow().getMessages().stream()
        .filter(packedMessage -> packedMessage.getEvent().equals(EventType.head.name()))
        .map(this::getSlotFromHeadEvent)
        .flatMap(Optional::stream)
        .collect(toList());
  }

  private Optional<UInt64> getSlotFromHeadEvent(
      final Eth2EventHandler.PackedMessage packedMessage) {
    try {
      final JsonNode node = OBJECT_MAPPER.readTree(packedMessage.getMessageEvent().getData());
      final UInt64 slot = UInt64.valueOf(node.get("slot").asText());
      return Optional.of(slot);
    } catch (JsonProcessingException e) {
      LOG.error("Failed to process head event", e);
      return Optional.empty();
    }
  }

  public void checkValidatorLiveness(
      final int epoch, final int totalValidatorCount, final ValidatorLivenessExpectation... args)
      throws IOException {
    final List<UInt64> validators = new ArrayList<>();
    for (UInt64 i = UInt64.ZERO; i.isLessThan(totalValidatorCount); i = i.increment()) {
      validators.add(i);
    }
    final Object2BooleanMap<UInt64> data =
        getValidatorLivenessAtEpoch(UInt64.valueOf(epoch), validators);
    for (ValidatorLivenessExpectation expectation : args) {
      expectation.verify(data);
    }
  }

  private Object2BooleanMap<UInt64> getValidatorLivenessAtEpoch(
      final UInt64 epoch, final List<UInt64> validators) throws IOException {

    final String response =
        httpClient.post(
            getRestApiUrl(),
            getValidatorLivenessUrl(epoch),
            OBJECT_MAPPER.writeValueAsString(validators.stream().map(UInt64::toString).toList()));
    final DeserializableTypeDefinition<List<ValidatorLivenessAtEpoch>> type =
        SharedApiTypes.withDataWrapper(
            "listOfLiveness", listOf(ValidatorLivenessAtEpoch.getJsonTypeDefinition()));

    final List<ValidatorLivenessAtEpoch> livenessResponse = JsonUtil.parse(response, type);
    final Object2BooleanMap<UInt64> output = new Object2BooleanOpenHashMap<>();
    for (ValidatorLivenessAtEpoch entry : livenessResponse) {
      output.put(entry.index(), entry.isLive());
    }
    return output;
  }

  public void setLogLevel(final String queryString, final String level) throws IOException {
    httpClient.put(
        getRestApiUrl(),
        PUT_LOG_LEVEL_URL,
        String.format("{\"level\":\"%s\", \"log_filter\":[\"%s\"]}", level, queryString));
  }

  public void postProposerSlashing(
      final UInt64 slot, final UInt64 index, final BLSSecretKey secretKey) throws IOException {
    final ForkInfo forkInfo = getForkInfo(slot);
    final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
    final SignedBeaconBlockHeader header1 =
        randomSignedBeaconBlockHeader(slot, index, secretKey, signingRootUtil, forkInfo);
    final SignedBeaconBlockHeader header2 =
        randomSignedBeaconBlockHeader(slot, index, secretKey, signingRootUtil, forkInfo);
    LOG.debug("Inserting proposer slashing for index {} at slot {}", index, slot);
    final String body =
        JsonUtil.serialize(
            new ProposerSlashing(header1, header2),
            ProposerSlashing.SSZ_SCHEMA.getJsonTypeDefinition());
    httpClient.post(getRestApiUrl(), POST_PROPOSER_SLASHING_URL, body);
  }

  private ForkInfo getForkInfo(final UInt64 slot) throws IOException {
    final Fork fork = spec.getForkSchedule().getFork(spec.computeEpochAtSlot(slot));
    final Bytes32 genesisValidatorRoot = getGenesisValidatorsRoot();
    return new ForkInfo(fork, genesisValidatorRoot);
  }

  public void postAttesterSlashing(
      final UInt64 slashingSlot,
      final UInt64 slashedIndex,
      final BLSSecretKey slashedValidatorSecretKey)
      throws IOException {
    final ForkInfo forkInfo = getForkInfo(slashingSlot);
    final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
    final IndexedAttestation indexedAttestation1 =
        randomIndexedAttestation(
            slashingSlot, slashedIndex, slashedValidatorSecretKey, signingRootUtil, forkInfo);
    final IndexedAttestation indexedAttestation2 =
        randomIndexedAttestation(
            slashingSlot, slashedIndex, slashedValidatorSecretKey, signingRootUtil, forkInfo);
    final AttesterSlashing attesterSlashing =
        spec.getGenesisSchemaDefinitions()
            .getAttesterSlashingSchema()
            .create(indexedAttestation1, indexedAttestation2);
    LOG.debug("Inserting attester slashing for index {} at slot {}", slashedIndex, slashingSlot);
    final String body =
        JsonUtil.serialize(
            attesterSlashing,
            spec.getGenesisSchemaDefinitions().getAttesterSlashingSchema().getJsonTypeDefinition());
    httpClient.post(getRestApiUrl(), POST_ATTESTER_SLASHING_URL, body);
  }

  private IndexedAttestation randomIndexedAttestation(
      final UInt64 slot,
      final UInt64 index,
      final BLSSecretKey secretKey,
      final SigningRootUtil signingRootUtil,
      final ForkInfo forkInfo) {
    final AttestationData attestationData =
        new AttestationData(
            slot,
            index,
            Bytes32.random(),
            new Checkpoint(UInt64.valueOf(1), Bytes32.random()),
            new Checkpoint(UInt64.valueOf(2), Bytes32.random()));

    final BLSSignature blsSignature1 =
        BLS.sign(
            secretKey,
            signingRootUtil.signingRootForSignAttestationData(attestationData, forkInfo));

    final IndexedAttestationSchema schema =
        spec.getGenesisSchemaDefinitions().getIndexedAttestationSchema();
    return schema.create(
        Stream.of(index).collect(schema.getAttestingIndicesSchema().collectorUnboxed()),
        attestationData,
        blsSignature1);
  }

  private SignedBeaconBlockHeader randomSignedBeaconBlockHeader(
      final UInt64 slot,
      final UInt64 index,
      final BLSSecretKey secretKey,
      final SigningRootUtil signingRootUtil,
      final ForkInfo forkInfo) {
    final BeaconBlockHeader beaconBlockHeader =
        new BeaconBlockHeader(slot, index, Bytes32.random(), Bytes32.random(), Bytes32.random());

    final Bytes blockHeaderSigningRoot =
        signingRootUtil.signingRootForSignBlockHeader(beaconBlockHeader, forkInfo);
    return new SignedBeaconBlockHeader(
        beaconBlockHeader, BLS.sign(secretKey, blockHeaderSigningRoot));
  }

  public void submitBlsToExecutionChange(
      final int validatorIndex,
      final BLSKeyPair validatorKeyPair,
      final Eth1Address executionAddress)
      throws Exception {
    final int currentEpoch = getCurrentEpoch();

    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        new BlsToExecutionChangeGenerator(spec, getGenesisValidatorsRoot())
            .createAndSign(
                UInt64.valueOf(validatorIndex),
                validatorKeyPair,
                executionAddress,
                UInt64.valueOf(currentEpoch));

    final DeserializableTypeDefinition<List<SignedBlsToExecutionChange>> jsonTypeDefinition =
        listOf(
            SchemaDefinitionsCapella.required(
                    spec.atEpoch(UInt64.valueOf(currentEpoch)).getSchemaDefinitions())
                .getSignedBlsToExecutionChangeSchema()
                .getJsonTypeDefinition());
    final String body = JsonUtil.serialize(List.of(signedBlsToExecutionChange), jsonTypeDefinition);

    httpClient.post(getRestApiUrl(), "/eth/v1/beacon/pool/bls_to_execution_changes", body);
  }

  public void waitForBlsToExecutionChangeEventForValidator(final int validatorIndex) {
    if (maybeEventStreamListener.isEmpty()) {
      fail(
          "Must start listening to events before waiting for them... Try calling TekuNode.startEventListener(..)!");
    }

    if (!maybeEventStreamListener.get().isReady()) {
      fail(
          "Event stream listener should have been ready, but wasn't! Logs:\n"
              + container.getLogs());
    }

    waitFor(
        () -> {
          final List<Integer> blsToExecutionChanges =
              getEventsOfTypeFromEventStream(
                  EventType.bls_to_execution_change, this::getValidatorIdsFromBlsChange);
          assertThat(blsToExecutionChanges.stream()).anyMatch(m -> validatorIndex == m);
        });
  }

  private JsonNode fetchSyncingStatus() throws IOException {
    final String syncingData = httpClient.get(getRestApiUrl(), "/eth/v1/node/syncing");
    return OBJECT_MAPPER.readTree(syncingData).get("data");
  }

  private boolean getStatusElOffline() throws IOException {
    try {
      return fetchSyncingStatus().get("el_offline").asBoolean();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean getStatusIsSyncing() throws IOException {
    try {
      return fetchSyncingStatus().get("is_syncing").asBoolean();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private <T> List<T> getEventsOfTypeFromEventStream(
      final EventType type, final Function<PackedMessage, T> mapperFn) {
    return maybeEventStreamListener
        .map(
            eventStreamListener ->
                eventStreamListener.getMessages().stream()
                    .filter(packedMessage -> packedMessage.getEvent().equals(type.name()))
                    .map(mapperFn)
                    .collect(toList()))
        .orElseGet(List::of);
  }

  private Integer getValidatorIdsFromBlsChange(final PackedMessage packedMessage) {
    try {
      final JsonNode jsonNode = OBJECT_MAPPER.readTree(packedMessage.getMessageEvent().getData());
      return jsonNode.get("message").get("validator_index").asInt();
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private String getValidatorLivenessUrl(final UInt64 epoch) {
    return LOCAL_VALIDATOR_LIVENESS_URL.replace("{epoch}", epoch.toString());
  }

  public void waitForGenesisTime(final UInt64 expectedGenesisTime) {
    waitFor(() -> assertThat(fetchGenesisTime()).isEqualTo(expectedGenesisTime));
  }

  public Bytes32 getGenesisValidatorsRoot() throws IOException {
    try {
      return Bytes32.fromHexString(fetchGenesis().get("genesis_validators_root").asText());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private JsonNode fetchGenesis() throws IOException {
    final String syncingData = httpClient.get(getRestApiUrl(), "/eth/v1/beacon/genesis");
    return OBJECT_MAPPER.readTree(syncingData).get("data");
  }

  private UInt64 fetchGenesisTime() throws IOException {
    try {
      return UInt64.valueOf(fetchGenesis().get("genesis_time").asText());
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public UInt64 getGenesisTime() throws IOException {
    waitForGenesis();
    return fetchGenesisTime();
  }

  public void waitForNewBlock() {
    final Bytes32 startingBlockRoot = waitForBeaconHead(null);
    waitFor(() -> assertThat(fetchBeaconHeadRoot().orElseThrow()).isNotEqualTo(startingBlockRoot));
  }

  public void waitForExecutionPayload(final Bytes32 beaconBlockRoot) {
    waitFor(() -> assertThat(fetchExecutionPayload(beaconBlockRoot.toHexString())).isPresent());
  }

  public void waitForOptimisticBlock() {
    waitForBeaconHead(true);
  }

  public void waitForNonOptimisticBlock() {
    waitForBeaconHead(false);
  }

  public void waitForNewFinalization() {
    final UInt64 startingFinalizedEpoch = getFinalizedEpoch().orElse(UInt64.ZERO);
    LOG.debug("Wait for finalized block");
    waitFor(
        () ->
            assertThat(getFinalizedEpoch().orElse(UInt64.ZERO))
                .isNotEqualTo(startingFinalizedEpoch),
        9,
        MINUTES);
  }

  public void waitForMilestone(final SpecMilestone expectedMilestone) {
    waitForLogMessageContaining("Activating network upgrade: " + expectedMilestone.name());
  }

  public void waitForNonDefaultExecutionPayload() {
    LOG.debug("Wait for a block containing a non default execution payload");

    waitFor(
        () -> {
          final Optional<SignedBeaconBlock> block = fetchHeadBlock();
          assertThat(block).isPresent();
          verifyExecutionPayloadIsNonDefault(block.get());
        },
        5,
        MINUTES);
  }

  private void verifyExecutionPayloadIsNonDefault(final SignedBeaconBlock signedBlock)
      throws IOException {
    final BeaconBlock block = signedBlock.getMessage();
    final UInt64 slot = block.getSlot();
    final BeaconBlockBody blockBody = block.getBody();
    final Optional<ExecutionPayload> maybeExecutionPayload;
    if (blockBody.getOptionalSignedExecutionPayloadBid().isPresent()) {
      maybeExecutionPayload =
          // we can directly query the execution payload at the head in Gloas
          fetchExecutionPayload("head")
              .map(SignedExecutionPayloadEnvelope::getMessage)
              .map(ExecutionPayloadEnvelope::getPayload);
    } else {
      maybeExecutionPayload = blockBody.getOptionalExecutionPayload();
    }
    assertThat(maybeExecutionPayload).isPresent();
    assertThat(maybeExecutionPayload.get().isDefault()).describedAs("Is default payload").isFalse();
    LOG.debug("Non default execution payload found at slot " + slot);
  }

  public void waitForBlockSatisfying(
      final ThrowingConsumer<? super SignedBeaconBlock> assertions,
      final int timeout,
      final TimeUnit timeUnit) {
    LOG.debug("Wait for a block satisfying certain assertions");

    waitFor(
        () -> {
          final Optional<SignedBeaconBlock> block = fetchHeadBlock();
          assertThat(block).isPresent();
          assertThat(block.get()).satisfies(assertions);
        },
        timeout,
        timeUnit);
  }

  public void waitForBlockSatisfying(final ThrowingConsumer<? super SignedBeaconBlock> assertions) {
    waitForBlockSatisfying(assertions, 1, MINUTES);
  }

  public void waitForGenesisWithNonDefaultExecutionPayload() {
    LOG.debug("Wait for genesis block containing a non default execution payload");

    waitFor(
        () -> {
          final Optional<BeaconState> maybeState = fetchState("genesis");
          assertThat(maybeState).isPresent();
          assertThat(maybeState.get().toVersionBellatrix()).isPresent();
          final BeaconStateBellatrix genesisState =
              maybeState.get().toVersionBellatrix().orElseThrow();
          assertThat(
                  genesisState
                      .getLatestExecutionPayloadHeader()
                      .map(ExecutionPayloadHeader::isDefault)
                      // >= Gloas
                      .orElse(false))
              .describedAs("Is latest execution payload header a default payload header")
              .isFalse();
        });
  }

  public void waitForFullSyncCommitteeAggregate() {
    LOG.debug("Wait for full sync committee aggregates");
    waitFor(
        () -> {
          final Optional<SignedBeaconBlock> maybeBlock = fetchHeadBlock();
          assertThat(maybeBlock).isPresent();
          final SignedBeaconBlock block = maybeBlock.get();

          final int syncCommitteeSize = spec.getSyncCommitteeSize(block.getMessage().getSlot());

          final SszBitvector syncCommitteeBits =
              block
                  .getMessage()
                  .getBody()
                  .getOptionalSyncAggregate()
                  .orElseThrow()
                  .getSyncCommitteeBits();
          final int actualSyncBitCount = syncCommitteeBits.getBitCount();
          final double percentageOfBitsSet =
              actualSyncBitCount == syncCommitteeSize
                  ? 1.0
                  : actualSyncBitCount / (double) syncCommitteeSize;
          assertThat(percentageOfBitsSet >= 1.0)
              .overridingErrorMessage(
                  "Sync committee bits are only %s%% full, expecting %s%%: %s",
                  percentageOfBitsSet * 100, 100, syncCommitteeBits)
              .isTrue();
        },
        5,
        MINUTES);
  }

  public void waitUntilInSyncWith(final TekuBeaconNode targetNode) {
    LOG.debug("Wait for {} to sync to {}", nodeAlias, targetNode.nodeAlias);
    waitFor(
        () -> {
          final Optional<Bytes32> beaconHead = fetchBeaconHeadRoot();
          assertThat(beaconHead).isPresent();
          final Optional<Bytes32> targetBeaconHead = targetNode.fetchBeaconHeadRoot();
          assertThat(targetBeaconHead).isPresent();
          assertThat(beaconHead).isEqualTo(targetBeaconHead);
        },
        5,
        MINUTES);
  }

  private Bytes32 waitForBeaconHead(final Boolean optimistic) {
    LOG.debug("Waiting for beacon head");
    final AtomicReference<Bytes32> beaconHead = new AtomicReference<>(null);
    waitFor(
        () -> {
          final Optional<Pair<Bytes32, Boolean>> beaconHeadRootData = fetchBeaconHeadRootData();
          assertThat(beaconHeadRootData).isPresent();
          if (optimistic != null) {
            assertEquals(optimistic, beaconHeadRootData.get().getRight());
          }
          beaconHead.set(beaconHeadRootData.get().getLeft());
        });
    LOG.debug("Retrieved beacon head: {}", beaconHead.get());
    return beaconHead.get();
  }

  private Optional<Pair<Bytes32, Boolean>> fetchBeaconHeadRootData() throws IOException {
    final String result = httpClient.get(getRestApiUrl(), "/eth/v1/beacon/blocks/head/root");
    if (result.isEmpty()) {
      return Optional.empty();
    }
    final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
    final Bytes32 root = Bytes32.fromHexString(jsonNode.get("data").get("root").asText());
    final boolean executionOptimistic = jsonNode.get("execution_optimistic").asBoolean();
    return Optional.of(Pair.of(root, executionOptimistic));
  }

  private Optional<Bytes32> fetchBeaconHeadRoot() throws IOException {
    return fetchBeaconHeadRootData().map(Pair::getLeft);
  }

  private Optional<UInt64> getFinalizedEpoch() {
    try {
      final String result =
          httpClient.get(getRestApiUrl(), "/eth/v1/beacon/states/head/finality_checkpoints");
      if (result.isEmpty()) {
        return Optional.empty();
      }

      final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
      final UInt64 finalizedEpoch =
          UInt64.valueOf(jsonNode.get("data").get("finalized").get("epoch").asText());
      return Optional.of(finalizedEpoch);
    } catch (final IOException e) {
      LOG.error("Failed to fetch finalized epoch", e);
      return Optional.empty();
    }
  }

  private Optional<SignedBeaconBlock> fetchHeadBlock() throws IOException {
    final String blockId = "head";
    return fetchBlock(blockId);
  }

  private Optional<SignedBeaconBlock> fetchBlock(final String blockId) throws IOException {
    final String result = httpClient.get(getRestApiUrl(), "/eth/v2/beacon/blocks/" + blockId);
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
      final UInt64 slot = UInt64.valueOf(jsonNode.get("data").get("message").get("slot").asText());
      final DeserializableTypeDefinition<SignedBeaconBlock> jsonTypeDefinition =
          SharedApiTypes.withDataWrapper(
              "block",
              spec.atSlot(slot)
                  .getSchemaDefinitions()
                  .getSignedBeaconBlockSchema()
                  .getJsonTypeDefinition());
      return Optional.of(JsonUtil.parse(result, jsonTypeDefinition));
    }
  }

  public Optional<SignedBeaconBlock> getBlockAtSlot(final UInt64 slot) throws IOException {
    final Optional<String> result =
        httpClient.getOptional(getRestApiUrl(), "/eth/v2/beacon/blocks/" + slot);
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      final DeserializableTypeDefinition<SignedBeaconBlock> jsonTypeDefinition =
          SharedApiTypes.withDataWrapper(
              "block",
              spec.atSlot(slot)
                  .getSchemaDefinitions()
                  .getSignedBeaconBlockSchema()
                  .getJsonTypeDefinition());
      return Optional.of(JsonUtil.parse(result.get(), jsonTypeDefinition));
    }
  }

  private Optional<SignedExecutionPayloadEnvelope> fetchExecutionPayload(final String blockId)
      throws IOException {
    final String result =
        httpClient.get(getRestApiUrl(), "/eth/v1/beacon/execution_payload_envelopes/" + blockId);
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
      final UInt64 slot =
          UInt64.valueOf(
              jsonNode.get("data").get("message").get("payload").get("slot_number").asText());
      final DeserializableTypeDefinition<SignedExecutionPayloadEnvelope> jsonTypeDefinition =
          SharedApiTypes.withDataWrapper(
              "execution_payload_envelope",
              SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions())
                  .getSignedExecutionPayloadEnvelopeSchema()
                  .getJsonTypeDefinition());
      return Optional.of(JsonUtil.parse(result, jsonTypeDefinition));
    }
  }

  public SignedBeaconBlock getFinalizedBlock() throws IOException {
    return fetchBlock("finalized").orElseThrow();
  }

  public SignedBeaconBlock getHeadBlock() throws IOException {
    return fetchBlock("head").orElseThrow();
  }

  private Optional<BeaconState> fetchHeadState() throws IOException {
    return fetchState("head");
  }

  private Optional<BeaconState> fetchState(final String stateId) throws IOException {
    final Bytes result =
        httpClient.getAsBytes(
            getRestApiUrl(),
            "/eth/v2/debug/beacon/states/" + stateId,
            Map.of("Accept", "application/octet-stream"));
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(spec.deserializeBeaconState(result));
    }
  }

  private Optional<Bytes32> fetchValidatorWithdrawalCredentials(final int validatorId)
      throws IOException {
    final String result =
        httpClient.get(getRestApiUrl(), "/eth/v1/beacon/states/head/validators/" + validatorId);
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
      return Optional.of(
          Bytes32.fromHexString(
              jsonNode.get("data").get("validator").get("withdrawal_credentials").asText()));
    }
  }

  public int getDataColumnSidecarCount(final String blockId) throws IOException {
    final String result =
        httpClient.get(getRestApiUrl(), "/eth/v1/debug/beacon/data_column_sidecars/" + blockId);
    final JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
    return jsonNode.get("data").size();
  }

  public Optional<List<Blob>> getBlobsAtSlot(final UInt64 slot) throws IOException {
    final Bytes result =
        httpClient.getAsBytes(
            getRestApiUrl(),
            "/eth/v1/beacon/blobs/" + slot,
            Map.of("Accept", "application/octet-stream"));
    if (result.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(spec.deserializeBlobsInBlock(result, slot).asList());
    }
  }

  public void waitForCustodyBackfill(final UInt64 slot, final int expectedCustodyCount) {
    waitFor(
        () -> {
          final Optional<SignedBeaconBlock> maybeBlock = getBlockAtSlot(slot);
          assertThat(maybeBlock).isPresent();
          assertThat(getDataColumnSidecarCount(maybeBlock.get().getRoot().toHexString()))
              .isEqualTo(expectedCustodyCount);
        },
        3,
        MINUTES);
  }

  public void waitForValidatorWithCredentials(
      final int validatorIndex, final Eth1Address executionAddress) {
    waitFor(
        () -> {
          final Optional<Bytes32> maybeWithdrawalCredentials =
              fetchValidatorWithdrawalCredentials(validatorIndex);
          assertThat(maybeWithdrawalCredentials).isPresent();
          assertThat(maybeWithdrawalCredentials.get())
              .isEqualTo(
                  BlockProcessorCapella.getWithdrawalAddressFromEth1Address(executionAddress));
        });
  }

  public void waitForAttestationBeingGossiped(
      final int validatorSeparationIndex, final int totalValidatorCount) {
    List<UInt64> node1Validators =
        IntStream.range(0, validatorSeparationIndex).mapToObj(UInt64::valueOf).toList();
    List<UInt64> node2Validators =
        IntStream.range(validatorSeparationIndex, totalValidatorCount)
            .mapToObj(UInt64::valueOf)
            .toList();
    waitFor(
        () -> {
          final Optional<SignedBeaconBlock> maybeBlock = fetchHeadBlock();
          final Optional<BeaconState> maybeState = fetchHeadState();
          assertThat(maybeBlock).isPresent();
          assertThat(maybeState).isPresent();
          final SignedBeaconBlock block = maybeBlock.get();
          BeaconState state = maybeState.get();

          // Check that the fetched block and state are in sync
          assertThat(state.getLatestBlockHeader().getParentRoot())
              .isEqualTo(block.getMessage().getParentRoot());

          UInt64 proposerIndex = block.getMessage().getProposerIndex();

          Set<UInt64> attesterIndicesInAttestations =
              block.getMessage().getBody().getAttestations().stream()
                  .map(a -> spec.getAttestingIndices(state, a))
                  .flatMap(Collection::stream)
                  .collect(toSet());

          if (node1Validators.contains(proposerIndex)) {
            assertThat(attesterIndicesInAttestations.stream().anyMatch(node2Validators::contains))
                .isTrue();
          } else if (node2Validators.contains(proposerIndex)) {
            assertThat(attesterIndicesInAttestations.stream().anyMatch(node1Validators::contains))
                .isTrue();
          } else {
            throw new IllegalStateException("Proposer index greater than total validator count");
          }
        },
        2,
        MINUTES);
  }

  public void addValidators(final ValidatorKeystores additionalKeystores) {
    LOG.debug("Adding {} validators", additionalKeystores.getValidatorCount());
    container.expandTarballToContainer(additionalKeystores.getTarball(), WORKING_DIRECTORY);
    container
        .getDockerClient()
        .killContainerCmd(container.getContainerId())
        .withSignal("HUP")
        .exec();
  }

  String getMultiAddr() {
    return "/dns4/" + nodeAlias + "/tcp/" + P2P_PORT + "/p2p/" + config.getPeerId();
  }

  public String getBeaconRestApiUrl() {
    final String url = "http://" + nodeAlias + ":" + REST_API_PORT;
    LOG.debug("Node REST url: " + url);
    return url;
  }

  @Override
  public TekuNodeConfig getConfig() {
    return config;
  }

  @Override
  public void captureDebugArtifacts(final File artifactDir) {
    if (container.isRunning()) {
      copyDirectoryToTar(WORKING_DIRECTORY, new File(artifactDir, nodeAlias + ".tar"));
    } else {
      // Can't capture artifacts if it's not running but then it probably didn't cause the failure
      LOG.debug("Not capturing artifacts from {} because it is not running", nodeAlias);
    }
  }

  public void waitForAggregateGossipReceived() {
    waitForMetric(
        withNameEqualsTo("libp2p_gossip_messages_total"),
        withLabelValueSubstring("topic", "beacon_aggregate_and_proof"),
        withValueGreaterThan(0));
  }

  public void expectNodeNotSyncing() throws IOException {
    assertThat(getStatusIsSyncing()).isFalse();
  }

  public void expectElOffline() throws IOException {
    assertThat(getStatusElOffline()).isTrue();
  }

  public void expectElOnline() throws IOException {
    assertThat(getStatusElOffline()).isFalse();
  }

  public MetadataMessage getMetadataMessage(final SpecMilestone specMilestone) throws IOException {
    final JsonNode identityData = fetchIdentity();
    final MetadataMessageSchema<?> metadataMessageSchema =
        spec.forMilestone(specMilestone).getSchemaDefinitions().getMetadataMessageSchema();
    return JsonUtil.parse(
        identityData.get("metadata").toString(), metadataMessageSchema.getJsonTypeDefinition());
  }

  private JsonNode fetchIdentity() throws IOException {
    final String identity = httpClient.get(getRestApiUrl(), "/eth/v1/node/identity");
    return OBJECT_MAPPER.readTree(identity).get("data");
  }
}
