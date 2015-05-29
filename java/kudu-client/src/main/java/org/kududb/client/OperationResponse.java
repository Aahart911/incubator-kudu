// Copyright (c) 2015, Cloudera, inc.
// Confidential Cloudera Information: Covered by NDA.
package org.kududb.client;

import org.kududb.tserver.Tserver;

public class OperationResponse extends KuduRpcResponse {

  private final long writeTimestamp;
  private final RowError rowError;
  private final Operation operation;

  /**
   * Package-private constructor to build an OperationResponse with a row error in the pb format.
   * @param elapsedMillis time in milliseconds since RPC creation to now
   * @param writeTimestamp HT's write timestamp
   * @param operation the operation that created this response
   * @param errorPB a row error in pb format, can be null
   */
  OperationResponse(long elapsedMillis, String tsUUID, long writeTimestamp,
                    Operation operation, Tserver.WriteResponsePB.PerRowErrorPB errorPB) {
    super(elapsedMillis, tsUUID);
    this.writeTimestamp = writeTimestamp;
    this.rowError = errorPB == null ? null : RowError.fromRowErrorPb(errorPB, operation, tsUUID);
    this.operation = operation;
  }

  /**
   * Package-private constructor to build an OperationResponse with a row error.
   * @param elapsedMillis time in milliseconds since RPC creation to now
   * @param writeTimestamp HT's write timestamp
   * @param operation the operation that created this response
   * @param rowError a parsed row error, can be null
   */
  OperationResponse(long elapsedMillis, String tsUUID, long writeTimestamp,
                    Operation operation, RowError rowError) {
    super(elapsedMillis, tsUUID);
    this.writeTimestamp = writeTimestamp;
    this.rowError = rowError;
    this.operation = operation;
  }

  /**
   * Gives the write timestamp that was returned by the Tablet Server.
   * @return a timestamp in milliseconds, 0 if the external consistency mode set in AsyncKuduSession
   * wasn't CLIENT_PROPAGATED
   */
  public long getWriteTimestamp() {
    return writeTimestamp;
  }

  /**
   * Returns a row error. If {@link #hasRowError()} returns false, then this method returns null.
   * @return a row error, or null if the operation was successful
   */
  public RowError getRowError() {
    return rowError;
  }

  /**
   * Tells if this operation response contains a row error.
   * @return true if this operation response has errors, else false
   */
  public boolean hasRowError() {
    return rowError != null;
  }

  /**
   * Returns the operation associated with this response.
   * @return an operation, cannot be null
   */
  Operation getOperation() {
    return operation;
  }
}
