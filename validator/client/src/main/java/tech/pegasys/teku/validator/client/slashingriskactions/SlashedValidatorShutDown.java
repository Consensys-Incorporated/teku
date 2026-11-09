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

package tech.pegasys.teku.validator.client.slashingriskactions;

import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;

public class SlashedValidatorShutDown implements SlashingRiskAction {

  private final StatusLogger statusLog;

  public SlashedValidatorShutDown() {
    this(StatusLogger.STATUS_LOG);
  }

  public SlashedValidatorShutDown(final StatusLogger statusLog) {
    this.statusLog = statusLog;
  }

  @Override
  public void perform(final List<BLSPublicKey> pubKeys) {
    if (!pubKeys.isEmpty()) {
      statusLog.validatorSlashedAlert(
          pubKeys.stream().map(BLSPublicKey::toHexString).collect(Collectors.toSet()));
      shutdown();
    }
  }
}
