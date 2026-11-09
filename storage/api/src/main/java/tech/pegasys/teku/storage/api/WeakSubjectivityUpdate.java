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

package tech.pegasys.teku.storage.api;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class WeakSubjectivityUpdate {
  private final Optional<Checkpoint> weakSubjectivityCheckpoint;

  private WeakSubjectivityUpdate(final Optional<Checkpoint> weakSubjectivityCheckpoint) {
    this.weakSubjectivityCheckpoint = weakSubjectivityCheckpoint;
  }

  public static WeakSubjectivityUpdate clearWeakSubjectivityCheckpoint() {
    return new WeakSubjectivityUpdate(Optional.empty());
  }

  public static WeakSubjectivityUpdate setWeakSubjectivityCheckpoint(
      final Checkpoint newCheckpoint) {
    return new WeakSubjectivityUpdate(Optional.of(newCheckpoint));
  }

  public Optional<Checkpoint> getWeakSubjectivityCheckpoint() {
    return weakSubjectivityCheckpoint;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final WeakSubjectivityUpdate that = (WeakSubjectivityUpdate) o;
    return Objects.equals(weakSubjectivityCheckpoint, that.weakSubjectivityCheckpoint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(weakSubjectivityCheckpoint);
  }
}
