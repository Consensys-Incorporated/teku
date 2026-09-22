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

package $TEST_PACKAGE$;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.Eth2ReferenceTestCase;

@DisplayName("$FORK$ - $CONFIG$ - $TEST_TYPE$")
public class $TEST_CLASS_NAME$ extends Eth2ReferenceTestCase {

  @Test
  @DisplayName("$TEST_NAME$")
  void $TEST_METHOD_NAME$() throws Throwable {
    runReferenceTest(
        new TestDefinition("$FORK$", "$CONFIG$", "$TEST_TYPE$", "$TEST_NAME$", "$RELATIVE_PATH$"));
  }
}
