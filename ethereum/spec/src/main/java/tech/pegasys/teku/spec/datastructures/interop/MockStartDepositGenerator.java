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

package tech.pegasys.teku.spec.datastructures.interop;

import java.util.List;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.util.DepositGenerator;

public class MockStartDepositGenerator {
  private final DepositGenerator depositGenerator;
  private final SpecConfig specConfig;

  public MockStartDepositGenerator(final Spec spec) {
    this(spec, new DepositGenerator(spec));
  }

  public MockStartDepositGenerator(final Spec spec, final DepositGenerator depositGenerator) {
    this.depositGenerator = depositGenerator;
    this.specConfig = spec.getGenesisSpecConfig();
  }

  public List<DepositData> createDeposits(final List<BLSKeyPair> validatorKeys) {
    return validatorKeys.stream().map(this::createDepositData).toList();
  }

  public List<DepositData> createDeposits(
      final List<BLSKeyPair> validatorKeys, final UInt64 depositBalance) {
    return validatorKeys.stream().map(key -> createDepositData(key, depositBalance)).toList();
  }

  private DepositData createDepositData(final BLSKeyPair keyPair) {
    return depositGenerator.createDepositData(
        keyPair, specConfig.getMaxEffectiveBalance(), keyPair.getPublicKey());
  }

  private DepositData createDepositData(final BLSKeyPair keyPair, final UInt64 depositBalance) {
    return depositGenerator.createDepositData(keyPair, depositBalance, keyPair.getPublicKey());
  }
}
