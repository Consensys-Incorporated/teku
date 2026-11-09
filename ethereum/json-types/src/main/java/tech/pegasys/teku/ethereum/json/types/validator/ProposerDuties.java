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

package tech.pegasys.teku.ethereum.json.types.validator;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;

public class ProposerDuties {
  private final Bytes32 dependentRoot;
  private final boolean executionOptimistic;
  private final List<ProposerDuty> duties;

  public ProposerDuties(
      final Bytes32 dependentRoot,
      final List<ProposerDuty> duties,
      final boolean executionOptimistic) {
    this.dependentRoot = dependentRoot;
    this.executionOptimistic = executionOptimistic;
    this.duties = duties;
  }

  public Bytes32 getDependentRoot() {
    return dependentRoot;
  }

  public boolean isExecutionOptimistic() {
    return executionOptimistic;
  }

  public List<ProposerDuty> getDuties() {
    return duties;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProposerDuties that = (ProposerDuties) o;
    return Objects.equals(dependentRoot, that.dependentRoot) && Objects.equals(duties, that.duties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dependentRoot, duties);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("dependentRoot", dependentRoot)
        .add("duties", duties)
        .toString();
  }
}
