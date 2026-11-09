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

package tech.pegasys.teku.validator.client.loader;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.unmodifiableSet;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.client.Validator;

public class OwnedValidators {
  private final Map<BLSPublicKey, Validator> validators;

  public OwnedValidators() {
    this.validators = new ConcurrentHashMap<>();
  }

  public OwnedValidators(final Map<BLSPublicKey, Validator> validators) {
    this.validators = new ConcurrentHashMap<>(validators);
  }

  public void addValidator(final Validator validator) {
    final Validator previousValidator = validators.putIfAbsent(validator.getPublicKey(), validator);
    checkState(
        previousValidator == null,
        "Attempting to replace an existing validator (%s)",
        validator.getPublicKey());
  }

  public Set<BLSPublicKey> getPublicKeys() {
    return unmodifiableSet(validators.keySet());
  }

  public boolean hasNoValidators() {
    return validators.isEmpty();
  }

  public int getValidatorCount() {
    return validators.size();
  }

  public Optional<Validator> getValidator(final BLSPublicKey publicKey) {
    return Optional.ofNullable(validators.get(publicKey));
  }

  public List<Validator> getValidators() {
    return ImmutableList.copyOf(validators.values());
  }

  public boolean hasValidator(final BLSPublicKey publicKey) {
    return validators.containsKey(publicKey);
  }

  public Optional<Validator> removeValidator(final BLSPublicKey publicKey) {
    return Optional.ofNullable(validators.remove(publicKey));
  }
}
