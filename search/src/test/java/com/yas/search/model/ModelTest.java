package com.yas.search.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.yas.search.constant.enums.SortType;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void testProduct_builderAndGettersSetters() {
        ZonedDateTime now = ZonedDateTime.now();
        Product product = Product.builder()
            .id(1L)
            .name("Laptop")
            .slug("laptop")
            .price(999.99)
            .isPublished(true)
            .isVisibleIndividually(true)
            .isAllowedToOrder(true)
            .isFeatured(false)
            .thumbnailMediaId(42L)
            .brand("Dell")
            .categories(List.of("Electronics", "Computers"))
            .attributes(List.of("RAM: 16GB"))
            .createdOn(now)
            .build();

        assertEquals(1L, product.getId());
        assertEquals("Laptop", product.getName());
        assertEquals("laptop", product.getSlug());
        assertEquals(999.99, product.getPrice());
        assertEquals(true, product.getIsPublished());
        assertEquals(true, product.getIsVisibleIndividually());
        assertEquals(true, product.getIsAllowedToOrder());
        assertEquals(false, product.getIsFeatured());
        assertEquals(42L, product.getThumbnailMediaId());
        assertEquals("Dell", product.getBrand());
        assertEquals(2, product.getCategories().size());
        assertEquals(1, product.getAttributes().size());
        assertEquals(now, product.getCreatedOn());
    }

    @Test
    void testProduct_noArgConstructorAndSetters() {
        Product product = new Product();
        product.setId(2L);
        product.setName("Phone");
        product.setSlug("phone");
        product.setPrice(499.99);
        product.setIsPublished(false);
        product.setIsVisibleIndividually(false);
        product.setIsAllowedToOrder(false);
        product.setIsFeatured(true);
        product.setThumbnailMediaId(10L);
        product.setBrand("Samsung");
        product.setCategories(List.of("Mobile"));
        product.setAttributes(List.of("Color: White"));
        ZonedDateTime now = ZonedDateTime.now();
        product.setCreatedOn(now);

        assertEquals(2L, product.getId());
        assertEquals("Phone", product.getName());
        assertEquals("phone", product.getSlug());
        assertEquals(499.99, product.getPrice());
        assertEquals(false, product.getIsPublished());
        assertEquals(false, product.getIsVisibleIndividually());
        assertEquals(false, product.getIsAllowedToOrder());
        assertEquals(true, product.getIsFeatured());
        assertEquals(10L, product.getThumbnailMediaId());
        assertEquals("Samsung", product.getBrand());
        assertEquals(List.of("Mobile"), product.getCategories());
        assertEquals(List.of("Color: White"), product.getAttributes());
        assertEquals(now, product.getCreatedOn());
    }

    @Test
    void testProduct_allArgsConstructor() {
        ZonedDateTime now = ZonedDateTime.now();
        Product product = new Product(
            3L, "Tablet", "tablet", 299.0,
            true, true, true, false,
            20L, "Apple",
            List.of("Electronics"), List.of("Screen: 10in"), now
        );

        assertEquals(3L, product.getId());
        assertEquals("Tablet", product.getName());
    }

    @Test
    void testProductCriteriaDto_record() {
        ProductCriteriaDto dto = new ProductCriteriaDto(
            "laptop", 0, 10, "Dell", "Electronics",
            "RAM", 500.0, 2000.0, SortType.PRICE_ASC
        );

        assertEquals("laptop", dto.keyword());
        assertEquals(0, dto.page());
        assertEquals(10, dto.size());
        assertEquals("Dell", dto.brand());
        assertEquals("Electronics", dto.category());
        assertEquals("RAM", dto.attribute());
        assertEquals(500.0, dto.minPrice());
        assertEquals(2000.0, dto.maxPrice());
        assertEquals(SortType.PRICE_ASC, dto.sortType());
    }

    @Test
    void testProductCriteriaDto_withNullOptionalFields() {
        ProductCriteriaDto dto = new ProductCriteriaDto(
            "phone", 1, 20, null, null, null, null, null, SortType.DEFAULT
        );

        assertEquals("phone", dto.keyword());
        assertNull(dto.brand());
        assertNull(dto.category());
        assertNull(dto.attribute());
        assertNull(dto.minPrice());
        assertNull(dto.maxPrice());
    }
}
