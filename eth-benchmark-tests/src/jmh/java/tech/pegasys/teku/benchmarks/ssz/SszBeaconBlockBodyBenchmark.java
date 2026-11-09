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
import tech.pegasys.teku.benchmarks.util.CustomRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SszBeaconBlockBodyBenchmark extends SszAbstractContainerBenchmark<BeaconBlockBody> {

  private static final Spec spec = TestSpecFactory.createMinimalPhase0();
  private static final DataStructureUtil dataStructureUtil = new DataStructureUtil(1, spec);
  private static final BeaconBlockBody beaconBlockBody = dataStructureUtil.randomBeaconBlockBody();

  @Override
  protected BeaconBlockBody createContainer() {
    return spec.getGenesisSpec()
        .getSchemaDefinitions()
        .getBeaconBlockBodySchema()
        .createBlockBody(
            builder -> {
              builder
                  .randaoReveal(beaconBlockBody.getRandaoReveal())
                  .eth1Data(beaconBlockBody.getEth1Data())
                  .graffiti(beaconBlockBody.getGraffiti())
                  .attestations(beaconBlockBody.getAttestations())
                  .proposerSlashings(beaconBlockBody.getProposerSlashings())
                  .attesterSlashings(beaconBlockBody.getAttesterSlashings())
                  .deposits(beaconBlockBody.getDeposits())
                  .voluntaryExits(beaconBlockBody.getVoluntaryExits());
              return SafeFuture.COMPLETE;
            })
        .join();
  }

  @Override
  protected SszSchema<BeaconBlockBody> getContainerType() {
    return SszSchema.as(
        BeaconBlockBody.class,
        spec.getGenesisSpec().getSchemaDefinitions().getBeaconBlockBodySchema());
  }

  @Override
  public void iterateData(BeaconBlockBody bbb, Blackhole bh) {
    SszBenchUtil.iterateData(bbb, bh);
  }

  public static void main(String[] args) {
    SszBeaconBlockBodyBenchmark benchmark = new SszBeaconBlockBodyBenchmark();
    BeaconBlockBody blockBody = benchmark.createContainer();
    new CustomRunner(1000000, 2000).withBench(bh -> benchmark.iterateData(blockBody, bh)).run();
  }
}
