package com.johnkusner.cse241final.interfaces.customer;

import java.io.PrintStream;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.johnkusner.cse241final.interfaces.ProductSearchInterface;
import com.johnkusner.cse241final.interfaces.UserInterface;
import com.johnkusner.cse241final.menu.Menu;
import com.johnkusner.cse241final.menu.MenuItem;
import com.johnkusner.cse241final.objects.CartItem;
import com.johnkusner.cse241final.objects.Product;
import com.johnkusner.cse241final.objects.Stock;

public class OnlineCustomerInterface extends UserInterface {

    private NumberFormat numberFormat;
    private NumberFormat currencyFormat;
    private List<CartItem> cart;
    
    public OnlineCustomerInterface(Scanner in, PrintStream out, Connection db) {
        super(in, out, db);
        
        cart = new ArrayList<>();
        
        numberFormat = NumberFormat.getNumberInstance();
        currencyFormat = NumberFormat.getCurrencyInstance();
    }

    @Override
    public void run() {
        clear();
        
        showMenu();
    }

    private void showMenu() {
        String cartStatus = "Your cart is empty.";
        
        Menu<Runnable> menu = new Menu<>("What would you like to do?", this);
        menu.addItem("Add a product to cart", () -> productSearch());
        if (!cart.isEmpty()) {
            menu.addItem("View/Edit cart", () -> editCart());
            menu.addItem("Checkout", () -> checkout()); 
            
            cartStatus = getCartStatusMessage();
        }
        
        menu.setPrompt(cartStatus + " What would you like to do?");
        MenuItem<Runnable> choice = menu.promptOptional();
        
        if (choice != null && choice.get() != null) {
            choice.get().run();
        } else {
            return;
        }
        
        showMenu();
    }
    
    private void productSearch() {
        ProductSearchInterface search = new ProductSearchInterface(in, out, db);
        search.run();
        
        Product chosen = search.getChosenProduct();
        
        if (chosen != null) {
            showAvailability(chosen);
        }
    }
    
    private void editCart() {
        String title = getCartStatusMessage();
        title += "\nSelect an item below to modify it.";
        
    	Menu<CartItem> cartDisplay = new Menu<>(title, CartItem.HEADER, this);
    	
        for (CartItem item : cart) {
        	cartDisplay.addItem(item);
        }
        
        MenuItem<CartItem> chosen = cartDisplay.promptOptional();
        
        if (chosen != null && chosen.get() != null) {
        	editItem(chosen.get());
        }
    }
    
    private void editItem(CartItem item) {
    	clear();
    	out.println("Your cart contains " + numberFormat(item.getQty()) + "x \"" + item.getProductName() + "\"");
    	int newQty = promptInt("Enter new quantity (0 to remove)", 0, item.getMaxQty());
    	if (newQty == 0) {
    		cart.remove(item);
    	} else {
    		item.setQty(newQty);
    	}
    }
    
    private void checkout() {
        if (!promptBool("Are you sure you would like to checkout?")) {
        	return;
        }
        clear();
        out.println("Processing transaction...");
        
        try {
        	db.setAutoCommit(false);
        	
        	CallableStatement cs = db.prepareCall("{ call begin_transaction(?) }");
        	
        	cs.registerOutParameter(1, Types.INTEGER);
        	
        	cs.execute();
        	
        	int transactionId = cs.getInt(1);
        	
        	out.println("Transaction ID: " + transactionId);
        	
        	cs.close();
        	
        	
        	cs = db.prepareCall("{ call purchase_product(?, ?, ?, ?, ?, ?) }");;
        	
        	int totalItemsPurchased = 0;
        	double totalMoneySpent = 0.0;

        	for (CartItem item : cart) {
        		out.println("Purchasing " + item.getProductName() + "...");
        		
        		cs.setInt(1, transactionId);
        		cs.setInt(2, item.getProductId());
        		cs.setInt(3, item.getQty());
        		cs.setDouble(4, item.getUnitPrice());
        		cs.registerOutParameter(5, Types.INTEGER); // amount_got
        		cs.registerOutParameter(6, Types.DOUBLE); // total_paid
        		
        		cs.execute();
        	
        		int purchasedQty = cs.getInt(5);
        		double purchasePrice = cs.getDouble(6);
        		
        		totalItemsPurchased += purchasedQty;
        		totalMoneySpent += purchasePrice;
        		
        		out.println("Purchased " + numberFormat(purchasedQty) + "/" + item.getQty() + ", for " + moneyFormat(purchasePrice));
        	}
        	
        	cs.close();
            
        	if (totalItemsPurchased == 0) {
            	db.rollback();
        	} else {
        	    out.println("Finishing transaction!");
        	    
        	    // TODO: pickup_order, shipped_order, payment_method
        	    cs = db.prepareCall("{ call finish_transaction(?, ?, ?, ?) }");
        	    
        	    cs.setInt(1, transactionId);
        	    cs.setDouble(2, 0.0); // tax rate
        	    cs.setDouble(3, 1); // payment method id !TODO!
        	    cs.registerOutParameter(4, Types.DOUBLE); // final total
        	    
        	    cs.execute();
        	    
        	    cs.close();
        	    
        		db.commit();
        		
        		out.println("Committed successfully");
        		
        		return;
        	}
        } catch (Exception e) {
            try {
                db.rollback();
            } catch (Exception e2) {
                // ignore
                e2.printStackTrace();
            }
            
            e.printStackTrace();
        } finally {
            try {
                db.setAutoCommit(true);
            }
            catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        // TODO: failure message
        pause();
    }
    
    private void showAvailability(Product prod) {
    	// If this product is already in cart, editItem instead
    	CartItem item = cart.stream().filter(i -> i.getProductId() == prod.getId()).findFirst().orElse(null);
    	if (item != null) {
    		editItem(item);
    		return;
    	}
        try (Statement stmt = db.createStatement();
                ResultSet rs = stmt.executeQuery("select * from warehouse_stock where product_id = " + prod.getId())) {
            clear();
            if (!rs.next()) {
                out.println("Sorry, " + prod.getName() + " is out of stock.");
                pause();
            } else {
                out.println("Availability for \"" + prod.getName() + "\": ");

                List<Stock> available = new ArrayList<Stock>();
                int totalAvailable = 0;
                
                do {
                    Stock stock = new Stock(rs);
                    available.add(stock);
                    totalAvailable += stock.getQty();

                    if (available.size() <= 5) {
                        out.println("- " + stock.toSimpleString(false));                        
                    }
                } while (rs.next());
                
                double averageCost = available.stream().mapToDouble(st -> st.getUnitPrice()).average().orElse(0);
                averageCost = Math.ceil(averageCost * 100) / 100.0;
                
                out.println("Cheaper items sell first. Your guaranteed price is <= "
                        + moneyFormat(averageCost) + "/each");
                int wanted = promptInt("Enter desired quantity (0 to cancel)", 0, totalAvailable);
                
                if (wanted == 0) {
                    return;
                }
                
                cart.add(new CartItem(new Stock(prod.getId(), prod.getName(), totalAvailable, averageCost), wanted));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private String getCartStatusMessage() {
    	int items = totalCartItems();
    	return "You have " + cart.size() + " product" + s(cart.size()) + " (" + numberFormat(items) + " item" + s(items)
    			+ ") in your cart. Sub-total: " + moneyFormat(totalCartPrice()) + ".";
    }

    private double totalCartPrice() {
        return cart.stream().mapToDouble(i -> i.getTotal()).sum();
    }
    
    private int totalCartItems() {
        return cart.stream().mapToInt(i -> i.getQty()).sum();
    }

    private String numberFormat(int num) {
        return numberFormat.format(num);
    }
    
    private String moneyFormat(double num) {
        return currencyFormat.format(num);
    }
    
    private String s(int val) {
    	return val != 1 ? "s" : "";
    }
    
    @Override
    public String getInterfaceName() {
        return "Shop On-line";
    }

    @Override
    public void close() {
        
    }

}
