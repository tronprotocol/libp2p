package org.tron.p2p.dns.update;


public class RecordSet {

  String[] values;
  long ttl;

  public RecordSet(String[] values, long ttl) {
    this.values = values;
    this.ttl = ttl;
  }
}
