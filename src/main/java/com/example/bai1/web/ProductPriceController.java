package com.example.bai1.web;

import com.example.bai1.service.ProductPriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductPriceController {

    private final ProductPriceService productPriceService;

    public ProductPriceController(ProductPriceService productPriceService) {
        this.productPriceService = productPriceService;
    }

    @GetMapping("/{productId}/price")
    public Integer getPrice(@PathVariable String productId) {
        return productPriceService.getProductPrice(productId);
    }

    @PutMapping("/{productId}/price")
    public void updatePrice(@PathVariable String productId, @RequestBody Integer newPrice) {
        productPriceService.updateProductPrice(productId, newPrice);
    }
}
