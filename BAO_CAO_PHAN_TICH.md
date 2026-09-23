# Báo cáo phân tích: Lỗi giá không đồng nhất trong hệ thống Flash Sale

## 1. Nguyên nhân gây ra hiện tượng "giá không đồng nhất"

### 1.1 Phân tích kỹ thuật

Đoạn mã gốc dùng một `HashMap` cục bộ làm cache:

```java
private final Map<String, Integer> localPriceCache = new HashMap<>();
```

Biến `localPriceCache` này là một **field instance**, sống trong **heap của tiến trình JVM** đang
chạy `ProductPriceService`. Hệ thống Flash Sale được deploy trên **10 instance** độc lập (10 tiến
trình JVM, thường là 10 pod/container khác nhau đứng sau load balancer). Vì Java không chia sẻ
heap giữa các JVM, **mỗi instance tự tạo ra một bản sao `HashMap` riêng, hoàn toàn cách biệt** với
9 bản sao còn lại.

Hệ quả:

1. Database (nguồn dữ liệu gốc) là nơi *duy nhất* được chia sẻ giữa 10 instance, và nó luôn nhất
   quán sau khi `updatePrice()` được gọi.
2. Nhưng `updateProductPrice()` chỉ cập nhật `localPriceCache` của **instance đang xử lý request
   đó**. 9 instance còn lại **không có cơ chế nào** (không pub/sub, không TTL, không invalidation
   event) để biết rằng giá vừa thay đổi.
3. Do đó, sau một lần cập nhật giá, tại một thời điểm sẽ tồn tại đồng thời:
   - 1 instance có cache mới (giá đúng),
   - 9 instance còn lại vẫn giữ cache cũ (giá sai) — cho đến khi cache đó bị loại (evict) một cách
     tình cờ nào đó (mà đoạn mã gốc thậm chí không có TTL/eviction, nên trên thực tế **cache cũ sẽ
     tồn tại vĩnh viễn** cho tới khi instance đó restart).
4. Vì load balancer phân phối request người dùng ngẫu nhiên/round-robin qua 10 instance, hai
   người dùng gọi API xem giá tại cùng một thời điểm rất có thể rơi vào hai instance khác nhau —
   một người thấy giá cũ, một người thấy giá mới. Đây chính xác là hiện tượng khách hàng phản ánh:
   "cùng sản phẩm, người thấy 80.000đ, người thấy 100.000đ".

Gốc rễ: **cache không có tính chất "chia sẻ" (shared/distributed) trong khi hệ thống lại chạy
nhiều instance** — cache cục bộ chỉ đúng khi ứng dụng chạy **1 instance duy nhất**.

### 1.2 Test case minh họa (sản phẩm P001)

Giả sử `instanceA` và `instanceB` là 2 trong 10 instance, cùng trỏ tới một database dùng chung
(`sharedDb`), giá gốc P001 = 100.000đ.

| Mốc thời gian | Diễn biến | Trạng thái `sharedDb` | Cache `instanceA` | Cache `instanceB` |
|---|---|---|---|---|
| **T0** | Hai khách hàng gọi `GET /price/P001`, request được route tới `instanceA` và `instanceB`. Cả hai đều cache-miss, đọc DB, nạp vào cache riêng của mình. | 100.000 | 100.000 | 100.000 |
| **T1** | Admin chạy Flash Sale, giảm giá P001 xuống 80.000đ. Request `updateProductPrice` được route tới `instanceA`. | 80.000 | 80.000 (tự cập nhật cache của chính nó) | *(chưa hay biết gì)* 100.000 |
| **T2** | Khách hàng A gọi lại `GET /price/P001`, request rơi vào `instanceA`. | 80.000 | 80.000 | 100.000 |
| **T3** | Khách hàng B gọi `GET /price/P001` **cùng lúc**, nhưng request rơi vào `instanceB`. | 80.000 | 80.000 | **100.000 (SAI — vẫn là giá cũ)** |

=> Tại **T3**, khách hàng A và khách hàng B cùng xem sản phẩm P001 nhưng nhận về 2 giá khác nhau,
mặc dù database đã nhất quán từ T1. Test case này được triển khai thành một unit test chạy thật
tại
[`LocalCacheInconsistencyTest`](src/test/java/com/example/bai1/legacy/LocalCacheInconsistencyTest.java)
(package `legacy`), và **pass** — chứng minh bug tồn tại một cách khách quan, không chỉ trên giấy.

## 2. Giải pháp: chuyển sang Redis distributed cache

### 2.1 Ý tưởng

Thay `HashMap` cục bộ bằng **Redis** — một cache server độc lập mà **cả 10 instance cùng kết nối
tới**. Khi đó:

- Redis là nơi lưu cache **duy nhất**, không còn 10 bản sao rời rạc.
- Dùng Spring Cache Abstraction (`@Cacheable`, `@CacheEvict`) để không phải tự viết logic
  get/put/evict — Spring tự tạo AOP proxy bọc quanh method.
- Khi `instanceA` gọi `updateProductPrice()` → `@CacheEvict` xóa key `P001` khỏi Redis ngay lập
  tức → **bất kỳ instance nào** (kể cả B) đọc lại `getProductPrice("P001")` ngay sau đó đều bị
  cache-miss trên Redis và phải nạp lại giá **mới nhất** từ DB. Không còn instance nào giữ giá cũ.

### 2.2 Service đã sửa — [`ProductPriceService`](src/main/java/com/example/bai1/service/ProductPriceService.java)

```java
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
```

### 2.3 Cấu hình Redis Cache Manager — [`RedisCacheConfig`](src/main/java/com/example/bai1/config/RedisCacheConfig.java)

```java
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() { /* xem mục 3.1 */ };
    }
}
```

`entryTtl(5 phút)` là lớp phòng thủ thứ hai: dù `@CacheEvict` có lỗi/miss vì lý do nào đó, key vẫn
tự hết hạn sau tối đa 5 phút thay vì tồn tại "vĩnh viễn" như HashMap gốc.

## 3. Xử lý các tình huống ngoại lệ

### 3.1 Redis server bị ngắt kết nối

Mặc định, nếu Redis timeout/refuse connection, Spring Cache sẽ **ném exception** và làm request
thất bại (lỗi 500) — tệ hơn cả không có cache. Ta đăng ký một `CacheErrorHandler` tùy chỉnh (qua
`CachingConfigurer`) để **log cảnh báo và nuốt lỗi** (không rethrow):

```java
@Override
public CacheErrorHandler errorHandler() {
    return new CacheErrorHandler() {
        @Override
        public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
            log.warn("Redis GET loi (cache={}, key={}) - fallback: doc truc tiep tu DB. Ly do: {}",
                    cache.getName(), key, ex.getMessage());
        }
        // handleCachePutError, handleCacheEvictError, handleCacheClearError tương tự
    };
}
```

Khi `handleCacheGetError` không rethrow, Spring Cache coi đây như một **cache-miss** và tự động
**gọi tiếp method gốc** (`productRepository.findPriceById(...)`) — nghĩa là ứng dụng vẫn trả về
giá đúng, chỉ chậm hơn (vì mất cache) chứ **không sập hoàn toàn**. Tương tự, nếu `@CacheEvict` thất
bại vì Redis down, `handleCacheEvictError` nuốt lỗi để `updateProductPrice()` vẫn cập nhật DB thành
công (không chặn nghiệp vụ chính vì cache phụ trợ bị lỗi). Được kiểm chứng bằng
[`RedisCacheErrorHandlerTest`](src/test/java/com/example/bai1/config/RedisCacheErrorHandlerTest.java).

### 3.2 `productId` null hoặc chuỗi rỗng

Hai lớp phòng thủ:

1. **`condition` trong `@Cacheable`**: `condition = "#productId != null && !#productId.isBlank()"`
   — nếu `productId` null/rỗng, Spring Cache **bỏ qua hoàn toàn việc tra/ghi Redis** cho lần gọi
   đó, tránh tạo ra các key rác kiểu `productPrice::null` hay `productPrice::""` trên Redis.
2. **`validateProductId()` trong thân method**: luôn chạy trước khi đụng tới `productRepository`,
   ném `InvalidProductIdException` (kế thừa `IllegalArgumentException`) ngay lập tức — đúng tinh
   thần fail-fast, thay vì âm thầm trả về `null` khiến lỗi trôi xuống tầng dưới rất khó debug.

Được kiểm chứng bằng
[`ProductPriceServiceValidationTest`](src/test/java/com/example/bai1/service/ProductPriceServiceValidationTest.java)
(3 test: null id, blank id, negative price — cả 3 đều throw và `verifyNoInteractions(productRepository)`,
nghĩa là request bị chặn trước khi chạm DB/Redis).

## 4. Kết quả kiểm chứng

Chạy `./gradlew test` — toàn bộ 4 test class pass:

- `LocalCacheInconsistencyTest` — tái tạo được đúng bug gốc bằng code chạy thật (không chỉ mô tả).
- `ProductPriceServiceValidationTest` — validate fail-fast cho input không hợp lệ.
- `RedisCacheErrorHandlerTest` — xác nhận fallback không ném lỗi khi Redis down.
- `Bai1ApplicationTests` (`contextLoads`) — context Spring khởi động thành công với Redis
  auto-config (Lettuce kết nối lazy, không cần Redis server chạy sẵn lúc build/test).

**Giới hạn phạm vi:** môi trường phát triển hiện tại không có Redis server thật đang chạy (cần
Docker), nên chưa thực hiện được kiểm thử tích hợp end-to-end qua `bootRun` + `curl` thật với Redis
sống. Để kiểm thử đầy đủ, chạy `docker run -p 6379:6379 redis:7-alpine` rồi `./gradlew bootRun` và
gọi `GET/PUT http://localhost:8080/api/products/P001/price`.
