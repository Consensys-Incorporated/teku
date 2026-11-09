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

package tech.pegasys.teku.networking.eth2.peers;

import com.google.common.base.Preconditions;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class RateTrackerImpl implements RateTracker {

  private final ConcurrentNavigableMap<RequestKey, Long> requests = new ConcurrentSkipListMap<>();

  private static final Logger LOG = LogManager.getLogger();
  private final int peerRateLimit;
  private final long timeoutSeconds;
  private final TimeProvider timeProvider;
  private final String name;

  private long objectsWithinWindow = 0L;
  private int newRequestId = 0;

  public RateTrackerImpl(
      final int peerRateLimit,
      final long timeoutSeconds,
      final TimeProvider timeProvider,
      final String name) {
    Preconditions.checkArgument(
        peerRateLimit > 0,
        "peerRateLimit should be a positive number but it was %s",
        peerRateLimit);
    this.peerRateLimit = peerRateLimit;
    this.timeoutSeconds = timeoutSeconds;
    this.timeProvider = timeProvider;
    this.name = name;
  }

  // boundary: if a request comes in and remaining capacity is at least 1, then
  // they can have the objects they request otherwise they get none.
  @Override
  public synchronized Optional<RequestKey> generateRequestKey(final long objectCount) {
    pruneRequests();
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    if (peerRateLimit - objectsWithinWindow <= 0) {
      return Optional.empty();
    }
    objectsWithinWindow += objectCount;
    final RequestKey requestKey = new RequestKey(currentTime, newRequestId++);
    requests.put(requestKey, objectCount);
    return Optional.of(requestKey);
  }

  @Override
  public synchronized long getAvailableObjectCount() {
    pruneRequests();
    return peerRateLimit - objectsWithinWindow;
  }

  @Override
  public synchronized void adjustRequestObjectCount(
      final RequestKey requestKey, final long returnedObjectsCount) {
    pruneRequests();
    final Long initialObjectsCount = requests.get(requestKey);
    if (initialObjectsCount != null) {
      requests.put(requestKey, returnedObjectsCount);
      objectsWithinWindow = objectsWithinWindow - initialObjectsCount + returnedObjectsCount;
    }
  }

  private void pruneRequests() {
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    if (currentTime.isLessThan(timeoutSeconds)) {
      LOG.debug(
          "Timeout(seconds): {} is greater than current time(seconds): {}",
          timeoutSeconds,
          currentTime);
      return;
    }

    final NavigableMap<RequestKey, Long> headMap =
        requests.headMap(new RequestKey(currentTime.minus(timeoutSeconds), 0), false);

    final long prunedCount = headMap.values().stream().mapToLong(Long::longValue).sum();
    headMap.clear();
    objectsWithinWindow = Math.max(objectsWithinWindow - prunedCount, 0L);
  }

  @Override
  public String toString() {
    return "RateTrackerImpl{"
        + "peerRateLimit="
        + peerRateLimit
        + ", objectsWithinWindow="
        + objectsWithinWindow
        + ", name='"
        + name
        + '\''
        + '}';
  }
}
