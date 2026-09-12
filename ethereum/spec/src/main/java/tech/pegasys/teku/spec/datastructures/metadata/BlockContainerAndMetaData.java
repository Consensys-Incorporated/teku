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

package tech.pegasys.teku.spec.datastructures.metadata;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;

public record BlockContainerAndMetaData(
    BlockContainer blockContainer,
    SpecMilestone milestone,
    UInt256 executionPayloadValue,
    UInt256 consensusBlockValue,
    boolean payloadIncluded,
    Optional<String> builderUrl) {

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private BlockContainer blockContainer;
    private SpecMilestone milestone;
    private UInt256 executionPayloadValue;
    private UInt256 consensusBlockValue;
    private boolean payloadIncluded;
    private Optional<String> builderUrl = Optional.empty();

    private Builder() {}

    private Builder(final BlockContainerAndMetaData source) {
      this.blockContainer = source.blockContainer;
      this.milestone = source.milestone;
      this.executionPayloadValue = source.executionPayloadValue;
      this.consensusBlockValue = source.consensusBlockValue;
      this.payloadIncluded = source.payloadIncluded;
      this.builderUrl = source.builderUrl;
    }

    public Builder blockContainer(final BlockContainer blockContainer) {
      this.blockContainer = blockContainer;
      return this;
    }

    public Builder milestone(final SpecMilestone milestone) {
      this.milestone = milestone;
      return this;
    }

    public Builder executionPayloadValue(final UInt256 executionPayloadValue) {
      this.executionPayloadValue = executionPayloadValue;
      return this;
    }

    public Builder consensusBlockValue(final UInt256 consensusBlockValue) {
      this.consensusBlockValue = consensusBlockValue;
      return this;
    }

    public Builder payloadIncluded(final boolean payloadIncluded) {
      this.payloadIncluded = payloadIncluded;
      return this;
    }

    public Builder builderUrl(final Optional<String> builderUrl) {
      this.builderUrl = builderUrl;
      return this;
    }

    public BlockContainerAndMetaData build() {
      checkNotNull(blockContainer);
      checkNotNull(milestone);
      checkNotNull(executionPayloadValue);
      checkNotNull(consensusBlockValue);
      return new BlockContainerAndMetaData(
          blockContainer,
          milestone,
          executionPayloadValue,
          consensusBlockValue,
          payloadIncluded,
          builderUrl);
    }
  }
}
