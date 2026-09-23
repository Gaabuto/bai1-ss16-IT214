package com.example.bai1.service;

import com.example.bai1.exception.InvalidProductIdException;
import com.example.bai1.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Ban da SUA: bo local HashMap, dung Redis (distributed cache) qua Spring
 * Cache Abstraction. Vi Redis la mot server rieng biet, ma TAT CA 10 instance
 * cung ket noi toi, nen du lieu trong cache la DUY NHAT va DONG BO cho toan bo
 * cluster - khi mot instance ghi/xoa cache, 9 instance con lai thay ngay.
 *
 * - @Cacheable: lan doc dau tien moi instance se MISS va nap gia tu DB vao
 *   Redis; tu lan thu 2 (o BAT KY instance nao) deu HIT thang len Redis.
 * - @CacheEvict: khi cap nhat gia, key tuong ung bi xoa khoi Redis ngay lap
 *   tuc -> lan doc ke tiep (o moi instance) buoc phai MISS va nap lai gia
 *   moi nhat tu DB, khong con instance nao giu gia cu.
 * - condition: khong cache (khong tao key rac) neu productId null/rong;
 *   validateProductId() van chay truoc trong than method de fail-fast bang
 *   exception ro rang thay vi tra ve null im lang.
 */
@Service
public class ProductPriceService {

    private static final String CACHE_NAME = "productPrice";

    private final ProductRepository productRepository;

    public ProductPriceService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Cacheable(
            value = CACHE_NAME,
            key = "#productId",
            condition = "#productId != null && !#productId.isBlank()",
            unless = "#result == null")
    public Integer getProductPrice(String productId) {
        validateProductId(productId);
        return productRepository.findPriceById(productId);
    }

    @CacheEvict(value = CACHE_NAME, key = "#productId")
    public void updateProductPrice(String productId, Integer newPrice) {
        validateProductId(productId);
        if (newPrice == null || newPrice < 0) {
            throw new IllegalArgumentException("newPrice phai la mot so khong am");
        }
        productRepository.updatePrice(productId, newPrice);
    }

    private void validateProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            throw new InvalidProductIdException("productId khong duoc null hoac rong");
        }
    }
}
