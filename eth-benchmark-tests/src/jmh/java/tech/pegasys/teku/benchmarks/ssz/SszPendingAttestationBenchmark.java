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

package tech.pegasys.teku.benchmarks.ssz;

import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation.PendingAttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SszPendingAttestationBenchmark
    extends SszAbstractContainerBenchmark<PendingAttestation> {

  private static final Spec spec = TestSpecFactory.createMinimalPhase0();
  private static final DataStructureUtil dataStructureUtil = new DataStructureUtil(1, spec);
  private static final PendingAttestation aPendingAttestation =
      dataStructureUtil.randomPendingAttestation();

  private static final SszBitlist aggregation_bits = aPendingAttestation.getAggregationBits();
  private static final AttestationData attestationData = aPendingAttestation.getData();
  private static final UInt64 inclusion_delay = aPendingAttestation.getInclusionDelay();
  private static final UInt64 proposer_index = aPendingAttestation.getProposerIndex();
  private final PendingAttestationSchema schema =
      BeaconStateSchemaPhase0.required(spec.getGenesisSchemaDefinitions().getBeaconStateSchema())
          .getPendingAttestationSchema();

  @Override
  protected PendingAttestation createContainer() {
    return new PendingAttestation(
        schema, aggregation_bits, attestationData, inclusion_delay, proposer_index);
  }

  @Override
  protected SszSchema<PendingAttestation> getContainerType() {
    return schema;
  }

  @Override
  protected void iterateData(PendingAttestation pa, Blackhole bh) {
    SszBenchUtil.iterateData(pa, bh);
  }

  public static void main(String[] args) {
    new SszPendingAttestationBenchmark().customRun(10, 100000);
  }

  public static void main1(String[] args) {
    SszPendingAttestationBenchmark benchmark = new SszPendingAttestationBenchmark();
    while (true) {
      benchmark.benchCreate(benchmark.blackhole);
    }
  }
}
