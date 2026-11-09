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

package tech.pegasys.teku.reference.phase0.genesis;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class GenesisInitializationTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final Spec spec = testDefinition.getSpec();
    final BeaconState expectedGenesisState = loadStateFromSsz(testDefinition, "state.ssz_snappy");
    final Eth1MetaData eth1MetaData = loadYaml(testDefinition, "eth1.yaml", Eth1MetaData.class);
    final GenesisMetaData metaData = loadYaml(testDefinition, "meta.yaml", GenesisMetaData.class);
    final List<Deposit> deposits =
        IntStream.range(0, metaData.getDepositsCount())
            .mapToObj(
                index ->
                    loadSsz(
                        testDefinition, "deposits_" + index + ".ssz_snappy", Deposit.SSZ_SCHEMA))
            .collect(Collectors.toList());

    final Optional<ExecutionPayloadHeader> executionPayloadHeader;
    if (metaData.hasExecutionPayloadHeader()) {
      executionPayloadHeader =
          Optional.of(
              loadSsz(
                  testDefinition,
                  "execution_payload_header.ssz_snappy",
                  SchemaDefinitionsBellatrix.required(spec.getGenesisSchemaDefinitions())
                      .getExecutionPayloadHeaderSchema()));
    } else {
      executionPayloadHeader = Optional.empty();
    }

    final BeaconState result =
        spec.initializeBeaconStateFromEth1(
            eth1MetaData.getEth1BlockHash(),
            eth1MetaData.getEth1Timestamp(),
            deposits,
            executionPayloadHeader);
    assertThat(result).isEqualTo(expectedGenesisState);
  }

  private static class GenesisMetaData {
    @SuppressWarnings("unused")
    @JsonProperty(value = "description", required = false)
    private String description;

    @JsonProperty(value = "deposits_count", required = true)
    private int depositsCount;

    @SuppressWarnings("FieldCanBeFinal")
    @JsonProperty(value = "execution_payload_header", required = false)
    private boolean executionPayloadHeader = false;

    public int getDepositsCount() {
      return depositsCount;
    }

    public boolean hasExecutionPayloadHeader() {
      return executionPayloadHeader;
    }
  }

  private static class Eth1MetaData {
    @JsonProperty(value = "eth1_block_hash", required = true)
    private String eth1BlockHash;

    @JsonProperty(value = "eth1_timestamp", required = true)
    private long eth1Timestamp;

    public Bytes32 getEth1BlockHash() {
      return Bytes32.fromHexString(eth1BlockHash);
    }

    public UInt64 getEth1Timestamp() {
      return UInt64.fromLongBits(eth1Timestamp);
    }
  }
}
