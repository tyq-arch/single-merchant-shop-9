package com.shop.web;

import com.shop.db.Db;
import com.shop.dto.BatchProductRequest;
import com.shop.dto.CartAddRequest;
import com.shop.dto.CartUpdateRequest;
import com.shop.dto.FavoriteRequest;
import com.shop.errors.ApiException;
import com.shop.services.PreselectService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 游客临时预选接口（FR-022 / TBD-12）：未登录可临时收藏、加购、多选转收藏；
 * 以 X-Guest-Id 标识匿名会话，登录时自动并入账号。**下单必须登录**（TBD-14）。
 */
@RestController
@RequestMapping("/api/guest")
public class GuestController {

    private final Db db;
    private final PreselectService preselect;

    public GuestController(Db db, PreselectService preselect) {
        this.db = db;
        this.preselect = preselect;
    }

    // --------------------------- 收藏 ---------------------------

    @GetMapping("/favorites")
    public Map<String, Object> listFavorites(
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String guest = Validation.guestId(guestId);
        return db.read(conn -> preselect.listFavorites(conn, guest, true, 1, PreselectService.MAX_FAVORITES));
    }

    @PostMapping("/favorites")
    public Map<String, Object> addFavorite(@RequestBody FavoriteRequest body,
                                           @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String guest = Validation.guestId(guestId);
        String productId = Validation.text(body.productId, "body", "product_id", 1, 64, "商品 ID");
        return db.transaction(conn -> preselect.addFavorite(conn, guest, true, productId));
    }

    @DeleteMapping("/favorites/{productId}")
    public Map<String, Object> removeFavorite(@PathVariable String productId,
                                              @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String guest = Validation.guestId(guestId);
        return db.transaction(conn -> preselect.removeFavorite(conn, guest, true, productId));
    }

    // --------------------------- 购物车 ---------------------------

    @GetMapping("/cart")
    public Map<String, Object> listCart(
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String guest = Validation.guestId(guestId);
        return db.read(conn -> preselect.listCart(conn, guest, true));
    }

    @PostMapping("/cart")
    public Map<String, Object> addCart(@RequestBody CartAddRequest body,
                                       @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String guest = Validation.guestId(guestId);
        String productId = Validation.text(body.productId, "body", "product_id", 1, 64, "商品 ID");
        int quantity = Validation.quantity(body.quantity, "body", "quantity");
        return db.transaction(conn -> preselect.addCart(conn, guest, true, productId, quantity));
    }

    @PatchMapping("/cart/{productId}")
    public Map<String, Object> updateCart(@PathVariable String productId,
                                          @RequestBody CartUpdateRequest body,
                                          @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String guest = Validation.guestId(guestId);
        int quantity = Validation.requiredQuantity(body.quantity, "body", "quantity");
        return db.transaction(conn -> preselect.updateCart(conn, guest, true, productId, quantity));
    }

    @DeleteMapping("/cart/{productId}")
    public Map<String, Object> removeCart(@PathVariable String productId,
                                          @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String guest = Validation.guestId(guestId);
        return db.transaction(conn -> preselect.removeCart(conn, guest, true, productId));
    }

    /** 游客多选转收藏（临时，登录后随游客数据一并合并）。 */
    @PostMapping("/cart/favorites")
    public Map<String, Object> cartToFavorites(@RequestBody BatchProductRequest body,
                                               @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        String guest = Validation.guestId(guestId);
        return db.transaction(conn -> preselect.moveToFavorites(conn,
                guest, true, Validation.productIds(body.productIds)));
    }

    /** 下单必须登录：明确提示引导注册/登录（FR-022 / TBD-14）。 */
    @PostMapping("/orders")
    public Map<String, Object> checkout(
            @RequestHeader(value = "X-Guest-Id", required = false) String guestId) {
        throw new ApiException(401, "UNAUTHORIZED", "下单必须登录，请先注册或登录");
    }
}
