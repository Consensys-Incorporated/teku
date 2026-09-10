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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.saveForFuture;

import com.google.errorprone.annotations.FormatMethod;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

@TestSpecContext(milestone = {SpecMilestone.GLOAS})
public class ProposerPreferencesGossipValidatorTest {

  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private ProposerPreferencesGossipValidator validator;

  private BeaconState state;
  private UInt64 proposalSlot;
  private UInt64 validatorIndex;
  private Bytes32 dependentRoot;
  private UInt64 lookaheadEpoch;
  private UInt64 lookaheadEpochStartSlot;
  private SignedProposerPreferences signedProposerPreferences;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();

    validator =
        new ProposerPreferencesGossipValidator(spec, gossipValidationHelper, recentChainData);

    state = dataStructureUtil.randomBeaconState();
    final BeaconStateGloas gloasState = BeaconStateGloas.required(state);

    // First entry of the next epoch portion of the proposer lookahead
    final int slotsPerEpoch = spec.atSlot(state.getSlot()).getConfig().getSlotsPerEpoch();
    validatorIndex = gloasState.getProposerLookahead().getElement(slotsPerEpoch);

    final UInt64 currentEpoch = spec.computeEpochAtSlot(state.getSlot());
    proposalSlot = spec.computeStartSlotAtEpoch(currentEpoch.plus(1));
    dependentRoot = dataStructureUtil.randomBytes32();

    signedProposerPreferences =
        createSignedProposerPreferences(dependentRoot, proposalSlot, validatorIndex);

    lookaheadEpoch =
        spec.computeEpochAtSlot(proposalSlot)
            .minusMinZero(spec.atSlot(proposalSlot).getConfig().getMinSeedLookahead());
    lookaheadEpochStartSlot = spec.computeStartSlotAtEpoch(lookaheadEpoch);

    when(gossipValidationHelper.hasSlotStarted(proposalSlot)).thenReturn(false);
    when(gossipValidationHelper.isSlotFromFuture(lookaheadEpochStartSlot)).thenReturn(false);
    when(gossipValidationHelper.isBlockAvailable(dependentRoot)).thenReturn(true);
    when(gossipValidationHelper.isPossibleDependentRoot(dependentRoot, lookaheadEpochStartSlot))
        .thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(true);
    when(recentChainData.retrieveCheckpointState(any(Checkpoint.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
  }

  @TestTemplate
  void shouldAccept() {
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(ProposerPreferencesGossipValidator.class, Level.TRACE)) {
      assertThatSafeFuture(validator.validate(signedProposerPreferences))
          .isCompletedWithValue(ACCEPT);
      assertThat(logCaptor.getTraceLogs().getFirst())
          .isEqualTo(
              "ProposerPreferences Gossip Validation Result: ACCEPT, context: "
                  + formatProposerPreferencesContext(signedProposerPreferences));
    }
  }

  @TestTemplate
  void shouldIgnore_whenProposalSlotHasAlreadyStarted() {
    when(gossipValidationHelper.hasSlotStarted(proposalSlot)).thenReturn(true);
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(
            ignorePreferences(signedProposerPreferences, "proposal slot has already started"));
    verify(recentChainData, never()).retrieveCheckpointState(any(Checkpoint.class));
  }

  @TestTemplate
  void shouldIgnore_whenLookaheadEpochHasNotStarted() {
    when(gossipValidationHelper.isSlotFromFuture(lookaheadEpochStartSlot)).thenReturn(true);
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(
            ignorePreferences(
                signedProposerPreferences, "proposer for the proposal slot is not yet known"));
    verify(recentChainData, never()).retrieveCheckpointState(any(Checkpoint.class));
  }

  @TestTemplate
  void shouldIgnore_whenDependentRootIsNotAPossibleDependentBlock() {
    when(gossipValidationHelper.isPossibleDependentRoot(dependentRoot, lookaheadEpochStartSlot))
        .thenReturn(false);
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(
            ignorePreferences(
                signedProposerPreferences, "dependent root is not a possible dependent block"));
    verify(recentChainData, never()).retrieveCheckpointState(any(Checkpoint.class));
  }

  @TestTemplate
  void shouldIgnoreDuplicate_whenOnlyTheValidatorIndexDiffers() {
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(ACCEPT);

    // Only one validator can be the proposer for a (dependent root, proposal slot) pair, so a
    // message differing only by validator index must not be validated again.
    final SignedProposerPreferences otherValidatorPreferences =
        createSignedProposerPreferences(dependentRoot, proposalSlot, validatorIndex.plus(1));
    assertThatSafeFuture(validator.validate(otherValidatorPreferences))
        .isCompletedWithValue(ignorePreferences(otherValidatorPreferences, "already received"));
    verify(recentChainData, times(1)).retrieveCheckpointState(any(Checkpoint.class));
  }

  @TestTemplate
  void shouldSaveForFuture_whenDependentRootBlockNotSeen() {
    when(gossipValidationHelper.isBlockAvailable(dependentRoot)).thenReturn(false);
    final InternalValidationResult expectedResult =
        savePreferencesForFuture(
            signedProposerPreferences,
            "dependent root has not been seen; saving for future processing");
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(ProposerPreferencesGossipValidator.class, Level.TRACE)) {
      assertThatSafeFuture(validator.validate(signedProposerPreferences))
          .isCompletedWithValue(expectedResult);
      assertThat(logCaptor.getTraceLogs().getFirst())
          .isEqualTo(formatProposerPreferencesValidationLog(expectedResult));
    }
    verify(recentChainData, never()).retrieveCheckpointState(any(Checkpoint.class));
  }

  @TestTemplate
  void shouldReject_whenDependentRootBlockIsNotBeforeLookaheadEpoch() {
    when(recentChainData.getSlotForBlockRoot(dependentRoot))
        .thenReturn(Optional.of(lookaheadEpochStartSlot));

    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(
            rejectPreferences(
                signedProposerPreferences,
                "dependent root is at slot %s but must be before the proposer lookahead epoch start slot %s",
                lookaheadEpochStartSlot,
                lookaheadEpochStartSlot));
    verify(recentChainData, never()).retrieveCheckpointState(any(Checkpoint.class));
  }

  @TestTemplate
  void shouldReject_whenCheckpointStateRetrievalFails() {
    when(recentChainData.retrieveCheckpointState(any(Checkpoint.class)))
        .thenReturn(SafeFuture.failedFuture(new IllegalStateException("checkpoint state failed")));
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(
            rejectPreferences(
                signedProposerPreferences,
                "unable to generate checkpoint state for lookahead epoch %s",
                lookaheadEpoch));
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
  }

  @TestTemplate
  void shouldIgnore_whenAlreadySeen() {
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(ACCEPT);

    final InternalValidationResult expectedResult =
        ignorePreferences(signedProposerPreferences, "already received");
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(ProposerPreferencesGossipValidator.class, Level.TRACE)) {
      assertThatSafeFuture(validator.validate(signedProposerPreferences))
          .isCompletedWithValue(expectedResult);
      assertThat(logCaptor.getTraceLogs().getFirst())
          .isEqualTo(formatProposerPreferencesValidationLog(expectedResult));
    }
    verify(recentChainData, times(1)).retrieveCheckpointState(any(Checkpoint.class));
  }

  @TestTemplate
  void shouldReject_whenValidatorIndexDoesNotMatchLookahead() {
    final UInt64 wrongValidatorIndex = validatorIndex.plus(9999);
    final SignedProposerPreferences wrongIndexPreferences =
        createSignedProposerPreferences(dependentRoot, proposalSlot, wrongValidatorIndex);

    assertThatSafeFuture(validator.validate(wrongIndexPreferences))
        .isCompletedWithValue(
            rejectPreferences(
                wrongIndexPreferences,
                "validator index does not match expected proposer %s",
                validatorIndex));
    verify(gossipValidationHelper, never())
        .isSignatureValidWithRespectToProposerIndex(any(), any(), any(), any());
  }

  @TestTemplate
  void shouldReject_whenSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(false);

    final String expectedMessage =
        formatProposerPreferencesMessage(signedProposerPreferences, "invalid signature");
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(ProposerPreferencesGossipValidator.class, Level.TRACE)) {
      assertThatSafeFuture(validator.validate(signedProposerPreferences))
          .isCompletedWithValue(reject("%s", expectedMessage));
      assertThat(logCaptor.getTraceLogs().getFirst())
          .isEqualTo(formatProposerPreferencesValidationLog(reject("%s", expectedMessage)));
    }
  }

  @TestTemplate
  void shouldNotMarkAsSeenIfValidationFails() {
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(false);

    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(rejectPreferences(signedProposerPreferences, "invalid signature"));

    // Fix the signature mock
    when(gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
            any(), any(), any(), any()))
        .thenReturn(true);

    // Should accept now, not ignore
    assertThatSafeFuture(validator.validate(signedProposerPreferences))
        .isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnore_whenDuplicateArrivesWhileValidating() {
    // Create a slow checkpoint state future
    final SafeFuture<Optional<BeaconState>> slowStateFuture = new SafeFuture<>();
    when(recentChainData.retrieveCheckpointState(any(Checkpoint.class)))
        .thenReturn(slowStateFuture);

    // Start first validation (will block on state)
    final SafeFuture<InternalValidationResult> firstResult =
        validator.validate(signedProposerPreferences);

    // Reset to return completed state for second validation
    when(recentChainData.retrieveCheckpointState(any(Checkpoint.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    // Second validation completes first
    final SafeFuture<InternalValidationResult> secondResult =
        validator.validate(signedProposerPreferences);
    assertThatSafeFuture(secondResult).isCompletedWithValue(ACCEPT);

    // Now complete first validation - should be ignored due to race condition
    slowStateFuture.complete(Optional.of(state));
    assertThatSafeFuture(firstResult)
        .isCompletedWithValue(ignorePreferences(signedProposerPreferences, "already received"));
  }

  @FormatMethod
  private InternalValidationResult rejectPreferences(
      final SignedProposerPreferences signedPreferences,
      final String descriptionTemplate,
      final Object... args) {
    return reject(
        "%s", formatProposerPreferencesMessage(signedPreferences, descriptionTemplate, args));
  }

  @FormatMethod
  private InternalValidationResult ignorePreferences(
      final SignedProposerPreferences signedPreferences,
      final String descriptionTemplate,
      final Object... args) {
    return ignore(
        "%s", formatProposerPreferencesMessage(signedPreferences, descriptionTemplate, args));
  }

  @FormatMethod
  private InternalValidationResult savePreferencesForFuture(
      final SignedProposerPreferences signedPreferences,
      final String descriptionTemplate,
      final Object... args) {
    return saveForFuture(
        "%s", formatProposerPreferencesMessage(signedPreferences, descriptionTemplate, args));
  }

  @FormatMethod
  private String formatProposerPreferencesMessage(
      final SignedProposerPreferences signedPreferences,
      final String descriptionTemplate,
      final Object... args) {
    return String.format(
        "%s: %s",
        formatProposerPreferencesContext(signedPreferences),
        String.format(descriptionTemplate, args));
  }

  private String formatProposerPreferencesContext(
      final SignedProposerPreferences signedPreferences) {
    final ProposerPreferences preferences = signedPreferences.getMessage();
    return String.format(
        "Proposer preferences (validator index %s, proposal slot %s, dependent root %s)",
        preferences.getValidatorIndex(),
        preferences.getProposalSlot(),
        preferences.getDependentRoot());
  }

  private String formatProposerPreferencesValidationLog(
      final InternalValidationResult validationResult) {
    return String.format(
        "ProposerPreferences Gossip Validation Result: %s, reason: %s",
        validationResult.code(), validationResult.getDescription().orElseThrow());
  }

  private SignedProposerPreferences createSignedProposerPreferences(
      final Bytes32 dependentRoot, final UInt64 proposalSlot, final UInt64 validatorIndex) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(proposalSlot).getSchemaDefinitions());
    final ProposerPreferencesSchema proposerPreferencesSchema =
        schemaDefinitions.getProposerPreferencesSchema();
    final SignedProposerPreferencesSchema signedProposerPreferencesSchema =
        schemaDefinitions.getSignedProposerPreferencesSchema();

    final ProposerPreferences proposerPreferences =
        proposerPreferencesSchema.create(
            dependentRoot,
            proposalSlot,
            validatorIndex,
            dataStructureUtil.randomEth1Address(),
            dataStructureUtil.randomUInt64());

    return signedProposerPreferencesSchema.create(
        proposerPreferences, dataStructureUtil.randomSignature());
  }
}
