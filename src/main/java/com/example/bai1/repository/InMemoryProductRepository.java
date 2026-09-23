package com.example.bai1.repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Gia lap database (VD: PostgreSQL/MySQL) bang mot ConcurrentHashMap dung chung.
 * Trong thuc te day se la mot database tap trung ma moi instance deu ket noi toi,
 * nen no luon la nguon du lieu nhat quan - van de nam o lop CACHE phia tren no.
 */
@Repository
public class InMemoryProductRepository implements ProductRepository {

    private final Map<String, Integer> priceTable = new ConcurrentHashMap<>();

    public InMemoryProductRepository() {
        priceTable.put("P001", 100_000);
        priceTable.put("P002", 250_000);
    }

    @Override
    public Integer findPriceById(String productId) {
        return priceTable.get(productId);
    }

    @Override
    public void updatePrice(String productId, Integer newPrice) {
        priceTable.put(productId, newPrice);
    }
}
