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

package tech.pegasys.teku.spec.datastructures.lightclient;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;

public class LightClientBootstrap
    extends Container3<LightClientBootstrap, LightClientHeader, SyncCommittee, SszBytes32Vector> {

  public LightClientBootstrap(
      final LightClientBootstrapSchema schema,
      final LightClientHeader lightClientHeader,
      final SyncCommittee syncCommittee,
      final SszBytes32Vector syncCommitteeBranch) {
    super(schema, lightClientHeader, syncCommittee, syncCommitteeBranch);
  }

  protected LightClientBootstrap(
      final LightClientBootstrapSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public LightClientHeader getLightClientHeader() {
    return getField0();
  }

  public SyncCommittee getCurrentSyncCommittee() {
    return getField1();
  }

  public SszBytes32Vector getSyncCommitteeBranch() {
    return getField2();
  }
}
