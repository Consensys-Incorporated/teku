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

import com.google.common.base.VerifyException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import java.util.Optional;

/**
 * Abstract {@link ByteBufDecoder} implementation which handles unprocessed {@link ByteBuf}s
 *
 * <p>This class is a standalone analog of Netty {@link io.netty.handler.codec.ByteToMessageDecoder}
 *
 * <p>This class is NOT thread safe. Calls should either be synchronized on a higher level or
 * performed from a single thread.
 */
public abstract class AbstractByteBufDecoder<TMessage, TException extends Exception>
    implements ByteBufDecoder<TMessage, TException> {

  // Bytes of earlier inputs that did not complete a message, appended to one growable buffer so
  // the copying stays proportional to the bytes received
  private ByteBuf cumulation;
  private boolean closed = false;

  @Override
  public Optional<TMessage> decodeOneMessage(final ByteBuf in) throws TException {
    if (!in.isReadable() || closed) {
      return Optional.empty();
    }

    try {
      return cumulation == null ? decodeFromInput(in) : decodeFromCumulation(in);
    } catch (Throwable t) {
      close();
      throw t;
    }
  }

  private Optional<TMessage> decodeFromInput(final ByteBuf in) throws TException {
    final Optional<TMessage> outBuf = decodeAvailable(in);
    if (outBuf.isEmpty() && in.isReadable()) {
      cumulation = Unpooled.buffer(in.readableBytes()).writeBytes(in);
    }
    return outBuf;
  }

  private Optional<TMessage> decodeFromCumulation(final ByteBuf in) throws TException {
    final int inputBytes = in.readableBytes();
    // no slice of the cumulation has been handed out yet, so read bytes can be dropped in place
    cumulation.discardSomeReadBytes();
    cumulation.writeBytes(in, in.readerIndex(), inputBytes);
    final Optional<TMessage> outBuf = decodeAvailable(cumulation);
    if (outBuf.isPresent()) {
      // earlier bytes alone could not complete a message, so whatever is left came from this
      // input: leave it there for the caller
      final int leftover = cumulation.readableBytes();
      if (leftover > inputBytes) {
        ReferenceCountUtil.release(outBuf.get());
        throw new VerifyException(
            "Decoded message left " + leftover + " bytes but only " + inputBytes + " were added");
      }
      in.skipBytes(inputBytes - leftover);
      releaseCumulation();
    } else {
      in.skipBytes(inputBytes);
      if (!cumulation.isReadable()) {
        releaseCumulation();
      }
    }
    return outBuf;
  }

  private void releaseCumulation() {
    if (cumulation != null) {
      cumulation.release();
      cumulation = null;
    }
  }

  private Optional<TMessage> decodeAvailable(final ByteBuf buf) throws TException {
    while (true) {
      final int readerIndex = buf.readerIndex();
      final Optional<TMessage> outBuf = decodeOneImpl(buf);
      if (outBuf.isPresent() || readerIndex == buf.readerIndex() || !buf.isReadable()) {
        return outBuf;
      }
    }
  }

  @Override
  public void complete() throws TException {
    assertNotClosed();
    try {
      if (cumulation != null) {
        throwUnprocessedDataException(cumulation.readableBytes());
      }
    } finally {
      close();
    }
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      releaseCumulation();
    }
  }

  private void assertNotClosed() {
    if (closed) {
      throw new IllegalStateException("Trying to reuse disposed decoder instance");
    }
  }

  protected abstract void throwUnprocessedDataException(int dataLeft) throws TException;

  /**
   * Decodes one message if the full data is available in the buffer
   *
   * <p>If the full data is not available then return {@code empty} and left buffer reader index
   * intact. Bytes that belong to no message, such as padding, may be consumed as they arrive.
   *
   * <p>If a message is read then the buffer reader position should be positioned after the end of
   * message data
   */
  protected abstract Optional<TMessage> decodeOneImpl(ByteBuf in) throws TException;
}
