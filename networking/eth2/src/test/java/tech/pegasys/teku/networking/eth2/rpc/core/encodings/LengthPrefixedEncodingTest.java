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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.Utils;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ChunkTooLongException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.LengthOutOfBoundsException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.MessageTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.PayloadTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.snappy.SnappyFramedCompressor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessageSchemaPhase0;

class LengthPrefixedEncodingTest {
  private static final Bytes TWO_BYTE_LENGTH_PREFIX = Bytes.fromHexString("0x8002");

  private final Spec spec = TestSpecFactory.createDefault();

  private final StatusMessageSchemaPhase0 statusMessageSchema =
      (StatusMessageSchemaPhase0) spec.getGenesisSchemaDefinitions().getStatusMessageSchema();

  private final Bytes prefixExceedingMaxLength =
      ProtobufEncoder.encodeVarInt(spec.getNetworkingConfig().getMaxPayloadSize() + 1);

  private final RpcEncoding encoding =
      RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());
  private final Compressor compressor = SnappyFramedCompressor.AIRCOMPRESSOR;

  @Test
  public void decodePayload_shouldReturnErrorWhenLengthPrefixIsTooLong() {
    List<List<ByteBuf>> testByteBufSlices =
        Utils.generateTestSlices(Bytes.fromHexString("0xAAAAAAAAAAAAAAAAAAAA80"));

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessagePhase0> decoder = encoding.createDecoder(statusMessageSchema);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  decoder.decodeOneMessage(bufSlice);
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
              })
          .isInstanceOf(ChunkTooLongException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldReturnErrorWhenLengthPrefixIsTooShortForMessageType() {
    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(Bytes.fromHexString("0x52"));

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessagePhase0> decoder = encoding.createDecoder(statusMessageSchema);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  decoder.decodeOneMessage(bufSlice);
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
              })
          .isInstanceOf(LengthOutOfBoundsException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldReturnErrorWhenLengthPrefixIsTooLongForMessageType() {
    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(Bytes.fromHexString("0x55"));

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessagePhase0> decoder = encoding.createDecoder(statusMessageSchema);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  decoder.decodeOneMessage(bufSlice);
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
              })
          .isInstanceOf(LengthOutOfBoundsException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldReturnErrorWhenNoPayloadIsPresent() {
    final Bytes statusMessageLengthPrefix = Bytes.fromHexString("0x54");
    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(statusMessageLengthPrefix);

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessagePhase0> decoder = encoding.createDecoder(statusMessageSchema);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  assertThat(decoder.decodeOneMessage(bufSlice)).isEmpty();
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
                decoder.complete();
              })
          .isInstanceOf(PayloadTruncatedException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldReturnErrorWhenPayloadTooShort() {
    final Bytes correctMessage = createValidStatusMessage(encoding);
    final int truncatedSize = correctMessage.size() - 5;
    List<List<ByteBuf>> testByteBufSlices =
        Utils.generateTestSlices(correctMessage.slice(0, truncatedSize));

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessagePhase0> decoder = encoding.createDecoder(statusMessageSchema);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  assertThat(decoder.decodeOneMessage(bufSlice)).isEmpty();
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
                decoder.complete();
              })
          .isInstanceOf(PayloadTruncatedException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldReadPayloadWhenExtraDataIsAppended() throws RpcException {
    final StatusMessagePhase0 originalMessage = StatusMessagePhase0.createPreGenesisStatus(spec);
    final Bytes encoded = encoding.encodePayload(originalMessage);
    final Bytes extraData = Bytes.of(1, 2, 3, 4);
    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(encoded, extraData);

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessagePhase0> decoder = encoding.createDecoder(statusMessageSchema);
      Optional<StatusMessagePhase0> result = Optional.empty();
      int unreadBytes = 0;
      for (ByteBuf bufSlice : bufSlices) {
        if (result.isEmpty()) {
          result = decoder.decodeOneMessage(bufSlice);
        }
        if (result.isPresent()) {
          unreadBytes += bufSlice.readableBytes();
        }
        bufSlice.release();
      }
      decoder.complete();
      assertThat(result).contains(originalMessage);
      assertThat(unreadBytes).isEqualTo(4);
      assertThat(bufSlices).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldRejectMessagesThatAreTooLong() {
    // We should reject the message based on the length prefix and skip reading the data entirely
    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(prefixExceedingMaxLength);

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<StatusMessagePhase0> decoder = encoding.createDecoder(statusMessageSchema);
      List<ByteBuf> usedBufs = new ArrayList<>();
      assertThatThrownBy(
              () -> {
                for (ByteBuf bufSlice : bufSlices) {
                  assertThat(decoder.decodeOneMessage(bufSlice)).isEmpty();
                  bufSlice.release();
                  usedBufs.add(bufSlice);
                }
                decoder.complete();
              })
          .isInstanceOf(ChunkTooLongException.class);
      assertThat(usedBufs).allMatch(b -> b.refCnt() == 0);
    }
  }

  @Test
  public void decodePayload_shouldRejectEmptyMessages() {
    final ByteBuf input = Utils.emptyBuf();
    RpcByteBufDecoder<StatusMessagePhase0> decoder = encoding.createDecoder(statusMessageSchema);

    assertThatThrownBy(
            () -> {
              decoder.decodeOneMessage(input);
              decoder.complete();
            })
        .isInstanceOf(MessageTruncatedException.class);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldThrowErrorWhenPrefixTruncated() {
    final ByteBuf input = inputByteBuffer(TWO_BYTE_LENGTH_PREFIX.slice(0, 1));
    assertThatThrownBy(
            () -> {
              RpcByteBufDecoder<StatusMessagePhase0> decoder =
                  encoding.createDecoder(statusMessageSchema);
              decoder.decodeOneMessage(input);
              decoder.complete();
            })
        .isInstanceOf(MessageTruncatedException.class);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void decodePayload_shouldThrowRpcExceptionIfMessageLengthPrefixIsMoreThanThreeBytes() {
    final ByteBuf input = inputByteBuffer("0x80808001");
    RpcByteBufDecoder<StatusMessagePhase0> decoder = encoding.createDecoder(statusMessageSchema);
    assertThatThrownBy(() -> decoder.decodeOneMessage(input)).isInstanceOf(RpcException.class);
    input.release();
    assertThat(input.refCnt()).isEqualTo(0);
  }

  @Test
  public void encodePayload_shouldEncodeBlocksByRootRequest() {
    final BeaconBlocksByRootRequestMessage.BeaconBlocksByRootRequestMessageSchema schema =
        spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema();
    final Bytes encoded =
        encoding.encodePayload(
            new BeaconBlocksByRootRequestMessage(schema, singletonList(Bytes32.ZERO)));
    // Just the length prefix and the hash itself.
    assertThat(encoded)
        .isEqualTo(Bytes.wrap(Bytes.fromHexString("0x20"), compressor.compress(Bytes32.ZERO)));
  }

  @Test
  void encodePayload_shouldReturnZeroBytesForEmptyMessages() {
    final Bytes result = encoding.encodePayload(EmptyMessage.EMPTY_MESSAGE);
    assertThat(result).isEqualTo(Bytes.EMPTY);
  }

  @Test
  void shouldDecodeEmptyMessage() throws Exception {
    final RpcByteBufDecoder<EmptyMessage> decoder = encoding.createDecoder(EmptyMessage.SSZ_SCHEMA);
    final Optional<EmptyMessage> message =
        decoder.decodeOneMessage(Unpooled.wrappedBuffer(new byte[0]));
    assertThat(message).contains(EmptyMessage.EMPTY_MESSAGE);
  }

  @Test
  public void roundtrip_blocksByRootRequest() throws Exception {
    final BeaconBlocksByRootRequestMessage request =
        new BeaconBlocksByRootRequestMessage(
            spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema(),
            List.of(Bytes32.ZERO, Bytes32.fromHexString("0x01"), Bytes32.fromHexString("0x02")));
    final Bytes data = encoding.encodePayload(request);
    final int expectedLengthPrefixLength = 1;
    final Bytes uncompressedPayload =
        Bytes.wrap(Bytes32.ZERO, Bytes32.fromHexString("0x01"), Bytes32.fromHexString("0x02"));
    final Bytes payload = compressor.compress(uncompressedPayload);
    assertThat(data.size()).isEqualTo(payload.size() + expectedLengthPrefixLength);

    List<List<ByteBuf>> testByteBufSlices = Utils.generateTestSlices(data);

    for (Iterable<ByteBuf> bufSlices : testByteBufSlices) {
      RpcByteBufDecoder<BeaconBlocksByRootRequestMessage> decoder =
          encoding.createDecoder(
              spec.getGenesisSchemaDefinitions().getBeaconBlocksByRootRequestMessageSchema());
      Optional<BeaconBlocksByRootRequestMessage> result = Optional.empty();
      for (ByteBuf bufSlice : bufSlices) {
        if (result.isEmpty()) {
          result = decoder.decodeOneMessage(bufSlice);
        } else {
          assertThat(decoder.decodeOneMessage(bufSlice)).isEmpty();
        }
        bufSlice.release();
      }
      decoder.complete();
      assertThat(result).contains(request);
      assertThat(bufSlices).allMatch(b -> b.refCnt() == 0);
    }
  }

  private Bytes createValidStatusMessage(final RpcEncoding encoding) {
    return encoding.encodePayload(
        new StatusMessagePhase0(
            new Bytes4(Bytes.of(0, 0, 0, 0)),
            Bytes32.ZERO,
            UInt64.ZERO,
            Bytes32.ZERO,
            UInt64.ZERO));
  }

  private ByteBuf inputByteBuffer(final Bytes... data) {
    return Utils.toByteBuf(data);
  }

  private ByteBuf inputByteBuffer(final String hexString) {
    return inputByteBuffer(Bytes.fromHexString(hexString));
  }
}
