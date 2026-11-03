package com.techpulse.wallet.serviceImpl;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.techpulse.wallet.dto.AddMoneyRequest;
import com.techpulse.wallet.dto.TransferRequest;
import com.techpulse.wallet.dto.WalletResponse;
import com.techpulse.wallet.dto.WalletTransactionResponse;

import com.techpulse.wallet.entity.Wallet;
import com.techpulse.wallet.entity.WalletTransaction;

import com.techpulse.wallet.enums.TransactionStatus;
import com.techpulse.wallet.enums.TransactionType;

import com.techpulse.wallet.repository.UserRepository;
import com.techpulse.wallet.repository.WalletRepository;
import com.techpulse.wallet.repository.WalletTransactionRepository;

@Service
public class WalletServiceImpl implements
        com.techpulse.wallet.service.WalletService {

    private final WalletRepository walletRepository;

    private final WalletTransactionRepository transactionRepository;

    private final UserRepository userRepository;

    public WalletServiceImpl(
            WalletRepository walletRepository,
            WalletTransactionRepository transactionRepository,
            UserRepository userRepository) {

        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------
    // 1. GET WALLET
    // -------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWallet(String email) {

        Wallet wallet = walletRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet not found"));

        return mapWalletResponse(wallet);
    }

    // -------------------------------------------------
    // 2. ADD MONEY
    // -------------------------------------------------

    @Override
    @Transactional
    public WalletTransactionResponse addMoney(
            String email,
            String idempotencyKey,
            AddMoneyRequest request) {

        validateIdempotencyKey(idempotencyKey);

        // Lock the authenticated user's wallet.
        Wallet wallet = walletRepository.findByUserEmailForUpdate(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet not found"));

        // Check whether this request was already processed.
        Optional<WalletTransaction> existing =
                transactionRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {

            WalletTransaction previous = existing.get();

            if (previous.getType() != TransactionType.ADD
                    || previous.getToWallet() == null
                    || !previous.getToWallet().getId().equals(wallet.getId())
                    || previous.getAmount().compareTo(request.getAmount()) != 0) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency key was already used for another request");
            }

            return mapTransactionResponse(previous);
        }

        // Add money.
        wallet.setBalance(
                wallet.getBalance().add(request.getAmount()));

        walletRepository.save(wallet);

        // Create transaction record.
        WalletTransaction transaction = new WalletTransaction();

        transaction.setFromWallet(null);
        transaction.setToWallet(wallet);
        transaction.setAmount(request.getAmount());
        transaction.setType(TransactionType.ADD);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setIdempotencyKey(idempotencyKey);

        transaction = transactionRepository.saveAndFlush(transaction);

        return mapTransactionResponse(transaction);
    }

    // -------------------------------------------------
    // 3. TRANSFER MONEY
    // -------------------------------------------------

    @Override
    @Transactional
    public WalletTransactionResponse transferMoney(
            String email,
            String idempotencyKey,
            TransferRequest request) {

        validateIdempotencyKey(idempotencyKey);

        String receiverEmail =
                request.getReceiverEmail().trim().toLowerCase();

        if (email.equalsIgnoreCase(receiverEmail)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot transfer money to your own wallet");
        }

        // Get sender and receiver wallet IDs first.
        Wallet senderReference = walletRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sender wallet not found"));

        Wallet receiverReference = walletRepository
                .findByUserEmail(receiverEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Receiver wallet not found"));

        Long senderId = senderReference.getId();
        Long receiverId = receiverReference.getId();

        // Always lock wallets in ascending ID order.
        // This reduces the risk of deadlocks.
        Long firstId = Math.min(senderId, receiverId);
        Long secondId = Math.max(senderId, receiverId);

        Wallet firstWallet = walletRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet not found"));

        Wallet secondWallet = walletRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet not found"));

        Wallet sender = senderId.equals(firstId)
                ? firstWallet : secondWallet;

        Wallet receiver = receiverId.equals(firstId)
                ? firstWallet : secondWallet;

        // Check idempotency after locking the wallets.
        Optional<WalletTransaction> existing =
                transactionRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {

            WalletTransaction previous = existing.get();

            if (previous.getType() != TransactionType.TRANSFER
                    || previous.getFromWallet() == null
                    || previous.getToWallet() == null
                    || !previous.getFromWallet().getId().equals(senderId)
                    || !previous.getToWallet().getId().equals(receiverId)
                    || previous.getAmount().compareTo(request.getAmount()) != 0) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency key was already used for another request");
            }

            return mapTransactionResponse(previous);
        }

        // Check sender balance.
        if (sender.getBalance().compareTo(request.getAmount()) < 0) {

            WalletTransaction failedTransaction =
                    createTransferTransaction(
                            sender,
                            receiver,
                            request.getAmount(),
                            idempotencyKey,
                            TransactionStatus.FAILED);

            failedTransaction =
                    transactionRepository.saveAndFlush(failedTransaction);

            return mapTransactionResponse(failedTransaction);
        }

        // Deduct money from sender.
        sender.setBalance(
                sender.getBalance().subtract(request.getAmount()));

        // Add money to receiver.
        receiver.setBalance(
                receiver.getBalance().add(request.getAmount()));

        walletRepository.save(sender);
        walletRepository.save(receiver);

        // Create successful transaction.
        WalletTransaction transaction =
                createTransferTransaction(
                        sender,
                        receiver,
                        request.getAmount(),
                        idempotencyKey,
                        TransactionStatus.SUCCESS);

        transaction = transactionRepository.saveAndFlush(transaction);

        return mapTransactionResponse(transaction);
    }

    // -------------------------------------------------
    // 4. GET TRANSACTION HISTORY
    // -------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(
            String email,
            Pageable pageable) {

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"));

        return transactionRepository
                .findByFromWallet_User_IdOrToWallet_User_Id(
                        user.getId(),
                        user.getId(),
                        pageable)
                .map(this::mapTransactionResponse);
    }

    // -------------------------------------------------
    // HELPER METHODS
    // -------------------------------------------------

    private void validateIdempotencyKey(String key) {

        if (key == null || key.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key header is required");
        }

        if (key.length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency key must not exceed 100 characters");
        }
    }

    private WalletTransaction createTransferTransaction(
            Wallet sender,
            Wallet receiver,
            BigDecimal amount,
            String key,
            TransactionStatus status) {

        WalletTransaction transaction = new WalletTransaction();

        transaction.setFromWallet(sender);
        transaction.setToWallet(receiver);
        transaction.setAmount(amount);
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(status);
        transaction.setIdempotencyKey(key);

        return transaction;
    }

    private WalletResponse mapWalletResponse(Wallet wallet) {

        return new WalletResponse(
                wallet.getId(),
                wallet.getUser().getEmail(),
                wallet.getBalance(),
                wallet.getVersion());
    }

    private WalletTransactionResponse mapTransactionResponse(
            WalletTransaction transaction) {

        Long fromWalletId = transaction.getFromWallet() == null
                ? null
                : transaction.getFromWallet().getId();

        Long toWalletId = transaction.getToWallet() == null
                ? null
                : transaction.getToWallet().getId();

        return new WalletTransactionResponse(
                transaction.getId(),
                fromWalletId,
                toWalletId,
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getIdempotencyKey(),
                transaction.getCreatedAt());
    }
} 