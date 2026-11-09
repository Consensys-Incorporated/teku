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

package tech.pegasys.teku.statetransition.forkchoice.fastconfirmation;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Per-slot snapshot captured on the fork-choice thread and handed to the fast confirmation runner.
 * Only the head root and slot are pinned here; all remaining inputs (epoch-boundary flags, greatest
 * unrealized justified checkpoint, source states) are derived on the runner to keep work off the
 * latency-critical fork-choice thread.
 */
record FastConfirmationInput(UInt64 slot, Bytes32 headRoot) {}
