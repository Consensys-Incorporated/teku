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

package tech.pegasys.teku.benchmarks.kzg;

import static tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader.TEST_TRUSTED_SETUP;

import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader;

public class KzgInstances {
  private final KZG kzg = KZG.getInstance(false);
  private final KZG rustKzg = KZG.getInstance(true);

  public KzgInstances(final int precompute) {
    kzg.loadTrustedSetup(TrustedSetupLoader.getTrustedSetupFile(TEST_TRUSTED_SETUP), precompute);
    rustKzg.loadTrustedSetup(TEST_TRUSTED_SETUP, precompute);
  }

  public KZG getKzg(final boolean isRustEnabled) {
    return isRustEnabled ? rustKzg : kzg;
  }
}
