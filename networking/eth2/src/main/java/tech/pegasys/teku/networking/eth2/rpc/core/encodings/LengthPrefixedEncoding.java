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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EmptyMessage;

/**
 * Represents an rpc payload encoding where the header consists of a single protobuf varint holding
 * the length of the uncompressed payload
 */
public class LengthPrefixedEncoding implements RpcEncoding {
  private static final RpcByteBufDecoder<EmptyMessage> EMPTY_MESSAGE_DECODER =
      new RpcByteBufDecoder<>() {
        @Override
        public Optional<EmptyMessage> decodeOneMessage(final ByteBuf input) {
          return Optional.of(EmptyMessage.EMPTY_MESSAGE);
        }

        @Override
        public void complete() {}

        @Override
        public void close() {}
      };

  private final String name;
  private final RpcPayloadEncoders payloadEncoders;
  private final Compressor compressor;
  private final int maxChunkSize;

  @SuppressWarnings({"unchecked", "TypeParameterUnusedInFormals"})
  private static <T> RpcByteBufDecoder<T> getEmptyMessageDecoder() {
    return (RpcByteBufDecoder<T>) EMPTY_MESSAGE_DECODER;
  }

  LengthPrefixedEncoding(
      final String name,
      final RpcPayloadEncoders payloadEncoders,
      final Compressor compressor,
      final int maxChunkSize) {
    this.name = name;
    this.payloadEncoders = payloadEncoders;
    this.compressor = compressor;
    this.maxChunkSize = maxChunkSize;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends SszData> Bytes encodePayload(final T message) {
    if (message instanceof EmptyMessage) {
      return Bytes.EMPTY;
    }
    final RpcPayloadEncoder<T> payloadEncoder =
        payloadEncoders.getEncoder((SszSchema<T>) message.getSchema());
    final Bytes payload = payloadEncoder.encode(message);
    if (payload.isEmpty()) {
      return payload;
    }
    return encodeMessageWithLength(payload);
  }

  @Override
  public <T extends SszData> RpcByteBufDecoder<T> createDecoder(final SszSchema<T> payloadType) {
    if (payloadType.equals(EmptyMessage.SSZ_SCHEMA)) {
      return getEmptyMessageDecoder();
    } else {
      return new LengthPrefixedPayloadDecoder<>(
          payloadEncoders.getEncoder(payloadType), compressor, maxChunkSize);
    }
  }

  private Bytes encodeMessageWithLength(final Bytes payload) {
    final Bytes header = ProtobufEncoder.encodeVarInt(payload.size());
    final Bytes compressedPayload = compressor.compress(payload);
    return Bytes.concatenate(header, compressedPayload);
  }

  @Override
  public String getName() {
    return name;
  }
}
