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

import com.google.common.collect.ImmutableMap;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.reference.TestExecutor;

public class BlsTests {

  public static final ImmutableMap<String, TestExecutor> BLS_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put("bls/verify", new BlsVerifyTestExecutor())
          .put("bls/batch_verify", new BlsBatchVerifyTestExecutor())
          .put("bls/aggregate", new BlsAggregateTestExecutor())
          .put("bls/aggregate_verify", new BlsAggregateVerifyTestExecutor())
          .put("bls/sign", new BlsSignTestExecutor())
          .put("bls/fast_aggregate_verify", new BlsFastAggregateVerifyTestExecutor())
          .put("bls/eth_aggregate_pubkeys", new BlsEthAggregatePublicKeysTestExecutor())
          .put("bls/eth_fast_aggregate_verify", new BlsEthFastAggregateVerifyTestExecutor())
          .put("bls/deserialization_G1", new BlsDeserializationG1TestExecutor())
          .put("bls/deserialization_G2", new BlsDeserializationG2TestExecutor())
          // Hash to G2 is an internal detail of our BLS library so we can't run these tests
          .put("bls/hash_to_G2", TestExecutor.IGNORE_TESTS)
          .build();

  public static BLSSignature parseSignature(final String value) {
    return BLSSignature.fromSSZBytes(Bytes.fromHexStringLenient(value, 96));
  }
}
