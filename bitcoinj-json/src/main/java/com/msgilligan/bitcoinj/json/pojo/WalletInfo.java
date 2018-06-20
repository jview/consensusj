package com.msgilligan.bitcoinj.json.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.bitcoinj.core.Coin;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletInfo {
    private String walletname;
    private Integer walletversion;
    private Coin balance;
    private Coin unconfirmed_balance;
    private Coin immature_balance;
    private Integer txcount;
    private Integer keypoololdest;
    private Integer keypoolsize;
    private Integer keypoolsize_hd_internal;
    private Coin paytxfee;
    private String hdmasterkeyid;

    public String getWalletname() {
        return walletname;
    }

    public void setWalletname(String walletname) {
        this.walletname = walletname;
    }

    public Integer getWalletversion() {
        return walletversion;
    }

    public void setWalletversion(Integer walletversion) {
        this.walletversion = walletversion;
    }

    public Coin getBalance() {
        return balance;
    }

    public void setBalance(Coin balance) {
        this.balance = balance;
    }

    public Coin getUnconfirmed_balance() {
        return unconfirmed_balance;
    }

    public void setUnconfirmed_balance(Coin unconfirmed_balance) {
        this.unconfirmed_balance = unconfirmed_balance;
    }

    public Coin getImmature_balance() {
        return immature_balance;
    }

    public void setImmature_balance(Coin immature_balance) {
        this.immature_balance = immature_balance;
    }

    public Integer getTxcount() {
        return txcount;
    }

    public void setTxcount(Integer txcount) {
        this.txcount = txcount;
    }

    public Integer getKeypoololdest() {
        return keypoololdest;
    }

    public void setKeypoololdest(Integer keypoololdest) {
        this.keypoololdest = keypoololdest;
    }

    public Integer getKeypoolsize() {
        return keypoolsize;
    }

    public void setKeypoolsize(Integer keypoolsize) {
        this.keypoolsize = keypoolsize;
    }

    public Integer getKeypoolsize_hd_internal() {
        return keypoolsize_hd_internal;
    }

    public void setKeypoolsize_hd_internal(Integer keypoolsize_hd_internal) {
        this.keypoolsize_hd_internal = keypoolsize_hd_internal;
    }

    public Coin getPaytxfee() {
        return paytxfee;
    }

    public void setPaytxfee(Coin paytxfee) {
        this.paytxfee = paytxfee;
    }

    public String getHdmasterkeyid() {
        return hdmasterkeyid;
    }

    public void setHdmasterkeyid(String hdmasterkeyid) {
        this.hdmasterkeyid = hdmasterkeyid;
    }
}
