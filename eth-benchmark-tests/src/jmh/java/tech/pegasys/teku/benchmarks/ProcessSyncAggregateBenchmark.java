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

package tech.pegasys.teku.benchmarks;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import tech.pegasys.teku.benchmarks.gen.KeyFileGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.BeaconBlockBodySchemaAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@BenchmarkMode(Mode.SingleShotTime)
@State(Scope.Thread)
@Threads(1)
public class ProcessSyncAggregateBenchmark {

  private Spec spec;
  private BeaconStateAltair state;
  private Iterator<SyncAggregate> syncAggregates;

  @Param({"400000"})
  int validatorsCount;

  @Setup(Level.Trial)
  public void init() throws Exception {
    spec =
        TestSpecFactory.createMainnetAltair(
            builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));

    final List<BLSKeyPair> validatorKeys = KeyFileGenerator.readValidatorKeys(validatorsCount);

    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    state =
        BeaconStateAltair.required(
            new GenesisStateBuilder()
                .spec(spec)
                .signDeposits(false)
                .addValidators(validatorKeys)
                .build());
    final MutableBeaconStateAltair mutableState = state.createWritableCopy();
    mutableState.setSlot(UInt64.ONE);
    state = mutableState.commitChanges();

    final SyncCommittee syncCommittee = state.getCurrentSyncCommittee();
    final int syncCommitteeSize = syncCommittee.size();
    final SyncAggregateSchema syncAggregateSchema =
        BeaconBlockBodySchemaAltair.required(
                spec.getGenesisSchemaDefinitions().getBeaconBlockBodySchema())
            .getSyncAggregateSchema();
    final List<SyncAggregate> aggregates = new ArrayList<>();
    for (int i = 0; i < 500; i++) {
      final IntList participantIndices = new IntArrayList();
      for (int p = 0; p < syncCommitteeSize; p++) {
        // MainNet sync committee participation is about 90%
        if (Math.random() < 0.9) {
          participantIndices.add(p);
        }
      }
      final BLSSignature signature;
      if (participantIndices.isEmpty()) {
        signature = BLSSignature.infinity();
      } else {
        signature = dataStructureUtil.randomSignature();
      }
      aggregates.add(syncAggregateSchema.create(participantIndices, signature));
    }
    syncAggregates = aggregates.iterator();
  }

  @Benchmark
  @Warmup(iterations = 3, batchSize = 32)
  @Measurement(iterations = 50)
  public void processSyncAggregate() throws Exception {
    final BlockProcessor blockProcessor = spec.getGenesisSpec().getBlockProcessor();
    blockProcessor.processSyncAggregate(
        state.createWritableCopy(), syncAggregates.next(), BLSSignatureVerifier.NOOP);
  }
}
