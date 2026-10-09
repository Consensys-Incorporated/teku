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

package tech.pegasys.teku.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;

import java.util.Optional;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class EventLoggerTest {

  private static final Bytes32 PREVIOUS_HEAD_ROOT = Bytes32.fromHexStringLenient("0x1");
  private static final UInt64 PREVIOUS_HEAD_SLOT = UInt64.valueOf(10);
  private static final Bytes32 NEW_HEAD_ROOT = Bytes32.fromHexStringLenient("0x2");
  private static final UInt64 NEW_HEAD_SLOT = UInt64.valueOf(11);
  private static final Bytes32 COMMON_ANCESTOR_ROOT = Bytes32.fromHexStringLenient("0x3");
  private static final UInt64 COMMON_ANCESTOR_SLOT = UInt64.valueOf(9);

  private LogCaptor logCaptor;

  @BeforeEach
  void setUp() {
    logCaptor = LogCaptor.forLoggerName(LoggingConfigurator.EVENT_LOGGER_NAME, Level.INFO);
  }

  @AfterEach
  void tearDown() {
    logCaptor.close();
  }

  @Test
  void reorgEvent_shouldNotAnnotateWhenNoPayloadStatusIsAvailable() {
    EVENT_LOG.reorgEvent(
        PREVIOUS_HEAD_ROOT,
        PREVIOUS_HEAD_SLOT,
        NEW_HEAD_ROOT,
        NEW_HEAD_SLOT,
        COMMON_ANCESTOR_ROOT,
        COMMON_ANCESTOR_SLOT,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());

    assertThat(logCaptor.getInfoLogs())
        .containsExactly(
            "Reorg Event *** New Head: "
                + block(NEW_HEAD_ROOT, NEW_HEAD_SLOT)
                + ", Previous Head: "
                + block(PREVIOUS_HEAD_ROOT, PREVIOUS_HEAD_SLOT)
                + ", Common Ancestor: "
                + block(COMMON_ANCESTOR_ROOT, COMMON_ANCESTOR_SLOT));
  }

  @Test
  void reorgEvent_shouldAnnotateHeadsAndCommonAncestorTransition() {
    EVENT_LOG.reorgEvent(
        PREVIOUS_HEAD_ROOT,
        PREVIOUS_HEAD_SLOT,
        NEW_HEAD_ROOT,
        NEW_HEAD_SLOT,
        COMMON_ANCESTOR_ROOT,
        COMMON_ANCESTOR_SLOT,
        Optional.of("EMPTY"),
        Optional.of("FULL"),
        Optional.of("FULL"),
        Optional.of("EMPTY"));

    assertThat(logCaptor.getInfoLogs())
        .containsExactly(
            "Reorg Event *** New Head: "
                + block(NEW_HEAD_ROOT, NEW_HEAD_SLOT)
                + " [EMPTY], Previous Head: "
                + block(PREVIOUS_HEAD_ROOT, PREVIOUS_HEAD_SLOT)
                + " [FULL], Common Ancestor: "
                + block(COMMON_ANCESTOR_ROOT, COMMON_ANCESTOR_SLOT)
                + " [FULL -> EMPTY]");
  }

  @Test
  void reorgEvent_shouldAnnotateCommonAncestorWithSingleStatusWhenBranchesAgree() {
    EVENT_LOG.reorgEvent(
        PREVIOUS_HEAD_ROOT,
        PREVIOUS_HEAD_SLOT,
        NEW_HEAD_ROOT,
        NEW_HEAD_SLOT,
        COMMON_ANCESTOR_ROOT,
        COMMON_ANCESTOR_SLOT,
        Optional.of("FULL"),
        Optional.of("EMPTY"),
        Optional.of("FULL"),
        Optional.of("FULL"));

    assertThat(logCaptor.getInfoLogs())
        .singleElement()
        .asString()
        .endsWith(
            "Common Ancestor: " + block(COMMON_ANCESTOR_ROOT, COMMON_ANCESTOR_SLOT) + " [FULL]");
  }

  @Test
  void reorgEvent_shouldNotAnnotateCommonAncestorWhenOneBranchStatusIsUnknown() {
    EVENT_LOG.reorgEvent(
        PREVIOUS_HEAD_ROOT,
        PREVIOUS_HEAD_SLOT,
        NEW_HEAD_ROOT,
        NEW_HEAD_SLOT,
        COMMON_ANCESTOR_ROOT,
        COMMON_ANCESTOR_SLOT,
        Optional.of("EMPTY"),
        Optional.empty(),
        Optional.empty(),
        Optional.of("EMPTY"));

    assertThat(logCaptor.getInfoLogs())
        .singleElement()
        .asString()
        .contains(" [EMPTY], Previous Head: " + block(PREVIOUS_HEAD_ROOT, PREVIOUS_HEAD_SLOT) + ",")
        .endsWith("Common Ancestor: " + block(COMMON_ANCESTOR_ROOT, COMMON_ANCESTOR_SLOT));
  }

  @Test
  void payloadReorgEvent_shouldLogHeadAndExecutionHeads() {
    final Bytes32 previousExecutionHead = Bytes32.fromHexStringLenient("0xa");
    final Bytes32 newExecutionHead = Bytes32.fromHexStringLenient("0xb");

    EVENT_LOG.payloadReorgEvent(
        NEW_HEAD_ROOT, NEW_HEAD_SLOT, previousExecutionHead, newExecutionHead);

    assertThat(logCaptor.getInfoLogs())
        .containsExactly(
            "Reorg Event *** Payload Reorg, Head: "
                + block(NEW_HEAD_ROOT, NEW_HEAD_SLOT)
                + ", Previous Execution Head: "
                + previousExecutionHead.toUnprefixedHexString()
                + ", New Execution Head: "
                + newExecutionHead.toUnprefixedHexString());
  }

  private static String block(final Bytes32 root, final UInt64 slot) {
    return root.toUnprefixedHexString() + " (" + slot + ")";
  }
}
