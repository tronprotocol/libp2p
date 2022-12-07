package org.tron.p2p.exception;


public class ENRInLinkTreeException extends Exception {

  public ENRInLinkTreeException() {
    super("enr entry in link tree");
  }

  public ENRInLinkTreeException(String message) {
    super(message);
  }
}
