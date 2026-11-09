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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.SIGNED_VALIDATOR_REGISTRATION_SCHEMA;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.VALIDATOR_REGISTRATION_SCHEMA;

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSConstants;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.builder.BuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedBuilderBid;
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BuilderBidValidatorTest {

  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final Spec specMock = mock(Spec.class);
  private final SpecVersion specVersionMock = mock(SpecVersion.class);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BlockProcessor blockProcessor = mock(BlockProcessor.class);
  private final EventLogger eventLogger = mock(EventLogger.class);

  private final BuilderBidValidatorImpl builderBidValidator =
      new BuilderBidValidatorImpl(spec, eventLogger);

  private final BuilderBidValidatorImpl builderBidValidatorWithMockSpec =
      new BuilderBidValidatorImpl(specMock, eventLogger);

  private BeaconState state = dataStructureUtil.randomBeaconState();

  private SignedValidatorRegistration validatorRegistration =
      dataStructureUtil.randomSignedValidatorRegistration();

  private final ExecutionPayload localExecutionPayload = dataStructureUtil.randomExecutionPayload();

  private SignedBuilderBid signedBuilderBid =
      dataStructureUtil.randomSignedBuilderBid(
          localExecutionPayload.getOptionalWithdrawalsRoot().orElseThrow());

  @BeforeEach
  void setUp() throws BlockProcessingException {
    BLSConstants.disableBLSVerification();
    when(specMock.getDomain(any(), any(), any(), any()))
        .thenReturn(dataStructureUtil.randomBytes32());
    when(specMock.computeBuilderApplicationSigningRoot(any(), any()))
        .thenReturn(dataStructureUtil.randomBytes32());
    when(specMock.atSlot(any())).thenReturn(specVersionMock);
    when(specVersionMock.getBlockProcessor()).thenReturn(blockProcessor);
    doNothing().when(blockProcessor).validateExecutionPayloadHeader(any(), any());
  }

  @AfterEach
  void enableBLS() {
    BLSConstants.enableBLSVerification();
  }

  @Test
  void shouldThrowWithInvalidSignature() {
    BLSConstants.enableBLSVerification();

    assertThatThrownBy(
            () ->
                builderBidValidator.validateBuilderBid(
                    signedBuilderBid, validatorRegistration, state, Optional.empty()))
        .isExactlyInstanceOf(BuilderBidValidationException.class)
        .hasMessage("Invalid Bid Signature");
  }

  @Test
  void shouldValidateSignatureAndThrowWithBlockProcessingExceptionCause() {
    BLSConstants.enableBLSVerification();
    prepareValidSignedBuilderBid();

    assertThatThrownBy(
            () ->
                builderBidValidator.validateBuilderBid(
                    signedBuilderBid, validatorRegistration, state, Optional.empty()))
        .isExactlyInstanceOf(BuilderBidValidationException.class)
        .hasMessage("Invalid proposed payload with respect to consensus.")
        .hasCauseInstanceOf(BlockProcessingException.class);
  }

  @Test
  void shouldBeValidIfLocalPayloadWithdrawalsRootMatchesTheRootOfTheBid() {
    builderBidValidatorWithMockSpec.validateBuilderBid(
        signedBuilderBid, validatorRegistration, state, Optional.of(localExecutionPayload));
  }

  @Test
  void shouldBeInvalidIfLocalPayloadWithdrawalsRootDoesNotMatchTheRootOfTheBid() {
    final SignedBuilderBid dodgySignedBuilderBid = dataStructureUtil.randomSignedBuilderBid();

    assertThatThrownBy(
            () ->
                builderBidValidatorWithMockSpec.validateBuilderBid(
                    dodgySignedBuilderBid,
                    validatorRegistration,
                    state,
                    Optional.of(localExecutionPayload)))
        .isExactlyInstanceOf(BuilderBidValidationException.class)
        .hasMessage(
            "Withdrawals root from the local payload (%s) was different from the proposed payload (%s)",
            localExecutionPayload.getOptionalWithdrawalsRoot().orElseThrow(),
            dodgySignedBuilderBid
                .getMessage()
                .getHeader()
                .getOptionalWithdrawalsRoot()
                .orElseThrow());
  }

  @Nested
  @TestSpecContext(milestone = {SpecMilestone.DENEB, SpecMilestone.ELECTRA, SpecMilestone.FULU})
  class BlobCommitments {
    private DataStructureUtil blobDataStructureUtil;
    private int maxBlobsPerBlock;

    @BeforeEach
    void setUp(final SpecContext specContext) {
      blobDataStructureUtil = specContext.getDataStructureUtil();
      state = blobDataStructureUtil.randomBeaconState();
      maxBlobsPerBlock =
          specContext.getSpec().getMaxBlobsPerBlockAtSlot(state.getSlot()).orElseThrow();
      when(specMock.getMaxBlobsPerBlockAtSlot(state.getSlot()))
          .thenReturn(Optional.of(maxBlobsPerBlock));
    }

    @TestTemplate
    void shouldAcceptCommitmentsAtMaxBlobsPerBlock() {
      prepareBid(maxBlobsPerBlock);

      assertThatCode(
              () ->
                  builderBidValidatorWithMockSpec.validateBuilderBid(
                      signedBuilderBid, validatorRegistration, state, Optional.empty()))
          .doesNotThrowAnyException();
    }

    @TestTemplate
    void shouldRejectCommitmentsExceedingMaxBlobsPerBlock() {
      prepareBid(maxBlobsPerBlock + 1);

      assertThatThrownBy(
              () ->
                  builderBidValidatorWithMockSpec.validateBuilderBid(
                      signedBuilderBid, validatorRegistration, state, Optional.empty()))
          .isExactlyInstanceOf(BuilderBidValidationException.class)
          .hasMessage(
              "Number of kzg commitments in builder bid (%s) exceeds max blobs per block (%s)",
              maxBlobsPerBlock + 1, maxBlobsPerBlock);
    }

    private void prepareBid(final int commitmentCount) {
      final BuilderBid builderBid =
          blobDataStructureUtil.randomBuilderBid(
              builder ->
                  builder.blobKzgCommitments(
                      blobDataStructureUtil.randomBlobKzgCommitments(commitmentCount)));
      signedBuilderBid = blobDataStructureUtil.randomSignedBuilderBid(builderBid);
    }
  }

  @ParameterizedTest(name = "parent.{0}_target.{1}_result.{2}")
  @MethodSource("expectedGasLimitPermutations")
  void expectedGasLimitTestCases(
      final long parentGasLimit, final long targetGasLimit, final long expectedGasLimit) {
    final UInt64 computedGasLimit =
        BuilderBidValidatorImpl.expectedGasLimit(
            UInt64.valueOf(parentGasLimit), UInt64.valueOf(targetGasLimit));

    assertThat(computedGasLimit).isEqualTo(UInt64.valueOf(expectedGasLimit));
  }

  @Test
  void shouldNotLogEventIfGasLimitDecreases() throws BuilderBidValidationException {
    // 1023001 is as high as it can move in 1 shot
    prepareGasLimit(UInt64.valueOf(1024_000), UInt64.valueOf(1023_001), UInt64.valueOf(1022_000));

    builderBidValidatorWithMockSpec.validateBuilderBid(
        signedBuilderBid, validatorRegistration, state, Optional.empty());

    verifyNoInteractions(eventLogger);
  }

  @Test
  void shouldNotLogEventIfGasLimitIncreases() throws BuilderBidValidationException {
    // 1024999 is as high as it can move in 1 shot
    prepareGasLimit(UInt64.valueOf(1024_000), UInt64.valueOf(1024_999), UInt64.valueOf(1025_000));

    builderBidValidatorWithMockSpec.validateBuilderBid(
        signedBuilderBid, validatorRegistration, state, Optional.empty());

    verifyNoInteractions(eventLogger);
  }

  @Test
  void shouldNotLogEventIfGasLimitStaysTheSame() throws BuilderBidValidationException {

    prepareGasLimit(UInt64.valueOf(1024_000), UInt64.valueOf(1024_000), UInt64.valueOf(1024_000));

    builderBidValidatorWithMockSpec.validateBuilderBid(
        signedBuilderBid, validatorRegistration, state, Optional.empty());

    verifyNoInteractions(eventLogger);
  }

  @Test
  void shouldLogEventIfGasLimitDoesNotDecrease() throws BuilderBidValidationException {

    prepareGasLimit(UInt64.valueOf(1024_000), UInt64.valueOf(1024_000), UInt64.valueOf(1020_000));

    builderBidValidatorWithMockSpec.validateBuilderBid(
        signedBuilderBid, validatorRegistration, state, Optional.empty());

    verify(eventLogger)
        .builderBidNotHonouringGasLimit(
            UInt64.valueOf(1024_000),
            UInt64.valueOf(1024_000),
            UInt64.valueOf(1023_001),
            UInt64.valueOf(1020_000));
  }

  @Test
  void shouldLogEventIfGasLimitDoesNotIncrease() throws BuilderBidValidationException {

    prepareGasLimit(UInt64.valueOf(1024_000), UInt64.valueOf(1020_000), UInt64.valueOf(1028_000));

    builderBidValidatorWithMockSpec.validateBuilderBid(
        signedBuilderBid, validatorRegistration, state, Optional.empty());

    verify(eventLogger)
        .builderBidNotHonouringGasLimit(
            UInt64.valueOf(1024_000),
            UInt64.valueOf(1020_000),
            UInt64.valueOf(1024_999),
            UInt64.valueOf(1028_000));
  }

  static Stream<Arguments> expectedGasLimitPermutations() {
    return Stream.of(
        // same, expect no change
        Arguments.of(36_000_000L, 36_000_000L, 36_000_000L),
        Arguments.of(1024_000L, 1024_000L, 1024_000L),
        // down inside bounds
        Arguments.of(1024_000L, 1023_500L, 1023_500L),
        // down outside bounds - results in a partial move
        Arguments.of(36_000_000L, 35_000_000L, 35_964_845L),
        Arguments.of(1024_000L, 1020_000L, 1023_001L),
        // increase outside bounds - results in a partial move
        Arguments.of(1024_000L, 1025_000L, 1024_999L),
        Arguments.of(30_000_000L, 36_000_000L, 30_029_295L),
        // increase inside bounds
        Arguments.of(1024_000L, 1024_500L, 1024_500L));
  }

  private void prepareValidSignedBuilderBid() {
    final BLSKeyPair keyPair = BLSTestUtil.randomKeyPair(1);
    final BuilderBid builderBid =
        dataStructureUtil.randomBuilderBid(builder -> builder.publicKey(keyPair.getPublicKey()));

    final Bytes signingRoot =
        spec.computeBuilderApplicationSigningRoot(state.getSlot(), builderBid);

    signedBuilderBid =
        spec.atSlot(state.getSlot())
            .getSchemaDefinitions()
            .toVersionBellatrix()
            .orElseThrow()
            .getSignedBuilderBidSchema()
            .create(builderBid, BLS.sign(keyPair.getSecretKey(), signingRoot));
  }

  private void prepareGasLimit(
      final UInt64 parentGasLimit, final UInt64 proposedGasLimit, final UInt64 preferredGasLimit) {

    UInt64 slot = dataStructureUtil.randomUInt64();

    SchemaDefinitionsBellatrix schemaDefinitions =
        spec.atSlot(slot).getSchemaDefinitions().toVersionBellatrix().orElseThrow();

    ExecutionPayloadHeader parentExecutionPayloadHeader =
        createExecutionPayloadHeaderWithGasLimit(schemaDefinitions, parentGasLimit);

    // create current state with parent gasLimit
    state =
        dataStructureUtil
            .randomBeaconState(slot)
            .updated(
                state ->
                    state
                        .toMutableVersionBellatrix()
                        .orElseThrow()
                        .setLatestExecutionPayloadHeader(parentExecutionPayloadHeader));

    // create bid with proposed gasLimit
    signedBuilderBid =
        schemaDefinitions
            .getSignedBuilderBidSchema()
            .create(
                schemaDefinitions
                    .getBuilderBidSchema()
                    .createBuilderBid(
                        builder ->
                            builder
                                .header(
                                    createExecutionPayloadHeaderWithGasLimit(
                                        schemaDefinitions, proposedGasLimit))
                                .value(dataStructureUtil.randomUInt256())
                                .publicKey(dataStructureUtil.randomPublicKey())),
                dataStructureUtil.randomSignature());

    // create validator registration with preferred gasLimit
    validatorRegistration =
        SIGNED_VALIDATOR_REGISTRATION_SCHEMA.create(
            VALIDATOR_REGISTRATION_SCHEMA.create(
                dataStructureUtil.randomEth1Address(),
                preferredGasLimit,
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomPublicKey()),
            dataStructureUtil.randomSignature());
  }

  private ExecutionPayloadHeader createExecutionPayloadHeaderWithGasLimit(
      final SchemaDefinitionsBellatrix schemaDefinitions, final UInt64 gasLimit) {
    return schemaDefinitions
        .getExecutionPayloadHeaderSchema()
        .createExecutionPayloadHeader(
            builder ->
                builder
                    .parentHash(Bytes32.random())
                    .feeRecipient(Bytes20.ZERO)
                    .stateRoot(Bytes32.ZERO)
                    .receiptsRoot(Bytes32.ZERO)
                    .logsBloom(Bytes.random(256))
                    .prevRandao(Bytes32.ZERO)
                    .blockNumber(UInt64.ZERO)
                    .gasLimit(gasLimit)
                    .gasUsed(UInt64.ZERO)
                    .timestamp(UInt64.ZERO)
                    .extraData(Bytes32.ZERO)
                    .baseFeePerGas(UInt256.ZERO)
                    .blockHash(Bytes32.random())
                    .transactionsRoot(Bytes32.ZERO)
                    .withdrawalsRoot(() -> Bytes32.ZERO)
                    .blobGasUsed(() -> UInt64.ONE)
                    .excessBlobGas(() -> UInt64.ONE));
  }
}
