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

package tech.pegasys.teku.spec.config;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SpecConfigAltair extends SpecConfig {

  static SpecConfigAltair required(final SpecConfig specConfig) {
    return specConfig
        .toVersionAltair()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected altair spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  UInt64 getInactivityPenaltyQuotientAltair();

  int getMinSlashingPenaltyQuotientAltair();

  int getProportionalSlashingMultiplierAltair();

  int getSyncCommitteeSize();

  UInt64 getInactivityScoreBias();

  UInt64 getInactivityScoreRecoveryRate();

  int getEpochsPerSyncCommitteePeriod();

  int getMinSyncCommitteeParticipants();

  int getUpdateTimeout();

  int getSyncMessageDueBps();

  int getContributionDueBps();

  @Override
  Optional<SpecConfigAltair> toVersionAltair();
}
