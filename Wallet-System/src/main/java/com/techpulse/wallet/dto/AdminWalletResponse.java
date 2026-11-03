package com.techpulse.wallet.dto;

import java.math.BigDecimal;

public class AdminWalletResponse {

    private Long walletId;
    private Long userId;
    private String email;
    private BigDecimal balance;
    private Long version;

    public AdminWalletResponse() {
    }

    public AdminWalletResponse(Long walletId, Long userId,
                               String email, BigDecimal balance,
                               Long version) {
        this.walletId = walletId;
        this.userId = userId;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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