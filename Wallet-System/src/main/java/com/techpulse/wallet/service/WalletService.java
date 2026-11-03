package com.techpulse.wallet.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.techpulse.wallet.dto.AddMoneyRequest;
import com.techpulse.wallet.dto.TransferRequest;
import com.techpulse.wallet.dto.WalletResponse;
import com.techpulse.wallet.dto.WalletTransactionResponse;

public interface WalletService {

    WalletResponse getWallet(String email);

    WalletTransactionResponse addMoney(
            String email,
            String idempotencyKey,
            AddMoneyRequest request);

    WalletTransactionResponse transferMoney(
            String email,
            String idempotencyKey,
            TransferRequest request);

    Page<WalletTransactionResponse> getTransactions(
            String email,
            Pageable pageable);
}