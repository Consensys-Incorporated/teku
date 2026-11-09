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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.time.TimeProvider;

public interface RateTracker {
  RateTracker NOOP =
      new RateTracker() {
        @Override
        public Optional<RequestKey> generateRequestKey(long objectCount) {
          return Optional.empty();
        }

        @Override
        public void adjustRequestObjectCount(RequestKey requestKey, long objectCount) {}

        @Override
        public long getAvailableObjectCount() {
          return 0;
        }
      };

  // boundary: if a request comes in and remaining capacity is at least 1, then
  // they can have the objects they request otherwise they get none.
  Optional<RequestKey> generateRequestKey(long objectCount);

  long getAvailableObjectCount();

  void adjustRequestObjectCount(RequestKey requestKey, long objectCount);

  static RateTracker create(
      final int peerRateLimit,
      final long timeoutSeconds,
      final TimeProvider timeProvider,
      final String name) {
    return new RateTrackerImpl(peerRateLimit, timeoutSeconds, timeProvider, name);
  }
}
