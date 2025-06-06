/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.lightclient;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigAltair;

public class LightClientUpdateResponseSchema
    extends ContainerSchema3<LightClientUpdateResponse, SszUInt64, SszBytes4, LightClientUpdate> {

  public LightClientUpdateResponseSchema(final SpecConfigAltair specConfigAltair) {
    super(
        "LightClientUpdateResponse",
        namedSchema("response_chunk_len", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("context", SszPrimitiveSchemas.BYTES4_SCHEMA),
        namedSchema("payload", new LightClientUpdateSchema(specConfigAltair)));
  }

  public LightClientUpdateResponse create(
      final SszUInt64 responseChunkLen, final SszBytes4 context, final LightClientUpdate payload) {
    return new LightClientUpdateResponse(this, responseChunkLen, context, payload);
  }

  @Override
  public LightClientUpdateResponse createFromBackingNode(final TreeNode node) {
    return new LightClientUpdateResponse(this, node);
  }
}
