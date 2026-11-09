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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.google.common.base.VerifyException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.TestForkDigestContextDecoder;

class AbstractByteBufDecoderTest {

  @Test
  void decodeOneMessage_afterComplete_returnsEmptyInsteadOfThrowing() throws Exception {
    final TestForkDigestContextDecoder decoder = new TestForkDigestContextDecoder();
    final ByteBuf buf = Unpooled.wrappedBuffer(Bytes.of(1, 2, 3, 4).toArray());

    final Optional<Bytes4> firstResult = decoder.decodeOneMessage(buf);
    assertThat(firstResult).isPresent();

    decoder.complete();

    // After complete(), decodeOneMessage must return empty rather than throw or access
    // the released buffer (Error 1) or re-enter complete() (Error 2).
    final ByteBuf buf2 = Unpooled.wrappedBuffer(Bytes.of(5, 6, 7, 8).toArray());
    final Optional<Bytes4> secondResult = decoder.decodeOneMessage(buf2);
    assertThat(secondResult).isEmpty();

    buf.release();
    buf2.release();
  }

  @Test
  void decodeOneMessage_afterClose_returnsEmptyInsteadOfThrowing() throws Exception {
    final TestForkDigestContextDecoder decoder = new TestForkDigestContextDecoder();
    final ByteBuf buf = Unpooled.wrappedBuffer(Bytes.of(1, 2, 3, 4).toArray());

    final Optional<Bytes4> firstResult = decoder.decodeOneMessage(buf);
    assertThat(firstResult).isPresent();

    decoder.close();

    final ByteBuf buf2 = Unpooled.wrappedBuffer(Bytes.of(5, 6, 7, 8).toArray());
    final Optional<Bytes4> secondResult = decoder.decodeOneMessage(buf2);
    assertThat(secondResult).isEmpty();

    buf.release();
    buf2.release();
  }

  @Test
  void decodeOneMessage_accumulatesManySmallInputsInLinearTime() {
    // well under a second when pending bytes are appended once; re-copying them every few inputs
    // takes over a minute
    final int messageLength = 10_000_000;
    final FixedLengthDecoder decoder = new FixedLengthDecoder(messageLength);
    // one input buffer refilled with a single byte each time
    final ByteBuf input = Unpooled.buffer(1);
    final List<ByteBuf> out = new ArrayList<>();
    try {
      assertTimeout(
          Duration.ofSeconds(30),
          () -> {
            for (int i = 0; i < messageLength; i++) {
              input.clear().writeByte(i);
              decoder.decodeOneMessage(input).ifPresent(out::add);
            }
          });
      assertThat(out).hasSize(1);
      assertThat(out.get(0).readableBytes()).isEqualTo(messageLength);
      assertThat(out.get(0).getByte(messageLength - 1)).isEqualTo((byte) (messageLength - 1));
    } finally {
      input.release();
      out.forEach(ByteBuf::release);
      decoder.close();
    }
  }

  @Test
  void decodeOneMessage_leavesBytesAfterMessageInInput() {
    final FixedLengthDecoder decoder = new FixedLengthDecoder(4);
    final ByteBuf first = Unpooled.wrappedBuffer(Bytes.of(1, 2).toArray());
    final ByteBuf second = Unpooled.wrappedBuffer(Bytes.of(3, 4, 5, 6, 7, 8, 9).toArray());

    assertThat(decoder.decodeOneMessage(first)).isEmpty();
    assertThat(first.isReadable()).isFalse();

    final Optional<ByteBuf> message = decoder.decodeOneMessage(second);
    assertThat(message).map(AbstractByteBufDecoderTest::readable).contains(Bytes.of(1, 2, 3, 4));
    assertThat(readable(second)).isEqualTo(Bytes.of(5, 6, 7, 8, 9));

    final Optional<ByteBuf> nextMessage = decoder.decodeOneMessage(second);
    assertThat(nextMessage)
        .map(AbstractByteBufDecoderTest::readable)
        .contains(Bytes.of(5, 6, 7, 8));
    assertThat(readable(second)).isEqualTo(Bytes.of(9));

    message.get().release();
    nextMessage.get().release();
    first.release();
    second.release();
    decoder.close();
    assertThat(first.refCnt()).isZero();
    assertThat(second.refCnt()).isZero();
  }

  @Test
  void complete_reportsBytesLeftFromEarlierInputs() {
    final FixedLengthDecoder decoder = new FixedLengthDecoder(4);
    final ByteBuf input = Unpooled.wrappedBuffer(Bytes.of(1, 2, 3).toArray());
    assertThat(decoder.decodeOneMessage(input)).isEmpty();
    input.release();

    assertThatThrownBy(decoder::complete)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unprocessed bytes: 3");
    assertThat(input.refCnt()).isZero();
  }

  @Test
  void decodeOneMessage_rejectsMessageThatLeavesEarlierBytesBehind() {
    // completes a message once 4 bytes are buffered but only consumes 1 of them
    final FixedLengthDecoder decoder = new FixedLengthDecoder(4, 1);
    final ByteBuf first = Unpooled.wrappedBuffer(Bytes.of(1, 2, 3).toArray());
    final ByteBuf second = Unpooled.wrappedBuffer(Bytes.of(4).toArray());

    assertThat(decoder.decodeOneMessage(first)).isEmpty();
    assertThatThrownBy(() -> decoder.decodeOneMessage(second)).isInstanceOf(VerifyException.class);

    assertThat(decoder.lastReturned.refCnt()).isZero();
    first.release();
    second.release();
  }

  @Test
  void close_releasesPendingBytes() {
    final FixedLengthDecoder decoder = new FixedLengthDecoder(4);
    final ByteBuf first = Unpooled.wrappedBuffer(Bytes.of(1).toArray());
    final ByteBuf second = Unpooled.wrappedBuffer(Bytes.of(2).toArray());
    assertThat(decoder.decodeOneMessage(first)).isEmpty();
    assertThat(decoder.decodeOneMessage(second)).isEmpty();
    // the second input is decoded from the buffer holding the pending bytes
    final ByteBuf pending = decoder.lastInput;
    assertThat(pending.refCnt()).isEqualTo(1);

    decoder.close();

    assertThat(pending.refCnt()).isZero();
    first.release();
    second.release();
  }

  private static Bytes readable(final ByteBuf buf) {
    return Bytes.wrap(ByteBufUtil.getBytes(buf));
  }

  /** Returns a slice of {@code consumed} bytes once {@code required} bytes are buffered. */
  private static class FixedLengthDecoder
      extends AbstractByteBufDecoder<ByteBuf, RuntimeException> {
    private final int required;
    private final int consumed;
    private ByteBuf lastInput;
    private ByteBuf lastReturned;

    private FixedLengthDecoder(final int messageLength) {
      this(messageLength, messageLength);
    }

    private FixedLengthDecoder(final int required, final int consumed) {
      this.required = required;
      this.consumed = consumed;
    }

    @Override
    protected Optional<ByteBuf> decodeOneImpl(final ByteBuf in) {
      lastInput = in;
      if (in.readableBytes() < required) {
        return Optional.empty();
      }
      lastReturned = in.readRetainedSlice(consumed);
      return Optional.of(lastReturned);
    }

    @Override
    protected void throwUnprocessedDataException(final int dataLeft) {
      throw new IllegalStateException("Unprocessed bytes: " + dataLeft);
    }
  }
}
