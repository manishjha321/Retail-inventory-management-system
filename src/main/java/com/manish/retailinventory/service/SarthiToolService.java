package com.manish.retailinventory.service;

import com.google.gson.*;
import com.manish.retailinventory.dto.InventoryDtos.InventoryResponse;
import com.manish.retailinventory.dto.MasterDtos.*;
import com.manish.retailinventory.dto.OrderDtos.OrderResponse;
import com.manish.retailinventory.dto.PurchaseDtos.PoResponse;
import com.manish.retailinventory.entity.OrderStatus;
import com.manish.retailinventory.entity.PoStatus;
import com.manish.retailinventory.entity.Role;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The ONLY things Sarthi can do. Every tool is a READ operation on an existing service.
 * There is no SQL access and no write tool, and each call runs inside a read-only
 * database transaction, so even a bug could not modify data.
 */
@Service
public class SarthiToolService {
    private static final Set<String> MANAGER_ONLY =
            Set.of("list_purchase_orders", "get_dashboard_summary", "stock_value_by_warehouse");

    private final ProductService products;
    private final InventoryService inventory;
    private final WarehouseService warehouses;
    private final CategoryService categories;
    private final SupplierService suppliers;
    private final CustomerService customers;
    private final SalesOrderService orders;
    private final PurchaseOrderService purchaseOrders;
    private final DashboardService dashboard;
    private final TransactionTemplate readOnlyTx;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, t, c) -> new JsonPrimitive(src.toString()))
            .create();

    public SarthiToolService(ProductService products, InventoryService inventory, WarehouseService warehouses,
                             CategoryService categories, SupplierService suppliers, CustomerService customers,
                             SalesOrderService orders, PurchaseOrderService purchaseOrders,
                             DashboardService dashboard, PlatformTransactionManager txManager) {
        this.products = products;
        this.inventory = inventory;
        this.warehouses = warehouses;
        this.categories = categories;
        this.suppliers = suppliers;
        this.customers = customers;
        this.orders = orders;
        this.purchaseOrders = purchaseOrders;
        this.dashboard = dashboard;
        this.readOnlyTx = new TransactionTemplate(txManager);
        this.readOnlyTx.setReadOnly(true);
    }

    // ------------------------------------------------------------------ tool catalogue
    public JsonArray definitions() {
        JsonArray a = new JsonArray();
        a.add(tool("search_products", "Search active products by name or SKU text, optionally within a category. Returns price and reorder level.",
                props(p("query", "string", "Text in product name or SKU"), p("category", "string", "Category name"),
                        p("limit", "integer", "Max rows, default 10"))));
        a.add(tool("get_stock", "Get stock of a product (by SKU or name) in every warehouse: on hand, reserved and available.",
                props(p("product", "string", "SKU or part of the product name")), "product"));
        a.add(tool("get_low_stock", "List products whose total available stock is at or below their reorder level.", props()));
        a.add(tool("list_warehouses", "List active warehouses with code and location.", props()));
        a.add(tool("list_categories", "List product categories.", props()));
        a.add(tool("list_suppliers", "List active suppliers with contact details.", props()));
        a.add(tool("search_customers", "Find customers by name or email text.",
                props(p("query", "string", "Name or email text")), "query"));
        a.add(tool("list_orders", "List recent sales orders, optionally filtered by status (PENDING, CONFIRMED, PACKED, SHIPPED, DELIVERED, CANCELLED).",
                props(p("status", "string", "Order status"), p("limit", "integer", "Max rows, default 10"))));
        a.add(tool("get_order", "Get full details of one sales order by its order number (for example SO-20260930-AB12CD).",
                props(p("orderNumber", "string", "Order number")), "orderNumber"));
        a.add(tool("list_purchase_orders", "List purchase orders to suppliers, optionally filtered by status (DRAFT, ORDERED, RECEIVED, CANCELLED).",
                props(p("status", "string", "PO status"), p("limit", "integer", "Max rows, default 10"))));
        a.add(tool("get_dashboard_summary", "Overall numbers: product count, total units, stock value, low-stock count, orders by status, open purchase orders.", props()));
        a.add(tool("recent_stock_movements", "Latest stock movements (receipts, sales, adjustments, transfers), optionally for one product SKU.",
                props(p("sku", "string", "Product SKU"), p("limit", "integer", "Max rows, default 10"))));
        a.add(tool("top_selling_products", "Best selling products by units sold (cancelled orders ignored).",
                props(p("limit", "integer", "How many, default 5"))));
        a.add(tool("stock_value_by_warehouse", "Units on hand and stock value (at cost price) for each warehouse.", props()));
        return a;
    }

    // ------------------------------------------------------------------ execution
    /** Runs one tool and returns a JSON string for the model. Never throws. */
    public String execute(String name, JsonObject args, Role role) {
        if (MANAGER_ONLY.contains(name) && role == Role.STAFF) {
            return "{\"error\":\"This information is only available to managers and admins.\"}";
        }
        try {
            Object result = readOnlyTx.execute(status -> run(name, args, role));
            String json = gson.toJson(result);
            return json.length() > 7000 ? json.substring(0, 7000) + "...(truncated)" : json;
        } catch (Exception ex) {
            return gson.toJson(Map.of("error", "Could not fetch data: " + ex.getMessage()));
        }
    }

    private Object run(String name, JsonObject a, Role role) {
        return switch (name) {
            case "search_products" -> searchProducts(a, role);
            case "get_stock" -> getStock(str(a, "product"));
            case "get_low_stock" -> inventory.lowStock();
            case "list_warehouses" -> warehouses.list();
            case "list_categories" -> categories.list();
            case "list_suppliers" -> suppliers.list().stream()
                    .map(s -> Map.of("name", s.name(), "contact", nz(s.contactPerson()), "email", nz(s.email()), "phone", nz(s.phone())))
                    .toList();
            case "search_customers" -> searchCustomers(str(a, "query"));
            case "list_orders" -> listOrders(str(a, "status"), limit(a, 10));
            case "get_order" -> orders.getByNumber(str(a, "orderNumber"));
            case "list_purchase_orders" -> listPurchaseOrders(str(a, "status"), limit(a, 10));
            case "get_dashboard_summary" -> dashboard.summary();
            case "recent_stock_movements" -> recentMovements(str(a, "sku"), limit(a, 10));
            case "top_selling_products" -> dashboard.topSelling(limit(a, 5));
            case "stock_value_by_warehouse" -> dashboard.stockByWarehouse();
            default -> Map.of("error", "Unknown tool: " + name);
        };
    }

    private Object searchProducts(JsonObject a, Role role) {
        Long categoryId = null;
        String cat = str(a, "category");
        if (cat != null) {
            categoryId = categories.list().stream().filter(c -> c.name().toLowerCase().contains(cat.toLowerCase()))
                    .map(CategoryResponse::id).findFirst().orElse(null);
        }
        var page = products.search(str(a, "query"), categoryId, 0, limit(a, 10));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductResponse p : page.content()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sku", p.sku());
            m.put("name", p.name());
            m.put("category", p.categoryName());
            m.put("supplier", p.supplierName());
            m.put("unitPrice", p.unitPrice());
            if (role != Role.STAFF) m.put("costPrice", p.costPrice());
            m.put("reorderLevel", p.reorderLevel());
            rows.add(m);
        }
        return Map.of("totalMatches", page.totalElements(), "products", rows);
    }

    private Object getStock(String query) {
        if (query == null) return Map.of("error", "product is required");
        List<Object> out = new ArrayList<>();
        for (ProductResponse p : products.search(query, null, 0, 3).content()) {
            List<InventoryResponse> rows = inventory.list(p.id(), null);
            List<Map<String, Object>> perWh = new ArrayList<>();
            int onHand = 0;
            int available = 0;
            for (InventoryResponse r : rows) {
                onHand += r.quantity();
                available += r.availableQuantity();
                perWh.add(Map.of("warehouse", r.warehouseName(), "onHand", r.quantity(),
                        "reserved", r.reservedQuantity(), "available", r.availableQuantity()));
            }
            out.add(Map.of("sku", p.sku(), "name", p.name(), "reorderLevel", p.reorderLevel(),
                    "totalOnHand", onHand, "totalAvailable", available, "warehouses", perWh));
        }
        return out.isEmpty() ? Map.of("message", "No product matches '" + query + "'") : out;
    }

    private Object searchCustomers(String q) {
        String needle = q == null ? "" : q.toLowerCase();
        return customers.list().stream()
                .filter(c -> c.name().toLowerCase().contains(needle) || nz(c.email()).toLowerCase().contains(needle))
                .limit(10)
                .map(c -> Map.of("name", c.name(), "email", nz(c.email()), "phone", nz(c.phone()), "address", nz(c.address())))
                .toList();
    }

    private Object listOrders(String status, int limit) {
        OrderStatus st = null;
        if (status != null) {
            try { st = OrderStatus.valueOf(status.trim().toUpperCase()); }
            catch (IllegalArgumentException e) { return Map.of("error", "Unknown status " + status); }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OrderResponse o : orders.list(st, 0, limit).content()) {
            rows.add(Map.of("orderNumber", o.orderNumber(), "customer", o.customerName(), "status", o.status().name(),
                    "total", o.totalAmount(), "items", o.items().size(), "createdAt", o.createdAt().toString()));
        }
        return rows;
    }

    private Object listPurchaseOrders(String status, int limit) {
        PoStatus st = null;
        if (status != null) {
            try { st = PoStatus.valueOf(status.trim().toUpperCase()); }
            catch (IllegalArgumentException e) { return Map.of("error", "Unknown status " + status); }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PoResponse p : purchaseOrders.list(st, 0, limit).content()) {
            rows.add(Map.of("poNumber", p.poNumber(), "supplier", p.supplierName(), "status", p.status().name(),
                    "totalCost", p.totalCost(), "items", p.items().size(), "createdAt", p.createdAt().toString()));
        }
        return rows;
    }

    private Object recentMovements(String sku, int limit) {
        Long productId = null;
        if (sku != null) {
            var found = products.search(sku, null, 0, 1).content();
            if (found.isEmpty()) return Map.of("message", "No product matches '" + sku + "'");
            productId = found.get(0).id();
        }
        return inventory.movements(productId, 0, limit).content().stream()
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("when", m.createdAt().toString());
                    row.put("type", m.movementType().name());
                    row.put("quantityChange", m.quantityChange());
                    row.put("sku", m.sku());
                    row.put("warehouse", m.warehouseName());
                    row.put("reason", nz(m.reason()));
                    row.put("by", nz(m.performedBy()));
                    return row;
                }).toList();
    }

    // ------------------------------------------------------------------ small helpers
    private static String str(JsonObject a, String key) {
        if (a == null || !a.has(key) || a.get(key).isJsonNull()) return null;
        String v = a.get(key).getAsString().trim();
        return v.isEmpty() ? null : v;
    }

    private static int limit(JsonObject a, int def) {
        String v = str(a, "limit");
        if (v == null) return def;
        try { return Math.min(Math.max(Integer.parseInt(v), 1), 20); }
        catch (NumberFormatException e) { return def; }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static JsonObject p(String name, String type, String desc) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("type", type);
        o.addProperty("description", desc);
        return o;
    }

    private static JsonObject props(JsonObject... fields) {
        JsonObject props = new JsonObject();
        for (JsonObject f : fields) {
            JsonObject def = new JsonObject();
            def.addProperty("type", f.get("type").getAsString());
            def.addProperty("description", f.get("description").getAsString());
            props.add(f.get("name").getAsString(), def);
        }
        return props;
    }

    private static JsonObject tool(String name, String description, JsonObject properties, String... required) {
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.add("properties", properties);
        JsonArray req = new JsonArray();
        for (String r : required) req.add(r);
        params.add("required", req);
        JsonObject fn = new JsonObject();
        fn.addProperty("name", name);
        fn.addProperty("description", description);
        fn.add("parameters", params);
        JsonObject t = new JsonObject();
        t.addProperty("type", "function");
        t.add("function", fn);
        return t;
    }
}
