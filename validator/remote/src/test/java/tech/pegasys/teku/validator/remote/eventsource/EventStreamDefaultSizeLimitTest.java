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

package tech.pegasys.teku.validator.remote.eventsource;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashingSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.IndexedAttestationPqSchema;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorConfig;

class EventStreamDefaultSizeLimitTest {

  // A maximum PQ attester slashing, with two MAX_STARK_PROOF_SIZE (1 MiB) proofs, is a 8,389,454
  // byte frame, over the 8 MB default. PQ (and Heze, which builds on it here) is left out until the
  // validator client follows PQ chains and the limit is settled for it.
  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = SpecMilestone.class,
      names = {"PQ", "HEZE"},
      mode = EnumSource.Mode.EXCLUDE)
  void mainnetMaximumAttesterSlashingFitsDefaultEventLimit(final SpecMilestone milestone)
      throws Exception {
    final Spec spec = TestSpecFactory.createMainnet(milestone);
    final DataStructureUtil data = new DataStructureUtil(spec);
    final AttesterSlashingSchema slashingSchema =
        spec.getGenesisSchemaDefinitions().getAttesterSlashingSchema();
    final AttesterSlashing slashing =
        spec.getGenesisSchemaDefinitions()
            .toVersionPq()
            .map(pqSchemas -> maximumPqAttesterSlashing(spec, pqSchemas, data))
            .orElseGet(() -> maximumAttesterSlashing(spec, data));

    final int frameSize =
        "event: attester_slashing\ndata: ".getBytes(UTF_8).length
            + JsonUtil.serialize(slashing, slashingSchema.getJsonTypeDefinition())
                .getBytes(UTF_8)
                .length
            + "\n\n".getBytes(UTF_8).length;

    assertThat(frameSize)
        .as("%s maximum attester slashing frame", milestone)
        .isLessThanOrEqualTo(
            ValidatorConfig.DEFAULT_EVENT_STREAM_MAX_EVENT_SIZE_MEGABYTES
                * EventStreamSizeLimitInterceptor.BYTES_PER_MEGABYTE);
  }

  private static AttesterSlashing maximumAttesterSlashing(
      final Spec spec, final DataStructureUtil data) {
    final AttesterSlashingSchema slashingSchema =
        spec.getGenesisSchemaDefinitions().getAttesterSlashingSchema();
    final IndexedAttestationSchema indexedSchema =
        spec.getGenesisSchemaDefinitions().getIndexedAttestationSchema();
    final int indexCount =
        Math.toIntExact(spec.getGenesisSpecConfig().getMaxValidatorsPerAttestation());
    final long registryLimit = spec.getGenesisSpecConfig().getValidatorRegistryLimit();
    final List<UInt64> validatorIndices =
        IntStream.range(0, indexCount)
            .mapToObj(index -> UInt64.valueOf(registryLimit - indexCount + index))
            .toList();
    final SszUInt64List indices = indexedSchema.getAttestingIndicesSchema().of(validatorIndices);
    return slashingSchema.create(
        indexedSchema.create(indices, data.randomAttestationData(), data.randomSignature()),
        indexedSchema.create(indices, data.randomAttestationData(), data.randomSignature()));
  }

  /** From PQ on, each indexed attestation carries a STARK proof of up to MAX_STARK_PROOF_SIZE. */
  private static AttesterSlashing maximumPqAttesterSlashing(
      final Spec spec, final SchemaDefinitionsPq schemaDefinitions, final DataStructureUtil data) {
    final IndexedAttestationPqSchema indexedSchema =
        schemaDefinitions.getPqAttestationSchemas().getIndexedAttestationSchema();
    final int indexCount =
        Math.toIntExact(indexedSchema.getAttestingIndicesSchema().getMaxLength());
    final long registryLimit = spec.getGenesisSpecConfig().getValidatorRegistryLimit();
    final List<UInt64> validatorIndices =
        IntStream.range(0, indexCount)
            .mapToObj(index -> UInt64.valueOf(registryLimit - indexCount + index))
            .toList();
    final SszUInt64List indices = indexedSchema.getAttestingIndicesSchema().of(validatorIndices);
    final int proofSize = Math.toIntExact(indexedSchema.getAttestationProofSchema().getMaxLength());
    return schemaDefinitions
        .getAttesterSlashingSchema()
        .create(
            indexedSchema.create(
                indices, data.randomAttestationData(), data.randomBytes(proofSize)),
            indexedSchema.create(
                indices, data.randomAttestationData(), data.randomBytes(proofSize)));
  }
}
