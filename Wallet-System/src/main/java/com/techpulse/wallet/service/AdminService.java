package com.techpulse.wallet.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.techpulse.wallet.dto.AdminWalletResponse;
import com.techpulse.wallet.dto.AdminTransactionResponse;

public interface AdminService {

    Page<AdminWalletResponse> getAllWallets(Pageable pageable);

    Page<AdminTransactionResponse> getAllTransactions(Pageable pageable);
}