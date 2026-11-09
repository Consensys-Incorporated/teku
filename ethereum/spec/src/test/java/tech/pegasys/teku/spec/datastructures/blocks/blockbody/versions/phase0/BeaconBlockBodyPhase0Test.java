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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.phase0;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;

public class BeaconBlockBodyPhase0Test extends AbstractBeaconBlockBodyTest<BeaconBlockBodyPhase0> {

  @BeforeEach
  void setup() {
    super.setUpBaseClass(SpecMilestone.PHASE0, () -> {});
  }

  @Override
  protected BeaconBlockBodyPhase0 createBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    final BeaconBlockBodyBuilder bodyBuilder = createBeaconBlockBodyBuilder();
    contentProvider.accept(bodyBuilder);
    return (BeaconBlockBodyPhase0) bodyBuilder.build();
  }

  @Override
  protected BlindedBeaconBlockBodyBellatrix createBlindedBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    return null;
  }
}
