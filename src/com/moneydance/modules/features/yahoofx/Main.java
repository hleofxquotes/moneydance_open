/************************************************************\
 *      Copyright (C) 2003 Reilly Technologies, L.L.C.      *
\************************************************************/

package com.moneydance.modules.features.yahoofx;

import com.moneydance.apps.md.controller.FeatureModule;
import com.moneydance.apps.md.controller.FeatureModuleContext;
import com.moneydance.apps.md.controller.ModuleUtil;
import com.moneydance.apps.md.controller.UserPreferences;

import com.moneydance.apps.md.model.*;
import com.moneydance.apps.md.controller.Util;
import com.moneydance.util.Constants;

import java.io.*;
import java.util.*;
import java.text.*;
import java.awt.*;

/** Pluggable module used to allow users to download exchange
 *  rates from finance.yahoo.com */
public class Main
  extends FeatureModule
{

  private boolean isUpdating = false;
  private static final int HISTORY_INTERVAL = 7; // snapshot minimum frequency, in days
  
  public void init() {
    // the first thing we will do is register this module to be invoked
    // via the application toolbar
    FeatureModuleContext context = getContext();
    context.registerFeature(this, "getrates", 
                            getIcon("icon-currencyexch"),
                            getName());
  }
  
  private Image getIcon(String action) {
    try {
      ClassLoader cl = getClass().getClassLoader();
      java.io.InputStream in = 
        cl.getResourceAsStream("/com/moneydance/modules/features/yahoofx/"+action+".png");
      if (in != null) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(1000);
        byte buf[] = new byte[256];
        int n = 0;
        while((n=in.read(buf, 0, buf.length))>=0)
          bout.write(buf, 0, n);
        return Toolkit.getDefaultToolkit().createImage(bout.toByteArray());
      }
    } catch (Throwable e) { }
    return null;
  }

  /** Process an invokation of this module with the given URI */
  public void invoke(String uri) {
    String command = uri;
    String parameters = "";
    int colonIdx = uri.indexOf(':');
    if(colonIdx>=0) {
      command = uri.substring(0, colonIdx);
      parameters = uri.substring(colonIdx+1);
    }

    if(command.equals("getrates")) {
      getRates();
    }
  }

  public String getName() {
    return "Exchange Rate Downloader";
  }

  private void getRates() {
    getContext().showURL("moneydance:setstatus:Connecting to finance.yahoo.com...");
    RootAccount root = getContext().getRootAccount();
    if(root==null) return;

    CurrencyTable ctable = root.getCurrencyTable();
    
    boolean success = false;
    try {
      Vector currenciesToCheck = new Vector();
      ctable.dumpCurrencies();
      for(Enumeration cenum=ctable.getAllValues(); cenum.hasMoreElements();) {
        CurrencyType ctype = (CurrencyType)cenum.nextElement();
        if(ctype.getCurrencyType()==CurrencyType.CURRTYPE_CURRENCY) {
          currenciesToCheck.addElement(ctype);
        }
      }
      for(int i=currenciesToCheck.size()-1; i>=0; i--) {
        getRate((CurrencyType)currenciesToCheck.elementAt(i), ctable);
      }
      success = true;
    } catch (Exception e) {
      getContext().showURL("moneydance:setstatus:Error downloading rates: "+e);
      success = false;
    } finally {
      ctable.fireCurrencyTableModified();
    }
    if(success) {
      getContext().showURL("moneydance:setstatus:Finished downloading exchange rates");
    }
  }


  private void getRate(CurrencyType currType, CurrencyTable cTable)
    throws Exception
  {
    // figure out the last date of an update...
    CurrencyType baseType = cTable.getBaseType();
    if(currType==baseType)
      return;
    
    getContext().showURL("moneydance:setstatus:Getting rate for "+
                         currType.getIDString()+" from finance.yahoo.com...");

    FXConnection fxConn = new FXConnection();
    FXConnection.ExchangeRate rateInfo =
      fxConn.getCurrentRate(currType.getIDString(), baseType.getIDString());
    if(rateInfo==null) {
      return;
    }
    
    double rate = rateInfo.getRate();
    if(rate <= 0.0)
      return;
    
    
    long lastDate = 0;
    for(int i=0; i<currType.getSnapshotCount(); i++) {
      CurrencyType.Snapshot snap = currType.getSnapshot(i);
      if(snap.getDate()>lastDate)
        lastDate = snap.getDate();
    }
    
    lastDate = Util.stripTimeFromDate(lastDate);
    long today = Util.stripTimeFromDate(System.currentTimeMillis());
    boolean addSnapshot = ((lastDate + 86400000 * HISTORY_INTERVAL) < today);

    if(addSnapshot)
      currType.setSnapshot(today, rate);
    currType.setUserRate(rate);
  }

  public FeatureModuleContext getExtContext() {
    return getContext();
  }
  
}

