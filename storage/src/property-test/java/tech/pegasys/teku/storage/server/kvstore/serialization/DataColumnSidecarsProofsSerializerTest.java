/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DataColumnSidecarsProofsSerializerTest {
  final Spec spec = TestSpecFactory.createMinimalFulu();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final SchemaDefinitionsFulu schemaDefinitionsFulu =
      SchemaDefinitionsFulu.required(spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
  final DataColumnSidecarsProofsSerializer serializer = new DataColumnSidecarsProofsSerializer();

  @Test
  final void testEmpty() {
    final byte[] serialize = serializer.serialize(List.of());
    assertThat(serializer.deserialize(serialize)).isEmpty();
  }

  @Test
  final void testRoundTrip() {
    final SignedBeaconBlockHeader header = dataStructureUtil.randomSignedBeaconBlockHeader();
    // we need to build sidecars with the same number of cells in each
    final List<KZGCommitment> kzgCommitments = dataStructureUtil.randomKZGCommitments(14);
    final SszList<SszKZGCommitment> sszKZGCommitments =
        schemaDefinitionsFulu
            .getDataColumnSidecarSchema()
            .getKzgCommitmentsSchema()
            .createFromElements(kzgCommitments.stream().map(SszKZGCommitment::new).toList());
    final List<DataColumnSidecar> dataColumnSidecarsExtension =
        Stream.iterate(UInt64.valueOf(64), UInt64::increment)
            .limit(64)
            .map(
                index ->
                    dataStructureUtil.randomDataColumnSidecar(header, sszKZGCommitments, index))
            .toList();
    final List<List<KZGProof>> expectedProofs =
        dataColumnSidecarsExtension.stream()
            .map(
                dataColumnSidecar ->
                    dataColumnSidecar.getKzgProofs().stream()
                        .map(SszKZGProof::getKZGProof)
                        .toList())
            .toList();
    final byte[] serialize = serializer.serialize(expectedProofs);
    assertThat(serializer.deserialize(serialize)).isEqualTo(expectedProofs);
  }

  @Test
  final void testSingleColumnSingleProofRoundTrip() {
    final List<List<KZGProof>> proofs = List.of(List.of(dataStructureUtil.randomKZGProof()));
    final byte[] serialize = serializer.serialize(proofs);
    assertThat(serializer.deserialize(serialize)).isEqualTo(proofs);
  }

  @Test
  final void testRaggedColumnsFailToDeserialize() {
    // columns of unequal length leave a trailing partial column that cannot be regrouped by the
    // uniform blob size written into the header
    final List<List<KZGProof>> ragged =
        List.of(
            List.of(dataStructureUtil.randomKZGProof(), dataStructureUtil.randomKZGProof()),
            List.of(dataStructureUtil.randomKZGProof()));
    final byte[] serialize = serializer.serialize(ragged);
    assertThatThrownBy(() -> serializer.deserialize(serialize))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Unexpected proofs found");
  }
}
