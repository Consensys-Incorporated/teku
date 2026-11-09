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

package tech.pegasys.teku.spec.datastructures.state;

import it.unimi.dsi.fastutil.ints.IntList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Committee {

  private final UInt64 index;
  private final IntList committee;

  public Committee(final UInt64 index, final IntList committee) {
    this.index = index;
    this.committee = committee;
  }

  public UInt64 getIndex() {
    return index;
  }

  public IntList getCommittee() {
    return committee;
  }

  public int getCommitteeSize() {
    return committee.size();
  }
}
