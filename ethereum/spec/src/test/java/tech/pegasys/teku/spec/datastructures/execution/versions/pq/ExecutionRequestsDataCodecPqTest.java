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

package tech.pegasys.teku.spec.datastructures.execution.versions.pq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsDataCodec;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

class ExecutionRequestsDataCodecPqTest {

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final ExecutionRequestsSchemaPq schema =
      SchemaDefinitionsPq.required(spec.forMilestone(SpecMilestone.PQ).getSchemaDefinitions())
          .getPqExecutionRequestsSchema();
  private final ExecutionRequestsDataCodec codec = new ExecutionRequestsDataCodec(schema);

  @Test
  void roundTripsEveryRequestType() {
    final ExecutionRequestsPq requests = new ExecutionRequestsSchemaPqTest().requestsOfEveryType();

    final List<Bytes> encoded = codec.encode(requests);

    assertThat(encoded.stream().map(request -> request.get(0)).toList())
        .containsExactly(
            (byte) 0x00,
            (byte) 0x01,
            (byte) 0x02,
            (byte) 0x03,
            (byte) 0x04,
            (byte) 0x07,
            (byte) 0x08,
            (byte) 0x09);
    final ExecutionRequests decoded = codec.decode(encoded);
    assertThat(decoded).isEqualTo(requests);
  }

  @Test
  void encodesOnlyTheNewTypesThatHaveRequests() {
    final ExecutionRequestsPq requests =
        schema.create(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(ExecutionRequestsSchemaPqTest.voluntaryExit(1)));

    final List<Bytes> encoded = codec.encode(requests);

    assertThat(encoded)
        .containsExactly(
            Bytes.concatenate(
                VoluntaryExitRequest.REQUEST_TYPE_PREFIX,
                ExecutionRequestsSchemaPqTest.voluntaryExit(1).sszSerialize()));
    assertThat(codec.decode(encoded)).isEqualTo(requests);
  }

  @Test
  void rejectsTheEip8483RegistrationTypes() {
    // A single (zeroed) 64-byte EIP-8483 registration request
    final Bytes registration = Bytes.wrap(new byte[72]);
    assertThatThrownBy(() -> codec.decode(List.of(Bytes.concatenate(Bytes.of(0x05), registration))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid execution request type: 5 (removed in PQ)");
    assertThatThrownBy(() -> codec.decode(List.of(Bytes.concatenate(Bytes.of(0x06), registration))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid execution request type: 6 (removed in PQ)");
  }

  @Test
  void earlierForksIgnoreThePqRequestTypes() {
    // Teku's convention: a fork's builder ignores request types it does not know yet
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final ExecutionRequestsDataCodec gloasCodec =
        new ExecutionRequestsDataCodec(
            gloasSpec
                .getGenesisSchemaDefinitions()
                .toVersionElectra()
                .orElseThrow()
                .getExecutionRequestsSchema());
    final Bytes encoded =
        Bytes.concatenate(
            VoluntaryExitRequest.REQUEST_TYPE_PREFIX,
            ExecutionRequestsSchemaPqTest.voluntaryExit(1).sszSerialize());
    assertThat(gloasCodec.decode(List.of(encoded)).getVoluntaryExits()).isEmpty();
  }

  @Test
  void rejectsAnUnknownType() {
    assertThatThrownBy(() -> codec.decode(List.of(Bytes.fromHexString("0x0a00"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid execution request type: 10");
  }
}
