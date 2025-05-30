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

package tech.pegasys.teku.infrastructure.ssz;

import java.io.OutputStream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;

/**
 * Represent the data which can be SSZ serialized
 *
 * <p>SSZ spec: https://github.com/protolambda/eth2.0-ssz
 */
public interface SimpleOffsetSerializable {

  /** Returns this data SSZ serialization */
  Bytes sszSerialize();

  /**
   * SSZ serializes this data to supplied {@code writer}
   *
   * @return number of bytes written
   */
  int sszSerialize(SszWriter writer);

  int sszSerialize(OutputStream out);
}
