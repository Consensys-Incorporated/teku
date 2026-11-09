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

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;

import java.util.List;
import tech.pegasys.teku.data.slashinginterchange.SlashingProtectionInterchangeFormat;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

record Step(
    boolean shouldSucceed,
    // we don't fail importing when the interchange contains slashable data, so can safely
    // ignore this field in the tests
    boolean containsSlashableData,
    SlashingProtectionInterchangeFormat interchange,
    List<Block> blocks,
    List<Attestation> attestations) {
  static DeserializableTypeDefinition<Step> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(Step.class, StepBuilder.class)
        .initializer(StepBuilder::new)
        .finisher(StepBuilder::build)
        .withField("should_succeed", BOOLEAN_TYPE, Step::shouldSucceed, StepBuilder::shouldSucceed)
        .withField(
            "contains_slashable_data",
            BOOLEAN_TYPE,
            Step::containsSlashableData,
            StepBuilder::containsSlashableData)
        .withField(
            "interchange",
            SlashingProtectionInterchangeFormat.getJsonTypeDefinition(),
            Step::interchange,
            StepBuilder::interchange)
        .withField(
            "blocks",
            DeserializableTypeDefinition.listOf(Block.getJsonTypeDefinition()),
            Step::blocks,
            StepBuilder::blocks)
        .withField(
            "attestations",
            DeserializableTypeDefinition.listOf(Attestation.getJsonTypeDefinition()),
            Step::attestations,
            StepBuilder::attestations)
        .build();
  }

  static class StepBuilder {
    boolean shouldSucceed;
    boolean containsSlashableData;
    SlashingProtectionInterchangeFormat interchange;
    List<Block> blocks;
    List<Attestation> attestations;

    StepBuilder shouldSucceed(final boolean shouldSucceed) {
      this.shouldSucceed = shouldSucceed;
      return this;
    }

    StepBuilder containsSlashableData(final boolean containsSlashableData) {
      this.containsSlashableData = containsSlashableData;
      return this;
    }

    StepBuilder interchange(final SlashingProtectionInterchangeFormat interchange) {
      this.interchange = interchange;
      return this;
    }

    StepBuilder blocks(final List<Block> blocks) {
      this.blocks = blocks;
      return this;
    }

    StepBuilder attestations(final List<Attestation> attestations) {
      this.attestations = attestations;
      return this;
    }

    Step build() {
      return new Step(shouldSucceed, containsSlashableData, interchange, blocks, attestations);
    }
  }
}
