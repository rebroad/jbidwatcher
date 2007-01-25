package com.jbidwatcher.auction;

import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.config.JConfigTab;

import junit.framework.TestCase;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.FileNotFoundException;

public class MockAuctionServer extends AuctionServer {
  public MockAuctionServer() {
    siteId = "testBay";
  }

  private static HashMap mCalled = new HashMap();
  private static final Integer ONE = new Integer(1);

  private void addCall(String method) {
    Object exists = mCalled.get(method);
    if(exists != null) {
      Integer i = (Integer)exists;
      i = new Integer(i.intValue()+1);
      mCalled.put(method, i);
    } else {
      mCalled.put(method, ONE);
    }
  }

  public static void dumpCalls() {
    for (Iterator it = mCalled.keySet().iterator(); it.hasNext();) {
      String key = (String)it.next();
      System.err.println(key + " - " + mCalled.get(key));
    }
  }

  public StringBuffer getAuctionViaAffiliate(CookieJar cj, AuctionEntry ae, String id) throws CookieJar.CookieException {
    addCall("getAuctionViaAffiliate");
    StringBuffer sb = null;
    try {
      sb = getAuction(getURLFromItem(id));
    } catch (FileNotFoundException e) {
      TestCase.fail(e.getMessage());
    }
    return sb;
  }

  public int buy(AuctionEntry ae, int quantity) {
    addCall("buy");
    return BID_BOUGHT_ITEM;
  }

  public long getSnipePadding() {
    addCall("getSnipePadding");
    return 100;
  }

  public String extractIdentifierFromURLString(String urlStyle) {
    addCall("extractIdentifierFromURLString");
    return "12345678";
  }

  public CookieJar getNecessaryCookie(boolean force) {
    addCall("getNecessaryCookie");
    return null;
  }

  public CookieJar getSignInCookie(CookieJar old_cj) {
    addCall("getSignInCookie");
    return null;
  }

  public void safeGetAffiliate(CookieJar cj, AuctionEntry inEntry) throws CookieJar.CookieException {
    TestCase.fail("Unexpected function called!");
  }

  public JHTML.Form getBidForm(CookieJar cj, AuctionEntry inEntry, Currency inCurr, int inQuant) throws BadBidException {
    TestCase.fail("Unexpected function called!");
    return null;
  }

  public int bid(AuctionEntry inEntry, Currency inBid, int inQuantity) {
    addCall("bid");
    return BID_WINNING;
  }

  public int placeFinalBid(CookieJar cj, JHTML.Form bidForm, AuctionEntry inEntry, Currency inBid, int inQuantity) {
    addCall("placeFinalBid");
    return BID_WINNING;
  }

  public boolean checkIfIdentifierIsHandled(String auctionId) {
    addCall("checkIfIdentifierIsHandled");
    return true;
  }

  public void establishMenu() {
    TestCase.fail("Unexpected function called!");
  }

  public JConfigTab getConfigurationTab() {
    TestCase.fail("Unexpected function called!");
    return null;
  }

  public void cancelSearches() {
    TestCase.fail("Unexpected function called!");
  }

  public void addSearches(SearchManagerInterface searchManager) {
    TestCase.fail("Unexpected function called!");
  }

  public Currency getMinimumBidIncrement(Currency currentBid, int bidCount) {
    addCall("getMinimumBidIncrement");
    return Currency.getCurrency("$1.00");
  }

  public boolean isHighDutch(AuctionEntry inAE) {
    addCall("isHighDutch");
    return false;
  }

  public void updateHighBid(AuctionEntry ae) {
    TestCase.fail("Unexpected function called!");
  }

  protected Date getOfficialTime() {
    addCall("getOfficialTime");
    return new Date();
  }

  public String getBrowsableURLFromItem(String itemID) {
    TestCase.fail("Unexpected function called!");
    return "http://www.jbidwatcher.com";
  }

  public String getStringURLFromItem(String itemID) {
    TestCase.fail("Unexpected function called!");
    return "http://www.jbidwatcher.com";
  }

  protected URL getURLFromItem(String itemID) {
    addCall("getURLFromItem");
    try {
      return new URL("http://www.jbidwatcher.com");
    } catch (MalformedURLException e) {
      TestCase.fail(e.getMessage());
      e.printStackTrace();
    }
    return null;
  }

  public SpecificAuction getNewSpecificAuction() {
    addCall("getNewSpecificAuction");
    return new MockSpecificAuction(new MockAuctionInfo());
  }

  public boolean doHandleThisSite(URL checkURL) {
    addCall("doHandleThisSite");
    return true;
  }

  public boolean checkIfSiteNameHandled(String serverName) {
    addCall("checkIfSiteNameHandled");
    return true;
  }
}