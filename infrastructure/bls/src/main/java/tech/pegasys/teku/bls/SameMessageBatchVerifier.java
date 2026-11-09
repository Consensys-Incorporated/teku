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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.impl.BlsException;

/**
 * Batch verification that groups signatures sharing a message (e.g. attestations with the same
 * AttestationData), so that each distinct message is hashed to G2 and paired only once.
 */
public final class SameMessageBatchVerifier {

  /**
   * Signatures whose message is unique are prepared this many at a time in one blst pairing
   * context, which runs one multi-pair Miller loop for the whole chunk.
   */
  private static final int SINGLETON_CHUNK_SIZE = 8;

  private SameMessageBatchVerifier() {}

  /**
   * Same contract as {@link BLS#batchVerify(List, List, List)}, grouping signatures that share a
   * message.
   *
   * @param parallel Uses the default {@link java.util.concurrent.ForkJoinPool} to parallelize the
   *     work
   * @return True if the verification is successful, false otherwise
   */
  public static boolean batchVerify(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures,
      final boolean parallel) {
    if (BLSConstants.verificationDisabled) {
      return true;
    }
    try {
      checkArgument(
          publicKeys.size() == messages.size() && publicKeys.size() == signatures.size(),
          "Different collection sizes");
      final int count = publicKeys.size();
      if (count == 0) {
        return false;
      }
      if (count == 1) {
        return BLS.fastAggregateVerify(publicKeys.get(0), messages.get(0), signatures.get(0));
      }

      final Map<Bytes, List<Integer>> indicesByMessage = new LinkedHashMap<>();
      for (int i = 0; i < count; i++) {
        indicesByMessage.computeIfAbsent(messages.get(i), __ -> new ArrayList<>()).add(i);
      }
      final List<Supplier<BatchSemiAggregate>> work = new ArrayList<>();
      final List<Integer> singletons = new ArrayList<>();
      for (final List<Integer> group : indicesByMessage.values()) {
        final int firstIndex = group.getFirst();
        if (group.size() == 1) {
          singletons.add(firstIndex);
        } else {
          work.add(
              () ->
                  prepareBatchVerifySameMessage(
                      group.stream().map(publicKeys::get).toList(),
                      messages.get(firstIndex),
                      group.stream().map(signatures::get).toList(),
                      parallel));
        }
      }
      for (final List<Integer> chunk : Lists.partition(singletons, SINGLETON_CHUNK_SIZE)) {
        work.add(
            () ->
                prepareBatchVerifyChunk(
                    chunk.stream().map(publicKeys::get).toList(),
                    chunk.stream().map(messages::get).toList(),
                    chunk.stream().map(signatures::get).toList()));
      }

      Stream<Supplier<BatchSemiAggregate>> workStream = work.stream();
      if (parallel) {
        workStream = workStream.parallel();
      }
      return BLS.completeBatchVerify(workStream.map(Supplier::get).toList());
    } catch (IllegalArgumentException e) {
      throw new BlsException("Failed to batchVerify", e);
    }
  }

  /**
   * Variant of {@link BLS#prepareBatchVerify(int, List, Bytes, BLSSignature)} for several
   * signatures over the same message: the message is hashed to the curve and paired only once. The
   * result can be mixed with the results of {@link BLS#prepareBatchVerify(int, List, Bytes,
   * BLSSignature)}.
   */
  private static BatchSemiAggregate prepareBatchVerifySameMessage(
      final List<List<BLSPublicKey>> publicKeys,
      final Bytes message,
      final List<BLSSignature> signatures,
      final boolean parallel) {
    try {
      return BLS.getBlsImpl()
          .prepareBatchVerifySameMessage(
              publicKeys.stream()
                  .map(keys -> keys.stream().map(BLSPublicKey::getPublicKey).toList())
                  .toList(),
              message,
              signatures.stream().map(BLSSignature::getSignature).toList(),
              parallel);
    } catch (BlsException e) {
      return new InvalidBatchSemiAggregate();
    }
  }

  /**
   * Prepares several signatures over distinct messages in one pairing context. The result can be
   * mixed with the results of {@link #prepareBatchVerifySameMessage}.
   */
  private static BatchSemiAggregate prepareBatchVerifyChunk(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures) {
    try {
      return BLS.getBlsImpl()
          .prepareBatchVerifyChunk(
              publicKeys.stream()
                  .map(keys -> keys.stream().map(BLSPublicKey::getPublicKey).toList())
                  .toList(),
              messages,
              signatures.stream().map(BLSSignature::getSignature).toList());
    } catch (BlsException e) {
      return new InvalidBatchSemiAggregate();
    }
  }

  /**
   * Marks a group that failed to prepare; {@link BLS#completeBatchVerify(List)} returns false when
   * it meets one. Mirrors the private class of the same name in {@link BLS}.
   */
  private static final class InvalidBatchSemiAggregate implements BatchSemiAggregate {}
}
