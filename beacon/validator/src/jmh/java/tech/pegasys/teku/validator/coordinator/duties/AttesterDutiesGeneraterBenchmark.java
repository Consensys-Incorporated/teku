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

package tech.pegasys.teku.validator.coordinator.duties;

import static tech.pegasys.teku.ethereum.json.types.EthereumTypes.PUBLIC_KEY_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tech.pegasys.teku.benchmarks.gen.KeyFileGenerator;
import tech.pegasys.teku.bls.BLSConstants;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuties;
import tech.pegasys.teku.ethereum.json.types.validator.AttesterDuty;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.MutableBeaconStateBellatrix;

@Fork(1)
@State(Scope.Thread)
public class AttesterDutiesGeneraterBenchmark {
  private static final SerializableTypeDefinition<AttesterDuty> ATTESTER_DUTY_TYPE =
      SerializableTypeDefinition.object(AttesterDuty.class)
          .name("AttesterDuty")
          .withField("pubkey", PUBLIC_KEY_TYPE, AttesterDuty::getPublicKey)
          .withField("validator_index", INTEGER_TYPE, AttesterDuty::getValidatorIndex)
          .withField("committee_index", INTEGER_TYPE, AttesterDuty::getCommitteeIndex)
          .withField("committee_length", INTEGER_TYPE, AttesterDuty::getCommitteeLength)
          .withField("committees_at_slot", INTEGER_TYPE, AttesterDuty::getCommitteesAtSlot)
          .withField(
              "validator_committee_index", INTEGER_TYPE, AttesterDuty::getValidatorCommitteeIndex)
          .withField("slot", UINT64_TYPE, AttesterDuty::getSlot)
          .build();
  public static final SerializableTypeDefinition<AttesterDuties> RESPONSE_TYPE =
      SerializableTypeDefinition.object(AttesterDuties.class)
          .name("GetAttesterDutiesResponse")
          .withField("dependent_root", BYTES32_TYPE, AttesterDuties::getDependentRoot)
          .withField(EXECUTION_OPTIMISTIC, BOOLEAN_TYPE, AttesterDuties::isExecutionOptimistic)
          .withField(
              "data",
              SerializableTypeDefinition.listOf(ATTESTER_DUTY_TYPE),
              AttesterDuties::getDuties)
          .build();
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private BeaconStateBellatrix state;
  private AttesterDutiesGenerator attesterDutiesGenerator;

  private UInt64 epoch;

  IntList validatorIndices = new IntArrayList();

  @Param({"20000"})
  int validatorsCount = 20_000;

  @Param({"20000"})
  int querySize = 20_000;

  @Setup(Level.Trial)
  public void init() {
    BLSConstants.disableBLSVerification();
    List<BLSKeyPair> validatorKeys = KeyFileGenerator.readValidatorKeys(validatorsCount);
    state =
        BeaconStateBellatrix.required(
            new GenesisStateBuilder()
                .spec(spec)
                .signDeposits(true)
                .addValidators(validatorKeys)
                .build());
    final MutableBeaconStateBellatrix mutableState = state.createWritableCopy();
    mutableState.setSlot(UInt64.ONE);
    state = mutableState.commitChanges();

    System.out.println("active validators: " + state.getValidators().size());

    for (int i = 0; i < querySize; i++) {
      validatorIndices.add(i);
    }

    attesterDutiesGenerator = new AttesterDutiesGenerator(spec);
    epoch = spec.computeEpochAtSlot(state.getSlot()).increment();

    int generatedDuties = computeAttesterDuties().getDuties().size();
    if (generatedDuties == 0) {
      throw new IllegalStateException("No duties generated, check the state");
    }

    System.out.println("computed duties: " + computeAttesterDuties().getDuties().size());
    System.out.println("Done!");
  }

  @Benchmark
  @Warmup(iterations = 5, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
  @Measurement(iterations = 10)
  public void computeAttesterDuties(Blackhole bh) {
    final AttesterDuties attesterDutiesFromIndicesAndState = computeAttesterDuties();

    bh.consume(attesterDutiesFromIndicesAndState);
  }

  private AttesterDuties computeAttesterDuties() {
    return attesterDutiesGenerator.getAttesterDutiesFromIndicesAndState(
        state, epoch, validatorIndices, false);
  }
}
