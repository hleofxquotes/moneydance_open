package com.moneydance.modules.features.cmdline;

import com.moneydance.apps.md.controller.*;
import com.moneydance.apps.md.model.*;
import com.moneydance.util.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.text.SimpleDateFormat;
import java.net.URLDecoder;

/** This is the main class of an extension that processes command-line
    commands submitted via the -invoke_and_quit method.  The invoke(String)
    method is called with the string "XXX:a=b&c=d" in the following example
    command-line:
    moneydance -invoke_and_quit=moneydance:fmodule:cmdline:XXX:a=b&c=d

    The currently implemented actions (the XXX part) are:
      addtxn

    The parameters for the command line should be:

      date = [date of transaction as mm/dd/yyyy (default=current date)]
      account = [name of bank account (default=Checking]
      category = [name of income/expense account (default=Misc)]
      checknum = [check number (default=blank)]
      payee = [the payee/description for the transaction (default=blank)]
      memo = [the memo for the transaction (default=blank)]
      amount = [amount of transaction (default=0)]
*/
public class Main
  extends FeatureModule
{
  
  public void init() {
  }
  
  /** Process an invocation of this module with the given URI */
  public void invoke(String uri) {
    String command = uri;
    String parameters = "";
    int colonIdx = uri.indexOf(':');
    Params params;
    if(colonIdx>=0) {
      command = uri.substring(0, colonIdx);
      params = new Params(uri.substring(colonIdx+1));
    } else {
      params = new Params("");
    }

    if(command.equals("addtxn")) {
      addTransaction(params);
    }
  }

  public String getName() {
    return "Command line functions";
  }

  private void addTransaction(Params params) {
    RootAccount root = getContext().getRootAccount();
    if(root==null) {
      JOptionPane.showMessageDialog(null, "Unable to add transaction - no file is open",
                                    "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    Calendar cal = Calendar.getInstance();
    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
    String bankAcctStr = params.get("account", "Checking");
    String categoryStr = params.get("category", "Misc");
    String checkNum = params.get("checknum", "");
    String payee = params.get("payee", "");
    String memo = params.get("memo", "");
    String dateStr = params.get("date", "");

    Account bankAcct = root.getAccountByName(bankAcctStr);
    if(bankAcct==null ||
       (bankAcct.getAccountType()!=Account.ACCOUNT_TYPE_BANK &&
        bankAcct.getAccountType()!=Account.ACCOUNT_TYPE_CREDIT_CARD)) {
      JOptionPane.showMessageDialog(null, "Unable to add transaction - account \"+bankAcctStr+\" not found",
                                    "Error", JOptionPane.ERROR_MESSAGE);
      System.exit(-1);
    }

    long amount = bankAcct.getCurrencyType().parse(params.get("amount", ""),'.');
    Account category = getCategory(categoryStr, root);
    
    Date date = new Date();
    try {
      date = dateFormat.parse(dateStr);
    } catch (Exception e) { }
    date = Util.stripTimeFromDate(date);

    
    // bankAcct, category, date, payee, memo, checknum, amount
    String message = "<html><body>Would you like to add the following transaction?<br><br>";
    message += "&nbsp;&nbsp;<b>Date:</b> "+dateFormat.format(date)+"<br>";
    message += "&nbsp;&nbsp;<b>Account:</b> "+bankAcct.getFullAccountName()+"<br>";
    message += "&nbsp;&nbsp;<b>Check#:</b> "+checkNum+"<br>";
    message += "&nbsp;&nbsp;<b>Payee:</b> "+payee+"<br>";
    message += "&nbsp;&nbsp;<b>Amount:</b> "+bankAcct.getCurrencyType().formatFancy(amount, '.')+"<br>";
    message += "&nbsp;&nbsp;<b>Category:</b> "+category.getFullAccountName()+"<br>";
    message += "&nbsp;&nbsp;<b>Memo:</b> "+memo+"<br>";
    message += "</body></html>";
    int result = JOptionPane.
      showConfirmDialog(null,
                        message,
                        "Confirm New Transaction",
                        JOptionPane.YES_NO_OPTION);

    if(result==JOptionPane.YES_OPTION) {
      ParentTxn txn = new ParentTxn(date.getTime(), date.getTime(),
                                    System.currentTimeMillis(),
                                    checkNum, bankAcct,
                                    payee, memo,
                                    -1, AbstractTxn.STATUS_UNRECONCILED);
      txn.addSplit(new SplitTxn(txn, -amount, 1.0, category, payee,
                                -1, AbstractTxn.STATUS_UNRECONCILED));
      txn.setTag("cmdline_addtxn_uri", params.getParamString());
      root.getTransactionSet().addNewTxn(txn);
      if(!((com.moneydance.apps.md.controller.Main)getContext()).saveCurrentAccount()) {
        System.out.println("ERROR: Unable to add transaction - account \""+bankAcctStr+"\" not found");
        JOptionPane.showMessageDialog(null, "Unable to save transaction!",
                                      "Error", JOptionPane.ERROR_MESSAGE);
        System.exit(-1);
      }
    }
  }

  private class Params {
    private ParamPair params[];
    private String paramString;
    
    Params(String paramStr) {
      this.paramString = paramStr;
      String paramArray[] = StringUtils.split(paramStr, '&');
      params = new ParamPair[paramArray.length];
      for(int i=0; i<paramArray.length; i++) {
        params[i] = new ParamPair(paramArray[i]);
      }
    }

    String getParamString() {
      return paramString;
    }

    String get(String key, String defaultVal) {
      for(int i=params.length-1; i>=0; i--) {
        if(params[i].key.equals(key))
          return params[i].val;
      }
      return defaultVal;
    }

  }

  private class ParamPair {
    String key;
    String val;

    ParamPair(String param) {
      int eqIdx = param.indexOf('=');
      if(eqIdx<0) {
        key = URLDecoder.decode(param);
        val = "";
      } else {
        key = URLDecoder.decode(param.substring(0, eqIdx));
        val = URLDecoder.decode(param.substring(eqIdx+1));
      }
    }
    
  }

  /** Find the expense account with the given name as a sub-account of the
   * given account.  If there is no expense account with that name, create
   * one and return it.  Sub-accounts are delimited by ':' (eg "Auto:Loan"),
   */
  private Account getCategory(String accountName, Account parentAccount) {
    if(accountName.startsWith(":") &&
       parentAccount.getAccountType()==Account.ACCOUNT_TYPE_ROOT) {
      accountName = accountName.substring(1);
    }

    int parentType = parentAccount.getAccountType();
    int colIndex = accountName.indexOf(':');
    String restOfAcctName;
    String thisAcctName;
    if(colIndex>=0) {
      restOfAcctName = accountName.substring(colIndex+1);
      thisAcctName = accountName.substring(0,colIndex);
    } else {
      restOfAcctName = null;
      thisAcctName = accountName;
    }

    // find an existing account
    for(int i=0; i<parentAccount.getSubAccountCount(); i++) {
      Account subAcct = parentAccount.getSubAccount(i);
      int subAcctType = subAcct.getAccountType();
      if(!(subAcctType==Account.ACCOUNT_TYPE_BANK ||
           subAcctType==Account.ACCOUNT_TYPE_CREDIT_CARD ||
           subAcctType==Account.ACCOUNT_TYPE_EXPENSE ||
           subAcctType==Account.ACCOUNT_TYPE_INCOME)) {
        continue;
      }
      if(subAcct.getAccountName().equalsIgnoreCase(thisAcctName)) {
        if(restOfAcctName==null) {
          return subAcct;
        } else {
          return getCategory(restOfAcctName, subAcct);
        }
      }
    }

    // no existing sub-account was found... create one
    Account newAccount;
    switch(parentType) {
      case Account.ACCOUNT_TYPE_INCOME:
        newAccount =
          new IncomeAccount(thisAcctName, -1, parentAccount.getCurrencyType(),
                            null, null, parentAccount);
        break;
      case Account.ACCOUNT_TYPE_BANK:
        newAccount =
          new BankAccount(thisAcctName, -1, parentAccount.getCurrencyType(),
                          null, null, parentAccount, 0);
        break;
      case Account.ACCOUNT_TYPE_CREDIT_CARD:
        newAccount =
          new CreditCardAccount(thisAcctName, -1, parentAccount.getCurrencyType(),
                                null, null, parentAccount, 0);
        break;
      case Account.ACCOUNT_TYPE_ROOT:
      case Account.ACCOUNT_TYPE_EXPENSE:
      default:
        newAccount =
          new ExpenseAccount(thisAcctName, -1, parentAccount.getCurrencyType(),
                             null, null, parentAccount);
        break; 
    }
    parentAccount.addSubAccount(newAccount);
    if(restOfAcctName==null) {
      return newAccount;
    } else {
      return getCategory(restOfAcctName, newAccount);
    }
  }

  

}
