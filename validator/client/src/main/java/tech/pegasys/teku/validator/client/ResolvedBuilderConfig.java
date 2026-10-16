/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.client;

import java.net.URL;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * The builder configuration that will be used for a validator during block proposal
 *
 * <p>The values are set following the order:
 *
 * <ol>
 *   <li>the proposer config for the validator
 *   <li>the runtime (Keymanager-API) config for the validator
 *   <li>the proposer config default
 *   <li>the validator client's own config
 * </ol>
 */
public record ResolvedBuilderConfig(
    UInt64 minBid, UInt64 builderBoostFactor, List<ResolvedBuilderEntry> builders) {

  public record ResolvedBuilderEntry(
      URL url,
      Optional<Bytes> authData,
      List<BLSPublicKey> builderPubkeys,
      UInt64 maxExecutionPayment,
      UInt64 minBid,
      UInt64 builderBoostFactor) {}
}
