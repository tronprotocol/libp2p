package org.tron.p2p.dns;

import java.net.UnknownHostException;
import java.util.Arrays;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.dns.lookup.LookUpTxt;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Name;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;

public class LookUpTxtTest {

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

  @Test
  public void testJoinTXTRecord_stringsWithWhitespace() throws TextParseException {
    Name name = Name.fromString("test.example.com.");
    TXTRecord record = new TXTRecord(name, DClass.IN, 300,
        Arrays.asList("  part1  ", "  part2  "));
    Assert.assertEquals("part1part2", LookUpTxt.joinTXTRecord(record));
  }

  @Test(expected = TextParseException.class)
  public void testLookUpTxt_invalidName_throwsTextParseException()
      throws TextParseException, UnknownHostException {
    // A label exceeding 63 characters is invalid per RFC 1035
    String invalidLabel = new String(new char[64]).replace('\0', 'a');
    LookUpTxt.lookUpTxt(invalidLabel + ".example.com");
  }

  @Test(expected = TextParseException.class)
  public void testLookUpTxt_hashAndDomain_invalidName_throwsTextParseException()
      throws TextParseException, UnknownHostException {
    // lookUpTxt(hash, domain) delegates to lookUpTxt(hash + "." + domain)
    // so an invalid hash label should propagate the same exception
    String invalidHash = new String(new char[64]).replace('\0', 'a');
    LookUpTxt.lookUpTxt(invalidHash, "example.com");
  }
}
