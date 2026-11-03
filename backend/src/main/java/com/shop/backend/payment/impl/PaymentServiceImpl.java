package com.shop.backend.payment.impl;

import com.shop.backend.common.exception.ResourceNotFoundException;
import com.shop.backend.einvoice.model.Invoice;
import com.shop.backend.einvoice.model.InvoiceType;
import com.shop.backend.einvoice.repository.InvoiceRepository;
import com.shop.backend.einvoice.service.InvoiceDomainService;
import com.shop.backend.order.Order;
import com.shop.backend.order.OrderRepository;
import com.shop.backend.order.OrderStatus;
import com.shop.backend.payment.Payment;
import com.shop.backend.payment.PaymentMethod;
import com.shop.backend.payment.PaymentRepository;
import com.shop.backend.payment.PaymentService;
import com.shop.backend.payment.PaymentStatus;
import com.shop.backend.payment.dto.PaymentProcessResult;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceDomainService invoiceDomainService;
    private final TransactionTemplate defaultTransactionTemplate;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public PaymentServiceImpl(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            InvoiceDomainService invoiceDomainService,
            PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceDomainService = invoiceDomainService;
        this.defaultTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public PaymentProcessResult processPaymentSuccess(Long orderId, String transactionCode, PaymentMethod method) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }

        Payment savedPayment = defaultTransactionTemplate.execute(status -> {
            Order order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

            Payment payment = paymentRepository.findFirstByOrderIdOrderByPaidAtDesc(orderId)
                    .orElseGet(() -> {
                        Payment p = Payment.builder()
                                .order(order)
                                .method(method != null ? method : (order.getPaymentMethod() != null ? order.getPaymentMethod() : PaymentMethod.COD))
                                .amount(order.getTotalAmount())
                                .status(PaymentStatus.PENDING)
                                .build();
                        return paymentRepository.save(p);
                    });

            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            if (transactionCode != null && !transactionCode.isBlank()) {
                payment.setTransactionCode(transactionCode);
            }
            if (method != null) {
                payment.setMethod(method);
            }
            return paymentRepository.save(payment);
        });

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order {} is CANCELLED but received payment success (txCode: {}). Payment marked SUCCESS; invoice creation skipped.",
                    orderId, transactionCode);
            return PaymentProcessResult.builder()
                    .payment(savedPayment)
                    .invoice(null)
                    .invoiceCreated(false)
                    .message("Payment successful but order is CANCELLED; invoice creation skipped")
                    .build();
        }

        Optional<Invoice> existingInvoice = invoiceRepository.findByOrderIdAndInvoiceType(orderId, InvoiceType.ORIGINAL);
        if (existingInvoice.isPresent()) {
            return PaymentProcessResult.builder()
                    .payment(savedPayment)
                    .invoice(existingInvoice.get())
                    .invoiceCreated(false)
                    .message("Payment successful; invoice already exists")
                    .build();
        }

        String idempotencyKey = "ORIG_ORD_" + orderId;
        Invoice invoice = null;
        try {
            invoice = invoiceDomainService.createOriginalInvoiceForOrder(orderId, idempotencyKey);
        } catch (Exception e) {
            log.warn("Automatic invoice creation failed for paid order {}: {}. Payment remains recorded as SUCCESS.",
                    orderId, e.getMessage());
        }

        boolean wasNewlyCreated = (invoice != null && invoice.isNewlyCreated());
        String message;
        if (wasNewlyCreated) {
            message = "Payment and invoice created successfully";
        } else if (invoice != null) {
            message = "Payment successful; invoice already exists";
        } else {
            message = "Payment successful; invoice creation deferred";
        }

        return PaymentProcessResult.builder()
                .payment(savedPayment)
                .invoice(invoice)
                .invoiceCreated(wasNewlyCreated)
                .message(message)
                .build();
    }

    @Override
    @Transactional
    public PaymentProcessResult processPaymentFailure(Long orderId, String transactionCode, PaymentMethod method, String failureReason) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        Payment payment = paymentRepository.findFirstByOrderIdOrderByPaidAtDesc(orderId)
                .orElseGet(() -> {
                    Payment p = Payment.builder()
                            .order(order)
                            .method(method != null ? method : (order.getPaymentMethod() != null ? order.getPaymentMethod() : PaymentMethod.COD))
                            .amount(order.getTotalAmount())
                            .status(PaymentStatus.PENDING)
                            .build();
                    return paymentRepository.save(p);
                });

        payment.setStatus(PaymentStatus.FAILED);
        if (transactionCode != null && !transactionCode.isBlank()) {
            payment.setTransactionCode(transactionCode);
        }
        if (method != null) {
            payment.setMethod(method);
        }
        Payment savedPayment = paymentRepository.save(payment);

        return PaymentProcessResult.builder()
                .payment(savedPayment)
                .invoice(null)
                .invoiceCreated(false)
                .message("Payment failed: " + failureReason)
                .build();
    }

    @Override
    @Transactional
    public PaymentProcessResult processCodPaymentCompletion(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getPaymentMethod() != PaymentMethod.COD) {
            return PaymentProcessResult.builder()
                    .payment(null)
                    .invoice(null)
                    .invoiceCreated(false)
                    .message("Order payment method is not COD")
                    .build();
        }

        Payment payment = paymentRepository.findFirstByOrderIdOrderByPaidAtDesc(orderId)
                .orElseGet(() -> {
                    Payment p = Payment.builder()
                            .order(order)
                            .method(PaymentMethod.COD)
                            .amount(order.getTotalAmount())
                            .status(PaymentStatus.PENDING)
                            .build();
                    return paymentRepository.save(p);
                });

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        payment.setTransactionCode("COD-" + orderId);
        payment.setMethod(PaymentMethod.COD);
        Payment savedPayment = paymentRepository.save(payment);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        requiresNewTransactionTemplate.execute(status -> {
                            return invoiceDomainService.createOriginalInvoiceForOrder(orderId, "ORIG_ORD_" + orderId);
                        });
                    } catch (Exception e) {
                        log.warn("Automatic invoice creation failed after COD completion for order {}: {}. Payment remains recorded as SUCCESS.",
                                orderId, e.getMessage());
                    }
                }
            });
        } else {
            try {
                requiresNewTransactionTemplate.execute(status -> {
                    return invoiceDomainService.createOriginalInvoiceForOrder(orderId, "ORIG_ORD_" + orderId);
                });
            } catch (Exception e) {
                log.warn("Automatic invoice creation failed for COD order {}: {}. Payment remains recorded as SUCCESS.",
                        orderId, e.getMessage());
            }
        }

        return PaymentProcessResult.builder()
                .payment(savedPayment)
                .invoice(null)
                .invoiceCreated(false)
                .message("COD payment marked SUCCESS; invoice creation scheduled after commit")
                .build();
    }

    @Override
    @Transactional
    public PaymentProcessResult retryInvoiceCreation(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId must not be null");
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        Payment payment = paymentRepository.findFirstByOrderIdOrderByPaidAtDesc(orderId)
                .orElseThrow(() -> new IllegalStateException("No payment record found for order: " + orderId));

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Cannot create invoice: payment status is not SUCCESS for order: " + orderId);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot create invoice for CANCELLED order: " + orderId);
        }

        Optional<Invoice> existingInvoice = invoiceRepository.findByOrderIdAndInvoiceType(orderId, InvoiceType.ORIGINAL);
        if (existingInvoice.isPresent()) {
            return PaymentProcessResult.builder()
                    .payment(payment)
                    .invoice(existingInvoice.get())
                    .invoiceCreated(false)
                    .message("Invoice already exists")
                    .build();
        }

        String idempotencyKey = "ORIG_ORD_" + orderId;
        Invoice invoice = invoiceDomainService.createOriginalInvoiceForOrder(orderId, idempotencyKey);
        if (invoice == null) {
            throw new IllegalStateException("Cannot create invoice: effective tax configuration is not configured for order " + orderId);
        }

        return PaymentProcessResult.builder()
                .payment(payment)
                .invoice(invoice)
                .invoiceCreated(true)
                .message("Invoice created successfully on retry")
                .build();
    }
}
