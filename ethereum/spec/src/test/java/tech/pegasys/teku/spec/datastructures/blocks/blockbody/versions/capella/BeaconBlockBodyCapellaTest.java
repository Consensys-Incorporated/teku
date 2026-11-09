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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodyAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BlindedBeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;

class BeaconBlockBodyCapellaTest extends AbstractBeaconBlockBodyTest<BeaconBlockBodyCapella> {

  protected SyncAggregate syncAggregate;
  protected ExecutionPayload executionPayload;
  protected ExecutionPayloadHeader executionPayloadHeader;
  protected SszList<SignedBlsToExecutionChange> blsToExecutionChanges;

  @BeforeEach
  void setup() {
    super.setUpBaseClass(
        SpecMilestone.CAPELLA,
        () -> {
          syncAggregate = dataStructureUtil.randomSyncAggregate();
          executionPayload = dataStructureUtil.randomExecutionPayload();
          executionPayloadHeader = dataStructureUtil.randomExecutionPayloadHeader();
          blsToExecutionChanges = dataStructureUtil.randomSignedBlsToExecutionChangesList();
        });
  }

  @Test
  void equalsReturnsFalseWhenBlsToExecutionChangesIsDifferent() {
    blsToExecutionChanges = dataStructureUtil.randomSignedBlsToExecutionChangesList();
    final BeaconBlockBodyAltair testBeaconBlockBody = createBlockBody();

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Override
  protected BeaconBlockBodyCapella createBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    final BeaconBlockBodyBuilder bodyBuilder = createBeaconBlockBodyBuilder();
    contentProvider.accept(bodyBuilder);
    return bodyBuilder.build().toVersionCapella().orElseThrow();
  }

  @Override
  protected BlindedBeaconBlockBodyBellatrix createBlindedBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    final BeaconBlockBodyBuilder bodyBuilder = createBeaconBlockBodyBuilder();
    contentProvider.accept(bodyBuilder);
    return bodyBuilder.build().toBlindedVersionCapella().orElseThrow();
  }

  @Override
  protected Consumer<BeaconBlockBodyBuilder> createContentProvider(final boolean blinded) {
    return super.createContentProvider(blinded)
        .andThen(
            builder -> {
              builder.syncAggregate(syncAggregate).blsToExecutionChanges(blsToExecutionChanges);
              if (blinded) {
                builder.executionPayloadHeader(executionPayloadHeader);
              } else {
                builder.executionPayload(executionPayload);
              }
            });
  }
}
