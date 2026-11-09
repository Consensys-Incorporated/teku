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

package tech.pegasys.teku.validator.client.restapi.apis.schema;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PostKeysRequest {
  private List<String> keystores = new ArrayList<>();
  private List<String> passwords = new ArrayList<>();
  private Optional<String> slashingProtection = Optional.empty();

  public PostKeysRequest() {}

  public PostKeysRequest(
      final List<String> keystores,
      final List<String> passwords,
      final Optional<String> slashingProtection) {
    this.keystores = keystores;
    this.passwords = passwords;
    this.slashingProtection = slashingProtection;
  }

  public List<String> getKeystores() {
    return keystores;
  }

  public void setKeystores(final List<String> keystores) {
    this.keystores = keystores;
  }

  public List<String> getPasswords() {
    return passwords;
  }

  public void setPasswords(final List<String> passwords) {
    this.passwords = passwords;
  }

  public Optional<String> getSlashingProtection() {
    return slashingProtection;
  }

  public void setSlashingProtection(final Optional<String> slashingProtection) {
    this.slashingProtection = slashingProtection;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PostKeysRequest that = (PostKeysRequest) o;
    return Objects.equals(keystores, that.keystores)
        && Objects.equals(passwords, that.passwords)
        && Objects.equals(slashingProtection, that.slashingProtection);
  }

  @Override
  public int hashCode() {
    return Objects.hash(keystores, passwords, slashingProtection);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("keystores", keystores)
        .add("passwords", passwords)
        .add("slashingProtection", slashingProtection)
        .toString();
  }
}
