package org.tron.p2p.exception;


public class HashMissMatchException extends Exception {

  public HashMissMatchException() {
    super("hash mismatch");
  }

  public HashMissMatchException(String message) {
    super(message);
  }
}
