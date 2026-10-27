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

package tech.pegasys.teku.bls;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.impl.BLS12381;
import tech.pegasys.teku.bls.impl.BlsException;
import tech.pegasys.teku.bls.impl.KeyPair;
import tech.pegasys.teku.bls.impl.PublicKey;
import tech.pegasys.teku.bls.impl.SecretKey;
import tech.pegasys.teku.bls.impl.Signature;

/**
 * A {@link BLS12381} that delegates every call and records the thread each batch-verification
 * prepare step ran on, plus the parallel argument of every same-message prepare call. Install it
 * with {@link BLS#setBlsImplementation(BLS12381)} and remove it with {@link
 * BLS#resetBlsImplementation()} in a {@code finally} block.
 */
public class RecordingBLS12381 implements BLS12381 {
  private final BLS12381 delegate;
  private final List<String> prepareThreadNames = new CopyOnWriteArrayList<>();
  private final List<Boolean> sameMessageParallelArguments = new CopyOnWriteArrayList<>();

  public RecordingBLS12381(final BLS12381 delegate) {
    this.delegate = delegate;
  }

  /**
   * Thread name of every prepareBatchVerify, prepareBatchVerify2, same-message and chunk prepare
   * call.
   */
  public List<String> getPrepareThreadNames() {
    return List.copyOf(prepareThreadNames);
  }

  /** The parallel argument of every prepareBatchVerifySameMessage call. */
  public List<Boolean> getSameMessageParallelArguments() {
    return List.copyOf(sameMessageParallelArguments);
  }

  public int getSameMessagePrepareCount() {
    return sameMessageParallelArguments.size();
  }

  private void recordPrepareThread() {
    prepareThreadNames.add(Thread.currentThread().getName());
  }

  @Override
  public KeyPair generateKeyPair(final Random random) {
    return delegate.generateKeyPair(random);
  }

  @Override
  public KeyPair generateKeyPair(final long seed) {
    return delegate.generateKeyPair(seed);
  }

  @Override
  public PublicKey publicKeyFromCompressed(final Bytes48 compressedPublicKeyBytes)
      throws BlsException {
    return delegate.publicKeyFromCompressed(compressedPublicKeyBytes);
  }

  @Override
  public Signature signatureFromCompressed(final Bytes compressedSignatureBytes) {
    return delegate.signatureFromCompressed(compressedSignatureBytes);
  }

  @Override
  public SecretKey secretKeyFromBytes(final Bytes32 secretKeyBytes) {
    return delegate.secretKeyFromBytes(secretKeyBytes);
  }

  @Override
  public PublicKey aggregatePublicKeys(final List<? extends PublicKey> publicKeys) {
    return delegate.aggregatePublicKeys(publicKeys);
  }

  @Override
  public Signature aggregateSignatures(final List<? extends Signature> signatures)
      throws IllegalArgumentException {
    return delegate.aggregateSignatures(signatures);
  }

  @Override
  public BatchSemiAggregate prepareBatchVerify(
      final int index,
      final List<? extends PublicKey> publicKeys,
      final Bytes message,
      final Signature signature) {
    recordPrepareThread();
    return delegate.prepareBatchVerify(index, publicKeys, message, signature);
  }

  @Override
  public BatchSemiAggregate prepareBatchVerify2(
      final int index,
      final List<? extends PublicKey> publicKeys1,
      final Bytes message1,
      final Signature signature1,
      final List<? extends PublicKey> publicKeys2,
      final Bytes message2,
      final Signature signature2) {
    recordPrepareThread();
    return delegate.prepareBatchVerify2(
        index, publicKeys1, message1, signature1, publicKeys2, message2, signature2);
  }

  @Override
  public BatchSemiAggregate prepareBatchVerifySameMessage(
      final List<? extends List<? extends PublicKey>> publicKeys,
      final Bytes message,
      final List<? extends Signature> signatures,
      final boolean parallel) {
    recordPrepareThread();
    sameMessageParallelArguments.add(parallel);
    return delegate.prepareBatchVerifySameMessage(publicKeys, message, signatures, parallel);
  }

  @Override
  public BatchSemiAggregate prepareBatchVerifyChunk(
      final List<? extends List<? extends PublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<? extends Signature> signatures) {
    recordPrepareThread();
    return delegate.prepareBatchVerifyChunk(publicKeys, messages, signatures);
  }

  @Override
  public boolean completeBatchVerify(final List<? extends BatchSemiAggregate> preparedList) {
    return delegate.completeBatchVerify(preparedList);
  }

  @Override
  public Signature randomSignature(final int seed) {
    return delegate.randomSignature(seed);
  }
}
