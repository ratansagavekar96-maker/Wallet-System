package com.techpulse.wallet.serviceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.techpulse.wallet.dto.AddMoneyRequest;
import com.techpulse.wallet.dto.TransferRequest;
import com.techpulse.wallet.dto.WalletResponse;
import com.techpulse.wallet.dto.WalletTransactionResponse;
import com.techpulse.wallet.entity.User;
import com.techpulse.wallet.entity.Wallet;
import com.techpulse.wallet.entity.WalletTransaction;
import com.techpulse.wallet.enums.Role;
import com.techpulse.wallet.enums.TransactionStatus;
import com.techpulse.wallet.enums.TransactionType;
import com.techpulse.wallet.repository.UserRepository;
import com.techpulse.wallet.repository.WalletRepository;
import com.techpulse.wallet.repository.WalletTransactionRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private WalletServiceImpl walletService;

    private User senderUser;
    private User receiverUser;
    private Wallet sender;
    private Wallet receiver;

    @BeforeEach
    void setUp() {
        senderUser = new User(1L, "sender@example.com", "encoded", Role.USER);
        receiverUser = new User(2L, "receiver@example.com", "encoded", Role.USER);
        sender = new Wallet(101L, senderUser, new BigDecimal("500.00"), 0L);
        receiver = new Wallet(102L, receiverUser, new BigDecimal("100.00"), 0L);
    }

    @Test
    void getWallet_shouldReturnOwnWallet() {
        when(walletRepository.findByUserEmail("sender@example.com")).thenReturn(Optional.of(sender));

        WalletResponse response = walletService.getWallet("sender@example.com");

        assertEquals(101L, response.getWalletId());
        assertEquals("sender@example.com", response.getEmail());
        assertEquals(new BigDecimal("500.00"), response.getBalance());
    }

    @Test
    void getWallet_shouldThrowNotFoundWhenWalletMissing() {
        when(walletRepository.findByUserEmail("missing@example.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletService.getWallet("missing@example.com"));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void addMoney_shouldIncreaseBalanceAndSaveTransaction() {
        AddMoneyRequest request = addRequest("150.00");
        when(walletRepository.findByUserEmailForUpdate("sender@example.com")).thenReturn(Optional.of(sender));
        when(transactionRepository.findByIdempotencyKey("add-001")).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(WalletTransaction.class))).thenAnswer(inv -> {
            WalletTransaction tx = inv.getArgument(0);
            tx.setId(501L);
            return tx;
        });

        WalletTransactionResponse response = walletService.addMoney("sender@example.com", "add-001", request);

        assertEquals(new BigDecimal("650.00"), sender.getBalance());
        assertEquals(TransactionType.ADD, response.getType());
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
        assertEquals("add-001", response.getIdempotencyKey());
        verify(walletRepository).save(sender);
        verify(transactionRepository).saveAndFlush(any(WalletTransaction.class));
    }

    @Test
    void addMoney_shouldReturnPreviousTransactionForSameIdempotencyRequest() {
        WalletTransaction previous = transaction(null, sender, "150.00", TransactionType.ADD,
                TransactionStatus.SUCCESS, "add-repeat");
        previous.setId(502L);

        when(walletRepository.findByUserEmailForUpdate("sender@example.com")).thenReturn(Optional.of(sender));
        when(transactionRepository.findByIdempotencyKey("add-repeat")).thenReturn(Optional.of(previous));

        WalletTransactionResponse response = walletService.addMoney(
                "sender@example.com", "add-repeat", addRequest("150.00"));

        assertEquals(502L, response.getTransactionId());
        assertEquals(new BigDecimal("500.00"), sender.getBalance());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void addMoney_shouldRejectIdempotencyKeyReusedWithDifferentAmount() {
        WalletTransaction previous = transaction(null, sender, "100.00", TransactionType.ADD,
                TransactionStatus.SUCCESS, "add-reused");
        when(walletRepository.findByUserEmailForUpdate("sender@example.com")).thenReturn(Optional.of(sender));
        when(transactionRepository.findByIdempotencyKey("add-reused")).thenReturn(Optional.of(previous));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletService.addMoney("sender@example.com", "add-reused", addRequest("200.00")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void addMoney_shouldRejectBlankOrOverlongIdempotencyKey() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> walletService.addMoney("sender@example.com", " ", addRequest("10.00"))).getStatusCode());

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> walletService.addMoney("sender@example.com", "x".repeat(101), addRequest("10.00"))).getStatusCode());

        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void addMoney_shouldReturnNotFoundWhenWalletDoesNotExist() {
        when(walletRepository.findByUserEmailForUpdate("missing@example.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletService.addMoney("missing@example.com", "key", addRequest("10.00")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void transferMoney_shouldTransferAtomicallyAndSaveSuccessTransaction() {
        when(walletRepository.findByUserEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserEmail("receiver@example.com")).thenReturn(Optional.of(receiver));
        when(walletRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(sender));
        when(walletRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(receiver));
        when(transactionRepository.findByIdempotencyKey("transfer-001")).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(WalletTransaction.class))).thenAnswer(inv -> {
            WalletTransaction tx = inv.getArgument(0);
            tx.setId(601L);
            return tx;
        });

        WalletTransactionResponse response = walletService.transferMoney(
                "sender@example.com", "transfer-001", transferRequest("receiver@example.com", "200.00"));

        assertEquals(new BigDecimal("300.00"), sender.getBalance());
        assertEquals(new BigDecimal("300.00"), receiver.getBalance());
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
        assertEquals(TransactionType.TRANSFER, response.getType());
        assertEquals(101L, response.getFromWalletId());
        assertEquals(102L, response.getToWalletId());
        verify(walletRepository).save(sender);
        verify(walletRepository).save(receiver);
        verify(transactionRepository).saveAndFlush(any(WalletTransaction.class));
    }

    @Test
    void transferMoney_shouldRecordFailedTransactionWhenInsufficientFunds() {
        sender.setBalance(new BigDecimal("50.00"));
        when(walletRepository.findByUserEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserEmail("receiver@example.com")).thenReturn(Optional.of(receiver));
        when(walletRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(sender));
        when(walletRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(receiver));
        when(transactionRepository.findByIdempotencyKey("transfer-low")).thenReturn(Optional.empty());
        when(transactionRepository.saveAndFlush(any(WalletTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        WalletTransactionResponse response = walletService.transferMoney(
                "sender@example.com", "transfer-low", transferRequest("receiver@example.com", "100.00"));

        assertEquals(TransactionStatus.FAILED, response.getStatus());
        assertEquals(new BigDecimal("50.00"), sender.getBalance());
        assertEquals(new BigDecimal("100.00"), receiver.getBalance());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository).saveAndFlush(any(WalletTransaction.class));
    }

    @Test
    void transferMoney_shouldRejectSelfTransfer() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletService.transferMoney("sender@example.com", "self-key",
                        transferRequest("SENDER@example.com", "10.00")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void transferMoney_shouldRejectMissingIdempotencyKey() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletService.transferMoney("sender@example.com", " ",
                        transferRequest("receiver@example.com", "10.00")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(walletRepository, transactionRepository);
    }

    @Test
    void transferMoney_shouldReturnNotFoundWhenSenderMissing() {
        when(walletRepository.findByUserEmail("sender@example.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletService.transferMoney("sender@example.com", "key",
                        transferRequest("receiver@example.com", "10.00")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void transferMoney_shouldReturnNotFoundWhenReceiverMissing() {
        when(walletRepository.findByUserEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserEmail("receiver@example.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletService.transferMoney("sender@example.com", "key",
                        transferRequest("receiver@example.com", "10.00")));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void transferMoney_shouldReplayPreviousMatchingRequest() {
        WalletTransaction previous = transaction(sender, receiver, "75.00", TransactionType.TRANSFER,
                TransactionStatus.SUCCESS, "transfer-repeat");
        previous.setId(602L);

        when(walletRepository.findByUserEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserEmail("receiver@example.com")).thenReturn(Optional.of(receiver));
        when(walletRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(sender));
        when(walletRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(receiver));
        when(transactionRepository.findByIdempotencyKey("transfer-repeat")).thenReturn(Optional.of(previous));

        WalletTransactionResponse response = walletService.transferMoney(
                "sender@example.com", "transfer-repeat",
                transferRequest("receiver@example.com", "75.00"));

        assertEquals(602L, response.getTransactionId());
        assertEquals(new BigDecimal("500.00"), sender.getBalance());
        assertEquals(new BigDecimal("100.00"), receiver.getBalance());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).saveAndFlush(any());
    }

    @Test
    void transferMoney_shouldRejectIdempotencyKeyReusedForDifferentTransfer() {
        WalletTransaction previous = transaction(sender, receiver, "75.00", TransactionType.TRANSFER,
                TransactionStatus.SUCCESS, "transfer-conflict");

        when(walletRepository.findByUserEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(walletRepository.findByUserEmail("receiver@example.com")).thenReturn(Optional.of(receiver));
        when(walletRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(sender));
        when(walletRepository.findByIdForUpdate(102L)).thenReturn(Optional.of(receiver));
        when(transactionRepository.findByIdempotencyKey("transfer-conflict")).thenReturn(Optional.of(previous));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletService.transferMoney("sender@example.com", "transfer-conflict",
                        transferRequest("receiver@example.com", "80.00")));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void getTransactions_shouldReturnPagedUserHistory() {
        when(userRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(senderUser));
        WalletTransaction tx = transaction(null, sender, "25.00", TransactionType.ADD,
                TransactionStatus.SUCCESS, "history-key");
        Page<WalletTransaction> page = new PageImpl<>(List.of(tx));
        when(transactionRepository.findByFromWallet_User_IdOrToWallet_User_Id(
                eq(1L), eq(1L), any())).thenReturn(page);

        Page<WalletTransactionResponse> result = walletService.getTransactions(
                "sender@example.com", PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertEquals(TransactionType.ADD, result.getContent().get(0).getType());
        assertNull(result.getContent().get(0).getFromWalletId());
    }

    @Test
    void getTransactions_shouldThrowNotFoundForUnknownUser() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> walletService.getTransactions("unknown@example.com", PageRequest.of(0, 10)));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private AddMoneyRequest addRequest(String amount) {
        AddMoneyRequest request = new AddMoneyRequest();
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private TransferRequest transferRequest(String email, String amount) {
        TransferRequest request = new TransferRequest();
        request.setReceiverEmail(email);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private WalletTransaction transaction(Wallet from, Wallet to, String amount,
            TransactionType type, TransactionStatus status, String key) {
        WalletTransaction tx = new WalletTransaction();
        tx.setFromWallet(from);
        tx.setToWallet(to);
        tx.setAmount(new BigDecimal(amount));
        tx.setType(type);
        tx.setStatus(status);
        tx.setIdempotencyKey(key);
        return tx;
    }
}
