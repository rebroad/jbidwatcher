package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.auction.server.LoginManager;
import com.jbidwatcher.auction.server.BadBidException;
import com.jbidwatcher.auction.server.AuctionServerInterface;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.Auctions;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.Externalized;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.config.JBConfig;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.Constants;

import java.net.URLConnection;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
* User: mrs
* Date: Feb 26, 2007
* Time: 1:38:12 AM
* To change this template use File | Settings | File Templates.
*/
public class ebayBidder {
  private static final String srcMatch = "(?i)src=\"([^\"]*?)\"";
  private static Pattern srcPat = Pattern.compile(srcMatch);

  private LoginManager mLogin;
  private HashMap<String, Integer> mResultHash = null;
  private String mBidResultRegex = null;

  private Pattern mFindBidResult;

  public ebayBidder(LoginManager login) {
    mLogin = login;
    /**
     * Build a simple hashtable of results that bidding might get.
     * Not the greatest solution, but it's working okay.  A better one
     * would be great.
     */
    if(mResultHash == null) {
      mResultHash = new HashMap<String, Integer>();
      mResultHash.put("you are not permitted to bid on their listings.", AuctionServer.BID_ERROR_BANNED);
      mResultHash.put("the item is no longer available because the auction has ended.", AuctionServer.BID_ERROR_ENDED);
      mResultHash.put("cannot proceed", AuctionServer.BID_ERROR_CANNOT);
      mResultHash.put("problem with bid amount", AuctionServer.BID_ERROR_AMOUNT);
      mResultHash.put("your bid must be at least ", AuctionServer.BID_ERROR_TOO_LOW);
      mResultHash.put("you have been outbid by another bidder", AuctionServer.BID_ERROR_OUTBID);
      mResultHash.put("your bid is confirmed!", AuctionServer.BID_DUTCH_CONFIRMED);
      mResultHash.put("you are bidding on this multiple item auction", AuctionServer.BID_DUTCH_CONFIRMED);
      mResultHash.put("you are the high bidder on all items you bid on", AuctionServer.BID_DUTCH_CONFIRMED);
      mResultHash.put("you are the current high bidder", AuctionServer.BID_WINNING);
      mResultHash.put("you purchased the item", AuctionServer.BID_WINNING);
      mResultHash.put("the reserve price has not been met", AuctionServer.BID_ERROR_RESERVE_NOT_MET);
      mResultHash.put("your new total must be higher than your current total", AuctionServer.BID_ERROR_TOO_LOW_SELF);
      mResultHash.put("this exceeds or is equal to your current bid", AuctionServer.BID_ERROR_TOO_LOW_SELF);
      mResultHash.put("you bought this item", AuctionServer.BID_BOUGHT_ITEM);
      mResultHash.put("you committed to buy", AuctionServer.BID_BOUGHT_ITEM);
      mResultHash.put("congratulations! you won!", AuctionServer.BID_BOUGHT_ITEM);
      mResultHash.put("account suspended", AuctionServer.BID_ERROR_ACCOUNT_SUSPENDED);
      mResultHash.put("to enter a higher maximum bid, please enter", AuctionServer.BID_ERROR_TOO_LOW_SELF);
      mResultHash.put("you are registered in a country to which the seller doesn.t ship.", AuctionServer.BID_ERROR_WONT_SHIP);
      mResultHash.put("this seller has set buyer requirements for this item and only sells to buyers who meet those requirements.", AuctionServer.BID_ERROR_REQUIREMENTS_NOT_MET);
      //      mResultHash.put("You are the current high bidder", new Integer(BID_SELFWIN));
    }

    //"If you want to submit another bid, your new total must be higher than your current total";
    StringBuffer superRegex = new StringBuffer("(");
    Iterator<String> it = mResultHash.keySet().iterator();
    while (it.hasNext()) {
      String key = it.next();
      superRegex.append(key);
      if(it.hasNext()) {
        superRegex.append('|');
      } else {
        superRegex.append(')');
      }
    }
    mBidResultRegex = new StringBuilder().append("(?i)").append(superRegex).toString();
    mFindBidResult = Pattern.compile(mBidResultRegex);
    mResultHash.put("sign in", AuctionServer.BID_ERROR_CANT_SIGN_IN);
  }

  public JHTML.Form getBidForm(CookieJar cj, AuctionEntry inEntry, com.jbidwatcher.util.Currency inCurr, int inQuant) throws BadBidException {
    String bidRequest = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidHost") + Externalized.getString("ebayServer.V3file");
    String bidInfo;
    if(inEntry.isDutch()) {
      bidInfo = Externalized.getString("ebayServer.bidCmd") + "&co_partnerid=" + Externalized.getString("ebayServer.itemCGI") + inEntry.getIdentifier() +
                "&fb=2" + Externalized.getString("ebayServer.quantCGI") + inQuant +
                Externalized.getString("ebayServer.bidCGI") + inCurr.getValue();
    } else {
      bidInfo = Externalized.getString("ebayServer.bidCmd") + "&co_partnerid=" + Externalized.getString("ebayServer.itemCGI") + inEntry.getIdentifier() + "&fb=2" +
                Externalized.getString("ebayServer.bidCGI") + inCurr.getValue();
    }
    StringBuffer loadedPage = null;
    JHTML htmlDocument = null;

    try {
      String pageName = bidRequest + '?' + bidInfo;
      boolean checked_signon = false;
      boolean checked_reminder = false;
      boolean done = false;
      boolean post = false;
      while (!done) {
        done = true;

        if(JConfig.debugging) inEntry.setLastStatus("Loading bid request...");
        URLConnection huc = cj.getAllCookiesFromPage(pageName, null, post);
        post = false;
        //  We failed to load, entirely.  Punt.
        if (huc == null) return null;

        loadedPage = Http.receivePage(huc);
        //  We failed to load.  Punt.
        if (loadedPage == null) return null;

        htmlDocument = new JHTML(loadedPage);
        JHTML.Form bidForm = htmlDocument.getFormWithInput("key");
        if(bidForm != null) {
          if(JConfig.debugging) inEntry.setLastStatus("Done loading bid request, got form...");
          return bidForm;
        }

        if(!checked_signon) {
          checked_signon = true;
          String signOn = htmlDocument.getFirstContent();
          if (signOn != null) {
            ErrorManagement.logDebug("Checking sign in as bid key load failed!");
            if (signOn.equalsIgnoreCase("Sign In")) {
              //  This means we somehow failed to keep the login in place.  Bad news, in the middle of a snipe.
              ErrorManagement.logDebug("Being prompted again for sign in, retrying.");
              if(JConfig.debugging) inEntry.setLastStatus("Not done loading bid request, got re-login request...");
              mLogin.resetCookie();
              mLogin.getNecessaryCookie(true);
              if(JConfig.debugging) inEntry.setLastStatus("Done re-logging in, retrying load bid request.");
              done = false;
            }
          }
        }

        if(!checked_reminder) {
          if(htmlDocument.grep("Buying.Reminder") != null) {
            JHTML.Form continueForm = htmlDocument.getFormWithInput("firedFilterId");
            if(continueForm != null) {
              inEntry.setLastStatus("Trying to 'continue' for the actual bid.");
              pageName = continueForm.getCGI();
              pageName = pageName.replaceFirst("%[A-F][A-F0-9]%A0", "%A0");
              post = false;
            }
            checked_reminder = true;
          }
        }
      }
    } catch (IOException e) {
      ErrorManagement.handleException("Failure to get the bid key!  BID FAILURE!", e);
    }

    if(htmlDocument != null) {
      String signOn = htmlDocument.getFirstContent();
      if(signOn != null && signOn.equalsIgnoreCase("Sign In")) throw new BadBidException("sign in", AuctionServerInterface.BID_ERROR_CANT_SIGN_IN);
      String errMsg = htmlDocument.grep(mBidResultRegex);
      if(errMsg != null) {
        Matcher bidMatch = mFindBidResult.matcher(errMsg);
        bidMatch.find();
        String matched_error = bidMatch.group().toLowerCase();
        throw new BadBidException(matched_error, mResultHash.get(matched_error));
      }
    }

    if(JConfig.debugging) inEntry.setLastStatus("Failed to bid. 'Show Last Error' from context menu to see the failure page from the bid attempt.");
    inEntry.setErrorPage(loadedPage);

    //  We don't recognize this error.  Damn.  Log it and freak.
    ErrorManagement.logFile(bidInfo, loadedPage);
    return null;
  }

  public int buy(AuctionEntry ae, int quantity) {
    String buyRequest = "http://offer.ebay.com/ws/eBayISAPI.dll?MfcISAPICommand=BinConfirm&fb=1&co_partnerid=&item=" + ae.getIdentifier() + "&quantity=" + quantity;

    //  This updates the cookies with the affiliate information, if it's not a test auction.
    if(ae.getTitle().toLowerCase().indexOf("test") == -1) {
      if(JBConfig.doAffiliate(ae.getEndDate().getTime())) {
        //  Ignoring the result as it's just called to trigger affiliate mode.
        ae.getServer().getAuction(ae, ae.getIdentifier());
      }
    }

    StringBuffer sb;

    try {
      sb = mLogin.getNecessaryCookie(false).getAllCookiesAndPage(buyRequest, null, false);
      JHTML doBuy = new JHTML(sb);
      JHTML.Form buyForm = doBuy.getFormWithInput("uiid");

      if (buyForm != null) {
        buyForm.delInput("BIN_button");
        CookieJar cj = mLogin.getNecessaryCookie(false);
        StringBuffer loadedPage = cj.getAllCookiesAndPage(buyForm.getCGI(), buyRequest, false);
        if (loadedPage == null) return AuctionServerInterface.BID_ERROR_CONNECTION;
        return handlePostBidBuyPage(cj, loadedPage, buyForm, ae);
      }
    } catch (CookieJar.CookieException ignored) {
      return AuctionServerInterface.BID_ERROR_CONNECTION;
    } catch (UnsupportedEncodingException uee) {
      ErrorManagement.handleException("UTF-8 not supported locally, can't URLEncode buy form.", uee);
      return AuctionServerInterface.BID_ERROR_CONNECTION;
    }

    ae.setErrorPage(sb);
    return AuctionServerInterface.BID_ERROR_UNKNOWN;
  }

  /**
   * @brief Perform the entire bidding process on an item.
   *
   * @param inEntry - The item to bid on.
   * @param inBid - The amount to bid.
   * @param inQuantity - The number of items to bid on.
   *
   * @return - A bid response code, or BID_ERROR_UNKNOWN if we can't
   * figure out what happened.
   */
  public int bid(AuctionEntry inEntry, com.jbidwatcher.util.Currency inBid, int inQuantity) {
    Auctions.startBlocking();
    if(JConfig.queryConfiguration("sound.enable", "false").equals("true")) MQFactory.getConcrete("sfx").enqueue("/audio/bid.mp3");

    try {
      //  If it's not closing within the next minute, then go ahead and try for the affiliate mode.
      if(inEntry.getEndDate().getTime() > (System.currentTimeMillis() + Constants.ONE_MINUTE)) {
        safeGetAffiliate(mLogin.getNecessaryCookie(false), inEntry);
      }
    } catch (CookieJar.CookieException ignore) {
      //  We don't care that much about connection refused in this case.
    }
    JHTML.Form bidForm;

    try {
      bidForm = getBidForm(mLogin.getNecessaryCookie(false), inEntry, inBid, inQuantity);
    } catch(BadBidException bbe) {
      Auctions.endBlocking();
      return bbe.getResult();
    }

    if (bidForm != null) {
      int rval = placeFinalBid(mLogin.getNecessaryCookie(false), bidForm, inEntry, inBid, inQuantity);
      Auctions.endBlocking();
      return rval;
    }
    ErrorManagement.logMessage("Bad/nonexistent key read in bid, or connection failure!");

    Auctions.endBlocking();
    return AuctionServerInterface.BID_ERROR_UNKNOWN;
  }

  public int placeFinalBid(CookieJar cj, JHTML.Form bidForm, AuctionEntry inEntry, com.jbidwatcher.util.Currency inBid, int inQuantity) {
    String bidRequest = Externalized.getString("ebayServer.protocol") + Externalized.getString("ebayServer.bidHost") + Externalized.getString("ebayServer.V3file");
    String bidInfo = Externalized.getString("ebayServer.bidCmd") + Externalized.getString("ebayServer.itemCGI") + inEntry.getIdentifier() +
        Externalized.getString("ebayServer.quantCGI") + inQuantity +
        Externalized.getString("ebayServer.bidCGI") + inBid.getValue();
    String bidURL = bidRequest + '?' + bidInfo;

    bidForm.delInput("BIN_button");
    StringBuffer loadedPage = null;

    //  This SHOULD be POSTed, but only works if sent with GET.
    try {
      if (JConfig.debugging) inEntry.setLastStatus("Submitting bid form.");
      loadedPage = cj.getAllCookiesAndPage(bidForm.getCGI(), bidURL, false);
      if (JConfig.debugging) inEntry.setLastStatus("Done submitting bid form.");
    } catch (UnsupportedEncodingException uee) {
      ErrorManagement.handleException("UTF-8 not supported locally, can't URLEncode bid form.", uee);
    } catch (CookieJar.CookieException ignored) {
      return AuctionServerInterface.BID_ERROR_CONNECTION;
    }

    if (loadedPage == null) {
      return AuctionServerInterface.BID_ERROR_CONNECTION;
    }
    return handlePostBidBuyPage(cj, loadedPage, bidForm, inEntry);
  }

  public void safeGetAffiliate(CookieJar cj, AuctionEntry inEntry) throws CookieJar.CookieException {
    //  This updates the cookies with the affiliate information, if it's not a test auction.
    if(inEntry.getTitle().toLowerCase().indexOf("test") == -1) {
      if(JBConfig.doAffiliate(inEntry.getEndDate().getTime())) {
        if(JConfig.debugging) inEntry.setLastStatus("Loading item...");
        inEntry.getServer().getAuction(inEntry, inEntry.getIdentifier());
        if(JConfig.debugging) inEntry.setLastStatus("Done loading item...");
      }
    }
  }

  private int handlePostBidBuyPage(CookieJar cj, StringBuffer loadedPage, JHTML.Form bidForm, AuctionEntry inEntry) {
    if(JConfig.debugging) inEntry.setLastStatus("Loading post-bid data.");
    JHTML htmlDocument = new JHTML(loadedPage);

    if(htmlDocument.grep("Buying.Reminder") != null) {
      JHTML.Form continueForm = htmlDocument.getFormWithInput("firedFilterId");
      if(continueForm != null) {
        try {
          inEntry.setLastStatus("Trying to 'continue' to the bid result page.");
          String cgi = continueForm.getCGI();
          //  For some reason, the continue page represents the currency as
          //  separated from the amount with a '0xA0' character.  When encoding,
          //  this becomes...broken somehow, and adds an extra character, which
          //  does not work when bidding.
          cgi = cgi.replaceFirst("%[A-F][A-F0-9]%A0", "%A0");
          URLConnection huc = cj.getAllCookiesFromPage(cgi, null, false);
          //  We failed to load, entirely.  Punt.
          if (huc == null) return AuctionServerInterface.BID_ERROR_CONNECTION;

          loadedPage = Http.receivePage(huc);
          //  We failed to load.  Punt.
          if (loadedPage == null) return AuctionServerInterface.BID_ERROR_CONNECTION;

          htmlDocument = new JHTML(loadedPage);
        } catch(Exception ignored) {
          return AuctionServerInterface.BID_ERROR_CONNECTION;
        }
      }
    }

    String errMsg = htmlDocument.grep(mBidResultRegex);
    if (errMsg != null) {
      Matcher bidMatch = mFindBidResult.matcher(errMsg);
      bidMatch.find();
      String matched_error = bidMatch.group().toLowerCase();
      Integer bidResult = mResultHash.get(matched_error);

      if(inEntry.getTitle().toLowerCase().indexOf("test") == -1) {
        if(JBConfig.doAffiliate(inEntry.getEndDate().getTime())) {
          List<String> images = htmlDocument.getAllImages();
          for (String tag : images) {
            Matcher tagMatch = srcPat.matcher(tag);
            if (tagMatch.find()) {
              int retry = 2;
              do {
                StringBuffer result = null;
                try {
                  result = mLogin.getNecessaryCookie(false).getAllCookiesAndPage(tagMatch.group(1), "http://offer.ebay.com/ws/eBayISAPI.dll", false);
                } catch (CookieJar.CookieException ignored) {
                  //  Ignore connection refused errors.
                }
                if (result == null) {
                  retry--;
                } else {
                  retry = 0;
                }
              } while (retry != 0);
            }
          }
        }
      }

      if(JConfig.debugging) inEntry.setLastStatus("Done loading post-bid data.");

      if(bidResult != null) return bidResult;
    }

    // Skipping the userID and Password, so this can be submitted as
    // debugging info.
    bidForm.setText("user", "HIDDEN");
    bidForm.setText("pass", "HIDDEN");
    String safeBidInfo = "";
    try {
      safeBidInfo = bidForm.getCGI();
    } catch(UnsupportedEncodingException uee) {
      ErrorManagement.handleException("UTF-8 not supported locally, can't URLEncode CGI for debugging.", uee);
    }

    if(JConfig.debugging) inEntry.setLastStatus("Failed to load post-bid data. 'Show Last Error' from context menu to see the failure page from the post-bid page.");
    inEntry.setErrorPage(loadedPage);

    ErrorManagement.logFile(safeBidInfo, loadedPage);
    return AuctionServerInterface.BID_ERROR_UNKNOWN;
  }
}