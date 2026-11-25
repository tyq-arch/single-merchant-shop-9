package com.shop.services;

import com.shop.db.Jdbc;
import com.shop.dto.AddressRequest;
import com.shop.errors.ConflictException;
import com.shop.errors.NotFoundException;
import com.shop.serializers.Serializers;
import com.shop.util.Utils;
import com.shop.web.Validation;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收货地址管理（FR-046 / FR-047 / FR-048）：多地址增删改、设置默认地址、下单选择地址。
 * 同一客户至多一个默认地址；删除默认地址时自动递补一条，保证下单可带出。
 */
@Service
public class AddressService {

    public Map<String, Object> list(Connection conn, String customerId) throws Exception {
        List<Map<String, Object>> rows = Jdbc.query(conn,
                "SELECT * FROM customer_address WHERE customer_id = ? "
                        + "ORDER BY is_default DESC, created_at DESC", customerId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(Serializers.address(row, false));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        return result;
    }

    public Map<String, Object> create(Connection conn, String customerId, AddressRequest body) throws Exception {
        String[] fields = validate(body, "body");
        boolean first = count(conn, customerId) == 0;
        boolean makeDefault = first || Boolean.TRUE.equals(body.isDefault);
        if (makeDefault) {
            clearDefault(conn, customerId);
        }
        String id = Utils.newId();
        String now = Utils.nowIso();
        Jdbc.update(conn,
                "INSERT INTO customer_address (id, customer_id, receiver_name, receiver_phone, province, city, "
                        + "district, detail, postal_code, is_default, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, customerId, fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6],
                makeDefault ? 1 : 0, now, now);
        return wrap(conn, customerId, id, "收货地址已保存");
    }

    public Map<String, Object> update(Connection conn, String customerId, String addressId, AddressRequest body)
            throws Exception {
        Map<String, Object> existing = requireOwned(conn, customerId, addressId);
        String[] fields = validate(body, "body");
        boolean makeDefault = Boolean.TRUE.equals(body.isDefault);
        if (makeDefault) {
            clearDefault(conn, customerId);
        }
        Jdbc.update(conn,
                "UPDATE customer_address SET receiver_name = ?, receiver_phone = ?, province = ?, city = ?, "
                        + "district = ?, detail = ?, postal_code = ?, is_default = ?, updated_at = ? "
                        + "WHERE id = ? AND customer_id = ?",
                fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6],
                makeDefault ? 1 : Jdbc.intOf(existing, "is_default"),
                Utils.nowIso(), addressId, customerId);
        return wrap(conn, customerId, addressId, "收货地址已更新");
    }

    public Map<String, Object> remove(Connection conn, String customerId, String addressId) throws Exception {
        Map<String, Object> row = requireOwned(conn, customerId, addressId);
        Jdbc.update(conn, "DELETE FROM customer_address WHERE id = ? AND customer_id = ?", addressId, customerId);
        if (Jdbc.intOf(row, "is_default") == 1) {
            Map<String, Object> next = Jdbc.queryOne(conn,
                    "SELECT id FROM customer_address WHERE customer_id = ? ORDER BY created_at ASC LIMIT 1",
                    customerId);
            if (next != null) {
                Jdbc.update(conn, "UPDATE customer_address SET is_default = 1, updated_at = ? WHERE id = ?",
                        Utils.nowIso(), Jdbc.strOf(next, "id"));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "收货地址已删除");
        return result;
    }

    public Map<String, Object> setDefault(Connection conn, String customerId, String addressId) throws Exception {
        requireOwned(conn, customerId, addressId);
        clearDefault(conn, customerId);
        Jdbc.update(conn, "UPDATE customer_address SET is_default = 1, updated_at = ? WHERE id = ?",
                Utils.nowIso(), addressId);
        return wrap(conn, customerId, addressId, "默认地址已设置");
    }

    /** 下单时解析地址：未指定则带出默认地址；无可用地址时提示先添加（FR-048）。 */
    public Map<String, Object> resolveForOrder(Connection conn, String customerId, String addressId)
            throws Exception {
        Map<String, Object> row;
        if (addressId == null || addressId.trim().isEmpty()) {
            row = Jdbc.queryOne(conn,
                    "SELECT * FROM customer_address WHERE customer_id = ? ORDER BY is_default DESC, created_at ASC "
                            + "LIMIT 1", customerId);
            if (row == null) {
                throw new ConflictException("请先在「收货地址」中添加地址后再下单");
            }
        } else {
            row = Jdbc.queryOne(conn, "SELECT * FROM customer_address WHERE id = ? AND customer_id = ?",
                    addressId.trim(), customerId);
            if (row == null) {
                throw new NotFoundException("收货地址不存在");
            }
        }
        return row;
    }

    private Map<String, Object> wrap(Connection conn, String customerId, String addressId, String message)
            throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT * FROM customer_address WHERE id = ? AND customer_id = ?", addressId, customerId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("address", Serializers.address(row, false));
        result.put("message", message);
        return result;
    }

    private Map<String, Object> requireOwned(Connection conn, String customerId, String addressId) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT * FROM customer_address WHERE id = ? AND customer_id = ?", addressId, customerId);
        if (row == null) {
            throw new NotFoundException("收货地址不存在");
        }
        return row;
    }

    private void clearDefault(Connection conn, String customerId) throws Exception {
        Jdbc.update(conn,
                "UPDATE customer_address SET is_default = 0, updated_at = ? WHERE customer_id = ? AND is_default = 1",
                Utils.nowIso(), customerId);
    }

    private int count(Connection conn, String customerId) throws Exception {
        Long total = Jdbc.scalarLong(conn,
                "SELECT COUNT(*) AS c FROM customer_address WHERE customer_id = ?", customerId);
        return total == null ? 0 : total.intValue();
    }

    /** 校验并归一化地址字段，返回 [收货人, 电话, 省, 市, 区, 详细地址, 邮编]。 */
    private String[] validate(AddressRequest body, String location) {
        if (body == null) {
            throw Validation.error(location, "body", "请求体不能为空");
        }
        String name = Validation.text(body.receiverName, location, "receiver_name", 1, 64, "收货人姓名");
        String phone = Validation.phone(body.receiverPhone, location, "receiver_phone");
        String province = Validation.text(body.province, location, "province", 1, 32, "省份");
        String city = Validation.text(body.city, location, "city", 1, 32, "城市");
        String district = Validation.text(body.district, location, "district", 1, 32, "区县");
        String detail = Validation.text(body.detail, location, "detail", 1, 200, "详细地址");
        String postalCode = null;
        if (body.postalCode != null && !body.postalCode.trim().isEmpty()) {
            postalCode = body.postalCode.trim();
            if (!postalCode.matches("\\d{6}")) {
                throw Validation.error(location, "postal_code", "邮政编码需为 6 位数字");
            }
        }
        return new String[]{name, phone, province, city, district, detail, postalCode};
    }
}
