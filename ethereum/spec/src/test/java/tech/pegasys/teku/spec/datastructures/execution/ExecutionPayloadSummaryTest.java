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

package tech.pegasys.teku.spec.datastructures.execution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ExecutionPayloadSummaryTest {
  private final Spec spec = TestSpecFactory.createMainnetBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldIdentifyDefaultPayload() {
    final ExecutionPayload defaultPayload =
        SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
            .getExecutionPayloadSchema()
            .getDefault();

    final ExecutionPayloadSummary summary = defaultPayload;
    assertThat(defaultPayload.isDefault()).isTrue();
    assertThat(defaultPayload.isDefaultPayload()).isTrue();
    assertThat(summary.isDefaultPayload()).isTrue();
  }

  @Test
  void shouldIdentifyNonDefaultPayload() {
    final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
    final ExecutionPayloadSummary summary = payload;
    assertThat(payload.isDefault()).isFalse();
    assertThat(payload.isDefaultPayload()).isFalse();
    assertThat(summary.isDefaultPayload()).isFalse();
  }

  @Test
  void shouldIdentifyDefaultPayloadHeader() {
    final ExecutionPayloadHeader header =
        SchemaDefinitionsBellatrix.required(spec.getGenesisSpec().getSchemaDefinitions())
            .getExecutionPayloadHeaderSchema()
            .getHeaderOfDefaultPayload();
    final ExecutionPayloadSummary summary = header;
    /* note - header identifies non default from the ssz `isDefault` */
    assertThat(header.isDefault()).isFalse();
    assertThat(summary.isDefaultPayload()).isTrue();
  }

  @Test
  void shouldIdentifyNonDefaultPayloadHeader() {
    final ExecutionPayloadHeader header = dataStructureUtil.randomExecutionPayloadHeader();
    final ExecutionPayloadSummary summary = header;
    assertThat(header.isDefault()).isFalse();
    assertThat(header.isDefaultPayload()).isFalse();
    assertThat(summary.isDefaultPayload()).isFalse();
  }
}
