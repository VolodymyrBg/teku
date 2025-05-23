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

package tech.pegasys.teku.bls.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;

public abstract class AbstractPublicKeyTest {

  protected abstract BLS12381 getBls();

  @Test
  public void shouldHaveConsistentHashCodeAndEquals() {
    final PublicKey key =
        getBls()
            .publicKeyFromCompressed(
                Bytes48.fromHexString(
                    "0x81283b7a20e1ca460ebd9bbd77005d557370cabb1f9a44f530c4c4c66230f675f8df8b4c2818851aa7d77a80ca5a4a5e"));
    final PublicKey same = getBls().publicKeyFromCompressed(key.toBytesCompressed());

    assertEquals(key, same);
    assertEquals(key.hashCode(), same.hashCode());
  }
}
