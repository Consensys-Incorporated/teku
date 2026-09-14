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

package tech.pegasys.teku.statetransition.execution;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface ProposerPreferencesManager {

  ProposerPreferencesManager NOOP =
      new ProposerPreferencesManager() {
        @Override
        public SafeFuture<InternalValidationResult> addLocal(
            final SignedProposerPreferences signedProposerPreferences) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public SafeFuture<InternalValidationResult> addRemote(
            final SignedProposerPreferences signedProposerPreferences) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public Optional<ProposerPreferences> getProposerPreferences(
            final UInt64 slot, final Bytes32 dependentRoot) {
          return Optional.empty();
        }

        @Override
        public Collection<ProposerPreferences> getProposerPreferencesForSlot(final UInt64 slot) {
          return List.of();
        }

        @Override
        public void subscribeOperationAdded(
            final OperationAddedSubscriber<SignedProposerPreferences> subscriber) {}
      };

  SafeFuture<InternalValidationResult> addLocal(
      SignedProposerPreferences signedProposerPreferences);

  SafeFuture<InternalValidationResult> addRemote(
      SignedProposerPreferences signedProposerPreferences);

  Optional<ProposerPreferences> getProposerPreferences(UInt64 slot, Bytes32 dependentRoot);

  Collection<ProposerPreferences> getProposerPreferencesForSlot(UInt64 slot);

  void subscribeOperationAdded(OperationAddedSubscriber<SignedProposerPreferences> subscriber);
}
