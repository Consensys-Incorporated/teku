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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.noop;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.AbstractByteBufDecoder;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.CompressionException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.compression.exceptions.PayloadSmallerThanExpectedException;

public class NoopDecoder extends AbstractByteBufDecoder<ByteBuf, CompressionException> {
  private final int expectedBytes;

  public NoopDecoder(final int expectedBytes) {
    this.expectedBytes = expectedBytes;
  }

  @Override
  protected Optional<ByteBuf> decodeOneImpl(final ByteBuf in) {
    if (in.readableBytes() < expectedBytes) {
      return Optional.empty();
    }
    return Optional.of(in.readRetainedSlice(expectedBytes));
  }

  @Override
  protected void throwUnprocessedDataException(final int dataLeft) throws CompressionException {
    throw new PayloadSmallerThanExpectedException(
        "The stream complete, but unprocessed data left: " + dataLeft);
  }
}
