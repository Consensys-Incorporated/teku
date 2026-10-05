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

package tech.pegasys.teku.weaksubjectivity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class WeakSubjectivityConfigTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final WeakSubjectivityConfig config =
      WeakSubjectivityConfig.builder().specProvider(spec).build();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();

  @Test
  public void build_withParsedParameters() {
    WeakSubjectivityConfig config = configBuilder().weakSubjectivityCheckpoint(checkpoint).build();

    assertThat(config.getWeakSubjectivityCheckpoint()).contains(checkpoint);
  }

  @Test
  public void build_withNoWeakSubjectivityCheckpoint() {
    WeakSubjectivityConfig config = configBuilder().build();
    assertThat(config.getWeakSubjectivityCheckpoint()).isEmpty();
  }

  @Test
  public void updated_setNewCheckpoint() {
    WeakSubjectivityConfig original = config;
    assertThat(original.getWeakSubjectivityCheckpoint()).isEmpty();

    WeakSubjectivityConfig updated =
        original.updated(b -> b.weakSubjectivityCheckpoint(checkpoint));
    assertThat(original.getWeakSubjectivityCheckpoint()).isEmpty();
    assertThat(updated.getWeakSubjectivityCheckpoint()).contains(checkpoint);
    assertThat(updated).isNotEqualTo(original);
  }

  @Test
  public void updated_shouldCloneAllProperties() {
    WeakSubjectivityConfig configA =
        configBuilder()
            .weakSubjectivityCheckpoint(checkpoint)
            .suppressWSPeriodChecksUntilEpoch(UInt64.ONE)
            .build();
    WeakSubjectivityConfig configB = configA.updated((__) -> {});

    assertThat(configA).isEqualTo(configB);
  }

  @Test
  public void updated_clearCheckpoint() {
    WeakSubjectivityConfig original =
        configBuilder().weakSubjectivityCheckpoint(checkpoint).build();
    assertThat(original.getWeakSubjectivityCheckpoint()).contains(checkpoint);

    WeakSubjectivityConfig updated =
        original.updated(b -> b.weakSubjectivityCheckpoint(Optional.empty()));
    assertThat(original.getWeakSubjectivityCheckpoint()).contains(checkpoint);
    assertThat(updated.getWeakSubjectivityCheckpoint()).isEmpty();
    assertThat(updated).isNotEqualTo(original);
  }

  @Test
  public void equals() {
    final WeakSubjectivityConfig configA = config;
    final WeakSubjectivityConfig configB =
        configBuilder().weakSubjectivityCheckpoint(checkpoint).build();

    assertThat(config).isEqualTo(configA);
    assertThat(configA).isNotEqualTo(configB);
  }

  private WeakSubjectivityConfig.Builder configBuilder() {
    return WeakSubjectivityConfig.builder().specProvider(spec);
  }
}
