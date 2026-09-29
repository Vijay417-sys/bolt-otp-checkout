package com.bolt.checkout.dto;

import com.bolt.checkout.entity.CheckoutRecord;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One page of order history.
 *
 * <p>Fields are copied out of the entity rather than serialising the entity itself, which
 * keeps the persistence model out of the API contract.
 */
public class CheckoutHistoryResponse {

    private List<Order> orders;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public CheckoutHistoryResponse() {
    }

    public CheckoutHistoryResponse(Page<CheckoutRecord> result) {
        this.orders = result.getContent().stream().map(Order::from).toList();
        this.page = result.getNumber();
        this.size = result.getSize();
        this.totalElements = result.getTotalElements();
        this.totalPages = result.getTotalPages();
        this.first = result.isFirst();
        this.last = result.isLast();
    }

    public List<Order> getOrders() { return orders; }
    public void setOrders(List<Order> orders) { this.orders = orders; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public boolean isFirst() { return first; }
    public void setFirst(boolean first) { this.first = first; }
    public boolean isLast() { return last; }
    public void setLast(boolean last) { this.last = last; }

    /** A single order in the history. */
    public static class Order {

        private Long id;
        private String email;
        private String phone;
        private String shippingAddress;
        private LocalDateTime createdAt;

        public Order() {
        }

        static Order from(CheckoutRecord record) {
            Order order = new Order();
            order.id = record.getId();
            order.email = record.getEmail();
            order.phone = record.getPhone();
            order.shippingAddress = record.getShippingAddress();
            order.createdAt = record.getCreatedAt();
            return order;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getShippingAddress() { return shippingAddress; }
        public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    }
}
