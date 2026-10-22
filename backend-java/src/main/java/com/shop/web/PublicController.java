package com.shop.web;

import com.shop.config.AppConfig;
import com.shop.db.Db;
import com.shop.db.Jdbc;
import com.shop.enums.Enums;
import com.shop.errors.NotFoundException;
import com.shop.services.CategoryService;
import com.shop.services.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 公开接口：商品浏览、关键词/类别检索、类别树。游客无需登录。 */
@RestController
@RequestMapping("/api")
public class PublicController {

    private final Db db;
    private final AppConfig config;
    private final ProductService products;
    private final CategoryService categories;

    public PublicController(Db db, AppConfig config, ProductService products, CategoryService categories) {
        this.db = db;
        this.config = config;
        this.products = products;
        this.categories = categories;
    }

    /** 商品列表/搜索：仅展示在售与交易中商品（FR-010 / FR-019）。 */
    @GetMapping("/products")
    public Map<String, Object> list(@RequestParam(value = "page", required = false) String page,
                                    @RequestParam(value = "page_size", required = false) String pageSize,
                                    @RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "category_id", required = false) String categoryId) {
        int current = Validation.page(page);
        int size = Validation.pageSize(pageSize, config.getDefaultPageSize(), config.getMaxPageSize());
        return db.read(conn -> products.listPublic(conn, current, size, keyword, categoryId));
    }

    @GetMapping("/products/{productId}")
    public Map<String, Object> detail(@PathVariable String productId) {
        return db.read(conn -> {
            Map<String, Object> row = products.getProduct(conn, productId);
            if (row == null || Enums.ProductStatus.OFF_SHELF.equals(Jdbc.strOf(row, "status"))) {
                throw new NotFoundException("商品不存在或已下架");
            }
            Map<String, Object> product = products.full(conn, row);
            Long queueCount = Jdbc.scalarLong(conn,
                    "SELECT COUNT(*) AS c FROM queue_item WHERE product_id = ?", productId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("product", product);
            result.put("queue_count", queueCount == null ? 0 : queueCount);
            result.put("can_buy", Enums.ProductStatus.ON_SALE.equals(Jdbc.strOf(row, "status"))
                    && Jdbc.intOf(row, "stock") > 0);
            return result;
        });
    }

    @GetMapping("/categories")
    public Map<String, Object> categoryTree() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", db.read(categories::tree));
        return result;
    }
}
