package com.example.bai1.repository;

/**
 * Nguon du lieu gia goc (single source of truth), duoc chia se giua TAT CA
 * cac instance cua ung dung - khac voi local cache, du lieu o day la NHAT QUAN
 * cho moi instance doc/ghi vao.
 */
public interface ProductRepository {

    Integer findPriceById(String productId);

    void updatePrice(String productId, Integer newPrice);
}
