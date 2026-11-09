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

package tech.pegasys.teku.api.migrated;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;

public class AllBlocksAtSlotData {
  private final SpecMilestone version;
  private final List<SignedBeaconBlock> blocks;

  public AllBlocksAtSlotData(final List<BlockAndMetaData> blocks) {
    Preconditions.checkArgument(!blocks.isEmpty(), "BlockAndMetaData list must not be empty");
    this.version = blocks.get(0).getMilestone();
    this.blocks = blocks.stream().map(ObjectAndMetaData::getData).toList();
  }

  public AllBlocksAtSlotData(final SpecMilestone version, final List<SignedBeaconBlock> blocks) {
    this.version = version;
    this.blocks = blocks;
  }

  public SpecMilestone getVersion() {
    return version;
  }

  public List<SignedBeaconBlock> getBlocks() {
    return blocks;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AllBlocksAtSlotData that = (AllBlocksAtSlotData) o;
    return version == that.version && Objects.equals(blocks, that.blocks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, blocks);
  }

  @Override
  public String toString() {
    return "AllBlocksAtSlotData{" + "version=" + version + ", blocks=" + blocks + '}';
  }
}
