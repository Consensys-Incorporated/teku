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

import static com.google.common.base.Preconditions.checkArgument;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;

public abstract class FixedSizeByteBufDecoder<TMessage, TException extends Exception>
    extends AbstractByteBufDecoder<TMessage, TException> {
  private final int fixedSize;

  protected FixedSizeByteBufDecoder(final int size) {
    checkArgument(size >= 1, "Fixed size must be >= 1");
    this.fixedSize = size;
  }

  @Override
  protected Optional<TMessage> decodeOneImpl(final ByteBuf in) {
    if (in.readableBytes() < fixedSize) {
      return Optional.empty();
    }
    final byte[] bytes = new byte[fixedSize];
    in.readBytes(bytes);
    TMessage msg = decodeBytes(Bytes.of(bytes));
    return Optional.of(msg);
  }

  protected abstract TMessage decodeBytes(final Bytes bytes);
}
