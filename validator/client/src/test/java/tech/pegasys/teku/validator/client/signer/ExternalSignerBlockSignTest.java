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

package tech.pegasys.teku.validator.client.signer;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.signatures.SigningRootUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExternalSignerBlockSignTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SigningRootUtil signingRootUtil = new SigningRootUtil(spec);
  private final ForkInfo fork = dataStructureUtil.randomForkInfo();

  @Test
  void blockAndBlockHeaderSigningRootShouldBeEqual() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(10);

    final Bytes blockHeaderSigningRoot =
        signingRootUtil.signingRootForSignBlockHeader(BeaconBlockHeader.fromBlock(block), fork);
    final Bytes blockSigningRoot = signingRootUtil.signingRootForSignBlock(block, fork);

    assertThat(blockHeaderSigningRoot).isEqualTo(blockSigningRoot);
  }
}
