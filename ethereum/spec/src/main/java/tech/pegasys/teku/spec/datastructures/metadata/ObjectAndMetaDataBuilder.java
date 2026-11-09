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

package tech.pegasys.teku.spec.datastructures.metadata;

import tech.pegasys.teku.spec.SpecMilestone;

public class ObjectAndMetaDataBuilder<T> {
  private T data;
  private SpecMilestone milestone;
  private boolean executionOptimistic;
  private boolean canonical;
  private boolean finalized;

  public ObjectAndMetaDataBuilder<T> data(final T data) {
    this.data = data;
    return this;
  }

  public ObjectAndMetaDataBuilder<T> milestone(final SpecMilestone milestone) {
    this.milestone = milestone;
    return this;
  }

  public ObjectAndMetaDataBuilder<T> executionOptimistic(final boolean executionOptimistic) {
    this.executionOptimistic = executionOptimistic;
    return this;
  }

  public ObjectAndMetaDataBuilder<T> canonical(final boolean canonical) {
    this.canonical = canonical;
    return this;
  }

  public ObjectAndMetaDataBuilder<T> finalized(final boolean finalized) {
    this.finalized = finalized;
    return this;
  }

  public ObjectAndMetaData<T> build() {
    return new ObjectAndMetaData<>(data, milestone, executionOptimistic, canonical, finalized);
  }
}
