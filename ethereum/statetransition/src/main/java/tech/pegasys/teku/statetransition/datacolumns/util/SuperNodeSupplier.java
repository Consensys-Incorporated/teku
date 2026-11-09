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

package tech.pegasys.teku.statetransition.datacolumns.util;

import com.google.common.base.Supplier;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;

public class SuperNodeSupplier implements Supplier<Boolean> {

  private final Spec spec;
  private final Supplier<CustodyGroupCountManager> custodyGroupCountManager;

  public SuperNodeSupplier(
      final Spec spec, final Supplier<CustodyGroupCountManager> custodyGroupCountManager) {
    this.spec = spec;
    this.custodyGroupCountManager = custodyGroupCountManager;
  }

  @Override
  public Boolean get() {
    if (!spec.isMilestoneSupported(SpecMilestone.FULU)) {
      return false;
    }
    return MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers())
        .isSuperNode(custodyGroupCountManager.get().getCustodyGroupCount());
  }
}
