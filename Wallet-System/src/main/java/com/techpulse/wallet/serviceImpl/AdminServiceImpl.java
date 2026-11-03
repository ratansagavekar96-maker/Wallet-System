package com.techpulse.wallet.serviceImpl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.techpulse.wallet.dto.AdminWalletResponse;
import com.techpulse.wallet.dto.AdminTransactionResponse;
import com.techpulse.wallet.entity.Wallet;
import com.techpulse.wallet.entity.WalletTransaction;
import com.techpulse.wallet.repository.WalletRepository;
import com.techpulse.wallet.repository.WalletTransactionRepository;
import com.techpulse.wallet.service.AdminService;

@Service
public class AdminServiceImpl implements AdminService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    public AdminServiceImpl(
            WalletRepository walletRepository,
            WalletTransactionRepository transactionRepository) {

        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    // Get all wallets
    @Override
    @Transactional(readOnly = true)
    public Page<AdminWalletResponse> getAllWallets(Pageable pageable) {

        Page<Wallet> wallets = walletRepository.findAll(pageable);

        return wallets.map(this::mapWalletResponse);
    }

    // Get all transactions
    @Override
    @Transactional(readOnly = true)
    public Page<AdminTransactionResponse> getAllTransactions(
            Pageable pageable) {

        Page<WalletTransaction> transactions =
                transactionRepository.findAll(pageable);

        return transactions.map(this::mapTransactionResponse);
    }

    // Convert Wallet entity into DTO
    private AdminWalletResponse mapWalletResponse(Wallet wallet) {

        return new AdminWalletResponse(
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getUser().getEmail(),
                wallet.getBalance(),
                wallet.getVersion()
        );
    }

    // Convert WalletTransaction entity into DTO
    private AdminTransactionResponse mapTransactionResponse(
            WalletTransaction transaction) {

        return new AdminTransactionResponse(
                transaction.getId(),

                transaction.getFromWallet() != null
                        ? transaction.getFromWallet().getId()
                        : null,

                transaction.getToWallet() != null
                        ? transaction.getToWallet().getId()
                        : null,

                transaction.getFromWallet() != null
                        ? transaction.getFromWallet().getUser().getEmail()
                        : null,

                transaction.getToWallet() != null
                        ? transaction.getToWallet().getUser().getEmail()
                        : null,

                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getIdempotencyKey(),
                transaction.getCreatedAt()
        );
    }
}