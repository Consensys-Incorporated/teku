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

package tech.pegasys.teku.validator.remote;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.validator.remote.RemoteValidatorApiHandler.MAX_PUBLIC_KEY_BATCH_SIZE;
import static tech.pegasys.teku.validator.remote.RemoteValidatorApiHandler.MAX_RATE_LIMITING_RETRIES;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import okhttp3.HttpUrl;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.migrated.ValidatorLivenessAtEpoch;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.node.PeerCount;
import tech.pegasys.teku.ethereum.json.types.node.PeerCountBuilder;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.ethereum.json.types.validator.BeaconCommitteeSelectionProof;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.ethereum.json.types.validator.PtcDuties;
import tech.pegasys.teku.ethereum.json.types.validator.PtcDuty;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.CommitteeSubscriptionRequest;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.remote.apiclient.PostStateValidatorsNotExistingException;
import tech.pegasys.teku.validator.remote.apiclient.RateLimitedException;
import tech.pegasys.teku.validator.remote.typedef.OkHttpValidatorTypeDefClient;

class RemoteValidatorApiHandlerTest {

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final HttpUrl endpoint = HttpUrl.get("http://localhost:5051");
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StubAsyncRunner readinessAsyncRunner = new StubAsyncRunner();

  private final OkHttpValidatorTypeDefClient typeDefClient =
      mock(OkHttpValidatorTypeDefClient.class);

  private RemoteValidatorApiHandler apiHandler;

  @BeforeEach
  public void beforeEach() {
    apiHandler =
        new RemoteValidatorApiHandler(
            endpoint, spec, typeDefClient, asyncRunner, readinessAsyncRunner, true);
  }

  @Test
  public void beaconCommitteeSelectionsRequest_ReturnBeaconCommitteeSelectionProof() {
    final String blsSignatureHex =
        dataStructureUtil.randomSignature().toBytesCompressed().toHexString();
    final BeaconCommitteeSelectionProof proof =
        new BeaconCommitteeSelectionProof.Builder()
            .validatorIndex(1)
            .slot(ONE)
            .selectionProof(blsSignatureHex)
            .build();

    when(typeDefClient.getBeaconCommitteeSelectionProof(any()))
        .thenReturn(Optional.of(List.of(proof)));

    final SafeFuture<Optional<List<BeaconCommitteeSelectionProof>>> future =
        apiHandler.getBeaconCommitteeSelectionProof(List.of(proof));
    asyncRunner.executeQueuedActions();

    final List<BeaconCommitteeSelectionProof> response = unwrapToValue(future);
    final BeaconCommitteeSelectionProof responseProof = response.get(0);
    assertThat(responseProof.getValidatorIndex()).isEqualTo(proof.getValidatorIndex());
    assertThat(responseProof.getSlot()).isEqualTo(proof.getSlot());
    assertThat(responseProof.getSelectionProof()).isEqualTo(proof.getSelectionProof());
  }

  @Test
  public void syncCommitteeSelectionsRequest_ReturnSyncCommitteeSelectionProof() {
    final String blsSignatureHex =
        dataStructureUtil.randomSignature().toBytesCompressed().toHexString();
    final SyncCommitteeSelectionProof proof =
        new SyncCommitteeSelectionProof.Builder()
            .validatorIndex(1)
            .slot(ONE)
            .subcommitteeIndex(2)
            .selectionProof(blsSignatureHex)
            .build();

    when(typeDefClient.getSyncCommitteeSelectionProof(any()))
        .thenReturn(Optional.of(List.of(proof)));

    final SafeFuture<Optional<List<SyncCommitteeSelectionProof>>> future =
        apiHandler.getSyncCommitteeSelectionProof(List.of(proof));
    asyncRunner.executeQueuedActions();

    final List<SyncCommitteeSelectionProof> response = unwrapToValue(future);
    final SyncCommitteeSelectionProof responseProof = response.get(0);
    assertThat(responseProof.getValidatorIndex()).isEqualTo(proof.getValidatorIndex());
    assertThat(responseProof.getSlot()).isEqualTo(proof.getSlot());
    assertThat(responseProof.getSubcommitteeIndex()).isEqualTo(proof.getSubcommitteeIndex());
    assertThat(responseProof.getSelectionProof()).isEqualTo(proof.getSelectionProof());
  }

  @Test
  public void getsEndpoint() {
    assertThat(apiHandler.getEndpoint()).isEqualTo(endpoint);
  }

  @Test
  public void getSyncingStatus_ReturnsSyncingStatus() {
    final SyncingStatus syncingStatus =
        new SyncingStatus(ONE, ONE, true, Optional.of(true), Optional.of(true));
    when(typeDefClient.getSyncingStatus()).thenReturn(syncingStatus);

    final SafeFuture<SyncingStatus> future = apiHandler.getSyncingStatus();

    readinessAsyncRunner.executeQueuedActions();

    assertThat(future).isCompletedWithValue(syncingStatus);
  }

  @Test
  public void getGenesisTime_WhenPresent_ReturnsValue() {
    final UInt64 genesisTime = dataStructureUtil.randomUInt64();
    when(typeDefClient.getGenesis())
        .thenReturn(Optional.of(new GenesisData(genesisTime, dataStructureUtil.randomBytes32())));

    final SafeFuture<Optional<GenesisData>> future = apiHandler.getGenesisData();

    assertThat(unwrapToValue(future).getGenesisTime()).isEqualTo(genesisTime);
  }

  @Test
  public void getGenesisTime_WhenNotPresent_ReturnsEmpty() {
    when(typeDefClient.getGenesis()).thenReturn(Optional.empty());

    final SafeFuture<Optional<GenesisData>> future = apiHandler.getGenesisData();

    assertThat(unwrapToOptional(future)).isNotPresent();
  }

  @Test
  void getValidatorIndices_WithEmptyPublicKeys_ReturnsEmptyMap() {
    final SafeFuture<Map<BLSPublicKey, Integer>> future =
        apiHandler.getValidatorIndices(emptyList());

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(safeJoin(future)).isEmpty();
  }

  @Test
  void getValidatorIndices_MakesSingleRequestUsingPost() {
    final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final List<String> expectedValidatorIds =
        List.of(
            key1.toBytesCompressed().toHexString(),
            key2.toBytesCompressed().toHexString(),
            key3.toBytesCompressed().toHexString());
    when(typeDefClient.postStateValidators(expectedValidatorIds))
        .thenReturn(Optional.of(List.of(stateValidatorData(1, key1), stateValidatorData(2, key2))));

    final SafeFuture<Map<BLSPublicKey, Integer>> future =
        apiHandler.getValidatorIndices(List.of(key1, key2, key3));

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(safeJoin(future)).containsOnly(entry(key1, 1), entry(key2, 2));
    verify(typeDefClient).postStateValidators(expectedValidatorIds);
  }

  @Test
  void getValidatorIndices_DoesNotAttemptPostAgainIfNotExisting() {
    final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final List<String> expectedValidatorIds =
        List.of(
            key1.toBytesCompressed().toHexString(),
            key2.toBytesCompressed().toHexString(),
            key3.toBytesCompressed().toHexString());

    // simulate POST not existing
    when(typeDefClient.postStateValidators(any()))
        .thenThrow(PostStateValidatorsNotExistingException.class);
    when(typeDefClient.getStateValidators(expectedValidatorIds))
        .thenReturn(Optional.of(List.of(stateValidatorData(1, key1), stateValidatorData(2, key2))));

    // call method twice
    final SafeFuture<Map<BLSPublicKey, Integer>> future =
        apiHandler.getValidatorIndices(List.of(key1, key2, key3));
    final SafeFuture<Map<BLSPublicKey, Integer>> future1 =
        apiHandler.getValidatorIndices(List.of(key1, key2, key3));

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(future1).isCompleted();
    assertThat(safeJoin(future)).containsOnly(entry(key1, 1), entry(key2, 2));
    assertThat(safeJoin(future1)).containsOnly(entry(key1, 1), entry(key2, 2));
    // POST only called once
    verify(typeDefClient, times(1)).postStateValidators(expectedValidatorIds);
    // GET called twice
    verify(typeDefClient, times(2)).getStateValidators(expectedValidatorIds);
  }

  @Test
  void getValidatorIndices_WithSmallNumberOfPublicKeys_RequestsSingleBatch() {
    // simulate POST not existing
    when(typeDefClient.postStateValidators(any()))
        .thenThrow(PostStateValidatorsNotExistingException.class);
    final BLSPublicKey key1 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key2 = dataStructureUtil.randomPublicKey();
    final BLSPublicKey key3 = dataStructureUtil.randomPublicKey();
    final List<String> expectedValidatorIds =
        List.of(
            key1.toBytesCompressed().toHexString(),
            key2.toBytesCompressed().toHexString(),
            key3.toBytesCompressed().toHexString());
    when(typeDefClient.getStateValidators(expectedValidatorIds))
        .thenReturn(Optional.of(List.of(stateValidatorData(1, key1), stateValidatorData(2, key2))));

    final SafeFuture<Map<BLSPublicKey, Integer>> future =
        apiHandler.getValidatorIndices(List.of(key1, key2, key3));

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(safeJoin(future)).containsOnly(entry(key1, 1), entry(key2, 2));
    verify(typeDefClient).getStateValidators(expectedValidatorIds);
  }

  @Test
  void getValidatorIndices_WithLargeNumberOfPublicKeys_CombinesMultipleBatches() {
    // simulate POST not existing
    when(typeDefClient.postStateValidators(any()))
        .thenThrow(PostStateValidatorsNotExistingException.class);
    // Need to ensure the URL length limit isn't exceeded, so send requests in batches
    final List<BLSPublicKey> allKeys =
        IntStream.range(0, MAX_PUBLIC_KEY_BATCH_SIZE * 3 - 2)
            .mapToObj(index -> dataStructureUtil.randomPublicKey())
            .collect(toList());

    final List<String> allSerializedKeys =
        allKeys.stream().map(key -> key.toBytesCompressed().toHexString()).collect(toList());

    final List<String> expectedBatch1 = allSerializedKeys.subList(0, MAX_PUBLIC_KEY_BATCH_SIZE);
    final List<String> expectedBatch2 =
        allSerializedKeys.subList(MAX_PUBLIC_KEY_BATCH_SIZE, MAX_PUBLIC_KEY_BATCH_SIZE * 2);
    final List<String> expectedBatch3 =
        allSerializedKeys.subList(MAX_PUBLIC_KEY_BATCH_SIZE * 2, allKeys.size());

    final List<StateValidatorData> batch1Data =
        List.of(stateValidatorData(10, allKeys.get(0)), stateValidatorData(11, allKeys.get(3)));
    final List<StateValidatorData> batch2Data =
        List.of(
            stateValidatorData(20, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE)),
            stateValidatorData(21, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE + 3)));
    final List<StateValidatorData> batch3Data =
        List.of(
            stateValidatorData(30, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2)),
            stateValidatorData(31, allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2 + 3)));

    when(typeDefClient.getStateValidators(expectedBatch1)).thenReturn(Optional.of(batch1Data));
    when(typeDefClient.getStateValidators(expectedBatch2)).thenReturn(Optional.of(batch2Data));
    when(typeDefClient.getStateValidators(expectedBatch3)).thenReturn(Optional.of(batch3Data));

    final SafeFuture<Map<BLSPublicKey, Integer>> future = apiHandler.getValidatorIndices(allKeys);

    asyncRunner.executeQueuedActions();
    assertThat(future).isCompleted();
    assertThat(safeJoin(future))
        .containsOnly(
            entry(allKeys.get(0), 10),
            entry(allKeys.get(3), 11),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE), 20),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE + 3), 21),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2), 30),
            entry(allKeys.get(MAX_PUBLIC_KEY_BATCH_SIZE * 2 + 3), 31));
    verify(typeDefClient).getStateValidators(expectedBatch1);
    verify(typeDefClient).getStateValidators(expectedBatch2);
    verify(typeDefClient).getStateValidators(expectedBatch3);
  }

  @Test
  public void getAttestationDuties_WithEmptyPublicKeys_ReturnsEmpty() {
    final SafeFuture<Optional<AttesterDuties>> future =
        apiHandler.getAttestationDuties(ONE, IntList.of());

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void getAttestationDuties_WhenNoneFound_ReturnsEmpty() {
    when(typeDefClient.postAttesterDuties(any(), any()))
        .thenReturn(
            Optional.of(
                new AttesterDuties(
                    false, dataStructureUtil.randomBytes32(), Collections.emptyList())));

    final SafeFuture<Optional<AttesterDuties>> future =
        apiHandler.getAttestationDuties(ONE, IntList.of(1234));

    assertThat(unwrapToValue(future).getDuties()).isEmpty();
  }

  @Test
  public void getAttestationDuties_WhenFound_ReturnsDuties() {
    final BLSPublicKey blsPublicKey = dataStructureUtil.randomPublicKey();
    final int validatorIndex = 472;
    final int committeeLength = 2;
    final int committeeIndex = 1;
    final int validatorCommitteeIndex = 3;
    final int committeesAtSlot = 15;
    final AttesterDuty expectedValidatorDuties =
        new AttesterDuty(
            blsPublicKey,
            validatorIndex,
            committeeLength,
            committeeIndex,
            committeesAtSlot,
            validatorCommitteeIndex,
            UInt64.ZERO);

    when(typeDefClient.postAttesterDuties(ONE, IntList.of(validatorIndex)))
        .thenReturn(
            Optional.of(
                new AttesterDuties(
                    false, dataStructureUtil.randomBytes32(), List.of(expectedValidatorDuties))));

    final SafeFuture<Optional<AttesterDuties>> future =
        apiHandler.getAttestationDuties(ONE, IntList.of(validatorIndex));

    final AttesterDuties validatorDuties = unwrapToValue(future);

    assertThat(validatorDuties.getDuties().get(0)).isEqualTo(expectedValidatorDuties);
  }

  @Test
  public void getPtcDuties_WhenFound_ReturnsDuties() {
    final UInt64 validatorIndex = UInt64.valueOf(472);
    final PtcDuty expectedValidatorDuties =
        new PtcDuty(dataStructureUtil.randomPublicKey(), validatorIndex, UInt64.ZERO);
    final PtcDuties response =
        new PtcDuties(false, dataStructureUtil.randomBytes32(), List.of(expectedValidatorDuties));

    when(typeDefClient.postPtcDuties(ONE, IntList.of(validatorIndex.intValue())))
        .thenReturn(Optional.of(response));

    final SafeFuture<Optional<PtcDuties>> future =
        apiHandler.getPtcDuties(ONE, IntList.of(validatorIndex.intValue()));

    assertThat(unwrapToValue(future)).isEqualTo(response);
  }

  @Test
  public void getProposerDuties_WithEmptyPublicKeys_ReturnsEmpty() {
    final SafeFuture<Optional<ProposerDuties>> future = apiHandler.getProposerDuties(ONE, false);

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void getProposerDuties_WhenNoneFound_ReturnsEmpty() {
    when(typeDefClient.getProposerDuties(any()))
        .thenReturn(
            Optional.of(
                new ProposerDuties(
                    Bytes32.fromHexString("0x1234"), Collections.emptyList(), false)));

    final SafeFuture<Optional<ProposerDuties>> future = apiHandler.getProposerDuties(ONE, false);

    assertThat(unwrapToValue(future).getDuties()).isEmpty();
  }

  @Test
  public void getProposerDuties_WhenFound_ReturnsDuties() {
    final BLSPublicKey blsPublicKey = dataStructureUtil.randomPublicKey();
    final int validatorIndex = 472;
    final ProposerDuty schemaValidatorDuties =
        new ProposerDuty(blsPublicKey, validatorIndex, UInt64.ZERO);
    final ProposerDuty expectedValidatorDuties =
        new ProposerDuty(blsPublicKey, validatorIndex, UInt64.ZERO);
    final ProposerDuties response =
        new ProposerDuties(Bytes32.fromHexString("0x1234"), List.of(schemaValidatorDuties), false);

    when(typeDefClient.getProposerDuties(ONE)).thenReturn(Optional.of(response));

    final SafeFuture<Optional<ProposerDuties>> future = apiHandler.getProposerDuties(ONE, false);

    final ProposerDuties validatorDuties = unwrapToValue(future);

    assertThat(validatorDuties.getDuties().get(0)).isEqualTo(expectedValidatorDuties);
    assertThat(validatorDuties.getDependentRoot()).isEqualTo(response.getDependentRoot());
  }

  @Test
  public void getProposerDuties_WhenGloasScheduled_UsesV2() {
    final Spec gloasSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(UInt64.valueOf(100));
    final RemoteValidatorApiHandler gloasHandler =
        new RemoteValidatorApiHandler(
            endpoint, gloasSpec, typeDefClient, asyncRunner, readinessAsyncRunner, true);

    final BLSPublicKey blsPublicKey = dataStructureUtil.randomPublicKey();
    final ProposerDuties response =
        new ProposerDuties(
            Bytes32.fromHexString("0x5678"),
            List.of(new ProposerDuty(blsPublicKey, 1, UInt64.ZERO)),
            false);
    when(typeDefClient.getProposerDutiesV2(ONE)).thenReturn(Optional.of(response));

    assertThat(unwrapToValue(gloasHandler.getProposerDuties(ONE, true))).isEqualTo(response);
    verify(typeDefClient, times(0)).getProposerDuties(any());
  }

  @Test
  public void getPeerCount_WhenAvailable_ReturnPeerCount() {
    final PeerCount response =
        new PeerCountBuilder()
            .connected(UInt64.valueOf(10))
            .disconnected(UInt64.valueOf(5))
            .build();
    when(typeDefClient.getPeerCount()).thenReturn(Optional.of(response));
    final SafeFuture<Optional<PeerCount>> peerCountFuture = apiHandler.getPeerCount();
    final PeerCount peerCount = unwrapToValue(readinessAsyncRunner, peerCountFuture);
    assertThat(peerCount).isEqualTo(response);
  }

  @Test
  public void createAttestationData_WhenNone_ReturnsEmpty() {
    when(typeDefClient.createAttestationData(any(), anyInt())).thenReturn(Optional.empty());

    final SafeFuture<Optional<AttestationData>> future = apiHandler.createAttestationData(ONE, 0);

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createAttestationData_WhenFound_ReturnsAttestation() {
    final Attestation attestation = dataStructureUtil.randomAttestation();

    when(typeDefClient.createAttestationData(ONE, 0))
        .thenReturn(Optional.of(attestation.getData()));

    final SafeFuture<Optional<AttestationData>> future = apiHandler.createAttestationData(ONE, 0);

    assertThatSszData(unwrapToValue(future)).isEqualByAllMeansTo(attestation.getData());
  }

  @Test
  public void createPayloadAttestationData_WhenNone_ReturnsEmpty() {
    when(typeDefClient.createPayloadAttestationData(ONE)).thenReturn(Optional.empty());

    final SafeFuture<Optional<PayloadAttestationData>> future =
        apiHandler.createPayloadAttestationData(ONE);

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createPayloadAttestationData_WhenFound_ReturnsPayloadAttestationData() {
    final PayloadAttestationData payloadAttestationData =
        new DataStructureUtil(TestSpecFactory.createMinimalGloas())
            .randomPayloadAttestationData(ONE);
    when(typeDefClient.createPayloadAttestationData(ONE))
        .thenReturn(Optional.of(payloadAttestationData));

    final SafeFuture<Optional<PayloadAttestationData>> future =
        apiHandler.createPayloadAttestationData(ONE);

    assertThatSszData(unwrapToValue(future)).isEqualByAllMeansTo(payloadAttestationData);
  }

  @Test
  public void createUnsignedExecutionPayload_WhenNone_ReturnsEmpty() {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    when(typeDefClient.getExecutionPayloadEnvelope(ONE, beaconBlockRoot))
        .thenReturn(Optional.empty());

    final SafeFuture<Optional<ExecutionPayloadEnvelope>> future =
        apiHandler.createUnsignedExecutionPayload(ONE, beaconBlockRoot);

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createUnsignedExecutionPayload_WhenFound_ReturnsEnvelope() {
    final ExecutionPayloadEnvelope envelope =
        new DataStructureUtil(TestSpecFactory.createMinimalGloas())
            .randomExecutionPayloadEnvelope(ONE);
    final Bytes32 beaconBlockRoot = envelope.getBeaconBlockRoot();
    when(typeDefClient.getExecutionPayloadEnvelope(ONE, beaconBlockRoot))
        .thenReturn(Optional.of(envelope));

    final SafeFuture<Optional<ExecutionPayloadEnvelope>> future =
        apiHandler.createUnsignedExecutionPayload(ONE, beaconBlockRoot);

    assertThatSszData(unwrapToValue(future)).isEqualByAllMeansTo(envelope);
  }

  @Test
  public void sendPayloadAttestationMessages_InvokeApiWithCorrectRequest() {
    final PayloadAttestationMessage payloadAttestationMessage =
        new DataStructureUtil(TestSpecFactory.createMinimalGloas())
            .randomPayloadAttestationMessage();
    final List<PayloadAttestationMessage> payloadAttestationMessages =
        List.of(payloadAttestationMessage);
    final List<SubmitDataError> expectedErrors =
        List.of(new SubmitDataError(UInt64.valueOf(3), "invalid payload attestation"));

    when(typeDefClient.sendPayloadAttestationMessages(payloadAttestationMessages))
        .thenReturn(expectedErrors);

    final SafeFuture<List<SubmitDataError>> result =
        apiHandler.sendPayloadAttestationMessages(payloadAttestationMessages);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(expectedErrors);
    verify(typeDefClient).sendPayloadAttestationMessages(payloadAttestationMessages);
  }

  @Test
  public void sendSignedProposerPreferences_InvokeApiWithCorrectRequest() {
    final SignedProposerPreferences signedProposerPreferences =
        new DataStructureUtil(TestSpecFactory.createMinimalGloas())
            .randomSignedProposerPreferences();
    final List<SignedProposerPreferences> signedProposerPreferencesList =
        List.of(signedProposerPreferences);
    final List<SubmitDataError> expectedErrors =
        List.of(new SubmitDataError(UInt64.valueOf(3), "invalid proposer preferences"));

    when(typeDefClient.sendSignedProposerPreferences(signedProposerPreferencesList))
        .thenReturn(expectedErrors);

    final SafeFuture<List<SubmitDataError>> result =
        apiHandler.sendSignedProposerPreferences(signedProposerPreferencesList);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompletedWithValue(expectedErrors);
    verify(typeDefClient).sendSignedProposerPreferences(signedProposerPreferencesList);
  }

  @Test
  public void createUnsignedBlock_WhenNoneFound_ReturnsEmpty() {
    final BLSSignature blsSignature = dataStructureUtil.randomSignature();

    final SafeFuture<Optional<BlockContainerAndMetaData>> future =
        apiHandler.createUnsignedBlock(
            ONE, blsSignature, Optional.of(Bytes32.random()), Optional.empty());

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createUnsignedBlock_WhenFound_ReturnsBlock() {
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(ONE);
    final BLSSignature blsSignature = dataStructureUtil.randomSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    when(typeDefClient.createUnsignedBlock(
            eq(blockContainerAndMetaData.blockContainer().getSlot()),
            eq(blsSignature),
            eq(graffiti),
            eq(false),
            eq(Optional.empty())))
        .thenReturn(Optional.of(blockContainerAndMetaData));

    final SafeFuture<Optional<BlockContainerAndMetaData>> future =
        apiHandler.createUnsignedBlock(ONE, blsSignature, graffiti, Optional.empty());

    final BlockContainerAndMetaData resultValue = unwrapToValue(future);
    assertThat(resultValue).isEqualTo(blockContainerAndMetaData);
    assertThatSszData(resultValue.blockContainer())
        .isEqualByAllMeansTo(blockContainerAndMetaData.blockContainer());
  }

  @Test
  public void createUnsignedBlock_viaBlockV3_WhenFound_ReturnsBlock() {
    final BlockContainerAndMetaData blockContainerAndMetaData =
        dataStructureUtil.randomBlockContainerAndMetaData(ONE);
    final BLSSignature blsSignature = dataStructureUtil.randomSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    // we expect new block API to be called (with proposer boost factor parameter instead of blinded
    // parameter)
    when(typeDefClient.createUnsignedBlock(
            eq(blockContainerAndMetaData.blockContainer().getSlot()),
            eq(blsSignature),
            eq(graffiti),
            eq(false),
            eq(Optional.of(BuilderConfig.withBuilderBoostFactor(ONE)))))
        .thenReturn(Optional.of(blockContainerAndMetaData));

    final SafeFuture<Optional<BlockContainerAndMetaData>> future =
        apiHandler.createUnsignedBlock(ONE, blsSignature, graffiti, Optional.of(ONE));

    final BlockContainerAndMetaData resultValue = unwrapToValue(future);
    assertThat(resultValue).isEqualTo(blockContainerAndMetaData);
    assertThatSszData(resultValue.blockContainer())
        .isEqualByAllMeansTo(blockContainerAndMetaData.blockContainer());
  }

  @Test
  public void createUnsignedBlock_WhenFound_ReturnsBlockContents() {
    final Spec denebSpec = TestSpecFactory.createMinimalDeneb();
    final DataStructureUtil denebDataStructureUtil = new DataStructureUtil(denebSpec);
    final BlockContainerAndMetaData blockContentsAndMetaData =
        denebDataStructureUtil.randomBlockContainerAndMetaData(ONE);
    final BLSSignature blsSignature = denebDataStructureUtil.randomSignature();
    final Optional<Bytes32> graffiti = Optional.of(Bytes32.random());

    when(typeDefClient.createUnsignedBlock(
            eq(blockContentsAndMetaData.blockContainer().getSlot()),
            eq(blsSignature),
            eq(graffiti),
            eq(false),
            eq(Optional.empty())))
        .thenReturn(Optional.of(blockContentsAndMetaData));

    final SafeFuture<Optional<BlockContainerAndMetaData>> future =
        apiHandler.createUnsignedBlock(ONE, blsSignature, graffiti, Optional.empty());

    final BlockContainerAndMetaData resultValue = unwrapToValue(future);
    assertThat(resultValue).isEqualTo(blockContentsAndMetaData);
    assertThatSszData(resultValue.blockContainer())
        .isEqualByAllMeansTo(blockContentsAndMetaData.blockContainer());
  }

  @Test
  public void sendSignedBlock_InvokeApiWithCorrectRequest() {
    final BeaconBlock beaconBlock = dataStructureUtil.randomBeaconBlock(ONE);
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.signedBlock(beaconBlock, signature);
    final SendSignedBlockResult expectedResult = SendSignedBlockResult.success(Bytes32.ZERO);

    when(typeDefClient.sendSignedBlock(any(), any())).thenReturn(expectedResult);

    final ArgumentCaptor<SignedBeaconBlock> argumentCaptor =
        ArgumentCaptor.forClass(SignedBeaconBlock.class);

    final SafeFuture<SendSignedBlockResult> result =
        apiHandler.sendSignedBlock(signedBeaconBlock, BroadcastValidationLevel.GOSSIP);
    asyncRunner.executeQueuedActions();

    verify(typeDefClient)
        .sendSignedBlock(argumentCaptor.capture(), eq(BroadcastValidationLevel.GOSSIP));
    assertThat(argumentCaptor.getValue()).isEqualTo(signedBeaconBlock);
    assertThat(result).isCompletedWithValue(expectedResult);
  }

  @Test
  public void createAggregate_WhenNotFound_ReturnsEmpty() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 attHashTreeRoot = Bytes32.random();

    doReturn(Optional.empty())
        .when(typeDefClient)
        .createAggregate(slot, attHashTreeRoot, Optional.empty());

    final SafeFuture<Optional<Attestation>> future =
        apiHandler.createAggregate(slot, attHashTreeRoot, Optional.of(ONE));

    assertThat(unwrapToOptional(future)).isEmpty();
  }

  @Test
  public void createAggregate_WhenFound_ReturnsAttestation() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 attHashTreeRoot = Bytes32.random();

    final Attestation attestation = dataStructureUtil.randomAttestation();
    final ObjectAndMetaData<Attestation> attestationAndMetaData =
        new ObjectAndMetaData<>(attestation, spec.atSlot(slot).getMilestone(), false, true, true);

    doReturn(Optional.of(attestationAndMetaData))
        .when(typeDefClient)
        .createAggregate(slot, attHashTreeRoot, Optional.of(ONE));

    final SafeFuture<Optional<Attestation>> future =
        apiHandler.createAggregate(slot, attHashTreeRoot, Optional.of(ONE));

    assertThatSszData(unwrapToValue(future)).isEqualByAllMeansTo(attestation);
  }

  @Test
  public void subscribeToBeaconCommitteeForAggregation_InvokeApi() {
    final int validatorIndex = 3;
    final int committeeIndex = 1;
    final UInt64 aggregationSlot = ONE;
    final boolean isAggregator = true;
    final UInt64 committeesAtSlot = UInt64.valueOf(23);
    final List<CommitteeSubscriptionRequest> requests =
        List.of(
            new CommitteeSubscriptionRequest(
                validatorIndex, committeeIndex, committeesAtSlot, aggregationSlot, isAggregator));

    final SafeFuture<Void> result = apiHandler.subscribeToBeaconCommittee(requests);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompleted();
    verify(typeDefClient).subscribeToBeaconCommittee(requests);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void subscribeToPersistentSubnets_InvokeApi() {
    final int subnetId = 1;
    final UInt64 slot = ONE;

    final SubnetSubscription subnetSubscription = new SubnetSubscription(subnetId, slot);

    final ArgumentCaptor<Set<SubnetSubscription>> argumentCaptor =
        ArgumentCaptor.forClass(Set.class);

    final SafeFuture<Void> result =
        apiHandler.subscribeToPersistentSubnets(Set.of(subnetSubscription));
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompleted();
    verify(typeDefClient).subscribeToPersistentSubnets(argumentCaptor.capture());

    final Set<SubnetSubscription> request = argumentCaptor.getValue();
    assertThat(request).hasSize(1);
    assertThat(request.stream().findFirst().orElseThrow()).isEqualTo(subnetSubscription);
  }

  @Test
  void shouldRetryAfterDelayWhenRequestRateLimited() {
    when(typeDefClient.getGenesis()).thenThrow(new RateLimitedException("/fork"));

    final SafeFuture<Optional<GenesisData>> result = apiHandler.getGenesisData();

    for (int i = 0; i < MAX_RATE_LIMITING_RETRIES; i++) {
      asyncRunner.executeQueuedActions();
      assertThat(result).isNotDone();
      verify(typeDefClient, times(i + 1)).getGenesis();
    }

    asyncRunner.executeQueuedActions();
    assertThatSafeFuture(result).isCompletedExceptionallyWith(RateLimitedException.class);
  }

  @Test
  public void registerValidators_invokeApiWithCorrectRequest() {
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(5);

    final SafeFuture<Void> result = apiHandler.registerValidators(validatorRegistrations);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompleted();
    verify(typeDefClient).registerValidators(validatorRegistrations);
  }

  @Test
  public void checkValidatorsDoppelganger_invokeApiWithCorrectRequest()
      throws ExecutionException, InterruptedException {
    final List<UInt64> validatorIndices =
        List.of(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64());
    final UInt64 epoch = dataStructureUtil.randomEpoch();
    final SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> result =
        apiHandler.getValidatorsLiveness(validatorIndices, epoch);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompleted();
    assertThat(result.get()).isEmpty();
    verify(typeDefClient).sendValidatorsLiveness(epoch, validatorIndices);
  }

  @Test
  public void checkValidatorsDoppelgangerShouldReturnDoppelgangerDetectionResult()
      throws ExecutionException, InterruptedException {
    final UInt64 firstIndex = dataStructureUtil.randomUInt64();
    final UInt64 secondIndex = dataStructureUtil.randomUInt64();
    final UInt64 thirdIndex = dataStructureUtil.randomUInt64();

    final UInt64 epoch = dataStructureUtil.randomEpoch();

    final List<UInt64> validatorIndices = List.of(firstIndex, secondIndex, thirdIndex);

    final List<ValidatorLivenessAtEpoch> validatorLivenesses =
        List.of(
            new ValidatorLivenessAtEpoch(firstIndex, false),
            new ValidatorLivenessAtEpoch(secondIndex, true),
            new ValidatorLivenessAtEpoch(thirdIndex, true));
    when(typeDefClient.sendValidatorsLiveness(any(), any()))
        .thenReturn(Optional.of(validatorLivenesses));

    final SafeFuture<Optional<List<ValidatorLivenessAtEpoch>>> result =
        apiHandler.getValidatorsLiveness(validatorIndices, epoch);
    asyncRunner.executeQueuedActions();

    assertThat(result).isCompleted();
    assertThat(result.get()).isPresent();
    final List<ValidatorLivenessAtEpoch> validatorLivenessAtEpochesResult = result.get().get();
    assertThat(validatorIsLive(validatorLivenessAtEpochesResult, firstIndex)).isFalse();
    assertThat(validatorIsLive(validatorLivenessAtEpochesResult, secondIndex)).isTrue();
    assertThat(validatorIsLive(validatorLivenessAtEpochesResult, thirdIndex)).isTrue();
    verify(typeDefClient).sendValidatorsLiveness(epoch, validatorIndices);
  }

  private boolean validatorIsLive(
      final List<ValidatorLivenessAtEpoch> validatorLivenessAtEpoches,
      final UInt64 validatorIndex) {
    return validatorLivenessAtEpoches.stream()
        .anyMatch(
            validatorLivenessAtEpoch ->
                validatorLivenessAtEpoch.index().equals(validatorIndex)
                    && validatorLivenessAtEpoch.isLive());
  }

  private <T> Optional<T> unwrapToOptional(final SafeFuture<Optional<T>> future) {
    try {
      asyncRunner.executeQueuedActions();
      return Waiter.waitFor(future);
    } catch (Exception e) {
      fail("Error unwrapping optional from SafeFuture", e);
      throw new RuntimeException(e);
    }
  }

  private <T> T unwrapToValue(final SafeFuture<Optional<T>> future) {
    return unwrapToValue(asyncRunner, future);
  }

  private <T> T unwrapToValue(
      final StubAsyncRunner asyncRunner, final SafeFuture<Optional<T>> future) {
    try {
      asyncRunner.executeQueuedActions();
      return Waiter.waitFor(future).orElseThrow();
    } catch (Exception e) {
      fail("Error unwrapping value from SafeFuture", e);
      throw new RuntimeException(e);
    }
  }

  private StateValidatorData stateValidatorData(final long index, final BLSPublicKey publicKey) {
    final Validator validator =
        new Validator(
            publicKey,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            false,
            UInt64.ZERO,
            UInt64.ZERO,
            FAR_FUTURE_EPOCH,
            FAR_FUTURE_EPOCH);
    return new StateValidatorData(
        UInt64.valueOf(index),
        dataStructureUtil.randomUInt64(),
        ValidatorStatus.active_ongoing,
        validator);
  }
}
