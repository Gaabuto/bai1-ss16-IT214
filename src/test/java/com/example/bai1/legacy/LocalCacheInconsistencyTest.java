package com.example.bai1.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bai1.repository.InMemoryProductRepository;
import com.example.bai1.repository.ProductRepository;
import org.junit.jupiter.api.Test;

/**
 * Chung minh bang code chay thuc te hien tuong "gia khong dong nhat" mo ta trong
 * BAO_CAO_PHAN_TICH.md. instanceA/instanceB dai dien cho 2 (trong 10) instance
 * Spring Boot: chung tro toi CUNG MOT database (sharedDb) nhung moi instance tu
 * tao mot HashMap localPriceCache RIENG trong heap cua minh - dung nhu khi chay
 * tren 2 pod/container khac nhau.
 */
class LocalCacheInconsistencyTest {

    @Test
    void staleLocalCache_causesDifferentInstancesToShowDifferentPrices() {
        ProductRepository sharedDb = new InMemoryProductRepository();
        ProductPriceServiceLocalCache instanceA = new ProductPriceServiceLocalCache(sharedDb);
        ProductPriceServiceLocalCache instanceB = new ProductPriceServiceLocalCache(sharedDb);

        // T0: ca hai instance cung phuc vu request doc gia P001 lan dau -> cung nap
        // gia goc 100_000 tu DB vao HashMap rieng cua tung instance.
        assertThat(instanceA.getProductPrice("P001")).isEqualTo(100_000);
        assertThat(instanceB.getProductPrice("P001")).isEqualTo(100_000);

        // T1: bo phan Marketing bam nut giam gia Flash Sale, request cap nhat gia
        // (80_000) tinh cho load balancer route toi instanceA.
        instanceA.updateProductPrice("P001", 80_000);

        // T2: DB (nguon chung) da la 80_000, va HashMap cua instanceA cung da duoc
        // cap nhat theo - vi no tu ghi de cache cua chinh minh trong updateProductPrice().
        assertThat(sharedDb.findPriceById("P001")).isEqualTo(80_000);
        assertThat(instanceA.getProductPrice("P001")).isEqualTo(80_000);

        // T3: BUG - instanceB KHONG he biet gi ve viec cap nhat nay (khong co co che
        // dong bo giua cac HashMap doc lap), nen van tra ve gia CU 100_000 duoc luu
        // tu T0 -> hai khach hang cung xem P001 cung thoi diem se thay 2 gia khac nhau.
        assertThat(instanceB.getProductPrice("P001")).isEqualTo(100_000);
    }
}
