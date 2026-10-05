/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.spec.logic.common.statetransition.blockvalidator;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BatchSemiAggregate;

/**
 * Implementation which doesn't perform any actual validations on {@link #verify(List, Bytes,
 * BLSSignature)} call but just collects signatures which are then validated in a batched optimized
 * way with {@link #batchVerify()} call.
 *
 * <p>Every instance of this class is disposable, i.e. it is intended for just a single batch and a
 * single {@link #batchVerify()} call.
 *
 * <p>This is thread-safe class.
 */
public class BatchSignatureVerifierImpl implements BatchSignatureVerifier {

  record Job(int idx, List<BLSPublicKey> publicKeys, Bytes message, BLSSignature signature) {}

  @VisibleForTesting final List<Job> toVerify = new ArrayList<>();
  private boolean complete = false;

  @Override
  public synchronized boolean verify(
      final List<BLSPublicKey> publicKeys, final Bytes message, final BLSSignature signature) {
    if (complete) {
      throw new IllegalStateException("Reuse of disposable instance");
    }

    checkArgument(!publicKeys.isEmpty(), "No public keys supplied for verify");
    toVerify.add(new Job(toVerify.size(), publicKeys, message, signature));
    return true;
  }

  @Override
  public boolean verify(
      final List<List<BLSPublicKey>> publicKeys,
      final List<Bytes> messages,
      final List<BLSSignature> signatures) {
    for (int i = 0; i < publicKeys.size(); i++) {
      if (!verify(publicKeys.get(i), messages.get(i), signatures.get(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Performs verification of all the signatures collected with one or more calls to {@link
   * #verify(List, Bytes, BLSSignature)}
   *
   * <p>After this method completes the instance should be disposed and any subsequent calls to this
   * instance methods would fail with exception
   */
  @Override
  public synchronized boolean batchVerify() {
    if (complete) {
      throw new IllegalStateException("Reuse of disposable instance");
    }
    List<BatchSemiAggregate> batchSemiAggregates =
        toVerify.stream()
            .parallel()
            .map(job -> BLS.prepareBatchVerify(job.idx, job.publicKeys, job.message, job.signature))
            .toList();
    complete = true;
    if (batchSemiAggregates.isEmpty()) {
      return true;
    }
    return BLS.completeBatchVerify(batchSemiAggregates);
  }
}
