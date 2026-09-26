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

package tech.pegasys.teku.storage.api;

import java.util.Collection;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;

public interface LightClientUpdateChannel extends ChannelInterface {

  SafeFuture<Void> onNewBestLightClientUpdate(
      UInt64 period, LightClientUpdate update, Bytes32 signatureBlockRoot);

  SafeFuture<Void> onRemoveBestLightClientUpdates(Collection<UInt64> periods);

  SafeFuture<Void> onPruneBestLightClientUpdatesBefore(UInt64 period);

  LightClientUpdateChannel NOOP =
      new LightClientUpdateChannel() {
        @Override
        public SafeFuture<Void> onNewBestLightClientUpdate(
            final UInt64 period, final LightClientUpdate update, final Bytes32 signatureBlockRoot) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Void> onRemoveBestLightClientUpdates(final Collection<UInt64> periods) {
          return SafeFuture.COMPLETE;
        }

        @Override
        public SafeFuture<Void> onPruneBestLightClientUpdatesBefore(final UInt64 period) {
          return SafeFuture.COMPLETE;
        }
      };
}
