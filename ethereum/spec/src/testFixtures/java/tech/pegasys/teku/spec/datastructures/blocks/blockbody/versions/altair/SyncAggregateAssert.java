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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair;

import static org.assertj.core.api.Assertions.assertThat;

import it.unimi.dsi.fastutil.ints.IntIterable;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.error.BasicErrorMessageFactory;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;

public class SyncAggregateAssert extends AbstractAssert<SyncAggregateAssert, SyncAggregate> {

  private SyncAggregateAssert(final SyncAggregate actual) {
    super(actual, SyncAggregateAssert.class);
  }

  public static SyncAggregateAssert assertThatSyncAggregate(final SyncAggregate actual) {
    return new SyncAggregateAssert(actual);
  }

  public SyncAggregateAssert isEmpty() {
    return hasSyncCommitteeBits().hasInfiniteSignature();
  }

  public SyncAggregateAssert hasSyncCommitteeBits(final IntIterable bits) {
    final SszBitvector actualBits = actual.getSyncCommitteeBits();
    for (int bit : bits) {
      assertThat(actualBits.getAllSetBits().contains(bit)).isTrue();
    }
    return this;
  }

  public SyncAggregateAssert hasSyncCommitteeBits(final int... bits) {
    assertThat(actual.getSyncCommitteeBits())
        .isEqualTo(actual.getSchema().getSyncCommitteeBitsSchema().ofBits(bits));
    return this;
  }

  public SyncAggregateAssert hasInfiniteSignature() {
    if (!actual.getSyncCommitteeSignature().getSignature().isInfinity()) {
      throwAssertionError(
          new BasicErrorMessageFactory(
              "%nExpecting%n  <%s>%nto be infinite", actual.getSyncCommitteeSignature()));
    }
    return this;
  }

  public SyncAggregateAssert hasSignature(final BLSSignature expected) {
    assertThat(actual.getSyncCommitteeSignature().getSignature()).isEqualTo(expected);
    return this;
  }
}
