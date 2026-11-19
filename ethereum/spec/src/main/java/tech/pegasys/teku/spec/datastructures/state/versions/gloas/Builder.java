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

package tech.pegasys.teku.spec.datastructures.state.versions.gloas;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * A builder in the registry. {@link BuilderGloas} is the BLS-keyed builder from Gloas up to PQ;
 * {@link tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPq} is keyed by a leanXMSS
 * public key and has no BLS key, so {@link #getPublicKey()} throws.
 */
public interface Builder extends SszContainer {

  /**
   * @throws UnsupportedOperationException for a PQ builder, which has no BLS key
   */
  BLSPublicKey getPublicKey();

  int getVersion();

  Eth1Address getExecutionAddress();

  UInt64 getBalance();

  UInt64 getDepositEpoch();

  UInt64 getWithdrawableEpoch();

  Builder copyWithNewWithdrawableEpoch(UInt64 withdrawableEpoch);

  Builder copyWithNewBalance(UInt64 balance);
}
