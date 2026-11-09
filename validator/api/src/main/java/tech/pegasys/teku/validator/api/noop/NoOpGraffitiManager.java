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

package tech.pegasys.teku.validator.api.noop;

import java.nio.file.Path;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.api.GraffitiManager;

public class NoOpGraffitiManager extends GraffitiManager {
  public NoOpGraffitiManager() {
    super(Path.of("."));
  }

  @Override
  public void setGraffiti(final BLSPublicKey publicKey, final String graffiti) {}

  @Override
  public void deleteGraffiti(final BLSPublicKey publicKey) {}

  @Override
  public Optional<Bytes32> getGraffiti(final BLSPublicKey publicKey) {
    return Optional.empty();
  }
}
