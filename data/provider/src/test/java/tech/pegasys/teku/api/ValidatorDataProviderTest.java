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

package tech.pegasys.teku.api;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderPreferencesEntry;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.metadata.BlockContainerAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.schemas.ApiSchemas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

@TestSpecContext(allMilestones = true)
public class ValidatorDataProviderTest {

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<List<Attestation>> args = ArgumentCaptor.forClass(List.class);

  private Spec spec;
  private SpecMilestone specMilestone;
  private DataStructureUtil dataStructureUtil;
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private ValidatorDataProvider provider;
  private BlockContainerAndMetaData blockContainerAndMetaDataInternal;
  private final BLSSignature signatureInternal = BLSTestUtil.randomSignature(1234);

  @BeforeEach
  public void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    provider = new ValidatorDataProvider(spec, validatorApiChannel, combinedChainDataClient);
    blockContainerAndMetaDataInternal = dataStructureUtil.randomBlockContainerAndMetaData(123);
    specMilestone = specContext.getSpecMilestone();
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_throwsWithoutSlotDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.produceBlock(null, null, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_shouldThrowWithoutRandaoDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.produceBlock(ONE, null, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_shouldThrowIfHistoricSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.produceBlock(ZERO, signatureInternal, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_shouldThrowIfFarFutureSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.produceBlock(
                    UInt64.valueOf(10L), signatureInternal, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void getUnsignedBeaconBlockAtSlot_PreDeneb_shouldCreateAnUnsignedBlock() {
    assumeThat(specMilestone).isLessThan(SpecMilestone.DENEB);
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ZERO);
    when(validatorApiChannel.createUnsignedBlock(
            ONE, signatureInternal, Optional.empty(), Optional.empty()))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaDataInternal)));

    SafeFuture<? extends Optional<BlockContainerAndMetaData>> data =
        provider.produceBlock(ONE, signatureInternal, Optional.empty(), Optional.empty());

    verify(validatorApiChannel)
        .createUnsignedBlock(ONE, signatureInternal, Optional.empty(), Optional.empty());

    assertThat(data).isCompleted();

    assertThat(data.getNow(null).orElseThrow())
        .usingRecursiveComparison()
        .isEqualTo(blockContainerAndMetaDataInternal);
  }

  @TestTemplate
  void produceBlock_throwsWithoutSlotDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.produceBlock(null, null, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void produceBlock_shouldThrowWithoutRandaoDefined() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.produceBlock(ONE, null, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void produceBlock_shouldThrowIfHistoricSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.produceBlock(ZERO, signatureInternal, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void produceBlock_shouldThrowIfFarFutureSlotRequested() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () ->
                provider.produceBlock(
                    UInt64.valueOf(10L), signatureInternal, Optional.empty(), Optional.empty()));
  }

  @TestTemplate
  void produceBlock_shouldCreateAnUnsignedBlock() {
    when(combinedChainDataClient.getCurrentSlot()).thenReturn(ONE);
    when(combinedChainDataClient.getStateAtSlotExact(
            blockContainerAndMetaDataInternal.blockContainer().getSlot().decrement()))
        .thenReturn(completedFuture(Optional.of(dataStructureUtil.randomBeaconState())));

    when(validatorApiChannel.createUnsignedBlock(
            ONE, signatureInternal, Optional.empty(), Optional.of(ONE)))
        .thenReturn(completedFuture(Optional.of(blockContainerAndMetaDataInternal)));

    SafeFuture<? extends Optional<? extends BlockContainerAndMetaData>> data =
        provider.produceBlock(ONE, signatureInternal, Optional.empty(), Optional.of(ONE));

    verify(validatorApiChannel)
        .createUnsignedBlock(ONE, signatureInternal, Optional.empty(), Optional.of(ONE));

    assertThat(data).isCompleted();

    assertThat(data.getNow(null).orElseThrow())
        .usingRecursiveComparison()
        .isEqualTo(blockContainerAndMetaDataInternal);
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldThrowIfFutureSlotRequested() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(validatorApiChannel.createAttestationData(ONE, 0))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("Computer says no")));

    final SafeFuture<Optional<AttestationData>> result =
        provider.createAttestationDataAtSlot(ONE, 0);
    verify(validatorApiChannel).createAttestationData(ONE, 0);
    SafeFutureAssert.assertThatSafeFuture(result)
        .isCompletedExceptionallyWith(BadRequestException.class);
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldThrowIfStoreNotFound() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(false);
    final SafeFuture<Optional<AttestationData>> result =
        provider.createAttestationDataAtSlot(ZERO, 0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCauseInstanceOf(ChainDataUnavailableException.class);
    verify(combinedChainDataClient).isStoreAvailable();
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldReturnEmptyIfBlockNotFound() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(validatorApiChannel.createAttestationData(ZERO, 0))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<AttestationData>> result =
        provider.createAttestationDataAtSlot(ZERO, 0);
    verify(validatorApiChannel).createAttestationData(ZERO, 0);
    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @TestTemplate
  void getAttestationDataAtSlot_shouldReturnAttestationData() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    final AttestationData internalData = dataStructureUtil.randomAttestationData();
    when(validatorApiChannel.createAttestationData(ONE, 0))
        .thenReturn(completedFuture(Optional.of(internalData)));

    final SafeFuture<Optional<AttestationData>> result =
        provider.createAttestationDataAtSlot(ONE, 0);
    assertThat(result).isCompleted();
    AttestationData data = safeJoin(result).orElseThrow();
    assertThat(data.getIndex()).isEqualTo(internalData.getIndex());
    assertThat(data.getSlot()).isEqualTo(internalData.getSlot());
    assertThat(data.getBeaconBlockRoot()).isEqualTo(internalData.getBeaconBlockRoot());
  }

  @TestTemplate
  void createPayloadAttestationData_shouldThrowIfStoreNotFound() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(false);

    final SafeFuture<Optional<PayloadAttestationData>> result =
        provider.createPayloadAttestationData(ZERO);

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::join).hasRootCauseInstanceOf(ChainDataUnavailableException.class);
    verify(combinedChainDataClient).isStoreAvailable();
  }

  @TestTemplate
  void createPayloadAttestationData_shouldReturnEmptyIfBlockNotFound() {
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    when(validatorApiChannel.createPayloadAttestationData(ZERO))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<PayloadAttestationData>> result =
        provider.createPayloadAttestationData(ZERO);

    verify(validatorApiChannel).createPayloadAttestationData(ZERO);
    assertThat(result).isCompletedWithValue(Optional.empty());
  }

  @TestTemplate
  void createPayloadAttestationData_shouldReturnPayloadAttestationData() {
    assumeThat(specMilestone).isEqualTo(SpecMilestone.GLOAS);
    when(combinedChainDataClient.isStoreAvailable()).thenReturn(true);
    final PayloadAttestationData internalData = dataStructureUtil.randomPayloadAttestationData(ONE);
    when(validatorApiChannel.createPayloadAttestationData(ONE))
        .thenReturn(completedFuture(Optional.of(internalData)));

    final SafeFuture<Optional<PayloadAttestationData>> result =
        provider.createPayloadAttestationData(ONE);

    assertThat(result).isCompleted();
    assertThatSszData(safeJoin(result).orElseThrow()).isEqualByAllMeansTo(internalData);
  }

  @TestTemplate
  void submitAttestation_shouldSubmitAnInternalAttestationStructure() {
    Attestation attestation = dataStructureUtil.randomAttestation();
    final List<SubmitDataError> errors = List.of(new SubmitDataError(ZERO, "Nope"));
    when(validatorApiChannel.sendSignedAttestations(any()))
        .thenReturn(SafeFuture.completedFuture(errors));

    assertThatSafeFuture(provider.submitAttestations(List.of(attestation)))
        .isCompletedWithValue(errors);

    verify(validatorApiChannel).sendSignedAttestations(args.capture());
    assertThat(args.getValue()).hasSize(1);
    assertThatSszData(args.getValue().get(0)).isEqualByAllMeansTo(attestation);
  }

  @TestTemplate
  void submitProposerPreferences_shouldPreserveIndexedErrors() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(SpecMilestone.GLOAS);
    final SignedProposerPreferences preferences =
        dataStructureUtil.randomSignedProposerPreferences();
    final List<SubmitDataError> errors =
        List.of(new SubmitDataError(ZERO, "Invalid proposer preferences signature"));
    when(validatorApiChannel.sendSignedProposerPreferences(List.of(preferences)))
        .thenReturn(SafeFuture.completedFuture(errors));

    assertThatSafeFuture(provider.submitProposerPreferences(List.of(preferences)))
        .isCompletedWithValue(errors);
  }

  @TestTemplate
  void submitBuilderPreferences_shouldPreserveIndexedErrors() {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(SpecMilestone.GLOAS);
    final SszList<BuilderPreferencesEntry> entries =
        ApiSchemas.BUILDER_PREFERENCES_ENTRIES_SCHEMA.createFromElements(
            List.of(dataStructureUtil.randomBuilderPreferencesEntry()));
    final List<SubmitDataError> errors =
        List.of(new SubmitDataError(ZERO, "Invalid builder preferences entry"));
    when(validatorApiChannel.sendBuilderPreferences(entries))
        .thenReturn(SafeFuture.completedFuture(errors));

    assertThatSafeFuture(provider.submitBuilderPreferences(entries)).isCompletedWithValue(errors);
  }

  @TestTemplate
  public void getAttesterDuties_shouldHandleEmptyIndicesList() {
    final Bytes32 previousTargetRoot = dataStructureUtil.randomBytes32();
    when(validatorApiChannel.getAttestationDuties(eq(ONE), any()))
        .thenReturn(
            completedFuture(
                Optional.of(new AttesterDuties(false, previousTargetRoot, emptyList()))));
    final SafeFuture<Optional<AttesterDuties>> future =
        provider.getAttesterDuties(UInt64.ONE, IntList.of());
    assertThat(future).isCompleted();
    Optional<AttesterDuties> maybeData = safeJoin(future);
    assertThat(maybeData.isPresent()).isTrue();
    assertThat(maybeData.get().getDuties()).isEmpty();
  }

  @TestTemplate
  public void getAttesterDuties_shouldReturnDutiesForKnownValidator() {
    AttesterDuty v1 = new AttesterDuty(BLSTestUtil.randomPublicKey(0), 1, 2, 3, 15, 4, ONE);
    AttesterDuty v2 = new AttesterDuty(BLSTestUtil.randomPublicKey(1), 11, 12, 13, 15, 14, ZERO);
    when(validatorApiChannel.getAttestationDuties(eq(ONE), any()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new AttesterDuties(
                        false, dataStructureUtil.randomBytes32(), List.of(v1, v2)))));

    final SafeFuture<Optional<AttesterDuties>> future =
        provider.getAttesterDuties(ONE, IntList.of(1, 11));
    assertThat(future).isCompleted();
    final Optional<AttesterDuties> maybeList = safeJoin(future);
    final AttesterDuties list = maybeList.orElseThrow();
    assertThat(list.getDuties()).containsExactlyInAnyOrder(v1, v2);
  }

  @TestTemplate
  void registerValidators_shouldReportErrorIfCannotRetrieveValidatorStatuses() {
    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(4);

    when(validatorApiChannel.getValidatorStatuses(anyCollection()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<Void> result = provider.registerValidators(validatorRegistrations);

    SafeFutureAssert.assertThatSafeFuture(result)
        .isCompletedExceptionallyWithMessage(
            "Couldn't retrieve validator statuses during registering. Most likely the BN is still syncing.");
  }

  @TestTemplate
  @SuppressWarnings("EnumOrdinal")
  void registerValidators_shouldIgnoreExitedAndUnknownValidators() {
    final int numOfValidatorRegistrationsAttempted = ValidatorStatus.values().length + 2;

    final SszList<SignedValidatorRegistration> validatorRegistrations =
        dataStructureUtil.randomSignedValidatorRegistrations(numOfValidatorRegistrationsAttempted);

    final Map<BLSPublicKey, StateValidatorData> knownValidators =
        IntStream.range(0, ValidatorStatus.values().length)
            .mapToObj(
                statusIdx ->
                    Map.entry(
                        validatorRegistrations.get(statusIdx).getMessage().getPublicKey(),
                        ValidatorStatus.values()[statusIdx]))
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    e ->
                        new StateValidatorData(
                            dataStructureUtil.randomValidatorIndex(),
                            dataStructureUtil.randomUInt64(),
                            e.getValue(),
                            dataStructureUtil.randomValidator())));

    final List<BLSPublicKey> exitedOrUnknownKeys =
        IntStream.range(
                ValidatorStatus.exited_unslashed.ordinal(), numOfValidatorRegistrationsAttempted)
            .mapToObj(
                statusOrdinal ->
                    validatorRegistrations.get(statusOrdinal).getMessage().getPublicKey())
            .toList();

    when(validatorApiChannel.getValidatorStatuses(anyCollection()))
        .thenAnswer(
            args -> {
              final Collection<BLSPublicKey> publicKeys = args.getArgument(0);
              final Map<BLSPublicKey, StateValidatorData> validatorData =
                  publicKeys.stream()
                      .filter(knownValidators::containsKey)
                      .collect(Collectors.toMap(Function.identity(), knownValidators::get));
              return SafeFuture.completedFuture(Optional.of(validatorData));
            });
    when(validatorApiChannel.registerValidators(any())).thenReturn(SafeFuture.COMPLETE);

    final SafeFuture<Void> result = provider.registerValidators(validatorRegistrations);

    assertThat(result).isCompleted();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<SszList<SignedValidatorRegistration>> argumentCaptor =
        ArgumentCaptor.forClass(SszList.class);

    verify(validatorApiChannel).registerValidators(argumentCaptor.capture());

    final SszList<SignedValidatorRegistration> capturedRegistrations = argumentCaptor.getValue();

    assertThat(capturedRegistrations)
        .hasSize(5)
        .map(signedRegistration -> signedRegistration.getMessage().getPublicKey())
        .doesNotContainAnyElementsOf(exitedOrUnknownKeys);
  }
}
