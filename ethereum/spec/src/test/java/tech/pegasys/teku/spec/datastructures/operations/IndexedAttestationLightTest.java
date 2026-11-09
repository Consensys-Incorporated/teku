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

package tech.pegasys.teku.spec.datastructures.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class IndexedAttestationLightTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final IndexedAttestationSchema indexedAttestationSchema =
      spec.getGenesisSchemaDefinitions().getIndexedAttestationSchema();

  @Test
  void fromSsz_copiesAllFields() {
    final IndexedAttestation ssz =
        dataStructureUtil.randomIndexedAttestation(
            UInt64.valueOf(7), UInt64.valueOf(3), UInt64.valueOf(42));

    final IndexedAttestationLight light = IndexedAttestationLight.fromSsz(ssz);

    assertThat(light.attestingIndices())
        .containsExactly(UInt64.valueOf(7), UInt64.valueOf(3), UInt64.valueOf(42));
    assertThat(light.data()).isEqualTo(ssz.getData());
    assertThat(light.signature()).isEqualTo(ssz.getSignature());
  }

  @Test
  void fromSsz_preservesEmptyIndices() {
    final IndexedAttestation ssz = dataStructureUtil.randomIndexedAttestation();
    final IndexedAttestation empty =
        indexedAttestationSchema.create(
            indexedAttestationSchema.getAttestingIndicesSchema().of(List.of()),
            ssz.getData(),
            ssz.getSignature());

    final IndexedAttestationLight light = IndexedAttestationLight.fromSsz(empty);

    assertThat(light.attestingIndices()).isEmpty();
    assertThat(light.data()).isEqualTo(empty.getData());
    assertThat(light.signature()).isEqualTo(empty.getSignature());
  }

  @Test
  void toSsz_sortsUnsortedIndicesToCanonicalForm() {
    // get_indexed_attestation in AttestationUtil no longer sorts (indices come in committee-
    // position order). toSsz MUST sort to produce the spec-canonical form so a resulting
    // AttesterSlashing passes peer is_valid_indexed_attestation checks.
    final List<UInt64> unsorted =
        List.of(UInt64.valueOf(42), UInt64.valueOf(3), UInt64.valueOf(19), UInt64.valueOf(7));
    final IndexedAttestationLight light =
        new IndexedAttestationLight(
            unsorted,
            dataStructureUtil.randomAttestationData(),
            dataStructureUtil.randomSignature());

    final IndexedAttestation ssz = light.toSsz(indexedAttestationSchema);

    assertThat(ssz.getAttestingIndices().asListUnboxed())
        .containsExactly(
            UInt64.valueOf(3), UInt64.valueOf(7), UInt64.valueOf(19), UInt64.valueOf(42));
    assertThat(ssz.getData()).isEqualTo(light.data());
    assertThat(ssz.getSignature()).isEqualTo(light.signature());
  }

  @Test
  void toSsz_preservesAlreadySortedIndices() {
    final List<UInt64> sorted = List.of(UInt64.valueOf(1), UInt64.valueOf(5), UInt64.valueOf(99));
    final IndexedAttestationLight light =
        new IndexedAttestationLight(
            sorted, dataStructureUtil.randomAttestationData(), dataStructureUtil.randomSignature());

    final IndexedAttestation ssz = light.toSsz(indexedAttestationSchema);

    assertThat(ssz.getAttestingIndices().asListUnboxed()).containsExactlyElementsOf(sorted);
  }

  @Test
  void toSsz_handlesSingleIndex() {
    final IndexedAttestationLight light =
        new IndexedAttestationLight(
            List.of(UInt64.valueOf(12345)),
            dataStructureUtil.randomAttestationData(),
            dataStructureUtil.randomSignature());

    final IndexedAttestation ssz = light.toSsz(indexedAttestationSchema);

    assertThat(ssz.getAttestingIndices().asListUnboxed()).containsExactly(UInt64.valueOf(12345));
    assertThat(ssz.getData()).isEqualTo(light.data());
    assertThat(ssz.getSignature()).isEqualTo(light.signature());
  }

  @Test
  void roundTrip_sortedSszThroughLight_yieldsEqualSsz() {
    // starting from a canonical (sorted) SSZ form, fromSsz then toSsz must reproduce it exactly
    final IndexedAttestation original =
        dataStructureUtil.randomIndexedAttestation(
            UInt64.valueOf(1), UInt64.valueOf(2), UInt64.valueOf(3));

    final IndexedAttestation roundTripped =
        IndexedAttestationLight.fromSsz(original).toSsz(indexedAttestationSchema);

    assertThat(roundTripped).isEqualTo(original);
  }

  @Test
  void roundTrip_unsortedSszIsCanonicalizedByToSsz() {
    // An SSZ IndexedAttestation with non-spec (unsorted) indices — fromSsz preserves order,
    // but toSsz canonicalizes back to sorted.
    final IndexedAttestation unsortedSsz =
        dataStructureUtil.randomIndexedAttestation(
            UInt64.valueOf(9), UInt64.valueOf(2), UInt64.valueOf(5));

    final IndexedAttestationLight light = IndexedAttestationLight.fromSsz(unsortedSsz);
    assertThat(light.attestingIndices())
        .containsExactly(UInt64.valueOf(9), UInt64.valueOf(2), UInt64.valueOf(5));

    final IndexedAttestation canonical = light.toSsz(indexedAttestationSchema);
    assertThat(canonical.getAttestingIndices().asListUnboxed())
        .containsExactly(UInt64.valueOf(2), UInt64.valueOf(5), UInt64.valueOf(9));
    assertThat(canonical.getData()).isEqualTo(unsortedSsz.getData());
    assertThat(canonical.getSignature()).isEqualTo(unsortedSsz.getSignature());
  }
}
