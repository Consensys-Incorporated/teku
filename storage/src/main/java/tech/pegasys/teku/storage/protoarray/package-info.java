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

/**
 * This package provides an implementation of the ProtoArray algorithm, which is an optimization to
 * the naive Fork Choice algorithm described in Ethereum 2.0 specifications, authored by Diederik
 * Loerakker.
 *
 * <p>The code is mostly translated from the Apache 2.0 licensed Lighthouse codebase. Thanks to Paul
 * Hauner and others for the implementation.
 * https://github.com/sigp/lighthouse/tree/master/eth2/proto_array_fork_choice
 */
package tech.pegasys.teku.storage.protoarray;
