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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import java.util.Optional;

/**
 * A fork choice node's data together with the data of its parent fork choice node, if the parent is
 * retained in the fork choice tree.
 *
 * <p>From Gloas, a block has several fork choice nodes, so the parent node isn't identified by the
 * block's parent root alone: the {@code EMPTY} and {@code FULL} nodes have the same block's {@code
 * PENDING} node as parent, and the {@code PENDING} node has the parent block's {@code EMPTY} or
 * {@code FULL} node.
 */
public record ProtoNodeDataWithParent(ProtoNodeData node, Optional<ProtoNodeData> parent) {}
