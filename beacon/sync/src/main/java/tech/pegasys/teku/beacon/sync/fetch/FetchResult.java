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

package tech.pegasys.teku.beacon.sync.fetch;

import java.util.Optional;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;

public final class FetchResult<T> {

  public enum Status {
    SUCCESSFUL,
    NO_AVAILABLE_PEERS,
    CANCELLED,
    FETCH_FAILED
  }

  private final Optional<Eth2Peer> peer;
  private final Status status;
  private final Optional<T> result;

  private FetchResult(
      final Optional<Eth2Peer> peer, final Status status, final Optional<T> result) {
    this.peer = peer;
    this.status = status;
    this.result = result;
  }

  public static <T> FetchResult<T> createSuccessful(final T result) {
    return new FetchResult<>(Optional.empty(), Status.SUCCESSFUL, Optional.of(result));
  }

  public static <T> FetchResult<T> createSuccessful(final Eth2Peer peer, final T result) {
    return new FetchResult<>(Optional.of(peer), Status.SUCCESSFUL, Optional.of(result));
  }

  public static <T> FetchResult<T> createFailed(final Status failureStatus) {
    return new FetchResult<>(Optional.empty(), failureStatus, Optional.empty());
  }

  public static <T> FetchResult<T> createFailed(final Eth2Peer peer, final Status failureStatus) {
    return new FetchResult<>(Optional.of(peer), failureStatus, Optional.empty());
  }

  public Optional<Eth2Peer> getPeer() {
    return peer;
  }

  public boolean isSuccessful() {
    return status == Status.SUCCESSFUL;
  }

  public Status getStatus() {
    return status;
  }

  /** Present if {@link #isSuccessful} returns true * */
  public Optional<T> getResult() {
    return result;
  }
}
