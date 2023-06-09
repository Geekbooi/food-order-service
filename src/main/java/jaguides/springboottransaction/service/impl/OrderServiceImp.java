package jaguides.springboottransaction.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jaguides.springboottransaction.dto.OrderRequest;
import jaguides.springboottransaction.dto.OrderResponse;
import jaguides.springboottransaction.entity.*;
import jaguides.springboottransaction.exception.PaymentException;
import jaguides.springboottransaction.repository.OrderRepository;
import jaguides.springboottransaction.repository.PaymentRepository;
import jaguides.springboottransaction.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class OrderServiceImp implements OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    @Autowired
    private final KafkaSender kafkaSender;

    @Autowired
    public OrderServiceImp(OrderRepository orderRepository, PaymentRepository paymentRepository, KafkaSender kafkaSender) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.kafkaSender = kafkaSender;
    }

    @Transactional(rollbackFor = PaymentException.class)
    @Override
    public OrderResponse placeOrder(OrderRequest orderRequest) {
        Order order = orderRequest.getOrder();
        order.setStatus("SUCCESS");
        order.setOrderTrackingNumber(UUID.randomUUID().toString());
        orderRepository.save(order);
        Payment payment = orderRequest.getPayment();

        if (!payment.getType().equals("DEBIT")) {
            throw new PaymentException("Payment card do not support");
        }
        payment.setOrderId(order.getId());
        paymentRepository.save(payment);


        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setOrderTrackingNumber(order.getOrderTrackingNumber());
        orderResponse.setStatus(order.getStatus());
        orderResponse.setId(order.getId());
        orderResponse.setMessage("SUCCESS");

        ObjectMapper objectMapper = new ObjectMapper();
        String orderAsString = null;
        try {
            orderAsString = objectMapper.writeValueAsString(orderAsString);
            kafkaSender.sendMessage("receipt", orderAsString);

        } catch (Exception e) {
            System.out.println("Unable to convert to string");
        }

        return orderResponse;
    }
    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId).orElse(null);
    }

    public boolean refundOrder(String orderId) {
        Optional<Order> optionalOrder = orderRepository.findById(orderId);
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            order.setStatus("REFUNDED");
            orderRepository.save(order);
            return true;
        }
        return false;
    }

    public boolean deleteOrder(String orderId) {
        Optional<Order> optionalOrder = orderRepository.findById(orderId);
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            orderRepository.delete(order);
            return true;
        }
        return false;
    }
    public void sendNotification(String notification) {
        String topic = "receipt";
    }

}
