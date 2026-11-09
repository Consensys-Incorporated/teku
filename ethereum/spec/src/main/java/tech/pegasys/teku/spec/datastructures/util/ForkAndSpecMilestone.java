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

package tech.pegasys.teku.spec.datastructures.util;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.state.Fork;

public class ForkAndSpecMilestone {
  private final Fork fork;
  private final SpecMilestone specMilestone;

  public ForkAndSpecMilestone(final Fork fork, final SpecMilestone specMilestone) {
    this.fork = fork;
    this.specMilestone = specMilestone;
  }

  public Fork getFork() {
    return fork;
  }

  public SpecMilestone getSpecMilestone() {
    return specMilestone;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ForkAndSpecMilestone that = (ForkAndSpecMilestone) o;
    return Objects.equals(fork, that.fork) && specMilestone == that.specMilestone;
  }

  @Override
  public int hashCode() {
    return Objects.hash(fork, specMilestone);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("fork", fork)
        .add("specMilestone", specMilestone)
        .toString();
  }
}
