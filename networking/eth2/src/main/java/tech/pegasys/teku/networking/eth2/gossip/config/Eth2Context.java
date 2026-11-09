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

package tech.pegasys.teku.networking.eth2.gossip.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;

public class Eth2Context {
  private final int activeValidatorCount;
  private final UInt64 currentSlot;
  private final Optional<Bytes4> forkDigest;
  private final GossipEncoding gossipEncoding;

  private Eth2Context(
      final int activeValidatorCount,
      final UInt64 currentSlot,
      final Optional<Bytes4> forkDigest,
      final GossipEncoding gossipEncoding) {
    this.activeValidatorCount = activeValidatorCount;
    this.currentSlot = currentSlot;
    this.forkDigest = forkDigest;
    this.gossipEncoding = gossipEncoding;
  }

  public static Builder builder() {
    return new Builder();
  }

  public int getActiveValidatorCount() {
    return activeValidatorCount;
  }

  public UInt64 getCurrentSlot() {
    return currentSlot;
  }

  public Optional<Bytes4> getForkDigest() {
    return forkDigest;
  }

  public GossipEncoding getGossipEncoding() {
    return gossipEncoding;
  }

  public static class Builder {
    private UInt64 currentSlot = UInt64.ZERO;
    private Optional<Bytes4> forkDigest = Optional.empty();
    private Integer activeValidatorCount;
    private GossipEncoding gossipEncoding;

    private Builder() {}

    public Eth2Context build() {
      validate();
      return new Eth2Context(activeValidatorCount, currentSlot, forkDigest, gossipEncoding);
    }

    private void validate() {
      checkNotNull(activeValidatorCount);
      checkNotNull(gossipEncoding);
    }

    public Builder currentSlot(final UInt64 currentSlot) {
      checkNotNull(currentSlot);
      this.currentSlot = currentSlot;
      return this;
    }

    public Builder forkDigest(final Bytes4 forkDigest) {
      checkNotNull(forkDigest);
      return forkDigest(Optional.of(forkDigest));
    }

    public Builder forkDigest(final Optional<Bytes4> forkDigest) {
      checkNotNull(forkDigest);
      this.forkDigest = forkDigest;
      return this;
    }

    public Builder activeValidatorCount(final Integer activeValidatorCount) {
      checkNotNull(activeValidatorCount);
      this.activeValidatorCount = activeValidatorCount;
      return this;
    }

    public Builder gossipEncoding(final GossipEncoding gossipEncoding) {
      checkNotNull(gossipEncoding);
      this.gossipEncoding = gossipEncoding;
      return this;
    }
  }
}
