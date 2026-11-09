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

package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGCellWithColumnId;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.KzgResolver;
import tech.pegasys.teku.spec.propertytest.suppliers.type.DiverseBlobBytesSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.KZGCellWithColumnIdSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.KZGCommitmentSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.type.KZGProofSupplier;

@AddLifecycleHook(KzgResolver.class)
public class CKZG4844PropertyTest {

  @Property(tries = 100)
  void fuzzBlobToCells(
      final KZG kzg, @ForAll(supplier = DiverseBlobBytesSupplier.class) final Bytes blob) {
    try {
      kzg.computeCells(blob);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzBlobToCellsAndProofs(
      final KZG kzg, @ForAll(supplier = DiverseBlobBytesSupplier.class) final Bytes blob) {
    try {
      kzg.computeCellsAndProofs(blob);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzVerifyCellProofBatch(
      final KZG kzg,
      @ForAll final List<@From(supplier = KZGCommitmentSupplier.class) KZGCommitment> commitments,
      @ForAll
          final List<@From(supplier = KZGCellWithColumnIdSupplier.class) KZGCellWithColumnId>
              kzgCellWithColumnIds,
      @ForAll final List<@From(supplier = KZGProofSupplier.class) KZGProof> proofs) {
    try {
      kzg.verifyCellProofBatch(commitments, kzgCellWithColumnIds, proofs);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzRecoverCellsAndProofs(
      final KZG kzg,
      @ForAll
          final List<@From(supplier = KZGCellWithColumnIdSupplier.class) KZGCellWithColumnId>
              kzgCellWithColumnIds) {
    try {
      kzg.recoverCellsAndProofs(kzgCellWithColumnIds);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }
}
