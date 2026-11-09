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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.Merkleizable;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.AttestationProcessingResult;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.versions.deneb.util.AttestationUtilDeneb;
import tech.pegasys.teku.spec.logic.versions.electra.util.AttestationUtilElectra;
import tech.pegasys.teku.spec.logic.versions.phase0.util.AttestationUtilPhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {PHASE0, DENEB, ELECTRA})
class AttestationUtilTest {

  private final MiscHelpers miscHelpers = mock(MiscHelpers.class);
  private final BeaconStateAccessors beaconStateAccessors = mock(BeaconStateAccessors.class);
  private final AsyncBLSSignatureVerifier asyncBLSSignatureVerifier =
      mock(AsyncBLSSignatureVerifier.class);
  final Int2IntOpenHashMap committeesSize = new Int2IntOpenHashMap();

  private Spec spec;
  private DataStructureUtil dataStructureUtil;

  private AttestationUtil attestationUtil;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = spy(specContext.getSpec());
    dataStructureUtil = specContext.getDataStructureUtil();
    final SpecVersion specVersion = spec.forMilestone(specContext.getSpecMilestone());
    doAnswer(invocation -> createBeaconCommittee(specVersion, invocation.getArgument(2)))
        .when(beaconStateAccessors)
        .getBeaconCommittee(any(), any(), any());
    doAnswer(invocation -> committeesSize).when(spec).getBeaconCommitteesSize(any(), any());
    when(beaconStateAccessors.getValidatorPubKey(any(), any()))
        .thenReturn(Optional.of(dataStructureUtil.randomPublicKey()));
    when(beaconStateAccessors.getDomain(any(), any(), any(), any()))
        .thenReturn(dataStructureUtil.randomBytes32());
    when(miscHelpers.computeSigningRoot(any(Merkleizable.class), any(Bytes32.class)))
        .thenReturn(dataStructureUtil.randomBytes(10));
    when(asyncBLSSignatureVerifier.verify(anyList(), any(Bytes.class), any(BLSSignature.class)))
        .thenReturn(SafeFuture.completedFuture(true));
    attestationUtil = spec.getGenesisSpec().getAttestationUtil();

    attestationUtil =
        switch (specContext.getSpecMilestone()) {
          case PHASE0 ->
              new AttestationUtilPhase0(
                  spec.getGenesisSpecConfig(),
                  specVersion.getSchemaDefinitions(),
                  beaconStateAccessors,
                  miscHelpers);
          case DENEB ->
              new AttestationUtilDeneb(
                  spec.getGenesisSpecConfig(),
                  specVersion.getSchemaDefinitions(),
                  beaconStateAccessors,
                  miscHelpers);
          case ELECTRA ->
              new AttestationUtilElectra(
                  spec.getGenesisSpecConfig(),
                  specVersion.getSchemaDefinitions(),
                  beaconStateAccessors,
                  miscHelpers);
          default -> throw new UnsupportedOperationException("unsupported milestone");
        };
  }

  @TestTemplate
  void noValidationIsDoneIfAttestationIsAlreadyValidAndIndexedAttestationIsPresent() {
    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, dataStructureUtil.randomAttestation());
    validatableAttestation.setValidIndexedAttestation();
    final IndexedAttestationLight indexedAttestation =
        IndexedAttestationLight.fromSsz(
            dataStructureUtil.randomIndexedAttestation(
                UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3)));
    validatableAttestation.setIndexedAttestation(indexedAttestation);

    final SafeFuture<AttestationProcessingResult> result =
        executeValidation(validatableAttestation);

    assertThat(result).isCompletedWithValue(AttestationProcessingResult.SUCCESSFUL);

    assertThat(validatableAttestation.isValidIndexedAttestation()).isTrue();
    assertThat(validatableAttestation.getIndexedAttestation()).hasValue(indexedAttestation);

    verifyNoInteractions(beaconStateAccessors, miscHelpers, asyncBLSSignatureVerifier);
  }

  @TestTemplate
  void createsAndValidatesIndexedAttestation(final SpecContext specContext) {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, attestation);

    final SafeFuture<AttestationProcessingResult> result =
        executeValidation(validatableAttestation);

    assertThat(result).isCompletedWithValue(AttestationProcessingResult.SUCCESSFUL);

    assertThat(validatableAttestation.getAttestation())
        .isSameAs(validatableAttestation.getUnconvertedAttestation());
    assertThat(validatableAttestation.getAttestation().isSingleAttestation()).isFalse();
    assertThat(validatableAttestation.isValidIndexedAttestation()).isTrue();
    assertThat(validatableAttestation.getIndexedAttestation()).isPresent();
    assertThat(validatableAttestation.getCommitteeShufflingSeed()).isPresent();
    if (specContext.getSpecMilestone().isGreaterThanOrEqualTo(ELECTRA)) {
      assertThat(validatableAttestation.getCommitteesSize()).contains(committeesSize);
    } else {
      assertThat(validatableAttestation.getCommitteesSize()).isEmpty();
    }

    verify(asyncBLSSignatureVerifier).verify(anyList(), any(Bytes.class), any(BLSSignature.class));
  }

  @TestTemplate
  void createsValidatesIndexedAttestationAndConvertsFromSingleAttestation(
      final SpecContext specContext) {
    specContext.assumeElectraActive();

    final UInt64 committeeIndex = UInt64.valueOf(2);
    final UInt64 validatorIndex = UInt64.valueOf(5);

    final Attestation attestation =
        dataStructureUtil.randomSingleAttestation(validatorIndex, committeeIndex);
    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.fromNetwork(spec, attestation, 1);

    // we want to make sure that we do signature verification before we do the committee lookup,
    // that may trigger shuffling calculation
    // To do that, let's control signature verification result
    final SafeFuture<Boolean> signatureVerificationResult = new SafeFuture<>();
    when(asyncBLSSignatureVerifier.verify(anyList(), any(Bytes.class), any(BLSSignature.class)))
        .thenReturn(signatureVerificationResult);

    // let validator be the second in the committee
    doAnswer(invocation -> IntList.of(validatorIndex.intValue() + 1, validatorIndex.intValue()))
        .when(beaconStateAccessors)
        .getBeaconCommittee(any(), any(), any());

    final SafeFuture<AttestationProcessingResult> result =
        executeValidation(validatableAttestation);

    // no beacon committee lookup before signature verification
    verify(beaconStateAccessors, never()).getBeaconCommittee(any(), any(), any());

    // validation still in progress
    assertThat(result).isNotDone();

    // signature verification completed
    signatureVerificationResult.complete(true);

    // now we should have the beacon committee lookup
    verify(beaconStateAccessors).getBeaconCommittee(any(), any(), any());

    // now we have successful validation
    assertThat(result).isCompletedWithValue(AttestationProcessingResult.SUCCESSFUL);

    assertThat(validatableAttestation.getUnconvertedAttestation().isSingleAttestation())
        .describedAs("Original is still single attestation")
        .isTrue();
    assertThat(validatableAttestation.getAttestation().isSingleAttestation())
        .describedAs("Aggregated format is not single attestation")
        .isFalse();
    assertThat(validatableAttestation.getAttestation().getAggregationBits().getBitCount())
        .describedAs("Refers to a single validator")
        .isEqualTo(1);
    assertThat(validatableAttestation.getAttestation().getCommitteeIndicesRequired())
        .describedAs("Refers to the correct committee")
        .containsExactly(UInt64.valueOf(2));

    assertThat(validatableAttestation.isValidIndexedAttestation()).isTrue();
    assertThat(validatableAttestation.getIndexedAttestation()).isPresent();
    assertThat(validatableAttestation.getCommitteeShufflingSeed()).isPresent();
    if (specContext.getSpecMilestone().isGreaterThanOrEqualTo(ELECTRA)) {
      assertThat(validatableAttestation.getCommitteesSize()).contains(committeesSize);
    } else {
      assertThat(validatableAttestation.getCommitteesSize()).isEmpty();
    }
  }

  @TestTemplate
  void createsButDoesNotValidateIndexedAttestationBecauseItHasAlreadyBeenValidated(
      final SpecContext specContext) {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    // reorged block does not require indexed attestation validation, however it requires the
    // creation of it
    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.fromReorgedBlock(spec, attestation);

    final SafeFuture<AttestationProcessingResult> result =
        executeValidation(validatableAttestation);

    assertThat(result).isCompletedWithValue(AttestationProcessingResult.SUCCESSFUL);

    assertThat(validatableAttestation.isValidIndexedAttestation()).isTrue();
    assertThat(validatableAttestation.getIndexedAttestation()).isPresent();
    assertThat(validatableAttestation.getCommitteeShufflingSeed()).isPresent();
    if (specContext.getSpecMilestone().isGreaterThanOrEqualTo(ELECTRA)) {
      assertThat(validatableAttestation.getCommitteesSize()).contains(committeesSize);
    } else {
      assertThat(validatableAttestation.getCommitteesSize()).isEmpty();
    }

    verifyNoInteractions(miscHelpers, asyncBLSSignatureVerifier);
  }

  @TestTemplate
  void getIndexedAttestationGetsBeaconCommitteeWhenAttestationIsNotSingle(
      final SpecContext specContext) {
    final Attestation attestation = dataStructureUtil.randomAttestation();
    final BeaconState beaconState = dataStructureUtil.randomBeaconState();
    final IndexedAttestationLight indexedAttestation =
        attestationUtil.getIndexedAttestation(beaconState, attestation);

    if (specContext.getSpecMilestone().isGreaterThanOrEqualTo(ELECTRA)) {
      attestation
          .getCommitteeIndicesRequired()
          .forEach(
              index ->
                  verify(beaconStateAccessors)
                      .getBeaconCommittee(beaconState, attestation.getData().getSlot(), index));
    } else {
      verify(beaconStateAccessors)
          .getBeaconCommittee(
              beaconState, attestation.getData().getSlot(), attestation.getData().getIndex());
    }

    assertThat(indexedAttestation.data()).isEqualTo(attestation.getData());
    assertThat(indexedAttestation.signature()).isEqualTo(attestation.getAggregateSignature());
  }

  @TestTemplate
  void getIndexedAttestationNoQueryToBeaconCommitteeWhenSingleAttestation(
      final SpecContext specContext) {
    specContext.assumeElectraActive();

    final Attestation attestation = dataStructureUtil.randomSingleAttestation();
    final BeaconState beaconState = dataStructureUtil.randomBeaconState();

    final IndexedAttestationLight indexedAttestation =
        attestationUtil.getIndexedAttestation(beaconState, attestation);

    verify(beaconStateAccessors, never()).getBeaconCommittee(any(), any(), any());

    assertThat(indexedAttestation.attestingIndices())
        .containsExactly(attestation.getValidatorIndexRequired());
    assertThat(indexedAttestation.data()).isEqualTo(attestation.getData());
    assertThat(indexedAttestation.signature()).isEqualTo(attestation.getAggregateSignature());
  }

  private SafeFuture<AttestationProcessingResult> executeValidation(
      final ValidatableAttestation validatableAttestation) {
    return attestationUtil.isValidIndexedAttestationAsync(
        dataStructureUtil.randomFork(),
        dataStructureUtil.randomBeaconState(),
        validatableAttestation,
        asyncBLSSignatureVerifier);
  }

  static final List<UInt64> KNOWN_COMMITTEES = new ArrayList<>();

  private IntList createBeaconCommittee(
      final SpecVersion specVersion, final UInt64 committeeIndex) {
    // we have to generate non overlapping committees, committeeIndex is a random UInt64, so we
    // can't use it directly
    int position = KNOWN_COMMITTEES.indexOf(committeeIndex);
    if (position == -1) {
      KNOWN_COMMITTEES.add(committeeIndex);
      position = KNOWN_COMMITTEES.size() - 1;
    }

    final int maxValidatorsPerCommittee = specVersion.getConfig().getMaxValidatorsPerCommittee();
    final int validatorIndexTranslation = position * maxValidatorsPerCommittee;

    final int[] committee =
        IntStream.rangeClosed(0, maxValidatorsPerCommittee - 1)
            .map(i -> i + validatorIndexTranslation)
            .toArray();
    return IntList.of(committee);
  }
}
