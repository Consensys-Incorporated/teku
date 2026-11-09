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
import java.util.Objects;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import net.jqwik.api.lifecycle.BeforeProperty;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.KZGException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.KzgResolver;
import tech.pegasys.teku.spec.propertytest.suppliers.DataStructureUtilSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.SpecSupplier;
import tech.pegasys.teku.spec.propertytest.suppliers.datacolumn.versions.fulu.DataColumnSidecarFuluSupplier;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * blob_kzg_commitments are removed in Gloas so for testing this class, we only consider Spec and
 * SignedBeaconBlock until Fulu.
 */
@AddLifecycleHook(KzgResolver.class)
public class MiscHelpersFuluPropertyTest {

  private final Spec spec =
      Objects.requireNonNull(new SpecSupplier(SpecMilestone.FULU, SpecMilestone.FULU).get())
          .sample();
  private final MiscHelpersFulu miscHelpers = (MiscHelpersFulu) spec.getGenesisSpec().miscHelpers();

  @BeforeProperty
  void beforeProperty(final KZG kzg) {
    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);
  }

  @Property(tries = 100)
  void fuzzVerifyDataColumnSidecar(
      @ForAll(supplier = DataColumnSidecarFuluSupplier.class)
          final DataColumnSidecar dataColumnSidecar) {
    miscHelpers.verifyDataColumnSidecar(dataColumnSidecar);
  }

  @Property(tries = 100)
  void fuzzVerifyDataColumnSidecarKzgProofs(
      @ForAll(supplier = DataColumnSidecarFuluSupplier.class)
          final DataColumnSidecar dataColumnSidecar) {
    try {
      miscHelpers.verifyDataColumnSidecarKzgProofs(dataColumnSidecar);
    } catch (final Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzVerifyDataColumnSidecarKzgProofsBatch(
      @ForAll
          final List<@From(supplier = DataColumnSidecarFuluSupplier.class) DataColumnSidecar>
              dataColumnSidecars) {
    try {
      miscHelpers.verifyDataColumnSidecarKzgProofsBatch(dataColumnSidecars);
    } catch (final Exception e) {
      assertThat(e).isInstanceOf(KZGException.class);
    }
  }

  @Property(tries = 100)
  void fuzzVerifyDataColumnSidecarInclusionProof(
      @ForAll(supplier = DataColumnSidecarFuluSupplier.class)
          final DataColumnSidecar dataColumnSidecar) {
    miscHelpers.verifyDataColumnSidecarInclusionProof(dataColumnSidecar);
  }

  @Property(tries = 100)
  void fuzzComputeDataColumnKzgCommitmentsInclusionProof(
      @ForAll(supplier = SignedBeaconBlockSupplier.class)
          final SignedBeaconBlock signedBeaconBlock) {
    try {
      miscHelpers.computeDataColumnKzgCommitmentsInclusionProof(
          signedBeaconBlock.getMessage().getBody());
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Property(tries = 100)
  void fuzzReconstructAllDataColumnSidecars(
      @ForAll
          final List<@From(supplier = DataColumnSidecarFuluSupplier.class) DataColumnSidecar>
              dataColumnSidecars) {
    try {
      miscHelpers.reconstructAllDataColumnSidecars(dataColumnSidecars);
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }

  private static class SignedBeaconBlockSupplier
      extends DataStructureUtilSupplier<SignedBeaconBlock> {
    public SignedBeaconBlockSupplier() {
      super(DataStructureUtil::randomSignedBeaconBlock, SpecMilestone.FULU, SpecMilestone.FULU);
    }
  }
}
