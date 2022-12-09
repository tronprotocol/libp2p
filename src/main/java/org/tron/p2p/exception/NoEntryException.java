package org.tron.p2p.exception;


public class NoEntryException extends Exception {

  public NoEntryException() {
    super("no valid tree entry found");
  }

  public NoEntryException(String message) {
    super(message);
  }

  public NoEntryException(Throwable throwable) {
    super(throwable);
  }
}
