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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.logging.Converter.gweiToEth;
import static tech.pegasys.teku.spec.config.Constants.MAX_SLOTS_TO_TRACK_BUILDERS_BIDS;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.PAYLOAD_BUILDER_VERSION;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.saveForFuture;

import com.google.errorprone.annotations.FormatMethod;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;

@TestSpecContext(milestone = {SpecMilestone.GLOAS})
public class ExecutionPayloadBidGossipValidatorTest {
  private static final int MIN_BID_INCREMENT_PERCENTAGE = 1;
  private static final UInt64 DEFAULT_GAS_LIMIT = UInt64.valueOf(60_000_000);

  private final Spec spec = mock(Spec.class);
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final ProposerPreferencesManager proposerPreferencesManager =
      mock(ProposerPreferencesManager.class);
  private ExecutionPayloadBidGossipValidator bidValidator;
  private DataStructureUtil dataStructureUtil;
  private SignedExecutionPayloadBid signedBid;
  private ExecutionPayloadBid bid;
  private UInt64 slot;
  private UInt64 builderIndex;
  private Bytes32 parentBlockRoot;
  private Bytes32 parentBlockHash;
  private BeaconState postState;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    this.dataStructureUtil = specContext.getDataStructureUtil();
    this.bidValidator =
        new ExecutionPayloadBidGossipValidator(
            spec, gossipValidationHelper, proposerPreferencesManager, MIN_BID_INCREMENT_PERCENTAGE);

    final ExecutionPayloadBid randomBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(UInt64.ZERO).getMessage();
    bid =
        randomBid
            .getSchema()
            .create(
                randomBid.getParentBlockHash(),
                randomBid.getParentBlockRoot(),
                randomBid.getBlockHash(),
                randomBid.getPrevRandao(),
                randomBid.getFeeRecipient(),
                DEFAULT_GAS_LIMIT,
                UInt64.ZERO,
                randomBid.getSlot(),
                randomBid.getValue(),
                randomBid.getExecutionPayment(),
                randomBid.getBlobKzgCommitments(),
                randomBid.getExecutionRequestsRoot());
    signedBid = dataStructureUtil.randomSignedExecutionPayloadBid(bid);
    slot = bid.getSlot();
    builderIndex = bid.getBuilderIndex();
    parentBlockRoot = bid.getParentBlockRoot();
    parentBlockHash = bid.getParentBlockHash();
    // Replace the random builders so that the bid's builder index is in range and every builder
    // carries PAYLOAD_BUILDER_VERSION; randomBuilder() assigns a random version, which the
    // payload-builder-version rule would reject.
    postState =
        dataStructureUtil
            .randomBeaconState()
            .updated(
                mutableState -> {
                  final SszMutableList<Builder> builders =
                      MutableBeaconStateGloas.required(mutableState).getBuilders();
                  builders.clear();
                  for (int i = 0; i < 8; i++) {
                    builders.append(
                        dataStructureUtil
                            .builderBuilder()
                            .version(PAYLOAD_BUILDER_VERSION)
                            .build());
                  }
                });

    final ProposerPreferences proposerPreferences = mock(ProposerPreferences.class);
    when(proposerPreferences.getFeeRecipient()).thenReturn(bid.getFeeRecipient());
    when(proposerPreferences.getTargetGasLimit()).thenReturn(bid.getGasLimit());
    when(proposerPreferencesManager.getProposerPreferences(any()))
        .thenReturn(Optional.of(proposerPreferences));

    when(gossipValidationHelper.isSlotCurrentOrNext(slot)).thenReturn(true);
    when(gossipValidationHelper.getGasLimitForExecutionPayload(parentBlockRoot, parentBlockHash))
        .thenReturn(Optional.of(bid.getGasLimit()));
    when(gossipValidationHelper.isBidCompatibleWithHead(any())).thenReturn(true);
    when(gossipValidationHelper.getSlotForBlockRoot(parentBlockRoot))
        .thenReturn(Optional.of(slot.decrement()));
    when(gossipValidationHelper.getParentStateInBlockEpoch(slot.decrement(), parentBlockRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    when(gossipValidationHelper.isActiveBuilder(builderIndex, postState, slot)).thenReturn(true);
    when(gossipValidationHelper.getRandaoMixForCurrentEpoch(postState, slot))
        .thenReturn(bid.getPrevRandao());
    when(gossipValidationHelper.builderHasEnoughBalanceForBid(
            bid.getValue(), builderIndex, postState, slot))
        .thenReturn(true);
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(true);
    final SpecVersion specVersion = mock(SpecVersion.class);
    final MiscHelpers miscHelpers = mock(MiscHelpers.class);
    final Bytes32 signingRoot = Bytes32.random();
    when(miscHelpers.computeSigningRoot(eq(signedBid.getMessage()), any())).thenReturn(signingRoot);
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    when(spec.atSlot(slot)).thenReturn(specVersion);
    when(spec.getActiveValidatorIndices(postState, slot))
        .thenReturn(IntList.of(builderIndex.intValue()));
  }

  @TestTemplate
  void shouldAccept() {
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(ExecutionPayloadBidGossipValidator.class, Level.TRACE)) {
      assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);
      assertThat(logCaptor.getTraceLogs().getFirst())
          .isEqualTo(
              "ExecutionPayloadBid Gossip Validation Result: ACCEPT, context: "
                  + formatBidContext(bid));
    }
  }

  @TestTemplate
  void shouldReject_whenExecutionPaymentIsNonZero() {
    final SignedExecutionPayloadBid bidWithPayment =
        dataStructureUtil.randomSignedExecutionPayloadBid(UInt64.ONE);
    assertThatSafeFuture(bidValidator.validate(bidWithPayment))
        .isCompletedWithValue(
            rejectBid(
                bidWithPayment,
                "execution payment should be 0 but was %s",
                bidWithPayment.getMessage().getExecutionPayment()));
  }

  @TestTemplate
  void shouldIgnore_whenSlotIsNotCurrentOrNext() {
    when(gossipValidationHelper.isSlotCurrentOrNext(slot)).thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            ignoreBid(signedBid, "must be for current or next slot but was for slot %s", slot));
  }

  @TestTemplate
  void shouldSaveForFuture_whenProposerPreferencesNotSeen() {
    when(proposerPreferencesManager.getProposerPreferences(slot)).thenReturn(Optional.empty());
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            saveBidForFuture(
                signedBid, "no proposer preferences available; saving for future processing"));
  }

  @TestTemplate
  void shouldIgnore_whenFeeRecipientDoesNotMatchProposerPreferences() {
    final ProposerPreferences mismatchedPreferences = mock(ProposerPreferences.class);
    when(mismatchedPreferences.getFeeRecipient()).thenReturn(dataStructureUtil.randomEth1Address());
    when(mismatchedPreferences.getTargetGasLimit()).thenReturn(bid.getGasLimit());
    when(proposerPreferencesManager.getProposerPreferences(slot))
        .thenReturn(Optional.of(mismatchedPreferences));

    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            ignoreBid(
                signedBid,
                "fee recipient %s does not match proposer preferences fee recipient %s",
                bid.getFeeRecipient(),
                mismatchedPreferences.getFeeRecipient()));
  }

  @TestTemplate
  void shouldIgnore_whenGasLimitIsNotCompatibleWithTargetGasLimit() {
    final UInt64 parentGasLimit = UInt64.valueOf(60_000_000);
    final UInt64 bidGasLimit = UInt64.valueOf(59_999_999);
    final UInt64 targetGasLimit = UInt64.valueOf(60_000_001);
    final SignedExecutionPayloadBid bidWithGasLimit = signedBidWithGasLimit(bidGasLimit);
    mockProposerPreferences(bidWithGasLimit, targetGasLimit);
    when(gossipValidationHelper.getGasLimitForExecutionPayload(parentBlockRoot, parentBlockHash))
        .thenReturn(Optional.of(parentGasLimit));

    assertThatSafeFuture(bidValidator.validate(bidWithGasLimit))
        .isCompletedWithValue(
            ignoreBid(
                bidWithGasLimit,
                "gas limit %s is not compatible with parent gas limit %s and proposer preferences target gas limit %s",
                bidWithGasLimit.getMessage().getGasLimit(),
                parentGasLimit,
                targetGasLimit));
  }

  @TestTemplate
  void shouldAccept_whenTargetGasLimitExceedsAllowedIncreaseAndBidUsesMaximumAllowedGasLimit() {
    final UInt64 parentGasLimit = UInt64.valueOf(60_000_000);
    final UInt64 bidGasLimit = UInt64.valueOf(60_058_592);
    final UInt64 targetGasLimit = UInt64.valueOf(100_000_000);
    final SignedExecutionPayloadBid bidWithGasLimit = signedBidWithGasLimit(bidGasLimit);
    mockProposerPreferences(bidWithGasLimit, targetGasLimit);
    when(gossipValidationHelper.getGasLimitForExecutionPayload(parentBlockRoot, parentBlockHash))
        .thenReturn(Optional.of(parentGasLimit));

    assertThatSafeFuture(bidValidator.validate(bidWithGasLimit)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnore_whenTargetGasLimitExceedsAllowedIncreaseAndBidGasLimitIsTooHigh() {
    final UInt64 parentGasLimit = UInt64.valueOf(60_000_000);
    final UInt64 bidGasLimit = UInt64.valueOf(60_058_593);
    final UInt64 targetGasLimit = UInt64.valueOf(100_000_000);
    final SignedExecutionPayloadBid bidWithGasLimit = signedBidWithGasLimit(bidGasLimit);
    mockProposerPreferences(bidWithGasLimit, targetGasLimit);
    when(gossipValidationHelper.getGasLimitForExecutionPayload(parentBlockRoot, parentBlockHash))
        .thenReturn(Optional.of(parentGasLimit));

    assertThatSafeFuture(bidValidator.validate(bidWithGasLimit))
        .isCompletedWithValue(
            ignoreBid(
                bidWithGasLimit,
                "gas limit %s is not compatible with parent gas limit %s and proposer preferences target gas limit %s",
                bidGasLimit,
                parentGasLimit,
                targetGasLimit));
  }

  @TestTemplate
  void shouldAccept_whenTargetGasLimitExceedsAllowedDecreaseAndBidUsesMinimumAllowedGasLimit() {
    final UInt64 parentGasLimit = UInt64.valueOf(60_000_000);
    final UInt64 bidGasLimit = UInt64.valueOf(59_941_408);
    final UInt64 targetGasLimit = UInt64.valueOf(30_000_000);
    final SignedExecutionPayloadBid bidWithGasLimit = signedBidWithGasLimit(bidGasLimit);
    mockProposerPreferences(bidWithGasLimit, targetGasLimit);
    when(gossipValidationHelper.getGasLimitForExecutionPayload(parentBlockRoot, parentBlockHash))
        .thenReturn(Optional.of(parentGasLimit));

    assertThatSafeFuture(bidValidator.validate(bidWithGasLimit)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldSaveForFuture_whenParentGasLimitIsUnavailable() {
    when(gossipValidationHelper.getGasLimitForExecutionPayload(parentBlockRoot, parentBlockHash))
        .thenReturn(Optional.empty());

    final InternalValidationResult expectedResult =
        saveBidForFuture(
            signedBid,
            "parent execution payload gas limit is unavailable for parent block hash %s; saving for future processing",
            parentBlockHash);
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(ExecutionPayloadBidGossipValidator.class, Level.TRACE)) {
      assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(expectedResult);
      assertThat(logCaptor.getTraceLogs().getFirst())
          .isEqualTo(formatBidValidationLog(expectedResult));
    }
  }

  @TestTemplate
  void shouldIgnore_whenAlreadySeen() {
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            ignoreBid(
                signedBid,
                "already received for parent block hash %s and parent block root %s",
                parentBlockHash,
                parentBlockRoot));
  }

  @TestTemplate
  void shouldAcceptBidsFromSameBuilderForDifferentParentBlockRoots() {
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);

    final SignedExecutionPayloadBid bidForDifferentParent =
        signedBidForParent(
            parentBlockHash, dataStructureUtil.randomBytes32(), builderIndex, bid.getValue());
    mockBidValidation(bidForDifferentParent);

    assertThatSafeFuture(bidValidator.validate(bidForDifferentParent)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldAcceptBidsFromSameBuilderForDifferentParentBlockHashes() {
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);

    final SignedExecutionPayloadBid bidForDifferentParent =
        signedBidForParent(
            dataStructureUtil.randomBytes32(), parentBlockRoot, builderIndex, bid.getValue());
    mockBidValidation(bidForDifferentParent);

    assertThatSafeFuture(bidValidator.validate(bidForDifferentParent)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldTrackHighestBidsSeparatelyForDifferentParentBlockRoots() {
    final UInt64 bidValue = UInt64.valueOf(1_000_000_000);
    final SignedExecutionPayloadBid firstBid =
        signedBidForParent(parentBlockHash, parentBlockRoot, builderIndex, bidValue);
    final SignedExecutionPayloadBid bidForDifferentParentRoot =
        signedBidForParent(
            parentBlockHash, dataStructureUtil.randomBytes32(), builderIndex.plus(1), bidValue);
    mockBidValidation(firstBid);
    mockBidValidation(bidForDifferentParentRoot);

    assertThatSafeFuture(bidValidator.validate(firstBid)).isCompletedWithValue(ACCEPT);
    assertThatSafeFuture(bidValidator.validate(bidForDifferentParentRoot))
        .isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldNotCacheHigherBidIfInvalid() {
    final UInt64 lowerValue = UInt64.valueOf(10_000);
    final SignedExecutionPayloadBid lowerValueBid = bidFromBuilder(builderIndex, lowerValue);

    mockBidValidation(lowerValueBid);
    assertThatSafeFuture(bidValidator.validate(lowerValueBid)).isCompletedWithValue(ACCEPT);

    final UInt64 differentBuilderIndex = builderIndex.plus(1);
    final UInt64 higherValue =
        lowerValue.plus(lowerValue.times(2 * MIN_BID_INCREMENT_PERCENTAGE).dividedBy(100));
    final SignedExecutionPayloadBid higherValueInvalidBid =
        bidFromBuilder(differentBuilderIndex, higherValue);

    mockBidValidation(higherValueInvalidBid);
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), eq(higherValueInvalidBid.getMessage().getBuilderIndex()), any(), any()))
        .thenReturn(false);

    assertThatSafeFuture(bidValidator.validate(higherValueInvalidBid))
        .isCompletedWithValue(
            rejectBid(higherValueInvalidBid, "invalid execution payload bid signature"));

    final UInt64 intermediateValue =
        lowerValue.times(200 + (3 * MIN_BID_INCREMENT_PERCENTAGE)).dividedBy(200);
    final UInt64 intermediateBidBuilderIndex = builderIndex.plus(2);
    final SignedExecutionPayloadBid intermediateValueValidBid =
        bidFromBuilder(intermediateBidBuilderIndex, intermediateValue);

    mockBidValidation(intermediateValueValidBid);
    assertThatSafeFuture(bidValidator.validate(intermediateValueValidBid))
        .isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldSaveForFuture_whenParentExecutionContextIsUnknown() {
    when(gossipValidationHelper.getGasLimitForExecutionPayload(parentBlockRoot, parentBlockHash))
        .thenReturn(Optional.empty());
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            saveBidForFuture(
                signedBid,
                "parent execution payload gas limit is unavailable for parent block hash %s; saving for future processing",
                parentBlockHash));
  }

  @TestTemplate
  void shouldIgnore_whenBidIsNotCompatibleWithHead() {
    when(gossipValidationHelper.isBidCompatibleWithHead(bid)).thenReturn(false);

    final String expectedMessage =
        formatBidMessage(
            signedBid,
            "is not compatible with the current head branch (parent block hash %s, parent block root %s)",
            parentBlockHash,
            parentBlockRoot);
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(ExecutionPayloadBidGossipValidator.class, Level.TRACE)) {
      assertThatSafeFuture(bidValidator.validate(signedBid))
          .isCompletedWithValue(ignore(expectedMessage));
      assertThat(logCaptor.getTraceLogs().getFirst())
          .isEqualTo(formatBidValidationLog(ignore(expectedMessage)));
    }
  }

  @TestTemplate
  void shouldSaveForFuture_whenParentBlockIsNotAvailable() {
    when(gossipValidationHelper.getSlotForBlockRoot(parentBlockRoot)).thenReturn(Optional.empty());
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            saveBidForFuture(
                signedBid,
                "parent block with root %s is unknown; saving for future processing",
                parentBlockRoot));
  }

  @TestTemplate
  void shouldReject_whenBidSlotIsNotGreaterThanParentBlockSlot() {
    when(gossipValidationHelper.getSlotForBlockRoot(parentBlockRoot)).thenReturn(Optional.of(slot));

    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            rejectBid(signedBid, "slot %s is not greater than parent block slot %s", slot, slot));
  }

  @TestTemplate
  void shouldReject_whenBlockHashEqualsParentBlockHash() {
    final SignedExecutionPayloadBid bidWithMatchingBlockHashes =
        signedBidWithBlockHash(parentBlockHash);

    assertThatSafeFuture(bidValidator.validate(bidWithMatchingBlockHashes))
        .isCompletedWithValue(
            rejectBid(bidWithMatchingBlockHashes, "block hash and parent block hash are the same"));
  }

  @TestTemplate
  void shouldSaveForFuture_whenStateIsUnavailable() {
    when(gossipValidationHelper.getParentStateInBlockEpoch(slot.decrement(), parentBlockRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            saveBidForFuture(
                signedBid,
                "state for parent block root %s at slot %s is unavailable; saving for future processing",
                parentBlockRoot,
                slot.decrement()));
  }

  @TestTemplate
  void shouldReject_whenPrevRandaoIsIncorrect() {
    final Bytes32 incorrectRandaoMix = dataStructureUtil.randomBytes32();
    when(gossipValidationHelper.getRandaoMixForCurrentEpoch(postState, slot))
        .thenReturn(incorrectRandaoMix);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            rejectBid(
                signedBid,
                "prev randao %s does not match expected RANDAO mix %s",
                bid.getPrevRandao(),
                incorrectRandaoMix));
  }

  @TestTemplate
  void shouldReject_whenBuilderIndexIsInvalid() {
    when(gossipValidationHelper.isActiveBuilder(builderIndex, postState, slot)).thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            rejectBid(
                signedBid, "builder index %s is not valid, active, and non-slashed", builderIndex));
  }

  @TestTemplate
  void shouldIgnore_whenBuilderHasInsufficientBalance() {
    final UInt64 bidValue = UInt64.valueOf(1_000_000_000);
    final SignedExecutionPayloadBid insufficientBalanceBid = bidFromBuilder(builderIndex, bidValue);
    mockBidValidation(insufficientBalanceBid, builderIndex, bidValue);
    when(gossipValidationHelper.builderHasEnoughBalanceForBid(
            bidValue, builderIndex, postState, slot))
        .thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(insufficientBalanceBid))
        .isCompletedWithValue(
            ignoreBid(insufficientBalanceBid, "value exceeds the builder's excess balance"));
  }

  @TestTemplate
  void shouldReject_whenSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    final InternalValidationResult expectedResult =
        rejectBid(signedBid, "invalid execution payload bid signature");
    try (final LogCaptor logCaptor =
        LogCaptor.forClass(ExecutionPayloadBidGossipValidator.class, Level.TRACE)) {
      assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(expectedResult);
      assertThat(logCaptor.getTraceLogs().getFirst())
          .isEqualTo(formatBidValidationLog(expectedResult));
    }
  }

  @TestTemplate
  void shouldIgnoreSeenBid() {
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);
    verify(gossipValidationHelper).getGasLimitForExecutionPayload(parentBlockRoot, parentBlockHash);
    verify(gossipValidationHelper).getParentStateInBlockEpoch(any(), any(), any());
    verify(gossipValidationHelper)
        .isSignatureValidWithRespectToBuilderIndex(any(), any(), any(), any());
    clearInvocations(gossipValidationHelper);

    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(
            ignoreBid(
                signedBid,
                "already received for parent block hash %s and parent block root %s",
                parentBlockHash,
                parentBlockRoot));
  }

  @TestTemplate
  void shouldNotMarkAsSeenIfValidationFails() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(bidValidator.validate(signedBid))
        .isCompletedWithValue(rejectBid(signedBid, "invalid execution payload bid signature"));

    // Fix the signature mock and try again
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(true);

    // It should be accepted now, not ignored
    assertThatSafeFuture(bidValidator.validate(signedBid)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnoreBidWhenBuilderAddedDuringValidation() {
    // Create an incomplete future that we control
    final SafeFuture<Optional<BeaconState>> slowStateFuture = new SafeFuture<>();

    // Mock the first bid's validation to use the slow future
    when(gossipValidationHelper.getParentStateInBlockEpoch(slot.decrement(), parentBlockRoot, slot))
        .thenReturn(slowStateFuture);

    // Start validating the first bid (it will wait on slowStateFuture)
    final SafeFuture<InternalValidationResult> firstBidResult = bidValidator.validate(signedBid);

    // Reset the mock to return completed future for subsequent calls
    when(gossipValidationHelper.getParentStateInBlockEpoch(slot.decrement(), parentBlockRoot, slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));

    // Validate and complete a second bid from the same builder while first is in flight
    final SignedExecutionPayloadBid secondBid =
        dataStructureUtil.randomSignedExecutionPayloadBid(signedBid.getMessage());
    final SafeFuture<InternalValidationResult> secondBidResult = bidValidator.validate(secondBid);

    // Wait for second bid to fully complete and add builder to set
    assertThatSafeFuture(secondBidResult).isCompletedWithValue(ACCEPT);
    secondBidResult.join();

    // Now complete the first bid's validation - it should hit the race condition check
    slowStateFuture.complete(Optional.of(postState));

    // First bid should be ignored because builder was added by second bid
    assertThatSafeFuture(firstBidResult)
        .isCompletedWithValue(
            ignoreBid(
                signedBid,
                "another bid for parent block hash %s and parent block root %s was processed concurrently",
                parentBlockHash,
                parentBlockRoot));
  }

  @TestTemplate
  void shouldEvictOldestSlotAfterMaxSlotsTracked() {
    final UInt64 startSlot = UInt64.valueOf(100);
    final UInt64 sameBuilder = builderIndex;
    final SpecVersion specVersion = mock(SpecVersion.class);
    final MiscHelpers miscHelpers = mock(MiscHelpers.class);
    when(miscHelpers.computeSigningRoot(any(Bytes.class), any(Bytes32.class)))
        .thenReturn(Bytes32.random());
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    when(spec.atSlot(any())).thenReturn(specVersion);

    // Submit bids for MAX_SLOTS_TO_TRACK_BUILDERS_BIDS + 1 slots to fill the cache and evict oldest
    // entries
    for (int i = 0; i < MAX_SLOTS_TO_TRACK_BUILDERS_BIDS + 1; i++) {
      final UInt64 bidValue = UInt64.valueOf(1000 + i);
      final SignedExecutionPayloadBid bid =
          signedBidForParent(
              parentBlockHash, parentBlockRoot, startSlot.plus(i), sameBuilder, bidValue);
      mockBidValidation(bid, sameBuilder, bidValue);
      assertThatSafeFuture(bidValidator.validate(bid)).isCompletedWithValue(ACCEPT);
    }

    // Submit another bid for slot 100 which has been evicted and should be accepted
    final SignedExecutionPayloadBid bidForEvictedSlot =
        signedBidForParent(
            parentBlockHash, parentBlockRoot, startSlot, sameBuilder, UInt64.valueOf(2000));
    mockBidValidation(bidForEvictedSlot, sameBuilder, UInt64.valueOf(2000));
    assertThatSafeFuture(bidValidator.validate(bidForEvictedSlot)).isCompletedWithValue(ACCEPT);

    // Submit another bid for most recent slot which is still in cache and should be ignored
    final UInt64 cachedSlot = startSlot.plus(MAX_SLOTS_TO_TRACK_BUILDERS_BIDS);
    final SignedExecutionPayloadBid bidForCachedSlot =
        signedBidForParent(
            parentBlockHash, parentBlockRoot, cachedSlot, sameBuilder, UInt64.valueOf(3000));
    mockBidValidation(bidForCachedSlot, sameBuilder, UInt64.valueOf(3000));
    assertThatSafeFuture(bidValidator.validate(bidForCachedSlot))
        .isCompletedWithValue(
            ignoreBid(
                bidForCachedSlot,
                "already received for parent block hash %s and parent block root %s",
                parentBlockHash,
                parentBlockRoot));
  }

  @TestTemplate
  void shouldAccept_whenBidMeetsMinimumIncrementThreshold() {
    // First bid with known value
    final UInt64 firstBidValue = UInt64.valueOf(10000);
    final SignedExecutionPayloadBid firstBid =
        signedBidForParent(parentBlockHash, parentBlockRoot, builderIndex, firstBidValue);
    mockBidValidation(firstBid, builderIndex, firstBidValue);
    assertThatSafeFuture(bidValidator.validate(firstBid)).isCompletedWithValue(ACCEPT);

    // Second bid at exactly MIN_BID_INCREMENT_PERCENTAGE higher
    final UInt64 secondBidValue =
        firstBidValue.times(100 + MIN_BID_INCREMENT_PERCENTAGE).dividedBy(100);
    final UInt64 differentBuilderIndex = builderIndex.plus(1);
    final SignedExecutionPayloadBid secondBid =
        signedBidForParent(parentBlockHash, parentBlockRoot, differentBuilderIndex, secondBidValue);

    mockBidValidation(secondBid, differentBuilderIndex, secondBidValue);
    assertThatSafeFuture(bidValidator.validate(secondBid)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnore_whenBidBelowMinimumIncrementThreshold() {
    // First bid with known value
    final UInt64 firstBidValue = UInt64.valueOf(1_000_000_000);
    final SignedExecutionPayloadBid firstBid =
        signedBidForParent(parentBlockHash, parentBlockRoot, builderIndex, firstBidValue);
    mockBidValidation(firstBid, builderIndex, firstBidValue);
    assertThatSafeFuture(bidValidator.validate(firstBid)).isCompletedWithValue(ACCEPT);

    // Second bid at half the threshold percentage (below MIN_BID_INCREMENT_PERCENTAGE threshold)
    final UInt64 secondBidValue =
        firstBidValue.times(200 + MIN_BID_INCREMENT_PERCENTAGE).dividedBy(200);
    final UInt64 differentBuilderIndex = builderIndex.plus(1);
    final SignedExecutionPayloadBid secondBid =
        signedBidForParent(parentBlockHash, parentBlockRoot, differentBuilderIndex, secondBidValue);

    final UInt64 minIncrement = firstBidValue.times(MIN_BID_INCREMENT_PERCENTAGE).dividedBy(100);
    final UInt64 minRequiredBid = firstBidValue.plus(minIncrement);

    mockBidValidation(secondBid, differentBuilderIndex, secondBidValue);
    assertThatSafeFuture(bidValidator.validate(secondBid))
        .isCompletedWithValue(
            ignoreBid(
                secondBid,
                "does not meet minimum increment threshold (%s%%); current highest is %s ETH and minimum required is %s ETH",
                MIN_BID_INCREMENT_PERCENTAGE,
                gweiToEth(firstBidValue),
                gweiToEth(minRequiredBid)));
  }

  @TestTemplate
  void shouldAccept_whenBidWellAboveMinimumIncrementThreshold() {
    // First bid with known value
    final UInt64 firstBidValue = UInt64.valueOf(10000);
    final SignedExecutionPayloadBid firstBid =
        signedBidForParent(parentBlockHash, parentBlockRoot, builderIndex, firstBidValue);
    mockBidValidation(firstBid, builderIndex, firstBidValue);
    assertThatSafeFuture(bidValidator.validate(firstBid)).isCompletedWithValue(ACCEPT);

    // Second bid at 5x the threshold percentage (well above MIN_BID_INCREMENT_PERCENTAGE threshold)
    final UInt64 secondBidValue =
        firstBidValue.times(100 + (5 * MIN_BID_INCREMENT_PERCENTAGE)).dividedBy(100);
    final UInt64 differentBuilderIndex = builderIndex.plus(1);
    final SignedExecutionPayloadBid secondBid =
        signedBidForParent(parentBlockHash, parentBlockRoot, differentBuilderIndex, secondBidValue);

    mockBidValidation(secondBid, differentBuilderIndex, secondBidValue);
    assertThatSafeFuture(bidValidator.validate(secondBid)).isCompletedWithValue(ACCEPT);
  }

  private void mockBidValidation(
      final SignedExecutionPayloadBid bid, final UInt64 builderIndex, final UInt64 bidValue) {
    final ExecutionPayloadBid message = bid.getMessage();
    final UInt64 slot = message.getSlot();
    final ProposerPreferences matchingPreferences = mock(ProposerPreferences.class);
    when(matchingPreferences.getFeeRecipient()).thenReturn(message.getFeeRecipient());
    when(matchingPreferences.getTargetGasLimit()).thenReturn(message.getGasLimit());
    when(proposerPreferencesManager.getProposerPreferences(slot))
        .thenReturn(Optional.of(matchingPreferences));
    when(gossipValidationHelper.isSlotCurrentOrNext(slot)).thenReturn(true);
    when(gossipValidationHelper.getGasLimitForExecutionPayload(
            message.getParentBlockRoot(), message.getParentBlockHash()))
        .thenReturn(Optional.of(message.getGasLimit()));
    when(gossipValidationHelper.getSlotForBlockRoot(message.getParentBlockRoot()))
        .thenReturn(Optional.of(slot.decrement()));
    when(gossipValidationHelper.getParentStateInBlockEpoch(
            slot.decrement(), message.getParentBlockRoot(), slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(postState)));
    when(gossipValidationHelper.isActiveBuilder(builderIndex, postState, slot)).thenReturn(true);
    when(gossipValidationHelper.getRandaoMixForCurrentEpoch(postState, slot))
        .thenReturn(message.getPrevRandao());
    when(gossipValidationHelper.builderHasEnoughBalanceForBid(
            bidValue, builderIndex, postState, slot))
        .thenReturn(true);
  }

  @FormatMethod
  private InternalValidationResult rejectBid(
      final SignedExecutionPayloadBid signedBid,
      final String descriptionTemplate,
      final Object... args) {
    return reject("%s", formatBidMessage(signedBid, descriptionTemplate, args));
  }

  @FormatMethod
  private InternalValidationResult ignoreBid(
      final SignedExecutionPayloadBid signedBid,
      final String descriptionTemplate,
      final Object... args) {
    return ignore(formatBidMessage(signedBid, descriptionTemplate, args));
  }

  @FormatMethod
  private InternalValidationResult saveBidForFuture(
      final SignedExecutionPayloadBid signedBid,
      final String descriptionTemplate,
      final Object... args) {
    return saveForFuture(formatBidMessage(signedBid, descriptionTemplate, args));
  }

  @FormatMethod
  private String formatBidMessage(
      final SignedExecutionPayloadBid signedBid,
      final String descriptionTemplate,
      final Object... args) {
    return String.format(
        "%s: %s",
        formatBidContext(signedBid.getMessage()), String.format(descriptionTemplate, args));
  }

  private String formatBidContext(final ExecutionPayloadBid executionPayloadBid) {
    return String.format(
        "Execution payload bid (builder index %s, slot %s, value %s ETH)",
        executionPayloadBid.getBuilderIndex(),
        executionPayloadBid.getSlot(),
        gweiToEth(executionPayloadBid.getValue()));
  }

  private String formatBidValidationLog(final InternalValidationResult validationResult) {
    return String.format(
        "ExecutionPayloadBid Gossip Validation Result: %s, reason: %s",
        validationResult.code(), validationResult.getDescription().orElseThrow());
  }

  private void mockBidValidation(final SignedExecutionPayloadBid bid) {
    mockBidValidation(bid, bid.getMessage().getBuilderIndex(), bid.getMessage().getValue());
  }

  private SignedExecutionPayloadBid bidFromBuilder(
      final UInt64 builderIndex, final UInt64 bidValue) {
    return signedBidForParent(parentBlockHash, parentBlockRoot, builderIndex, bidValue);
  }

  private SignedExecutionPayloadBid signedBidWithGasLimit(final UInt64 gasLimit) {
    final ExecutionPayloadBid bidWithGasLimit =
        bid.getSchema()
            .create(
                bid.getParentBlockHash(),
                bid.getParentBlockRoot(),
                bid.getBlockHash(),
                bid.getPrevRandao(),
                bid.getFeeRecipient(),
                gasLimit,
                bid.getBuilderIndex(),
                bid.getSlot(),
                bid.getValue(),
                bid.getExecutionPayment(),
                bid.getBlobKzgCommitments(),
                bid.getExecutionRequestsRoot());
    return dataStructureUtil.randomSignedExecutionPayloadBid(bidWithGasLimit);
  }

  private SignedExecutionPayloadBid signedBidWithBlockHash(final Bytes32 blockHash) {
    final ExecutionPayloadBid bidWithBlockHash =
        bid.getSchema()
            .create(
                bid.getParentBlockHash(),
                bid.getParentBlockRoot(),
                blockHash,
                bid.getPrevRandao(),
                bid.getFeeRecipient(),
                bid.getGasLimit(),
                bid.getBuilderIndex(),
                bid.getSlot(),
                bid.getValue(),
                bid.getExecutionPayment(),
                bid.getBlobKzgCommitments(),
                bid.getExecutionRequestsRoot());
    return dataStructureUtil.randomSignedExecutionPayloadBid(bidWithBlockHash);
  }

  private SignedExecutionPayloadBid signedBidForParent(
      final Bytes32 parentHash,
      final Bytes32 parentRoot,
      final UInt64 builderIndex,
      final UInt64 value) {
    return signedBidForParent(parentHash, parentRoot, bid.getSlot(), builderIndex, value);
  }

  private SignedExecutionPayloadBid signedBidForParent(
      final Bytes32 parentHash,
      final Bytes32 parentRoot,
      final UInt64 slot,
      final UInt64 builderIndex,
      final UInt64 value) {
    final ExecutionPayloadBid bidForParent =
        bid.getSchema()
            .create(
                parentHash,
                parentRoot,
                bid.getBlockHash(),
                bid.getPrevRandao(),
                bid.getFeeRecipient(),
                bid.getGasLimit(),
                builderIndex,
                slot,
                value,
                bid.getExecutionPayment(),
                bid.getBlobKzgCommitments(),
                bid.getExecutionRequestsRoot());
    return dataStructureUtil.randomSignedExecutionPayloadBid(bidForParent);
  }

  private void mockProposerPreferences(
      final SignedExecutionPayloadBid signedBid, final UInt64 targetGasLimit) {
    final ProposerPreferences proposerPreferences = mock(ProposerPreferences.class);
    when(proposerPreferences.getFeeRecipient())
        .thenReturn(signedBid.getMessage().getFeeRecipient());
    when(proposerPreferences.getTargetGasLimit()).thenReturn(targetGasLimit);
    when(proposerPreferencesManager.getProposerPreferences(signedBid.getMessage().getSlot()))
        .thenReturn(Optional.of(proposerPreferences));
  }
}
