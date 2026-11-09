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

package tech.pegasys.teku.spec.logic.common.util;

/**
 * Common interface for tracking data column sidecars across forks.
 *
 * <p>Different forks use different tracking keys:
 *
 * <ul>
 *   <li>Fulu: (slot, proposerIndex, columnIndex)
 *   <li>Gloas: (beaconBlockRoot, columnIndex)
 * </ul>
 *
 * <p>This abstraction allows the same tracking set to handle both fork types during fork
 * transitions.
 */
public sealed interface DataColumnSidecarTrackingKey permits FuluTrackingKey, GloasTrackingKey {}
