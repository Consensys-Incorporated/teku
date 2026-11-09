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

package tech.pegasys.teku.spec.datastructures.lightclient.versions.gloas;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeader;

public class LightClientHeaderGloas
    extends Container3<LightClientHeaderGloas, BeaconBlockHeader, SszBytes32, SszBytes32Vector>
    implements LightClientHeader {

  public LightClientHeaderGloas(
      final LightClientHeaderSchemaGloas schema,
      final BeaconBlockHeader beacon,
      final SszBytes32 executionBlockHash,
      final SszBytes32Vector executionBranch) {
    super(schema, beacon, executionBlockHash, executionBranch);
  }

  protected LightClientHeaderGloas(
      final LightClientHeaderSchemaGloas type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public BeaconBlockHeader getBeacon() {
    return getField0();
  }

  public SszBytes32 getExecutionBlockHash() {
    return getField1();
  }

  public SszBytes32Vector getExecutionBranch() {
    return getField2();
  }

  @Override
  public Optional<LightClientHeaderGloas> toVersionGloas() {
    return Optional.of(this);
  }
}
