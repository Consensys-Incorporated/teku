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

package tech.pegasys.teku.spec.datastructures.blocks;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.BeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.SignedBeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.bellatrix.BlindedBeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.bellatrix.SignedBlindedBeaconBlockSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.blocks.versions.deneb.SignedBlockContentsSupplier;

public class BeaconBlockInvariantsPropertyTest {

  @Property
  void extractSlotFromBeaconBlock(
      @ForAll(supplier = BeaconBlockSupplier.class) final BeaconBlock beaconBlock) {
    assertThat(BeaconBlockInvariants.extractBeaconBlockSlot(beaconBlock.sszSerialize()))
        .isEqualTo(beaconBlock.getSlot());
  }

  @Property
  void extractSlotFromBlindedBeaconBlock(
      @ForAll(supplier = BlindedBeaconBlockSupplier.class) final BeaconBlock beaconBlock) {
    assertThat(BeaconBlockInvariants.extractBeaconBlockSlot(beaconBlock.sszSerialize()))
        .isEqualTo(beaconBlock.getSlot());
  }

  @Property
  void extractSlotFromSignedBeaconBlock(
      @ForAll(supplier = SignedBeaconBlockSupplier.class)
          final SignedBeaconBlock signedBeaconBlock) {
    assertThat(
            BeaconBlockInvariants.extractSignedBlockContainerSlot(signedBeaconBlock.sszSerialize()))
        .isEqualTo(signedBeaconBlock.getSlot());
  }

  @Property
  void extractSlotFromSignedBlindedBeaconBlock(
      @ForAll(supplier = SignedBlindedBeaconBlockSupplier.class)
          final SignedBeaconBlock signedBlindedBeaconBlock) {
    assertThat(
            BeaconBlockInvariants.extractSignedBlockContainerSlot(
                signedBlindedBeaconBlock.sszSerialize()))
        .isEqualTo(signedBlindedBeaconBlock.getSlot());
  }

  @Property
  void extractSlotFromSignedBlockContents(
      @ForAll(supplier = SignedBlockContentsSupplier.class)
          final SignedBlockContainer signedBlockContents) {
    assertThat(
            BeaconBlockInvariants.extractSignedBlockContainerSlot(
                signedBlockContents.sszSerialize()))
        .isEqualTo(signedBlockContents.getSlot());
  }
}
