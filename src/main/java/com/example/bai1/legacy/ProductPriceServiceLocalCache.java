package com.example.bai1.legacy;

import com.example.bai1.repository.ProductRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * BAN GOC (giu nguyen theo de bai) - KHONG duoc dung trong production.
 *
 * Day chinh la nguyen nhan gay ra hien tuong "gia khong dong nhat" duoc mo ta
 * trong bao cao BAO_CAO_PHAN_TICH.md: moi instance Spring Boot co MOT bien
 * localPriceCache rieng, song trong heap cua chinh instance do. Khi 10 instance
 * chay song song (VD sau load balancer), moi instance se tao ra 10 ban sao
 * HashMap doc lap, khong co bat ky co che dong bo / thong bao nao giua chung.
 */
@Service
public class ProductPriceServiceLocalCache {

    // Local Cache - moi instance co mot ban sao rieng
    private final Map<String, Integer> localPriceCache = new HashMap<>();

    private final ProductRepository productRepository;

    public ProductPriceServiceLocalCache(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Integer getProductPrice(String productId) {
        if (localPriceCache.containsKey(productId)) {
            return localPriceCache.get(productId);
        }
        Integer price = productRepository.findPriceById(productId);
        localPriceCache.put(productId, price);
        return price;
    }

    public void updateProductPrice(String productId, Integer newPrice) {
        productRepository.updatePrice(productId, newPrice);
        localPriceCache.put(productId, newPrice);
    }
}
