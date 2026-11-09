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

/**
 * A disposable decoder which decodes one or more messages from a 'stream' which is fed to the
 * decoder as a series of {@link ByteBuf}'s
 *
 * <p>The decoder stores partial unread buffers thus it shouldn't be reused or/and shared across
 * streams.
 *
 * <p>The implementation may limit the number of decoded messages
 *
 * <p>The implementation is assumed to be NOT thread-safe
 *
 * @param <TMessage> the decoded message type
 */
public interface ByteBufDecoder<TMessage, TException extends Exception> {

  /**
   * Decodes exactly one message if available (with all previously buffered partial data chunks plus
   * the new passed {@code in} buffer).
   *
   * <p>The method reads exactly the number of bytes from the {@code in} buffer required to decode a
   * single message. If not all bytes are available to read a single message then the buffer bytes
   * are fully read and buffered inside decoder as partial data for the next message
   *
   * <p>After the call the passed {@code in} buffer can be released by the calling side if required
   *
   * @param in the next chunk of data which may or may not contain full data to decode a message
   * @return {@code empty} if there are not not enough bytes yet to read the full message in this
   *     case {@code in} would be completely read and its content would be buffered by decoder as a
   *     part of the next message If a non-empty value returned the {@code in} buffer may still
   *     contain unread bytes.
   * @throws TException in case of decoding errors or exceeding the maximum number of decoded
   *     messages
   */
  Optional<TMessage> decodeOneMessage(ByteBuf in) throws TException;

  /**
   * Tells the decoder that the input stream is over and no more bytes would be supplied. After
   * {@code complete()} call calling any Decoder method would result in error
   *
   * <p>The {@link #close()} should be invoked by implementation on either success or error so on
   * {@code complete()} return all resources should be released by a decoder
   *
   * <p>To abort the decoder use {@link #close()} method
   *
   * @throws TException if any unprocessed data left
   */
  void complete() throws TException;

  /**
   * Releases all associated resources. Normally should be used to release resources on abrupt
   * completion. Use {@link #complete()} method for regular decoder completion.
   *
   * <p>After {@code close()} calling any Decoder method would result in error.
   */
  void close();
}
