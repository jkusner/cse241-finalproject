package com.johnkusner.cse241final.objects;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Inventory {
	private int productId;
	private String productName;
	private int qty;
	private double unitPrice;

	public Inventory(int productId, String productName, int qty, double unitPrice) {
		this.productId = productId;
		this.productName = productName;
		this.qty = qty;
		this.unitPrice = unitPrice;
	}
	
	public Inventory(ResultSet rs) throws SQLException {
		this(rs.getInt("product_id"), rs.getString("product_name"), rs.getInt("qty"), rs.getDouble("unit_price"));
	}

	public int getProductId() {
		return productId;
	}
	
	public int getQty() {
		return qty;
	}
	
	public double getUnitPrice() {
		return unitPrice;
	}
	
	public String toString() {
		return String.format("%20s | %6d | $%8.2f", productName, qty, unitPrice);
	}

	public static final String HEADER = String.format("%20s | %6s | %8s", "Product Name", "QTY", "Unit Price");
}