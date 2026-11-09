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

package tech.pegasys.teku.reference.phase0.ssz_generic.containers;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class BitsStruct
    extends Container5<
        BitsStruct, SszBitlist, SszBitvector, SszBitvector, SszBitlist, SszBitvector> {

  protected BitsStruct(
      final ContainerSchema5<
              BitsStruct, SszBitlist, SszBitvector, SszBitvector, SszBitlist, SszBitvector>
          schema,
      final TreeNode backingNode) {
    super(schema, backingNode);
  }
}
