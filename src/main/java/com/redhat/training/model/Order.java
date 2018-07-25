package com.redhat.training.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

public class Order implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private String orderId;
	private Date orderDate;
	private BigDecimal amountHT;
	private BigDecimal amountTTC;
	private Customer customer;
	private Address address;
	
	
	
	public String getOrderId() {
		return orderId;
	}
	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}
	public Date getOrderDate() {
		return orderDate;
	}
	public void setOrderDate(Date orderDate) {
		this.orderDate = orderDate;
	}
	public Customer getCustomer() {
		return customer;
	}
	public void setCustomer(Customer customer) {
		this.customer = customer;
	}
	public BigDecimal getAmountHT() {
		return amountHT;
	}
	public void setAmountHT(BigDecimal amountHT) {
		this.amountHT = amountHT;
	}
	public BigDecimal getAmountTTC() {
		return amountTTC;
	}
	public void setAmountTTC(BigDecimal amountTTC) {
		this.amountTTC = amountTTC;
	}
	public static long getSerialversionuid() {
		return serialVersionUID;
	}
	public Address getAddress() {
		return address;
	}
	public void setAddress(Address address) {
		this.address = address;
	}
	

	
	
	
}
