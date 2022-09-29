package org.tron.p2p.exception;

public class P2pException extends Exception {

  private TypeEnum type;

  public P2pException(TypeEnum type, String errMsg) {
    super(errMsg);
    this.type = type;
  }

  public P2pException(TypeEnum type, Throwable throwable) {
    super(throwable);
    this.type = type;
  }

  public P2pException(TypeEnum type, String errMsg, Throwable throwable) {
    super(errMsg, throwable);
    this.type = type;
  }

  public TypeEnum getType() {
    return type;
  }

  public enum TypeEnum {
    NO_SUCH_MESSAGE(1, "no such message"),
    PARSE_MESSAGE_FAILED(2, "parse message failed"),
    MESSAGE_WITH_WRONG_LENGTH(3, "message with wrong length"),
    BAD_MESSAGE(4, "bad message"),
    P2P_HANDLE_TYPE_EXIST(5, "p2p handle type exist");

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
      return value + ", " + desc;
    }
  }

}
