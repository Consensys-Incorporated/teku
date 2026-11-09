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

package tech.pegasys.teku.bls.impl;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Streams;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;

/**
 * Tuple of {@link PublicKey} and message {@link Bytes} used for batch verification
 *
 * @see Signature#verify(List)
 */
public class PublicKeyMessagePair {

  public static List<PublicKeyMessagePair> fromLists(
      final List<PublicKey> publicKeys, final List<Bytes> messages) {
    checkArgument(publicKeys.size() == messages.size());
    return Streams.zip(publicKeys.stream(), messages.stream(), PublicKeyMessagePair::new).toList();
  }

  private final PublicKey publicKey;
  private final Bytes message;

  public PublicKeyMessagePair(final PublicKey publicKey, final Bytes message) {
    this.publicKey = publicKey;
    this.message = message;
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public Bytes getMessage() {
    return message;
  }
}
