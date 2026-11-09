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

package tech.pegasys.teku.infrastructure.async;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class OccurrenceCounter {
  private final int[] snapshots;

  private int position = 0;

  private final AtomicInteger counter = new AtomicInteger(0);

  public OccurrenceCounter(final int snapshotCount) {
    this.snapshots = new int[snapshotCount];
    Arrays.fill(snapshots, 0);
  }

  public int increment() {
    return counter.incrementAndGet();
  }

  public int poll() {
    final int count = counter.getAndSet(0);
    snapshots[position] = count;
    position = nextSlot();
    return count;
  }

  public int getCount() {
    return counter.get();
  }

  public int getTotalCount() {
    return Arrays.stream(snapshots).sum() + counter.get();
  }

  int currentSlot() {
    return position;
  }

  int nextSlot() {
    return (position + 1) % snapshots.length;
  }
}
