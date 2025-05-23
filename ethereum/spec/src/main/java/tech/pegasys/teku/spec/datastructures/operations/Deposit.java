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

package tech.pegasys.teku.spec.datastructures.operations;

import static tech.pegasys.teku.ethereum.pow.api.DepositConstants.DEPOSIT_CONTRACT_TREE_DEPTH;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class Deposit extends Container2<Deposit, SszBytes32Vector, DepositData> {

  public static class DepositSchema
      extends ContainerSchema2<Deposit, SszBytes32Vector, DepositData> {

    public DepositSchema() {
      super(
          "Deposit",
          namedSchema("proof", SszBytes32VectorSchema.create(DEPOSIT_CONTRACT_TREE_DEPTH + 1)),
          namedSchema("data", DepositData.SSZ_SCHEMA));
    }

    public SszBytes32VectorSchema<?> getProofSchema() {
      return (SszBytes32VectorSchema<?>) getFieldSchema0();
    }

    @Override
    public Deposit createFromBackingNode(final TreeNode node) {
      return new Deposit(this, node);
    }
  }

  public static final DepositSchema SSZ_SCHEMA = new DepositSchema();

  private static final SszBytes32Vector EMPTY_PROOF = SSZ_SCHEMA.getProofSchema().getDefault();

  private Deposit(final DepositSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Deposit(final DepositData data) {
    this(EMPTY_PROOF, data);
  }

  public Deposit(final SszBytes32Vector proof, final DepositData data) {
    super(SSZ_SCHEMA, proof, data);
  }

  public SszBytes32Vector getProof() {
    return getField0();
  }

  public DepositData getData() {
    return getField1();
  }

  @Override
  public DepositSchema getSchema() {
    return (DepositSchema) super.getSchema();
  }
}
