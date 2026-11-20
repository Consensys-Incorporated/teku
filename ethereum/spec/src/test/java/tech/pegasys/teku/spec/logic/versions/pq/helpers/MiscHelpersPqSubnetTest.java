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

package tech.pegasys.teku.spec.logic.versions.pq.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/** PQ's stable attestation subnets, chosen from ATTESTATION_SUBNET_COUNT_PQ subnets. */
class MiscHelpersPqSubnetTest {

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final MiscHelpers pqHelpers = spec.forMilestone(SpecMilestone.PQ).miscHelpers();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void attestationSubnetCountIsThePqCount() {
    assertThat(pqHelpers.getAttestationSubnetCount()).isEqualTo(16);
    assertThat(spec.forMilestone(SpecMilestone.GLOAS).miscHelpers().getAttestationSubnetCount())
        .isEqualTo(64);
  }

  /**
   * compute_subscribed_subnet in PQ is the phase 0 function with ATTESTATION_SUBNET_COUNT_PQ in
   * place of ATTESTATION_SUBNET_COUNT, for both the prefix bits and the modulus. Phase 0 helpers on
   * a config with 16 subnets compute it independently.
   */
  @Test
  void subscribedSubnetsMatchPhase0WithSixteenSubnets() {
    final MiscHelpers sixteenSubnetHelpers =
        TestSpecFactory.createMinimalPhase0(builder -> builder.attestationSubnetCount(16))
            .getGenesisSpec()
            .miscHelpers();
    for (int i = 0; i < 50; i++) {
      final UInt256 nodeId = dataStructureUtil.randomUInt256();
      final UInt64 epoch = UInt64.valueOf(i * 97L);
      assertThat(pqHelpers.computeSubscribedSubnets(nodeId, epoch))
          .isEqualTo(sixteenSubnetHelpers.computeSubscribedSubnets(nodeId, epoch))
          .allMatch(subnet -> subnet.isLessThan(16));
    }
  }
}
