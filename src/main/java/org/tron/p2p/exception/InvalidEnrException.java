package org.tron.p2p.exception;


public class InvalidEnrException extends Exception {

  public InvalidEnrException() {
    super("invalid node record");
  }

  public InvalidEnrException(String message) {
    super(message);
  }

  public InvalidEnrException(Throwable throwable) {
    super(throwable);
  }
}
