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

package tech.pegasys.teku.reference.phase0.bls;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethtests.finder.TestDefinition;

public class BlsEthAggregatePublicKeysTestExecutor extends BlsTestExecutor {

  @Override
  public void runTestImpl(final TestDefinition testDefinition) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final Optional<BLSPublicKey> output = data.getOutput();

    if (output.isPresent()) {
      final List<BLSPublicKey> input = data.getInput();
      assertThat(BLSPublicKey.aggregate(input))
          .describedAs("Public keys %s", input)
          .isEqualTo(output.get());
    } else {
      assertThatThrownBy(
              () -> {
                if (!BLSPublicKey.aggregate(data.getInput()).isValid()) {
                  // Tests don't differentiate between unparsable and invalid
                  throw new IllegalArgumentException("Resulting public key not in group");
                }
              })
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private List<String> input;

    @JsonProperty(value = "output", required = true)
    private String output;

    public List<BLSPublicKey> getInput() {
      return input.stream()
          .map(Bytes::fromHexString)
          .map(BLSPublicKey::fromSSZBytes)
          .collect(toList());
    }

    public Optional<BLSPublicKey> getOutput() {
      return output == null || output.isEmpty()
          ? Optional.empty()
          : Optional.of(BLSPublicKey.fromSSZBytes(Bytes48.fromHexString(output)));
    }
  }
}
