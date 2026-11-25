package com.shop.web;

import com.shop.config.AppConfig;
import com.shop.db.Db;
import com.shop.db.Jdbc;
import com.shop.dto.AddressRequest;
import com.shop.dto.AfterSaleRequest;
import com.shop.dto.BatchProductRequest;
import com.shop.dto.CartAddRequest;
import com.shop.dto.CartUpdateRequest;
import com.shop.dto.ChangePasswordRequest;
import com.shop.dto.CheckoutRequest;
import com.shop.dto.CustomerLoginRequest;
import com.shop.dto.CustomerRegisterRequest;
import com.shop.dto.FavoriteRequest;
import com.shop.dto.PayRequest;
import com.shop.enums.Enums;
import com.shop.serializers.Serializers;
import com.shop.services.AddressService;
import com.shop.services.AfterSaleService;
import com.shop.services.AuthService;
import com.shop.services.OrderService;
import com.shop.services.PaymentService;
import com.shop.services.PreselectService;
import com.shop.services.ShipmentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户接口（挑战C 买家端）：账号、收藏、购物车、多选下单、支付、地址、订单、物流、售后。
 *
 * <p>未登录临时收藏/加购见 {@link GuestController}；登录时携带 X-Guest-Id 自动合并。
 */
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final Db db;
    private final AppConfig config;
    private final AuthService auth;
    private final PreselectService preselect;
    private final OrderService orders;
    private final AddressService addresses;
    private final PaymentService payments;
    private final ShipmentService shipments;
    private final AfterSaleService afterSales;

    public CustomerController(Db db, AppConfig config, AuthService auth, PreselectService preselect,
                              OrderService orders, AddressService addresses, PaymentService payments,
                              ShipmentService shipments, AfterSaleService afterSales) {
        this.db = db;
        this.config = config;
        this.auth = auth;
        this.preselect = preselect;
        this.orders = orders;
        this.addresses = addresses;
        this.payments = payments;
        this.shipments = shipments;
        this.afterSales = afterSales;
    }

    // --------------------------- 账号 ---------------------------

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody CustomerRegisterRequest body) {
        String username = Validation.text(body.username, "body", "username", 1, 64, "用户名");
        String password = Validation.password(body.password, "body", "password");
        String phone = Validation.phone(body.phone, "body", "phone");
        String location = Validation.text(body.tradeLocation, "body", "trade_location", 1, 200, "默认交易地点");
        return auth.registerCustomer(username, password, phone, location);
    }

    /** 登录；携带 X-Guest-Id 时把游客临时收藏/购物车并入账号（TBD-12）。 */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody CustomerLoginRequest body,
                                     @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String username = Validation.text(body.username, "body", "username", 1, 64, "用户名");
        String password = Validation.text(body.password, "body", "password", 1, 128, "密码");
        Map<String, Object> result = auth.loginCustomer(username, password);
        if (guestId != null && !guestId.trim().isEmpty()) {
            String guest = Validation.guestId(guestId);
            String customerId = Jdbc.strOf((Map<String, Object>) result.get("customer"), "id");
            result.put("preselect_merged", db.transaction(conn ->
                    preselect.mergeGuest(conn, customerId, guest)));
        }
        return result;
    }

    /** 无状态令牌登出：服务端确认身份，客户端丢弃令牌即可。 */
    @PostMapping("/logout")
    public Map<String, Object> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireCustomer(authorization);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "已退出登录");
        return result;
    }

    @GetMapping("/me")
    public Map<String, Object> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        String customerId = Jdbc.strOf(customer, "id");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customer", Serializers.customer(customer, false));
        result.put("purchase_count", db.read(conn -> {
            Long count = Jdbc.scalarLong(conn,
                    "SELECT COUNT(*) AS c FROM shop_order WHERE customer_id = ? AND status = ?",
                    customerId, Enums.OrderStatus.COMPLETED);
            return count == null ? 0 : count;
        }));
        result.put("favorite_count", db.read(conn -> {
            Long count = Jdbc.scalarLong(conn,
                    "SELECT COUNT(*) AS c FROM favorite WHERE customer_id = ?", customerId);
            return count == null ? 0 : count;
        }));
        result.put("cart_item_count", db.read(conn -> {
            Long count = Jdbc.scalarLong(conn,
                    "SELECT COUNT(*) AS c FROM cart_item WHERE customer_id = ?", customerId);
            return count == null ? 0 : count;
        }));
        return result;
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody ChangePasswordRequest body,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        String oldPassword = Validation.text(body.oldPassword, "body", "old_password", 1, 128, "旧密码");
        String newPassword = Validation.password(body.newPassword, "body", "new_password");
        auth.changeCustomerPassword(Jdbc.strOf(customer, "id"), oldPassword, newPassword);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "密码修改成功");
        return result;
    }

    // --------------------------- 收藏（FR-018 / FR-019） ---------------------------

    @GetMapping("/favorites")
    public Map<String, Object> listFavorites(@RequestParam(value = "page", required = false) String page,
                                             @RequestParam(value = "page_size", required = false) String pageSize,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        return db.read(conn -> preselect.listFavorites(conn, Jdbc.strOf(customer, "id"), false, current, size));
    }

    @PostMapping("/favorites")
    public Map<String, Object> addFavorite(@RequestBody FavoriteRequest body,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        String productId = Validation.text(body.productId, "body", "product_id", 1, 64, "商品 ID");
        return db.transaction(conn ->
                preselect.addFavorite(conn, Jdbc.strOf(customer, "id"), false, productId));
    }

    @DeleteMapping("/favorites/{productId}")
    public Map<String, Object> removeFavorite(@PathVariable String productId,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn ->
                preselect.removeFavorite(conn, Jdbc.strOf(customer, "id"), false, productId));
    }

    // --------------------------- 购物车（FR-020 / FR-021） ---------------------------

    @GetMapping("/cart")
    public Map<String, Object> listCart(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.read(conn -> preselect.listCart(conn, Jdbc.strOf(customer, "id"), false));
    }

    @PostMapping("/cart")
    public Map<String, Object> addCart(@RequestBody CartAddRequest body,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        String productId = Validation.text(body.productId, "body", "product_id", 1, 64, "商品 ID");
        int quantity = Validation.quantity(body.quantity, "body", "quantity");
        return db.transaction(conn ->
                preselect.addCart(conn, Jdbc.strOf(customer, "id"), false, productId, quantity));
    }

    @PatchMapping("/cart/{productId}")
    public Map<String, Object> updateCart(@PathVariable String productId,
                                          @RequestBody CartUpdateRequest body,
                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        int quantity = Validation.requiredQuantity(body.quantity, "body", "quantity");
        return db.transaction(conn ->
                preselect.updateCart(conn, Jdbc.strOf(customer, "id"), false, productId, quantity));
    }

    @DeleteMapping("/cart/{productId}")
    public Map<String, Object> removeCart(@PathVariable String productId,
                                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn ->
                preselect.removeCart(conn, Jdbc.strOf(customer, "id"), false, productId));
    }

    /** 购物车多选转收藏（FR-024）。 */
    @PostMapping("/cart/favorites")
    public Map<String, Object> cartToFavorites(@RequestBody BatchProductRequest body,
                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn -> preselect.moveToFavorites(conn,
                Jdbc.strOf(customer, "id"), false, Validation.productIds(body.productIds)));
    }

    // --------------------------- 订单（FR-023 / FR-025 / FR-027 / FR-028 / FR-030） ---------------------------

    /** 从购物车多选下单（下单必经购物车，TBD-14），必须选择收货地址（FR-048）。 */
    @PostMapping("/orders")
    public Map<String, Object> checkout(@RequestBody CheckoutRequest body,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn -> orders.checkout(conn, Jdbc.strOf(customer, "id"),
                Validation.productIds(body.productIds), body.addressId));
    }

    @GetMapping("/orders")
    public Map<String, Object> myOrders(@RequestParam(value = "page", required = false) String page,
                                        @RequestParam(value = "page_size", required = false) String pageSize,
                                        @RequestParam(value = "status", required = false) String status,
                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        return db.read(conn -> orders.listMine(conn, Jdbc.strOf(customer, "id"), current, size, status));
    }

    @GetMapping("/orders/{orderId}")
    public Map<String, Object> myOrder(@PathVariable String orderId,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.read(conn -> orders.detail(conn, Jdbc.strOf(customer, "id"), orderId));
    }

    /** 客户取消：仅「开始发货」前可用；已支付订单原路退款，占用库存回补（FR-028 / FR-044）。 */
    @PostMapping("/orders/{orderId}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable String orderId,
                                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn -> orders.cancel(conn, Jdbc.strOf(customer, "id"), orderId));
    }

    // --------------------------- 支付（FR-039 ~ FR-042） ---------------------------

    /** 发起支付：待支付订单选择支付方式；失败保持待支付可重试。 */
    @PostMapping("/orders/{orderId}/pay")
    public Map<String, Object> pay(@PathVariable String orderId, @RequestBody PayRequest body,
                                   @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn ->
                payments.pay(conn, Jdbc.strOf(customer, "id"), orderId, body));
    }

    // --------------------------- 收货地址（FR-046 ~ FR-048） ---------------------------

    @GetMapping("/addresses")
    public Map<String, Object> listAddresses(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.read(conn -> addresses.list(conn, Jdbc.strOf(customer, "id")));
    }

    @PostMapping("/addresses")
    public Map<String, Object> createAddress(@RequestBody AddressRequest body,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn -> addresses.create(conn, Jdbc.strOf(customer, "id"), body));
    }

    @PatchMapping("/addresses/{addressId}")
    public Map<String, Object> updateAddress(@PathVariable String addressId, @RequestBody AddressRequest body,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn -> addresses.update(conn, Jdbc.strOf(customer, "id"), addressId, body));
    }

    @DeleteMapping("/addresses/{addressId}")
    public Map<String, Object> removeAddress(@PathVariable String addressId,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn -> addresses.remove(conn, Jdbc.strOf(customer, "id"), addressId));
    }

    @PostMapping("/addresses/{addressId}/default")
    public Map<String, Object> setDefaultAddress(@PathVariable String addressId,
                                                 @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn -> addresses.setDefault(conn, Jdbc.strOf(customer, "id"), addressId));
    }

    // --------------------------- 物流（FR-050） ---------------------------

    @GetMapping("/orders/{orderId}/shipment")
    public Map<String, Object> orderShipment(@PathVariable String orderId,
                                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.read(conn -> shipments.query(conn, Jdbc.strOf(customer, "id"), orderId));
    }

    // --------------------------- 售后（FR-051 / FR-052） ---------------------------

    @GetMapping("/after-sales")
    public Map<String, Object> listAfterSales(@RequestParam(value = "page", required = false) String page,
                                              @RequestParam(value = "page_size", required = false) String pageSize,
                                              @RequestParam(value = "status", required = false) String status,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        return db.read(conn ->
                afterSales.list(conn, Jdbc.strOf(customer, "id"), status, current, size));
    }

    @PostMapping("/after-sales")
    public Map<String, Object> createAfterSale(@RequestBody AfterSaleRequest body,
                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.transaction(conn -> afterSales.create(conn, Jdbc.strOf(customer, "id"), body));
    }

    @GetMapping("/after-sales/{afterSaleId}")
    public Map<String, Object> afterSaleDetail(@PathVariable String afterSaleId,
                                               @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> customer = auth.requireCustomer(authorization);
        return db.read(conn -> afterSales.detail(conn, Jdbc.strOf(customer, "id"), afterSaleId));
    }
}
