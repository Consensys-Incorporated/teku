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

package tech.pegasys.teku.reference.phase0.bls;

import static tech.pegasys.teku.ethtests.finder.BlsTestFinder.BLS_DATA_FILE;

import java.io.IOException;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.impl.blst.BlstLoader;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;

public abstract class BlsTestExecutor implements TestExecutor {

  @Override
  public final void runTest(final TestDefinition testDefinition) throws Throwable {
    if (BlstLoader.INSTANCE.isPresent()) {
      try {
        BLS.setBlsImplementation(BlstLoader.INSTANCE.get());
        runTestImpl(testDefinition);
      } finally {
        BLS.resetBlsImplementation();
      }
    }
  }

  protected <T> T loadDataFile(final TestDefinition testDefinition, final Class<T> type)
      throws IOException {
    String dataFile =
        testDefinition.getTestName().endsWith(".yaml")
            ? testDefinition.getTestName()
            : BLS_DATA_FILE;
    return TestDataUtils.loadYaml(testDefinition, dataFile, type);
  }

  protected abstract void runTestImpl(TestDefinition testDefinition) throws Throwable;
}
