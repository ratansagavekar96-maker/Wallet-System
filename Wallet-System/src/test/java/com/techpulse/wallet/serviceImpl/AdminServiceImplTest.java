package com.techpulse.wallet.serviceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.techpulse.wallet.entity.User;
import com.techpulse.wallet.entity.Wallet;
import com.techpulse.wallet.entity.WalletTransaction;
import com.techpulse.wallet.enums.Role;
import com.techpulse.wallet.enums.TransactionStatus;
import com.techpulse.wallet.enums.TransactionType;
import com.techpulse.wallet.repository.WalletRepository;
import com.techpulse.wallet.repository.WalletTransactionRepository;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @InjectMocks private AdminServiceImpl adminService;

    @Test
    void getAllWallets_shouldMapWalletEntitiesToAdminDtos() {
        User user = new User(5L, "admin-view@example.com", "encoded", Role.USER);
        Wallet wallet = new Wallet(55L, user, new BigDecimal("450.00"), 3L);
        when(walletRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(wallet)));

        Page<com.techpulse.wallet.dto.AdminWalletResponse> result =
                adminService.getAllWallets(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        var dto = result.getContent().get(0);
        assertEquals(55L, dto.getWalletId());
        assertEquals(5L, dto.getUserId());
        assertEquals("admin-view@example.com", dto.getEmail());
        assertEquals(new BigDecimal("450.00"), dto.getBalance());
        assertEquals(3L, dto.getVersion());
    }

    @Test
    void getAllWallets_shouldReturnEmptyPageWhenNoWalletsExist() {
        when(walletRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());

        Page<com.techpulse.wallet.dto.AdminWalletResponse> result =
                adminService.getAllWallets(PageRequest.of(0, 10));

        assertTrue(result.isEmpty());
    }

    @Test
    void getAllTransactions_shouldMapTransferAndAddTransactions() {
        User senderUser = new User(1L, "sender@example.com", "x", Role.USER);
        User receiverUser = new User(2L, "receiver@example.com", "x", Role.USER);
        Wallet sender = new Wallet(11L, senderUser, new BigDecimal("80.00"), 1L);
        Wallet receiver = new Wallet(12L, receiverUser, new BigDecimal("120.00"), 1L);

        WalletTransaction transfer = new WalletTransaction();
        transfer.setId(100L);
        transfer.setFromWallet(sender);
        transfer.setToWallet(receiver);
        transfer.setAmount(new BigDecimal("20.00"));
        transfer.setType(TransactionType.TRANSFER);
        transfer.setStatus(TransactionStatus.SUCCESS);
        transfer.setIdempotencyKey("transfer-100");
        transfer.setCreatedAt(LocalDateTime.now());

        WalletTransaction add = new WalletTransaction();
        add.setId(101L);
        add.setFromWallet(null);
        add.setToWallet(receiver);
        add.setAmount(new BigDecimal("50.00"));
        add.setType(TransactionType.ADD);
        add.setStatus(TransactionStatus.SUCCESS);
        add.setIdempotencyKey("add-101");
        add.setCreatedAt(LocalDateTime.now());

        when(transactionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transfer, add)));

        Page<com.techpulse.wallet.dto.AdminTransactionResponse> result =
                adminService.getAllTransactions(PageRequest.of(0, 10));

        assertEquals(2, result.getTotalElements());

        var transferDto = result.getContent().get(0);
        assertEquals(100L, transferDto.getTransactionId());
        assertEquals(11L, transferDto.getFromWalletId());
        assertEquals(12L, transferDto.getToWalletId());
        assertEquals("sender@example.com", transferDto.getSenderEmail());
        assertEquals("receiver@example.com", transferDto.getReceiverEmail());

        var addDto = result.getContent().get(1);
        assertNull(addDto.getFromWalletId());
        assertNull(addDto.getSenderEmail());
        assertEquals(12L, addDto.getToWalletId());
        assertEquals("receiver@example.com", addDto.getReceiverEmail());
    }

    @Test
    void getAllTransactions_shouldReturnEmptyPageWhenNoTransactionsExist() {
        when(transactionRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());

        Page<com.techpulse.wallet.dto.AdminTransactionResponse> result =
                adminService.getAllTransactions(PageRequest.of(0, 10));

        assertTrue(result.isEmpty());
    }
}
