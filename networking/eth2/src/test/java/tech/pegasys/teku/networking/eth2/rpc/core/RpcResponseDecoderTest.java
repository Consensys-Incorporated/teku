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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ContextPayloadMismatchException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.LengthOutOfBoundsException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.MessageTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.PayloadTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.ForkDigestPayloadContext;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.TestForkDigestContextDecoder;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

class RpcResponseDecoderTest extends RpcDecoderTestBase {
  private static final Bytes SUCCESS_CODE = Bytes.of(RpcResponseStatus.SUCCESS_RESPONSE_CODE);
  private static final Bytes ERROR_CODE = Bytes.of(1);
  private static final UInt64 GLOAS_FORK_EPOCH = UInt64.ONE;
  private static final Bytes4 FULU_FORK_DIGEST = Bytes4.fromHexStringLenient("0x0a");
  private static final Bytes4 GLOAS_FORK_DIGEST = Bytes4.fromHexStringLenient("0x0b");

  private final RpcResponseDecoder<BeaconBlocksByRootRequestMessage, ?> decoder = responseDecoder;

  @Test
  public void decodeNextResponse_shouldParseSingleResponse() throws Exception {
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(SUCCESS_CODE, lengthPrefix, messageData)) {

      List<BeaconBlocksByRootRequestMessage> results = new ArrayList<>();
      for (ByteBuf byteBuf : testByteBufSlice) {
        results.addAll(decoder.decodeNextResponses(byteBuf));
        byteBuf.release();
      }
      decoder.complete();
      assertThat(results).containsExactly(message);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldParseMultipleResponses() throws Exception {
    final BeaconBlocksByRootRequestMessage secondMessage = createRequestMessage(2);
    final Bytes secondMessageData = secondMessage.sszSerialize();
    final Bytes compressedPayload = compressor.compress(secondMessageData);

    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(
            SUCCESS_CODE,
            lengthPrefix,
            messageData,
            SUCCESS_CODE,
            getLengthPrefix(secondMessageData.size()),
            compressedPayload)) {

      List<BeaconBlocksByRootRequestMessage> results = new ArrayList<>();
      for (ByteBuf byteBuf : testByteBufSlice) {
        results.addAll(decoder.decodeNextResponses(byteBuf));
        byteBuf.release();
      }
      decoder.complete();
      assertThat(results).containsExactly(message, secondMessage);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldThrowErrorIfStatusCodeIsNotSuccess() throws Exception {
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(ERROR_CODE, errorMessageLengthPrefix, errorMessageData)) {

      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                }
                decoder.complete();
              })
          .isEqualTo(new RpcException(ERROR_CODE.get(0), ERROR_MESSAGE));
    }
  }

  @Test
  public void decodeNextResponse_shouldThrowErrorIfStatusCodeIsNotSuccess_largeErrorCode()
      throws Exception {
    final Bytes errorCode = Bytes.of(255);
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(errorCode, errorMessageLengthPrefix, errorMessageData)) {

      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                }
                decoder.complete();
              })
          .isEqualTo(new RpcException(errorCode.get(0), ERROR_MESSAGE));
    }
  }

  @Test
  public void complete_shouldThrowErrorIfLengthPrefixNotFullyProcessed() {
    final Bytes lengthPrefix = getLengthPrefix(1024);
    assertThat(lengthPrefix.size()).isGreaterThan(1);
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(SUCCESS_CODE, lengthPrefix.slice(0, 1))) {

      RpcResponseDecoder<?, ?> decoder = createForkAwareDecoder();
      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                  byteBuf.release();
                }
                decoder.complete();
              })
          .isInstanceOf(MessageTruncatedException.class);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void complete_shouldThrowErrorIfContextNotFullyProcessed() {
    for (Iterable<ByteBuf> testByteBufSlice : testByteBufSlices(SUCCESS_CODE, Bytes.of(1, 2))) {

      RpcResponseDecoder<?, ?> decoder = createForkAwareDecoder();
      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                  byteBuf.release();
                }
                decoder.complete();
              })
          .isInstanceOf(MessageTruncatedException.class);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void complete_shouldThrowErrorIfErrorMsgNotFullyProcessed() {
    final Bytes errorCode = Bytes.of(255);
    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(errorCode, errorMessageLengthPrefix, errorMessageData.slice(0, 2))) {

      RpcResponseDecoder<?, ?> decoder = createForkAwareDecoder();
      assertThatThrownBy(
              () -> {
                for (ByteBuf byteBuf : testByteBufSlice) {
                  decoder.decodeNextResponses(byteBuf);
                  byteBuf.release();
                }
                decoder.complete();
              })
          .isInstanceOf(PayloadTruncatedException.class);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldDecodeMultipleContextAwareResponses() throws Exception {
    // Two messages with different fork digests in a single ByteBuf — exercises the fix where
    // context/payload decoders are properly closed after each message so the snappy frame decoder
    // inside the decompressor isn't reused in a disposed state.
    final BeaconState phase0State = beaconState(true);
    final Bytes serializedPhase0 = phase0State.sszSerialize();
    final Bytes contextBytesPhase0 = TestContextCodec.getContextBytes(true).getWrappedBytes();
    final Bytes compressedPhase0 = compressor.compress(serializedPhase0);

    final BeaconState altairState = beaconState(false);
    final Bytes serializedAltair = altairState.sszSerialize();
    final Bytes contextBytesAltair = TestContextCodec.getContextBytes(false).getWrappedBytes();
    final Bytes compressedAltair = compressor.compress(serializedAltair);

    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(
            SUCCESS_CODE,
            contextBytesPhase0,
            getLengthPrefix(serializedPhase0.size()),
            compressedPhase0,
            SUCCESS_CODE,
            contextBytesAltair,
            getLengthPrefix(serializedAltair.size()),
            compressedAltair)) {

      final RpcResponseDecoder<BeaconState, ?> decoder = createForkAwareDecoder();
      final List<BeaconState> results = new ArrayList<>();
      for (ByteBuf byteBuf : testByteBufSlice) {
        results.addAll(decoder.decodeNextResponses(byteBuf));
        byteBuf.release();
      }
      decoder.complete();
      assertThat(results).containsExactly(phase0State, altairState);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldDecodeBasedOnContext_matchingPhase0Input() throws Exception {
    testDecodeBasedOnContext(true);
  }

  @Test
  public void decodeNextResponse_shouldDecodeBasedOnContext_matchingAltairInput() throws Exception {
    testDecodeBasedOnContext(false);
  }

  private void testDecodeBasedOnContext(final boolean usePhase0State) throws Exception {
    final BeaconState state = beaconState(usePhase0State);
    final Bytes serializedState = state.sszSerialize();
    final Bytes lengthPrefix = getLengthPrefix(serializedState.size());
    final Bytes contextBytes = TestContextCodec.getContextBytes(usePhase0State).getWrappedBytes();
    final Bytes compressedState = compressor.compress(serializedState);

    final RpcResponseDecoder<BeaconState, ?> decoder = createForkAwareDecoder();

    for (Iterable<ByteBuf> testByteBufSlice :
        testByteBufSlices(SUCCESS_CODE, contextBytes, lengthPrefix, compressedState)) {

      List<BeaconState> results = new ArrayList<>();
      for (ByteBuf byteBuf : testByteBufSlice) {
        results.addAll(decoder.decodeNextResponses(byteBuf));
        byteBuf.release();
      }
      decoder.complete();
      assertThat(results).containsExactly(state);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldDecodeBasedOnContext_mismatchedPhase0Input()
      throws Exception {
    testDecodeBasedOnContextWithMismatchedPayload(true);
  }

  @Test
  public void decodeNextResponse_shouldDecodeBasedOnContext_mismatchedAltairInput()
      throws Exception {
    testDecodeBasedOnContextWithMismatchedPayload(false);
  }

  private void testDecodeBasedOnContextWithMismatchedPayload(final boolean usePhase0) {
    final RpcResponseDecoder<BeaconState, ?> decoder = createForkAwareDecoder();

    final BeaconState state = beaconState(usePhase0);
    final Bytes serializedState = state.sszSerialize();
    final Bytes lengthPrefix = getLengthPrefix(serializedState.size());
    // Use wrong context for state
    final Bytes contextBytes = TestContextCodec.getContextBytes(!usePhase0).getWrappedBytes();
    final Bytes compressedState = compressor.compress(serializedState);

    for (List<ByteBuf> testByteBufSlice :
        testByteBufSlices(SUCCESS_CODE, contextBytes, lengthPrefix, compressedState)) {

      List<BeaconState> results = new ArrayList<>();
      List<Exception> failures = new ArrayList<>();
      boolean failed = false;
      for (ByteBuf byteBuf : testByteBufSlice) {
        try {
          if (!failed) {
            results.addAll(decoder.decodeNextResponses(byteBuf));
          }
        } catch (Exception e) {
          failed = true;
          failures.add(e);
        } finally {
          byteBuf.release();
        }
      }
      completeIgnoringUnprocessedData(decoder);
      assertThat(results).isEmpty();
      assertThat(failures.size()).isEqualTo(1);
      for (Exception failure : failures) {
        assertThat(failure)
            .isInstanceOfAny(
                DeserializationFailedException.class, LengthOutOfBoundsException.class);
      }
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldRejectPayloadOutsideTheContextFork() {
    final Spec transitionSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_FORK_EPOCH);
    final DataColumnSidecar sidecar =
        fuluSchemaSidecarAtSlot(transitionSpec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH));
    final Bytes serializedSidecar = sidecar.sszSerialize();

    for (final List<ByteBuf> testByteBufSlice :
        testByteBufSlices(
            SUCCESS_CODE,
            FULU_FORK_DIGEST.getWrappedBytes(),
            getLengthPrefix(serializedSidecar.size()),
            compressor.compress(serializedSidecar))) {
      final RpcResponseDecoder<DataColumnSidecar, Bytes4> decoder =
          createDataColumnSidecarDecoder(transitionSpec);
      final List<DataColumnSidecar> results = new ArrayList<>();
      final List<Exception> failures = new ArrayList<>();
      for (final ByteBuf byteBuf : testByteBufSlice) {
        try {
          if (failures.isEmpty()) {
            results.addAll(decoder.decodeNextResponses(byteBuf));
          }
        } catch (final Exception e) {
          failures.add(e);
        } finally {
          byteBuf.release();
        }
      }
      assertThat(results).isEmpty();
      assertThat(failures).hasSize(1);
      assertThat(failures.get(0)).isInstanceOf(ContextPayloadMismatchException.class);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldAcceptPayloadInTheContextFork() throws Exception {
    final Spec transitionSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_FORK_EPOCH);
    final DataColumnSidecar sidecar = fuluSchemaSidecarAtSlot(UInt64.ONE);
    final Bytes serializedSidecar = sidecar.sszSerialize();

    for (final List<ByteBuf> testByteBufSlice :
        testByteBufSlices(
            SUCCESS_CODE,
            FULU_FORK_DIGEST.getWrappedBytes(),
            getLengthPrefix(serializedSidecar.size()),
            compressor.compress(serializedSidecar))) {
      final RpcResponseDecoder<DataColumnSidecar, Bytes4> decoder =
          createDataColumnSidecarDecoder(transitionSpec);
      final List<DataColumnSidecar> results = new ArrayList<>();
      for (final ByteBuf byteBuf : testByteBufSlice) {
        results.addAll(decoder.decodeNextResponses(byteBuf));
        byteBuf.release();
      }
      decoder.complete();
      assertThat(results).containsExactly(sidecar);
      assertThat(testByteBufSlice).allSatisfy(b -> assertThat(b.refCnt()).isEqualTo(0));
    }
  }

  @Test
  public void decodeNextResponse_shouldDropAlreadyDecodedChunksWhenALaterChunkMismatches() {
    final Spec transitionSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(GLOAS_FORK_EPOCH);
    final Bytes validSidecar = fuluSchemaSidecarAtSlot(UInt64.ONE).sszSerialize();
    final Bytes mislabelledSidecar =
        fuluSchemaSidecarAtSlot(transitionSpec.computeStartSlotAtEpoch(GLOAS_FORK_EPOCH))
            .sszSerialize();
    final ByteBuf buffer =
        Unpooled.wrappedBuffer(
            Bytes.concatenate(
                    SUCCESS_CODE,
                    FULU_FORK_DIGEST.getWrappedBytes(),
                    getLengthPrefix(validSidecar.size()),
                    compressor.compress(validSidecar),
                    SUCCESS_CODE,
                    FULU_FORK_DIGEST.getWrappedBytes(),
                    getLengthPrefix(mislabelledSidecar.size()),
                    compressor.compress(mislabelledSidecar))
                .toArrayUnsafe());
    final RpcResponseDecoder<DataColumnSidecar, Bytes4> decoder =
        createDataColumnSidecarDecoder(transitionSpec);

    assertThatThrownBy(() -> decoder.decodeNextResponses(buffer))
        .isInstanceOf(ContextPayloadMismatchException.class);
    buffer.release();
  }

  private static DataColumnSidecar fuluSchemaSidecarAtSlot(final UInt64 slot) {
    final DataStructureUtil fuluDataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMinimalFulu());
    return fuluDataStructureUtil.randomDataColumnSidecar(
        fuluDataStructureUtil.randomSignedBeaconBlockHeader(slot), UInt64.ZERO);
  }

  private RpcResponseDecoder<DataColumnSidecar, Bytes4> createDataColumnSidecarDecoder(
      final Spec transitionSpec) {
    final RecentChainData recentChainData = mock(RecentChainData.class);
    when(recentChainData.getMilestoneByForkDigest(FULU_FORK_DIGEST))
        .thenReturn(Optional.of(SpecMilestone.FULU));
    when(recentChainData.getForkDigest(eq(SpecMilestone.FULU), any())).thenReturn(FULU_FORK_DIGEST);
    when(recentChainData.getForkDigest(eq(SpecMilestone.GLOAS), any()))
        .thenReturn(GLOAS_FORK_DIGEST);
    return RpcResponseDecoder.create(
        encoding,
        RpcContextCodec.forkDigest(
            transitionSpec, recentChainData, ForkDigestPayloadContext.DATA_COLUMN_SIDECAR));
  }

  private BeaconState beaconState(final boolean usePhase0State) {
    final Spec spec =
        usePhase0State
            ? TestSpecFactory.createMinimalPhase0()
            : TestSpecFactory.createMinimalAltair();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    return usePhase0State
        ? dataStructureUtil.stateBuilderPhase0().build()
        : dataStructureUtil.stateBuilderAltair().build();
  }

  private void completeIgnoringUnprocessedData(final RpcResponseDecoder<?, ?> decoder) {
    try {
      decoder.complete();
    } catch (Exception e) {
      assertThat(e)
          .isInstanceOfAny(MessageTruncatedException.class, PayloadTruncatedException.class);
    }
  }

  private RpcResponseDecoder<BeaconState, Bytes4> createForkAwareDecoder() {
    return RpcResponseDecoder.create(encoding, new TestContextCodec());
  }

  private static class TestContextCodec implements RpcContextCodec<Bytes4, BeaconState> {
    private static final Spec PHASE0_SPEC = TestSpecFactory.createMinimalPhase0();
    private static final Spec ALTAIR_SPEC = TestSpecFactory.createMinimalAltair();

    static final Bytes4 FORK_DIGEST_PHASE0 = Bytes4.fromHexStringLenient("0x01");
    static final Bytes4 FORK_DIGEST_ALTAIR = Bytes4.fromHexStringLenient("0x02");

    public static Bytes4 getContextBytes(final boolean forPhase0) {
      return forPhase0 ? FORK_DIGEST_PHASE0 : FORK_DIGEST_ALTAIR;
    }

    @Override
    public RpcByteBufDecoder<Bytes4> getContextDecoder() {
      return new TestForkDigestContextDecoder();
    }

    @Override
    public Bytes encodeContext(final BeaconState responsePayload) {
      // Unused for these tests
      return Bytes.EMPTY;
    }

    @Override
    public Optional<SszSchema<BeaconState>> getSchemaFromContext(final Bytes4 forkDigest) {
      final SszSchema<BeaconState> phase0Schema =
          SszSchema.as(
              BeaconState.class,
              BeaconStateSchemaPhase0.create(PHASE0_SPEC.getGenesisSpecConfig()));
      final SszSchema<BeaconState> altairSchema =
          SszSchema.as(
              BeaconState.class,
              BeaconStateSchemaAltair.create(ALTAIR_SPEC.getGenesisSpecConfig()));
      Optional<SszSchema<BeaconState>> schema = Optional.empty();
      if (forkDigest.equals(FORK_DIGEST_PHASE0)) {
        schema = Optional.of(phase0Schema);
      } else if (forkDigest.equals(FORK_DIGEST_ALTAIR)) {
        schema = Optional.of(altairSchema);
      }
      return schema;
    }

    @Override
    public void verifyPayloadMatchesContext(
        final Bytes4 forkDigest, final BeaconState responsePayload) {
      // Unused for these tests
    }
  }
}
