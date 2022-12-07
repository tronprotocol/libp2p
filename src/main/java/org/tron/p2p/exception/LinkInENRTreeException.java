package org.tron.p2p.exception;


public class LinkInENRTreeException extends Exception {

  public LinkInENRTreeException() {
    super("link entry in ENR tree");
  }

  public LinkInENRTreeException(String message) {
    super(message);
  }
}
