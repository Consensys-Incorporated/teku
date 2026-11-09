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

package tech.pegasys.teku.spec.util;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;

public class BuilderBuilder {

  private BLSPublicKey publicKey;
  private int version;
  private Eth1Address executionAddress;
  private UInt64 balance;
  private UInt64 depositEpoch = FAR_FUTURE_EPOCH;
  private UInt64 withdrawableEpoch = FAR_FUTURE_EPOCH;

  public BuilderBuilder(final Spec spec, final DataStructureUtil dataStructureUtil) {
    this.publicKey = dataStructureUtil.randomPublicKey();
    this.version = dataStructureUtil.randomUInt8();
    this.executionAddress = dataStructureUtil.randomEth1Address();
    this.balance = spec.getGenesisSpec().getConfig().getMaxEffectiveBalance();
  }

  public BuilderBuilder publicKey(final BLSPublicKey publicKey) {
    this.publicKey = publicKey;
    return this;
  }

  public BuilderBuilder version(final int version) {
    this.version = version;
    return this;
  }

  public BuilderBuilder executionAddress(final Eth1Address executionAddress) {
    this.executionAddress = executionAddress;
    return this;
  }

  public BuilderBuilder balance(final UInt64 balance) {
    this.balance = balance;
    return this;
  }

  public BuilderBuilder depositEpoch(final UInt64 depositEpoch) {
    this.depositEpoch = depositEpoch;
    return this;
  }

  public BuilderBuilder withdrawableEpoch(final UInt64 withdrawableEpoch) {
    this.withdrawableEpoch = withdrawableEpoch;
    return this;
  }

  public Builder build() {
    return new Builder(
        publicKey, version, executionAddress, balance, depositEpoch, withdrawableEpoch);
  }
}
