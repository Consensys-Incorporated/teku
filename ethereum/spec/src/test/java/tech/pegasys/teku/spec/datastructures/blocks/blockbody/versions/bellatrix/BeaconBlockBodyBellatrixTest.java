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

package tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodyBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.AbstractBeaconBlockBodyTest;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;

class BeaconBlockBodyBellatrixTest extends AbstractBeaconBlockBodyTest<BeaconBlockBodyBellatrix> {

  protected SyncAggregate syncAggregate;
  protected ExecutionPayload executionPayload;
  protected ExecutionPayloadHeader executionPayloadHeader;

  @BeforeEach
  void setup() {
    super.setUpBaseClass(
        SpecMilestone.BELLATRIX,
        () -> {
          syncAggregate = dataStructureUtil.randomSyncAggregate();
          executionPayload = dataStructureUtil.randomExecutionPayload();
          executionPayloadHeader = dataStructureUtil.randomExecutionPayloadHeader();
        });
  }

  @Test
  void equalsReturnsFalseWhenExecutionPayloadIsDifferent() {
    executionPayload = dataStructureUtil.randomExecutionPayload();
    final BeaconBlockBodyBellatrix testBeaconBlockBody = createBlockBody();

    assertNotEquals(defaultBlockBody, testBeaconBlockBody);
  }

  @Test
  void equalsReturnsFalseWhenExecutionPayloadHeaderIsDifferent() {
    executionPayloadHeader = dataStructureUtil.randomExecutionPayloadHeader();
    final BlindedBeaconBlockBodyBellatrix testBlindedBeaconBlockBody = createBlindedBlockBody();

    assertNotEquals(defaultBlindedBlockBody, testBlindedBeaconBlockBody);
  }

  @Test
  public void builderShouldFailIfNotPassingPayloadNorPayloadHeader() {
    final Exception exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                createBlockBody(
                    createContentProvider(false)
                        // let's erase all payloads, so we can test as if they were not set
                        .andThen(b -> b.executionPayload(null).executionPayloadHeader(null))));
    assertEquals(
        exception.getMessage(),
        "Exactly one of 'executionPayload' or 'executionPayloadHeader' must be set");
  }

  @Test
  public void builderShouldFailIfPassingPayloadAndPayloadHeader() {
    final Exception exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                createBlockBody(
                    createContentProvider(true)
                        .andThen(
                            b ->
                                // payload header is already set because we requested a blinded
                                // content
                                b.executionPayload(executionPayload))));
    assertEquals(
        exception.getMessage(),
        "Exactly one of 'executionPayload' or 'executionPayloadHeader' must be set");
  }

  @Test
  @SuppressWarnings("unchecked")
  void builderShouldFailWhenBlindedSchemaIsSetAndFullPayloadIsSet() {
    BeaconBlockBodyBuilderBellatrix beaconBlockBodyBuilderBellatrix =
        new BeaconBlockBodyBuilderBellatrix(null, blindedBlockBodySchema);
    Exception exception =
        assertThrows(
            NullPointerException.class,
            () ->
                beaconBlockBodyBuilderBellatrix
                    .randaoReveal(mock(BLSSignature.class))
                    .eth1Data(mock(Eth1Data.class))
                    .graffiti(mock(Bytes32.class))
                    .attestations(mock(SszList.class))
                    .proposerSlashings(mock(SszList.class))
                    .attesterSlashings(mock(SszList.class))
                    .deposits(mock(SszList.class))
                    .voluntaryExits(mock(SszList.class))
                    .executionPayload(mock(ExecutionPayload.class))
                    .syncAggregate(mock(SyncAggregate.class))
                    .build());
    assertEquals(exception.getMessage(), "Schema must be specified");
  }

  @Test
  @SuppressWarnings("unchecked")
  void builderShouldFailWhenNonBlindedSchemaIsSetAndPayloadHeaderIsSet() {
    BeaconBlockBodyBuilderBellatrix beaconBlockBodyBuilderBellatrix =
        new BeaconBlockBodyBuilderBellatrix(
            (BeaconBlockBodySchema<? extends BeaconBlockBodyBellatrix>) blockBodySchema, null);
    Exception exception =
        assertThrows(
            NullPointerException.class,
            () ->
                beaconBlockBodyBuilderBellatrix
                    .randaoReveal(mock(BLSSignature.class))
                    .eth1Data(mock(Eth1Data.class))
                    .graffiti(mock(Bytes32.class))
                    .attestations(mock(SszList.class))
                    .proposerSlashings(mock(SszList.class))
                    .attesterSlashings(mock(SszList.class))
                    .deposits(mock(SszList.class))
                    .voluntaryExits(mock(SszList.class))
                    .executionPayloadHeader(mock(ExecutionPayloadHeader.class))
                    .syncAggregate(mock(SyncAggregate.class))
                    .build());
    assertEquals(exception.getMessage(), "Blinded schema must be specified");
  }

  @Override
  protected BeaconBlockBodyBellatrix createBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    final BeaconBlockBodyBuilder bodyBuilder = createBeaconBlockBodyBuilder();
    contentProvider.accept(bodyBuilder);
    return bodyBuilder.build().toVersionBellatrix().orElseThrow();
  }

  @Override
  protected BlindedBeaconBlockBodyBellatrix createBlindedBlockBody(
      final Consumer<BeaconBlockBodyBuilder> contentProvider) {
    final BeaconBlockBodyBuilder bodyBuilder = createBeaconBlockBodyBuilder();
    contentProvider.accept(bodyBuilder);
    return bodyBuilder.build().toBlindedVersionBellatrix().orElseThrow();
  }

  @Override
  protected Consumer<BeaconBlockBodyBuilder> createContentProvider(final boolean blinded) {
    return super.createContentProvider(blinded)
        .andThen(
            builder -> {
              builder.syncAggregate(syncAggregate);
              if (blinded) {
                builder.executionPayloadHeader(executionPayloadHeader);
              } else {
                builder.executionPayload(executionPayload);
              }
            });
  }
}
