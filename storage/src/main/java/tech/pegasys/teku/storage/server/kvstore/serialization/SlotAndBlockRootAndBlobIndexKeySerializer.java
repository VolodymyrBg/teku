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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

/**
 * This serializer is intended to be used as a Key so that it preserve slot ordering when we stream
 * data. This is useful for values that are always looked up by root, slot and blobIndex, giving us
 * the ability to quickly lookup most recent\oldest values by slot as well as perform pruning based
 * on slot
 */
class SlotAndBlockRootAndBlobIndexKeySerializer
    implements KvStoreSerializer<SlotAndBlockRootAndBlobIndex> {
  static final int SLOT_SIZE = Long.BYTES;
  static final int BLOCK_ROOT_SIZE = Bytes32.SIZE;
  static final int BLOB_INDEX_SIZE = Long.BYTES;

  static final int SLOT_OFFSET = 0;
  static final int BLOCK_ROOT_OFFSET = SLOT_OFFSET + SLOT_SIZE;
  static final int BLOB_INDEX_OFFSET = BLOCK_ROOT_OFFSET + BLOCK_ROOT_SIZE;
  static final int DATA_SIZE = BLOB_INDEX_OFFSET + BLOB_INDEX_SIZE;

  @Override
  public SlotAndBlockRootAndBlobIndex deserialize(final byte[] data) {
    checkArgument(data.length == DATA_SIZE);
    final UInt64 slot = UInt64Serializer.deserialize(data, SLOT_OFFSET);
    final Bytes32 blockRoot = Bytes32.wrap(data, BLOCK_ROOT_OFFSET);
    final UInt64 blobIndex = UInt64Serializer.deserialize(data, BLOB_INDEX_OFFSET);
    return new SlotAndBlockRootAndBlobIndex(slot, blockRoot, blobIndex);
  }

  @Override
  public byte[] serialize(final SlotAndBlockRootAndBlobIndex value) {
    return Bytes.concatenate(
            Bytes.wrap(Longs.toByteArray(value.getSlot().longValue())),
            value.getBlockRoot(),
            Bytes.wrap(Longs.toByteArray(value.getBlobIndex().longValue())))
        .toArrayUnsafe();
  }
}
