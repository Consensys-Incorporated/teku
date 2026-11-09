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
import tech.pegasys.teku.bls.BLSPublicKey;

public class DeleteKeysRequest {
  private List<BLSPublicKey> publicKeys = new ArrayList<>();

  public List<BLSPublicKey> getPublicKeys() {
    return publicKeys;
  }

  public void setPublicKeys(final List<BLSPublicKey> publicKeys) {
    this.publicKeys = publicKeys;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeleteKeysRequest that = (DeleteKeysRequest) o;
    return Objects.equals(publicKeys, that.publicKeys);
  }

  @Override
  public int hashCode() {
    return Objects.hash(publicKeys);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("publicKeys", publicKeys).toString();
  }
}
