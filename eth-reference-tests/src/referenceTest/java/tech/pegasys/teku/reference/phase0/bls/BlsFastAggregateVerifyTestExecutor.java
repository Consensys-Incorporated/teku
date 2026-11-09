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

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethtests.finder.TestDefinition;

public class BlsFastAggregateVerifyTestExecutor extends BlsTestExecutor {

  @Override
  public void runTestImpl(final TestDefinition testDefinition) throws Throwable {
    final Data data = loadDataFile(testDefinition, Data.class);
    final List<BLSPublicKey> publicKeys = data.input.getPublicKeys();
    final Bytes message = data.input.getMessage();
    final BLSSignature signature = data.input.getSignature();
    final boolean expectedResult = data.getOutput();
    assertThat(BLS.fastAggregateVerify(publicKeys, message, signature))
        .describedAs("Public keys %s message %s signature %s", publicKeys, message, signature)
        .isEqualTo(expectedResult);
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
    @JsonProperty(value = "pubkeys", required = true)
    private List<String> publicKeys;

    @JsonProperty(value = "message", required = true)
    @JsonAlias({"messages"})
    private String message;

    @JsonProperty(value = "signature", required = true)
    private String signature;

    public List<BLSPublicKey> getPublicKeys() {
      return publicKeys.stream()
          .map(Bytes::fromHexString)
          .map(BLSPublicKey::fromSSZBytes)
          .collect(toList());
    }

    public Bytes getMessage() {
      return Bytes.fromHexString(message);
    }

    public BLSSignature getSignature() {
      return BlsTests.parseSignature(signature);
    }
  }
}
