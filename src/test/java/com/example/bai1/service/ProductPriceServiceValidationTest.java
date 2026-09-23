package com.example.bai1.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.bai1.exception.InvalidProductIdException;
import com.example.bai1.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiem tra yeu cau (c): productId null/rong phai fail-fast bang exception ro rang,
 * va TUYET DOI khong duoc goi xuong repository/Redis (tranh tao key rac).
 * Test nay goi truc tiep method Java (khong qua Spring AOP proxy) nen chua kich
 * hoat @Cacheable/@CacheEvict thuc su - do la muc dich: chi xac minh phan
 * validate fail-fast ben trong than method.
 */
@ExtendWith(MockitoExtension.class)
class ProductPriceServiceValidationTest {

    @Mock
    private ProductRepository productRepository;

    @Test
    void getProductPrice_withNullId_throwsAndNeverTouchesRepository() {
        ProductPriceService service = new ProductPriceService(productRepository);

        assertThatThrownBy(() -> service.getProductPrice(null))
                .isInstanceOf(InvalidProductIdException.class);

        verifyNoInteractions(productRepository);
    }

    @Test
    void getProductPrice_withBlankId_throwsAndNeverTouchesRepository() {
        ProductPriceService service = new ProductPriceService(productRepository);

        assertThatThrownBy(() -> service.getProductPrice("   "))
                .isInstanceOf(InvalidProductIdException.class);

        verifyNoInteractions(productRepository);
    }

    @Test
    void updateProductPrice_withNegativePrice_throwsAndNeverTouchesRepository() {
        ProductPriceService service = new ProductPriceService(productRepository);

        assertThatThrownBy(() -> service.updateProductPrice("P001", -1))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(productRepository);
    }

    @Test
    void getProductPrice_withValidId_delegatesToRepository() {
        when(productRepository.findPriceById("P001")).thenReturn(100_000);
        ProductPriceService service = new ProductPriceService(productRepository);

        assertThat(service.getProductPrice("P001")).isEqualTo(100_000);
    }
}
