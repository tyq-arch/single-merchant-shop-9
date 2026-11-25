package com.shop.services;

import com.shop.db.Jdbc;
import com.shop.errors.NotFoundException;
import com.shop.serializers.Serializers;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 物流查询（FR-050）：客户查看发货后的物流服务商、运单号与轨迹。 */
@Service
public class ShipmentService {

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
        Map<String, Object> shipment = Serializers.shipment(row);
        List<Map<String, Object>> tracks = new ArrayList<>();
        for (Map<String, Object> track : Jdbc.query(conn,
                "SELECT * FROM shipment_track WHERE shipment_id = ? ORDER BY occurred_at ASC, rowid ASC",
                Jdbc.strOf(row, "id"))) {
            tracks.add(Serializers.shipmentTrack(track));
        }
        shipment.put("tracks", tracks);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shipment", shipment);
        return result;
    }
}
