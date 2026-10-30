package org.tron.p2p.dns.lookup;


import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Credibility;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.SOARecord;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;

public class LookUpTxtCacheTest {

  private static final int[] TYPES = {Type.TXT, Type.A, Type.AAAA};
  private static final long TTL = Integer.MAX_VALUE;
  private Cache previousCache;
  private Cache sharedCache;

  @Before
  public void setUp() {
    previousCache = Lookup.getDefaultCache(DClass.IN);
    sharedCache = new Cache(DClass.IN);
    Lookup.setDefaultCache(sharedCache, DClass.IN);
  }

  @After
  public void tearDown() {
    Lookup.setDefaultCache(previousCache, DClass.IN);
  }

  @Test
  public void longLivedNegativeAnswerDoesNotSuppressTheNextResolver() throws Exception {
    for (int type : TYPES) {
      Name name = Name.fromString("retry-" + type + ".example.invalid.");
      Lookup lookup = LookUpTxt.createLookup(name.toString(), type);
      StubResolver missing = new StubResolver(null);
      lookup.setResolver(missing);

      Assert.assertNull(lookup.run());
      Assert.assertEquals(Lookup.HOST_NOT_FOUND, lookup.getResult());
      Assert.assertEquals(1, missing.calls);

      Record healthy = answer(name, type, false);
      assertAnswer(lookup, new StubResolver(healthy), healthy);
    }
  }

  @Test
  public void positiveAnswerDoesNotPoisonLaterLookupsOrTheSharedCache() throws Exception {
    for (int type : TYPES) {
      Name name = Name.fromString("positive-" + type + ".example.invalid.");
      Record bad = answer(name, type, true);
      assertAnswer(LookUpTxt.createLookup(name.toString(), type), new StubResolver(bad), bad);

      Record healthy = answer(name, type, false);
      assertAnswer(LookUpTxt.createLookup(name.toString(), type),
          new StubResolver(healthy), healthy);
      Assert.assertTrue(sharedCache.lookupRecords(name, type, Credibility.NORMAL).isUnknown());
    }
  }

  @Test
  public void poisonedSharedCacheDoesNotAffectNewLookups() throws Exception {
    for (int type : TYPES) {
      for (boolean negative : new boolean[]{false, true}) {
        Name name = Name.fromString("shared-" + type + "-" + negative + ".example.invalid.");
        StubResolver poison = new StubResolver(negative ? null : answer(name, type, true));
        Message query = Message.newQuery(Record.newRecord(name, type, DClass.IN));
        sharedCache.addMessage(poison.send(query));
        Assert.assertFalse(sharedCache.lookupRecords(name, type, Credibility.NORMAL).isUnknown());

        Record healthy = answer(name, type, false);
        assertAnswer(LookUpTxt.createLookup(name.toString(), type),
            new StubResolver(healthy), healthy);

        Assert.assertSame(sharedCache, Lookup.getDefaultCache(DClass.IN));
        Assert.assertFalse(sharedCache.lookupRecords(name, type, Credibility.NORMAL).isUnknown());
      }
    }
  }

  private static void assertAnswer(Lookup lookup, StubResolver resolver, Record expected) {
    lookup.setResolver(resolver);
    Record[] records = lookup.run();
    Assert.assertNotNull(records);
    Assert.assertEquals(1, records.length);
    Assert.assertEquals(expected, records[0]);
    Assert.assertEquals(1, resolver.calls);
  }

  private static Record answer(Name name, int type, boolean bad) throws Exception {
    switch (type) {
      case Type.TXT:
        return new TXTRecord(name, DClass.IN, TTL, bad ? "forged TXT" : "healthy TXT");
      case Type.A:
        return new ARecord(name, DClass.IN, TTL,
            InetAddress.getByName(bad ? "192.0.2.66" : "192.0.2.1"));
      case Type.AAAA:
        return new AAAARecord(name, DClass.IN, TTL,
            InetAddress.getByName(bad ? "2001:db8::66" : "2001:db8::1"));
      default:
        throw new AssertionError("Unexpected record type: " + type);
    }
  }

  private static class StubResolver extends SimpleResolver {
    private final Record answer;
    private int calls;

    private StubResolver(Record answer) throws UnknownHostException {
      super("127.0.0.1");
      this.answer = answer;
    }

    @Override
    public Message send(Message query) {
      calls++;
      Message response = new Message(query.getHeader().getID());
      response.getHeader().setFlag(Flags.QR);
      response.getHeader().setFlag(Flags.AA);
      response.addRecord(query.getQuestion(), Section.QUESTION);
      if (answer == null) {
        response.getHeader().setRcode(Rcode.NXDOMAIN);
        Name zone = Name.fromConstantString("example.invalid.");
        response.addRecord(new SOARecord(zone, DClass.IN, TTL,
            Name.fromConstantString("ns.example.invalid."),
            Name.fromConstantString("admin.example.invalid."),
            1, 3600, 60, 86400, TTL), Section.AUTHORITY);
      } else {
        response.addRecord(answer, Section.ANSWER);
      }
      return response;
    }
  }
}
