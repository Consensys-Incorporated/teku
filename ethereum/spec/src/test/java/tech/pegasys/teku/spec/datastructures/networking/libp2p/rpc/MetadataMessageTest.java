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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collections;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.altair.MetadataMessageSchemaAltair;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.versions.phase0.MetadataMessageSchemaPhase0;

class MetadataMessageTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final MetadataMessageSchemaPhase0 phase0Schema =
      new MetadataMessageSchemaPhase0(spec.getNetworkingConfig());
  private final MetadataMessageSchemaAltair altairSchema =
      new MetadataMessageSchemaAltair(spec.getNetworkingConfig());
  private final Bytes expectedSsz = Bytes.fromHexString("0x23000000000000000100000000000080");
  private final MetadataMessage message =
      phase0Schema.create(
          UInt64.valueOf(0x23), IntList.of(0, 63), Collections.emptyList(), Optional.empty());

  @Test
  public void shouldSerializeToSsz() {
    final Bytes result = message.sszSerialize();
    assertThat(result).isEqualTo(expectedSsz);
  }

  @Test
  public void shouldDeserializeFromSsz() {
    MetadataMessage result = phase0Schema.sszDeserialize(expectedSsz);
    assertThatSszData(result).isEqualByAllMeansTo(message);
  }

  @Test
  public void shouldRejectOutOfBoundsAttnets() {
    assertThatThrownBy(
            () ->
                phase0Schema.create(
                    UInt64.valueOf(15),
                    IntList.of(spec.getNetworkingConfig().getAttestationSubnetCount()),
                    Collections.emptyList(),
                    Optional.empty()))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  public void shouldRejectOutOfBoundsSyncnets() {
    assertThatThrownBy(
            () ->
                altairSchema.create(
                    UInt64.valueOf(15),
                    Collections.emptyList(),
                    IntList.of(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT),
                    Optional.empty()))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }
}
