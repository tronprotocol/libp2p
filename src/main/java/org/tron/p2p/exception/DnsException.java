package org.tron.p2p.exception;


public class DnsException extends Exception {

  private DnsException.TypeEnum type;

  public DnsException(DnsException.TypeEnum type, String errMsg) {
    super(type.desc + ", " + errMsg);
    this.type = type;
  }

  public DnsException(DnsException.TypeEnum type, Throwable throwable) {
    super(throwable);
    this.type = type;
  }

  public DnsException(DnsException.TypeEnum type, String errMsg, Throwable throwable) {
    super(errMsg, throwable);
    this.type = type;
  }

  public DnsException.TypeEnum getType() {
    return type;
  }

  public enum TypeEnum {
    LOOK_UP_FAILED(0, "look up txt failed"),
    //Resolver/sync errors
    NO_ROOT_FOUND(1, "no valid root found"),
    NO_ENTRY_FOUND(2, "no valid tree entry found"),
    HASH_MISS_MATCH(3, "hash miss match"),
    ENR_IN_LINK_TREE(4, "enr entry in link tree"),
    LINK_IN_ENR_TREE(5, "link entry in enr tree"),

    // Entry parse errors
    UNKNOWN_ENTRY(6, "unknown entry type"),
    NO_PUBKEY(7, "missing public key"),
    BAD_PUBKEY(8, "invalid public key"),
    INVALID_ENR(9, "invalid node record"),
    INVALID_CHILD(10, "invalid child hash"),
    INVALID_SIGNATURE(11, "invalid base64 signature"),
    INVALID_ROOT_SYNTAX(12, "invalid root syntax"),
    INVALID_SCHEME_URL(13, "invalid scheme url"),
    DEPLOY_DOMAIN_FAILED(14, "failed to deploy domain"),
    OTHER_ERROR(15, "other error");

    private Integer value;
    private String desc;

    TypeEnum(Integer value, String desc) {
      this.value = value;
      this.desc = desc;
    }

    public Integer getValue() {
      return value;
    }

    public String getDesc() {
      return desc;
    }

    @Override
    public String toString() {
      return value + "-" + desc;
    }
  }
}
