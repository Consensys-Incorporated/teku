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

package tech.pegasys.teku.infrastructure.ssz.tree;

import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.NodeRelation;

class TillIndexVisitor implements TreeVisitor {

  static TreeVisitor create(final TreeVisitor delegate, final long tillGeneralizedIndex) {
    return new TillIndexVisitor(delegate, tillGeneralizedIndex, true);
  }

  private final TreeVisitor delegate;
  private final long tillGIndex;
  private final boolean inclusive;

  public TillIndexVisitor(
      final TreeVisitor delegate, final long tillGIndex, final boolean inclusive) {
    this.delegate = delegate;
    this.tillGIndex = tillGIndex;
    this.inclusive = inclusive;
  }

  @Override
  public boolean visit(final TreeNode node, final long generalizedIndex) {
    NodeRelation compareRes = GIndexUtil.gIdxCompare(generalizedIndex, tillGIndex);
    if (inclusive && compareRes == NodeRelation.RIGHT) {
      return false;
    } else if (!inclusive && (compareRes == NodeRelation.SAME)) {
      return false;
    } else {
      return delegate.visit(node, generalizedIndex);
    }
  }
}
