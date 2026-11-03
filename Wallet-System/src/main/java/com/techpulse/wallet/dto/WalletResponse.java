package com.techpulse.wallet.dto;

import java.math.BigDecimal;

public class WalletResponse {

    private Long walletId;
    private String email;
    private BigDecimal balance;
    private Long version;

    public WalletResponse() {
    }

    public WalletResponse(
            Long walletId,
            String email,
            BigDecimal balance,
            Long version) {

        this.walletId = walletId;
        this.email = email;
        this.balance = balance;
        this.version = version;
    }

    public Long getWalletId() {
        return walletId;
    }

    public void setWalletId(Long walletId) {
        this.walletId = walletId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}