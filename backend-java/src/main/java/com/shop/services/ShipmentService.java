package com.shop.services;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.shop.db.Jdbc;
import com.shop.enums.Enums;
import com.shop.errors.ConflictException;
import com.shop.errors.NotFoundException;
import com.shop.serializers.Serializers;
import com.shop.util.Utils;

/**
 * 配送物流（FR-050 / TBD-09）：发货时录入物流服务商与运单号，物流状态由商家手动单向更新
 * （已发货 → 运输中 → 派送中 → 已签收），客户可查看发货后的物流轨迹。
 */
@Service
public class ShipmentService {

    /** 客户查看物流（FR-050）：订单存在但未发货时提示尚未发货。 */
    public Map<String, Object> query(Connection conn, String customerId, String orderId) throws Exception {
        Map<String, Object> order = Jdbc.queryOne(conn,
                "SELECT id FROM shop_order WHERE id = ? AND customer_id = ?", orderId, customerId);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        Map<String, Object> row = Jdbc.queryOne(conn,
                "SELECT * FROM shipment WHERE order_id = ?", orderId);
        if (row == null) {
            throw new NotFoundException("订单尚未发货，暂无物流信息");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shipment", build(conn, row));
        return result;
    }

    /** 序列化物流（含轨迹），供订单详情内嵌展示。 */
    public Map<String, Object> build(Connection conn, Map<String, Object> row) throws Exception {
        Map<String, Object> shipment = Serializers.shipment(row);
        List<Map<String, Object>> traces = new ArrayList<>();
        for (Map<String, Object> track : Jdbc.query(conn,
                "SELECT * FROM shipment_track WHERE shipment_id = ? ORDER BY occurred_at ASC, rowid ASC",
                Jdbc.strOf(row, "id"))) {
            traces.add(Serializers.shipmentTrack(track));
        }
        shipment.put("traces", traces);
        shipment.put("tracks", traces);
        return shipment;
    }

    /** 卖家发货：创建物流记录与首条「已发货」轨迹，返回序列化后的物流。 */
    public Map<String, Object> create(Connection conn, String orderId, String carrier, String trackingNo)
            throws Exception {
        Map<String, Object> order = Jdbc.queryOne(conn, "SELECT id FROM shop_order WHERE id = ?", orderId);
        if (order == null) {
            throw new NotFoundException("订单不存在");
        }
        Map<String, Object> existing = Jdbc.queryOne(conn, "SELECT * FROM shipment WHERE order_id = ?", orderId);
        if (existing != null) {
            throw new ConflictException("该订单已发货，不能重复录入物流信息");
        }
        String now = Utils.nowIso();
        String shipmentId = Utils.newId();
        Jdbc.update(conn,
                "INSERT INTO shipment (id, order_id, carrier, tracking_no, status, shipped_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                shipmentId, orderId, carrier, trackingNo, Enums.ShipmentStatus.SHIPPED, now, now);
        Jdbc.update(conn,
                "INSERT INTO shipment_track (id, shipment_id, status, description, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                Utils.newId(), shipmentId, Enums.ShipmentStatus.SHIPPED, "商家已发货，等待物流揽收", now);
        return build(conn, Jdbc.queryOne(conn, "SELECT * FROM shipment WHERE id = ?", shipmentId));
    }

    /** 卖家更新物流状态：只能按单向流转推进到下一节点（TBD-09）。 */
    public Map<String, Object> updateStatus(Connection conn, String orderId, String status) throws Exception {
        Map<String, Object> row = Jdbc.queryOne(conn, "SELECT * FROM shipment WHERE order_id = ?", orderId);
        if (row == null) {
            throw new NotFoundException("订单尚未发货，无法更新物流状态");
        }
        String current = Jdbc.strOf(row, "status");
        String expected = Enums.ShipmentStatus.NEXT.get(current);
        if (expected == null) {
            throw new ConflictException("当前物流状态「" + Enums.ShipmentStatus.label(current)
                    + "」已到终点，无法继续更新");
        }
        if (!expected.equals(status)) {
            throw new ConflictException("物流状态只能由「" + Enums.ShipmentStatus.label(current)
                    + "」更新为「" + Enums.ShipmentStatus.label(expected) + "」");
        }
        String now = Utils.nowIso();
        Jdbc.update(conn, "UPDATE shipment SET status = ?, updated_at = ? WHERE id = ?",
                status, now, Jdbc.strOf(row, "id"));
        Jdbc.update(conn,
                "INSERT INTO shipment_track (id, shipment_id, status, description, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                Utils.newId(), Jdbc.strOf(row, "id"), status, descriptionOf(status), now);
        return build(conn, Jdbc.queryOne(conn, "SELECT * FROM shipment WHERE id = ?", Jdbc.strOf(row, "id")));
    }

    private String descriptionOf(String status) {
        if (Enums.ShipmentStatus.IN_TRANSIT.equals(status)) {
            return "包裹正在运输途中";
        }
        if (Enums.ShipmentStatus.DELIVERING.equals(status)) {
            return "包裹已到达派送网点，正在派送";
        }
        if (Enums.ShipmentStatus.DELIVERED.equals(status)) {
            return "包裹已签收";
        }
        return Enums.ShipmentStatus.label(status);
    }
}