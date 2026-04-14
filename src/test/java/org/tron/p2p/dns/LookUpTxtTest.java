package org.tron.p2p.dns;

import java.net.InetAddress;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.dns.lookup.LookUpTxt;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Name;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;

public class LookUpTxtTest {

  @Before
  public void setUp() {
    Parameter.p2pConfig = new P2pConfig();
  }

  @Test
  public void testJoinTXTRecord_singleString() throws TextParseException {
    Name name = Name.fromString("test.example.com.");
    TXTRecord record = new TXTRecord(name, DClass.IN, 300, "hello");
    Assert.assertEquals("hello", LookUpTxt.joinTXTRecord(record));
  }

  @Test
  public void testJoinTXTRecord_multipleStrings() throws TextParseException {
    Name name = Name.fromString("test.example.com.");
    TXTRecord record = new TXTRecord(name, DClass.IN, 300,
        Arrays.asList("enrtree-root:v1 ", "e=ABCDE ", "l=FGHIJ seq=1 sig=XYZ"));
    // joinTXTRecord trims each string before concatenating, so trailing spaces are removed
    Assert.assertEquals("enrtree-root:v1e=ABCDEl=FGHIJ seq=1 sig=XYZ",
        LookUpTxt.joinTXTRecord(record));
  }

  // -------------------------------------------------------------------------
  // lookUpIp tests
  // -------------------------------------------------------------------------

  /**
   * "localhost" is always present in /etc/hosts on every OS, so InetAddress.getByName resolves it
   * locally without issuing any DNS query — this validates the /etc/hosts fast path.
   */
  @Test
  public void testLookUpIp_localhost_resolvesViaHosts() {
    InetAddress address = LookUpTxt.lookUpIp("localhost");
    Assert.assertNotNull("localhost must resolve via /etc/hosts", address);
    // /etc/hosts maps localhost to the loopback interface
    Assert.assertTrue("Expected loopback address, got: " + address.getHostAddress(),
        address.isLoopbackAddress());
  }

  /**
   * example.com is a stable IANA-reserved domain that always has an A record.
   * This validates the normal DNS resolution path (Step 1 via OS resolver).
   */
  @Test
  public void testLookUpIp_wellKnownDomain_returnsNonNull() {
    InetAddress address = LookUpTxt.lookUpIp("example.com");
    Assert.assertNotNull("example.com should resolve to an InetAddress", address);
    Assert.assertFalse("Resolved address must not be a wildcard",
        address.isAnyLocalAddress());
  }

  /**
   * The ".invalid" TLD is RFC 2606-reserved and guaranteed never to resolve.
   * All three resolution steps (OS, default DNS, public DNS) should fail, returning null.
   */
  @Test
  public void testLookUpIp_nonexistentDomain_returnsNull() throws Exception {
    // Limit public-DNS retries to 1 via reflection so the test exits quickly.
    java.lang.reflect.Field field = LookUpTxt.class.getDeclaredField("maxRetryTimes");
    field.setAccessible(true);
    int saved = (int) field.get(null);
    field.set(null, 1);
    try {
      InetAddress address = LookUpTxt.lookUpIp("this.domain.absolutely.does.not.exist.invalid");
      Assert.assertNull("Non-existent domain should return null", address);
    } finally {
      field.set(null, saved);
    }
  }
}
