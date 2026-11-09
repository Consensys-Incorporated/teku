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

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.bls.BLSPublicKey;

public class DoppelgangerDetectionShutDown implements SlashingRiskAction {
  @Override
  public void perform(final List<BLSPublicKey> doppelgangers) {
    STATUS_LOG.exitOnDoppelgangerDetected(
        doppelgangers.stream()
            .map(BLSPublicKey::toAbbreviatedString)
            .collect(Collectors.joining(", ")));
    shutdown();
  }
}
