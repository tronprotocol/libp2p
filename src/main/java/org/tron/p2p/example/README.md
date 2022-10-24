# Run independently
command of start a p2p node:
```bash
$ nohup java -jar libp2p.jar [options] >> start.log 2>&1 &
```

available cli options:
```bash
 -a,--active-nodes <arg>       active node(s), ip:port[,ip:port[...]]
 -d,--discover <arg>           enable p2p discover, 0/1, default 1
 -M,--max-connection <arg>     max connection number, int, default 50
 -m,--min-connection <arg>     minConnections, default 8
 -p,--port <arg>               UDP & TCP port, int, default 18888
 -s,--seed-nodes <arg>         seed node(s), required, ip:port[,ip:port[...]]
 -t,--trust-ips <arg>          trust ip(s), ip[,ip[...]]
 -v,--version <arg>            p2p version, int, default 1
```
For details please check [StartApp](https://github.com/tronprotocol/libp2p/blob/main/src/main/java/org/tron/p2p/example/StartApp.java).

For example
Node A, starts with default configuration parameters. Let's say its IP is 127.0.0.1
```bash
$ nohup java -jar libp2p.jar >> start.log 2>&1 &
```

Node B, start with seed nodes(127.0.0.1:18888). Let's say its IP is 127.0.0.2
```bash
$ nohup java -jar libp2p.jar -s 127.0.0.1:18888 >> start.log 2>&1 &
```

Node C, start with with seed nodes(127.0.0.1:18888). Let's say its IP is 127.0.0.3
```bash
$ nohup java -jar libp2p.jar -s 127.0.0.1:18888 >> start.log 2>&1 &
```

After the three nodes are successfully started, the usual situation is that node B can discover node C (or node C can discover B), and the three of them can establish a TCP connection with each other.


# Use as a dependency

## Core classes
* [P2pService](https://github.com/tronprotocol/libp2p/blob/main/src/main/java/org/tron/p2p/P2pService.java) is the entry class of p2p service and provides the startup interface of p2p service and the main interfaces provided by p2p module.
* [P2pConfig](https://github.com/tronprotocol/libp2p/blob/main/src/main/java/org/tron/p2p/P2pConfig.java) defines all the configurations of the p2p module, such as the listening port, the maximum number of connections, etc.
* [P2pEventHandler](https://github.com/tronprotocol/libp2p/blob/main/src/main/java/org/tron/p2p/P2pEventHandler.java) is the abstract class for p2p event handler.
* [Channel](https://github.com/tronprotocol/libp2p/blob/main/src/main/java/org/tron/p2p/connection/Channel.java) is an implementation of the TCP connection channel in the p2p module. The new connection channel is obtained through the `P2pEventHandler.onConnect` method.

## Interface
* `P2pService.start` 
  - @param: p2pConfig P2pConfig
  - @return: void
  - desc: the startup interface of p2p service
* `P2pService.close` 
  - @param: 
  - @return: void
  - desc: the close interface of p2p service
* `P2pService.register` 
  - @param: p2PEventHandler P2pEventHandler
  - @return: void
  - desc: register p2p event handler
* `P2pService.connect` 
  - @param: address InetSocketAddress
  - @return: void
  - desc: connect to a node with a socket address
* `P2pService.getAllNodes` 
  - @param: 
  - @return: List<Node>
  - desc: get all the nodes
* `P2pService.getTableNodes` 
  - @param: 
  - @return: List<Node>
  - desc: get all the nodes that in the hash table
* `P2pService.getConnectableNodes` 
  - @param: 
  - @return: List<Node>
  - desc: get all the nodes that can be connected
* `P2pService.getP2pStats()` 
  - @param: 
  - @return: void
  - desc: get statistics information of p2p service
* `Channel.send`
  - @param: data byte[]
  - @return: void
  - desc: send messages to the peer node through the channel
* `Channel.close` 
  - @param: 
  - @return: void
  - desc: the close interface of channel

## Steps for usage
1. Config p2p parameters
2. Implement P2pEventHandler and register p2p event handler
3. Start p2p service
4. Use Channel's send and close interfaces as needed
5. Use P2pService's interfaces as needed

### Config
New p2p config instance
```bash
P2pConfig config = new P2pConfig();
```

Set p2p version
```bash
config.setVersion(11111);
```

Set TCP and UDP listen port
```bash
config.setPort(18888);
```

Turn node discovery on or off
```bash
config.setDiscoverEnable(true);
```

Set discover seed nodes
```bash
List<InetSocketAddress> seedNodeList = new ArrayList<>();
seedNodeList.add(new InetSocketAddress("13.124.62.58", 18888));
seedNodeList.add(new InetSocketAddress("18.196.99.16", 18888));
config.setSeedNodes(seedNodeList);
```

Set active nodes
```bash
List<InetAddress> trustNodeList = new ArrayList<>();
trustNodeList.add((new InetSocketAddress("127.0.0.2", 18888)).getAddress());
config.setTrustNodes(trustNodeList);
```

Set the minimum number of connections
```bash
config.setMinConnections(8);
```

Set the minimum number of actively established connections
```bash
config.setMinActiveConnections(2);
```

Set the maximum number of connections
```bash
config.setMaxConnections(30);
```

Set the maximum number of connections with the same IP
```bash
config.setMaxConnectionsWithSameIp(2);
```

### Handler
Implement definition message
```bash
public class TestMessage {
    protected MessageTypes type;
    protected byte[] data;
    public TestMessage(byte[] data) {
      this.type = MessageTypes.TEST;
      this.data = data;
    }

}

public enum MessageTypes {

    FIRST((byte)0x00),

    TEST((byte)0x01),

    LAST((byte)0x8f);

    private final byte type;

    MessageTypes(byte type) {
      this.type = type;
    }

    public byte getType() {
      return type;
    }

    private static final Map<Byte, MessageTypes> map = new HashMap<>();

    static {
      for (MessageTypes value : values()) {
        map.put(value.type, value);
      }
    }

    public static MessageTypes fromByte(byte type) {
      return map.get(type);
    }
  }
```

Inheritance implements the P2pEventHandler class.  
* `onConnect` is called back after the TCP connection is established. 
* `onDisconnect` is called back after the TCP connection is closed.
* `onMessage` is called back after receiving a message on the channel. Note that `data[0]` is the message type.
```bash
public class MyP2pEventHandler extends P2pEventHandler {

    public MyP2pEventHandler() {
      this.typeSet = new HashSet<>();
      this.typeSet.add(MessageTypes.TEST.getType());
    }

    @Override
    public void onConnect(Channel channel) {
      channels.put(channel.getInetSocketAddress(), channel);
    }

    @Override
    public void onDisconnect(Channel channel) {
      channels.remove(channel.getInetSocketAddress());
    }

    @Override
    public void onMessage(Channel channel, byte[] data) {
      byte type = data[0];
      byte[] messageData = ArrayUtils.subarray(data, 1, data.length);
      switch (MessageTypes.fromByte(type)) {
        case TEST:
          TestMessage message = new TestMessage(messageData);
          // process TestMessage
          break;
        default:
          // todo
      }
    }
}
```

### Start p2p service
Start p2p service with P2pConfig and P2pEventHandler
```bash
P2pService p2pService = new P2pService();
MyP2pEventHandler myP2pEventHandler = new MyP2pEventHandler();
try {
  p2pService.register(myP2pEventHandler);
} catch (P2pException e) {
  // todo process exception
}
p2pService.start(config);
```

For details please check [ImportUsing](https://github.com/tronprotocol/libp2p/blob/main/src/main/java/org/tron/p2p/example/ImportUsing.java)


