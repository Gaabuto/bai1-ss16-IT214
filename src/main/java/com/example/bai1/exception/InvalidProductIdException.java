package com.example.bai1.exception;

/**
 * Nem ra ngay khi productId null/rong, truoc khi cham vao cache hoac database,
 * de tranh tao "key rac" tren Redis (fail-fast).
 */
public class InvalidProductIdException extends IllegalArgumentException {

    public InvalidProductIdException(String message) {
        super(message);
    }
}
