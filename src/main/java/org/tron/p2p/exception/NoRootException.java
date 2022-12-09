package org.tron.p2p.exception;


public class NoRootException extends Exception {

  public NoRootException() {
    super("no valid root found");
  }

  public NoRootException(String message) {
    super(message);
  }

  public NoRootException(Throwable throwable) {
    super(throwable);
  }
}
