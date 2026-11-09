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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethtests.finder.TestDefinition;

public class BlsDeserializationG1TestExecutor extends BlsTestExecutor {

  @Override
  protected void runTestImpl(final TestDefinition testDefinition) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final String publicKey = data.input.getPublicKey();
    final boolean expectedResult = data.getOutput();

    if (expectedResult) {
      assertThat(parseKey(publicKey).isInGroup()).isTrue();
    } else {
      assertThatThrownBy(
              () -> {
                if (!parseKey(publicKey).isInGroup()) {
                  // Reference tests don't differentiate between being unable to parse and not being
                  // in group
                  throw new IllegalArgumentException("Not in group");
                }
              })
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  private BLSPublicKey parseKey(final String publicKey) {
    return BLSPublicKey.fromBytesCompressed(Bytes48.fromHexString(publicKey));
  }

  private static class Data {
    @JsonProperty(value = "input", required = true)
    private Input input;

    @JsonProperty(value = "output", required = true)
    private boolean output;

    public boolean getOutput() {
      return output;
    }
  }

  private static class Input {
    @JsonProperty(value = "pubkey", required = true)
    private String publicKey;

    public String getPublicKey() {
      return publicKey;
    }
  }
}
