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

package tech.pegasys.teku.beacon.sync.forward.multipeer.batches;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.AbstractAssert;
import tech.pegasys.teku.beacon.sync.forward.multipeer.chains.TargetChain;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BatchAssert extends AbstractAssert<BatchAssert, Batch> {

  private BatchAssert(final Batch batch, final Class<?> selfType) {
    super(batch, selfType);
  }

  public static BatchAssert assertThatBatch(final Batch batch) {
    return new BatchAssert(batch, BatchAssert.class);
  }

  public void isComplete() {
    assertThat(actual.isComplete())
        .withFailMessage("Expected batch %s to be complete but was not", actual)
        .isTrue();
  }

  public void isNotComplete() {
    assertThat(actual.isComplete())
        .withFailMessage("Expected batch %s to not be complete but was", actual)
        .isFalse();
  }

  public void isConfirmed() {
    assertThat(actual.isConfirmed())
        .withFailMessage("Expected batch %s to be confirmed but was not", actual)
        .isTrue();
  }

  public void isNotConfirmed() {
    assertThat(actual.isConfirmed())
        .withFailMessage("Expected batch %s to not be confirmed but was", actual)
        .isFalse();
  }

  public void hasConfirmedFirstBlock() {
    assertThat(actual.isFirstBlockConfirmed())
        .withFailMessage(
            "Expected batch %s to have first block confirmed but was unconfirmed", actual)
        .isTrue();
  }

  public void hasUnconfirmedFirstBlock() {
    assertThat(actual.isFirstBlockConfirmed())
        .withFailMessage(
            "Expected batch %s to have first block unconfirmed but was confirmed", actual)
        .isFalse();
  }

  public void isEmpty() {
    assertThat(actual.isEmpty())
        .withFailMessage("Expected batch %s to be empty but was not", actual)
        .isTrue();
  }

  public void isNotEmpty() {
    assertThat(actual.isEmpty())
        .withFailMessage("Expected batch %s to not be empty but was", actual)
        .isFalse();
  }

  public void isContested() {
    assertThat(actual.isContested())
        .withFailMessage("Expected batch %s to be contested but was not", actual)
        .isTrue();
  }

  public void isNotContested() {
    assertThat(actual.isContested())
        .withFailMessage("Expected batch %s to not be contested but was", actual)
        .isFalse();
  }

  public void isConfirmedAsEmpty() {
    isComplete();
    isConfirmed();
    isEmpty();
  }

  public void hasFirstSlot(final long expected) {
    hasFirstSlot(UInt64.valueOf(expected));
  }

  public void hasFirstSlot(final UInt64 expected) {
    assertThat(actual.getFirstSlot()).describedAs("firstSlot").isEqualTo(expected);
  }

  public void hasLastSlot(final long expected) {
    hasLastSlot(UInt64.valueOf(expected));
  }

  public void hasLastSlot(final UInt64 expected) {
    assertThat(actual.getLastSlot()).describedAs("lastSlot").isEqualTo(expected);
  }

  public void hasRange(final long firstSlot, final long lastSlot) {
    hasFirstSlot(firstSlot);
    hasLastSlot(lastSlot);
  }

  public void hasTargetChain(final TargetChain expected) {
    assertThat(actual.getTargetChain()).isEqualTo(expected);
  }

  public void isAwaitingBlocks() {
    assertThat(actual.isAwaitingBlocks()).describedAs("awaiting blocks").isTrue();
  }

  public void isNotAwaitingBlocks() {
    assertThat(actual.isAwaitingBlocks()).describedAs("awaiting blocks").isFalse();
  }
}
