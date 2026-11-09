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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

public class DeleteKeyResultTest {

  @Test
  void shouldProduceSuccessResult() {
    assertThat(DeleteKeyResult.success())
        .isEqualTo(new DeleteKeyResult(DeletionStatus.DELETED, Optional.empty()));
  }

  @Test
  void shouldProduceNotFoundResult() {
    assertThat(DeleteKeyResult.notFound())
        .isEqualTo(new DeleteKeyResult(DeletionStatus.NOT_FOUND, Optional.empty()));
  }

  @Test
  void shouldProduceNotActiveResult() {
    assertThat(DeleteKeyResult.notActive())
        .isEqualTo(new DeleteKeyResult(DeletionStatus.NOT_ACTIVE, Optional.empty()));
  }

  @Test
  void shouldProduceErrorResult() {
    assertThat(DeleteKeyResult.error("message"))
        .isEqualTo(new DeleteKeyResult(DeletionStatus.ERROR, Optional.of("message")));
  }
}
