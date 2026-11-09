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

package tech.pegasys.teku.bls.impl.blst;

import static com.google.common.base.Preconditions.checkNotNull;

import supranational.blst.BLST_ERROR;
import supranational.blst.Pairing;
import tech.pegasys.teku.bls.BatchSemiAggregate;

public final class BlstSemiAggregate implements BatchSemiAggregate {

  static BlstSemiAggregate createInvalid() {
    return new BlstSemiAggregate();
  }

  private Pairing ctx;

  private BlstSemiAggregate() {
    ctx = null;
  }

  BlstSemiAggregate(final Pairing ctx) {
    checkNotNull(ctx);
    this.ctx = ctx;
  }

  Pairing getCtx() {
    if (!isValid()) {
      throw new IllegalStateException("No ctx for invalid BlstSemiAggregate");
    }
    return ctx;
  }

  boolean isValid() {
    return ctx != null;
  }

  void mergeWith(final BlstSemiAggregate other) {
    if (!isValid() || !other.isValid()) {
      ctx = null;
      return;
    }

    BLST_ERROR ret = getCtx().merge(other.getCtx());
    if (ret != BLST_ERROR.BLST_SUCCESS) {
      throw new IllegalStateException("Error merging Blst pairing contexts: " + ret);
    }
  }
}
