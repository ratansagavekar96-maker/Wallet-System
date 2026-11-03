package com.techpulse.wallet.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.techpulse.wallet.dto.AddMoneyRequest;
import com.techpulse.wallet.dto.TransferRequest;
import com.techpulse.wallet.dto.WalletResponse;
import com.techpulse.wallet.dto.WalletTransactionResponse;

import com.techpulse.wallet.enums.TransactionStatus;

import com.techpulse.wallet.service.WalletService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/wallet")
@Validated
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    // -----------------------------------------
    // GET WALLET
    // -----------------------------------------

    @GetMapping
    public ResponseEntity<WalletResponse> getWallet(
            @AuthenticationPrincipal UserDetails userDetails) {

        WalletResponse response =
                walletService.getWallet(userDetails.getUsername());

        return ResponseEntity.ok(response);
    }

    // -----------------------------------------
    // ADD MONEY
    // -----------------------------------------

    @PostMapping("/add")
    public ResponseEntity<WalletTransactionResponse> addMoney(

            @AuthenticationPrincipal UserDetails userDetails,

            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody AddMoneyRequest request) {

        WalletTransactionResponse response =
                walletService.addMoney(
                        userDetails.getUsername(),
                        idempotencyKey,
                        request);

        return transactionResponse(response);
    }

    // -----------------------------------------
    // TRANSFER MONEY
    // -----------------------------------------

    @PostMapping("/transfer")
    public ResponseEntity<WalletTransactionResponse> transferMoney(

            @AuthenticationPrincipal UserDetails userDetails,

            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody TransferRequest request) {

        WalletTransactionResponse response =
                walletService.transferMoney(
                        userDetails.getUsername(),
                        idempotencyKey,
                        request);

        return transactionResponse(response);
    }

    // -----------------------------------------
    // GET TRANSACTION HISTORY
    // -----------------------------------------

    @GetMapping("/transactions")
    public ResponseEntity<Page<WalletTransactionResponse>> getTransactions(

            @AuthenticationPrincipal UserDetails userDetails,

            @RequestParam(defaultValue = "0") int page,

            @RequestParam(defaultValue = "10") int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pagination parameters");
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<WalletTransactionResponse> response =
                walletService.getTransactions(
                        userDetails.getUsername(),
                        pageable);

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<WalletTransactionResponse> transactionResponse(
            WalletTransactionResponse response) {

        if (response.getStatus() == TransactionStatus.FAILED) {
            return ResponseEntity.unprocessableEntity().body(response);
        }

        return ResponseEntity.ok(response);
    }
}