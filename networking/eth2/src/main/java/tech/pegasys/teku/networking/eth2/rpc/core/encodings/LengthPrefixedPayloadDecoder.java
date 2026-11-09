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

import io.libp2p.etc.types.ByteBufExtKt;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ChunkTooLongException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DecompressFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ExtraDataAppendedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.LengthOutOfBoundsException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.MessageTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.PayloadTruncatedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.Compressor.Decompressor;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadLargerThanExpectedException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadSmallerThanExpectedException;

class LengthPrefixedPayloadDecoder<T> implements RpcByteBufDecoder<T> {

  private final RpcPayloadEncoder<T> payloadEncoder;
  private final Compressor compressor;
  private Optional<Decompressor> decompressor = Optional.empty();
  private Optional<VarIntDecoder> varIntDecoder = Optional.empty();
  private boolean decoded = false;
  private boolean disposed = false;
  private final int maxChunkSize;

  public LengthPrefixedPayloadDecoder(
      final RpcPayloadEncoder<T> payloadEncoder,
      final Compressor compressor,
      final int maxChunkSize) {
    this.payloadEncoder = payloadEncoder;
    this.compressor = compressor;
    this.maxChunkSize = maxChunkSize;
  }

  @Override
  public Optional<T> decodeOneMessage(final ByteBuf in) throws RpcException {
    if (disposed) {
      throw new IllegalStateException("Trying to reuse disposed LengthPrefixedPayloadDecoder");
    }
    if (!in.isReadable()) {
      return Optional.empty();
    }
    if (decoded) {
      throw new RpcException.ExtraDataAppendedException();
    }

    if (decompressor.isEmpty()) {
      final Optional<Integer> maybeLength = readLengthPrefixHeader(in);
      if (maybeLength.isPresent()) {
        final int length = maybeLength.get();
        if (!payloadEncoder.isLengthWithinBounds(length)) {
          throw new LengthOutOfBoundsException();
        }
        decompressor = Optional.of(compressor.createDecompressor(length));
      }
    }
    if (decompressor.isPresent()) {
      final Optional<ByteBuf> ret;
      try {
        ret = decompressor.get().decodeOneMessage(in);
      } catch (PayloadSmallerThanExpectedException e) {
        throw new PayloadTruncatedException();
      } catch (PayloadLargerThanExpectedException e) {
        throw new ExtraDataAppendedException();
      } catch (CompressionException e) {
        throw new DecompressFailedException();
      }

      if (ret.isPresent()) {
        decompressor = Optional.empty();
        try {
          // making a copy here since the Bytes.wrapByteBuf(buf).slice(...)
          // would be broken after [in] buffer is released
          byte[] arr = new byte[ret.get().readableBytes()];
          ret.get().readBytes(arr);
          Bytes bytes = Bytes.wrap(arr);
          decoded = true;
          return Optional.of(payloadEncoder.decode(bytes));
        } finally {
          ret.get().release();
        }
      } else {
        return Optional.empty();
      }
    } else {
      return Optional.empty();
    }
  }

  @Override
  public void complete() throws RpcException {
    if (disposed) {
      throw new IllegalStateException("Trying to reuse disposable LengthPrefixedPayloadDecoder");
    }
    disposed = true;

    try {
      if (varIntDecoder.isPresent()) {
        // if varIntDecoder exists then payload length was not read completely
        throw new MessageTruncatedException();
      }
      if (decompressor.isPresent()) {
        // if decompressor still exists then not enough data was fed to it
        throw new PayloadTruncatedException();
      }
      if (!decoded) {
        throw new MessageTruncatedException();
      }
    } finally {
      close();
    }
  }

  @Override
  public void close() {
    varIntDecoder.ifPresent(AbstractByteBufDecoder::close);
    decompressor.ifPresent(ByteBufDecoder::close);
  }

  /** Decode the length-prefix header, which contains the length of the uncompressed payload */
  private Optional<Integer> readLengthPrefixHeader(final ByteBuf in) throws RpcException {

    if (varIntDecoder.isEmpty()) {
      varIntDecoder = Optional.of(new VarIntDecoder());
    }

    Optional<Long> lengthMaybe;
    try {
      lengthMaybe = varIntDecoder.get().decodeOneMessage(in);
    } catch (IllegalStateException e) {
      // varint overflow
      throw new ChunkTooLongException();
    }
    if (lengthMaybe.isEmpty()) {
      // wait for more byte to read length field
      return Optional.empty();
    }

    varIntDecoder = Optional.empty();

    long length = lengthMaybe.get();
    if (length > maxChunkSize) {
      throw new ChunkTooLongException();
    }
    return Optional.of((int) length);
  }

  private static class VarIntDecoder extends AbstractByteBufDecoder<Long, RuntimeException> {
    @Override
    protected Optional<Long> decodeOneImpl(final ByteBuf in) {
      long length = ByteBufExtKt.readUvarint(in);
      if (length < 0) {
        // wait for more byte to read length field
        return Optional.empty();
      }
      return Optional.of(length);
    }

    @Override
    protected void throwUnprocessedDataException(final int dataLeft) throws RuntimeException {
      // Do nothing, exceptional case is handled upstream
    }
  }
}
