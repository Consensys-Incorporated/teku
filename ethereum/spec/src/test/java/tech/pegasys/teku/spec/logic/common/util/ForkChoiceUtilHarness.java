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

package tech.pegasys.teku.spec.logic.common.util;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;

class ForkChoiceUtilHarness extends ForkChoiceUtil {
  boolean headWeak;
  boolean parentStrong;
  int headWeakChecks;
  int parentStrongChecks;

  ForkChoiceUtilHarness(
      final SpecConfig specConfig,
      final BeaconStateAccessors beaconStateAccessors,
      final EpochProcessor epochProcessor,
      final AttestationUtil attestationUtil,
      final MiscHelpers miscHelpers) {
    super(specConfig, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
  }

  @Override
  public boolean isHeadWeak(
      final ReadOnlyStore store, final Bytes32 root, final UInt64 reorgThreshold) {
    headWeakChecks++;
    return headWeak;
  }

  @Override
  public boolean isParentStrong(
      final ReadOnlyStore store, final SignedBeaconBlock head, final UInt64 parentThreshold) {
    parentStrongChecks++;
    return parentStrong;
  }

  @Override
  protected int getProposerIndex(final BeaconState proposerPreState) {
    return 1;
  }
}
