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

package tech.pegasys.teku.reference.phase0.slashing_protection_interchange;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

record TestData(String name, Bytes32 genesisValidatorsRoot, List<Step> steps) {

  static DeserializableTypeDefinition<TestData> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(TestData.class, TestDataBuilder.class)
        .initializer(TestDataBuilder::new)
        .finisher(TestDataBuilder::build)
        .withField("name", STRING_TYPE, TestData::name, TestDataBuilder::name)
        .withField(
            "genesis_validators_root",
            BYTES32_TYPE,
            TestData::genesisValidatorsRoot,
            TestDataBuilder::genesisValidatorsRoot)
        .withField(
            "steps", listOf(Step.getJsonTypeDefinition()), TestData::steps, TestDataBuilder::steps)
        .build();
  }

  static class TestDataBuilder {
    private String name;
    private Bytes32 genesisValidatorsRoot;
    private List<Step> steps;

    TestDataBuilder steps(final List<Step> steps) {
      this.steps = steps;
      return this;
    }

    TestDataBuilder name(final String name) {
      this.name = name;
      return this;
    }

    TestDataBuilder genesisValidatorsRoot(final Bytes32 genesisValidatorsRoot) {
      this.genesisValidatorsRoot = genesisValidatorsRoot;
      return this;
    }

    TestData build() {
      return new TestData(name, genesisValidatorsRoot, steps);
    }
  }
}
