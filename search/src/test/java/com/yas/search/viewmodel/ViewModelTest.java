package com.yas.search.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.yas.search.model.Product;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ViewModelTest {

    @Test
    void testProductGetVm_fromModel_mapsAllFields() {
        ZonedDateTime now = ZonedDateTime.now();
        Product product = Product.builder()
            .id(1L)
            .name("Test Product")
            .slug("test-product")
            .thumbnailMediaId(100L)
            .price(29.99)
            .isAllowedToOrder(true)
            .isPublished(true)
            .isFeatured(false)
            .isVisibleIndividually(true)
            .createdOn(now)
            .build();

        ProductGetVm result = ProductGetVm.fromModel(product);

        assertEquals(1L, result.id());
        assertEquals("Test Product", result.name());
        assertEquals("test-product", result.slug());
        assertEquals(100L, result.thumbnailId());
        assertEquals(29.99, result.price());
        assertEquals(true, result.isAllowedToOrder());
        assertEquals(true, result.isPublished());
        assertEquals(false, result.isFeatured());
        assertEquals(true, result.isVisibleIndividually());
        assertEquals(now, result.createdOn());
    }

    @Test
    void testProductGetVm_fromModel_nullFields() {
        Product product = Product.builder().build();

        ProductGetVm result = ProductGetVm.fromModel(product);

        assertNull(result.id());
        assertNull(result.name());
        assertNull(result.slug());
        assertNull(result.thumbnailId());
        assertNull(result.price());
        assertNull(result.isAllowedToOrder());
        assertNull(result.isPublished());
        assertNull(result.isFeatured());
        assertNull(result.isVisibleIndividually());
        assertNull(result.createdOn());
    }

    @Test
    void testProductNameGetVm_fromModel() {
        Product product = Product.builder().name("Laptop").build();

        ProductNameGetVm result = ProductNameGetVm.fromModel(product);

        assertEquals("Laptop", result.name());
    }

    @Test
    void testProductNameGetVm_fromModel_nullName() {
        Product product = Product.builder().build();

        ProductNameGetVm result = ProductNameGetVm.fromModel(product);

        assertNull(result.name());
    }

    @Test
    void testProductNameListVm_record() {
        List<ProductNameGetVm> names = List.of(
            new ProductNameGetVm("Product A"),
            new ProductNameGetVm("Product B")
        );

        ProductNameListVm vm = new ProductNameListVm(names);

        assertEquals(2, vm.productNames().size());
        assertEquals("Product A", vm.productNames().get(0).name());
        assertEquals("Product B", vm.productNames().get(1).name());
    }

    @Test
    void testProductListGetVm_record() {
        ProductGetVm productGetVm = new ProductGetVm(
            1L, "P", "p-slug", 10L, 9.99, true, true, false, true, ZonedDateTime.now()
        );
        Map<String, Map<String, Long>> aggregations = Map.of(
            "brands", Map.of("Nike", 5L)
        );
        ProductListGetVm vm = new ProductListGetVm(
            List.of(productGetVm), 0, 10, 100, 10, false, aggregations
        );

        assertEquals(1, vm.products().size());
        assertEquals(0, vm.pageNo());
        assertEquals(10, vm.pageSize());
        assertEquals(100, vm.totalElements());
        assertEquals(10, vm.totalPages());
        assertEquals(false, vm.isLast());
        assertEquals(5L, vm.aggregations().get("brands").get("Nike"));
    }

    @Test
    void testProductEsDetailVm_record() {
        ProductEsDetailVm vm = new ProductEsDetailVm(
            1L, "Phone", "phone-slug", 199.99,
            true, true, true, false,
            200L, "Samsung",
            List.of("Electronics"), List.of("Color: Black")
        );

        assertEquals(1L, vm.id());
        assertEquals("Phone", vm.name());
        assertEquals("phone-slug", vm.slug());
        assertEquals(199.99, vm.price());
        assertEquals(true, vm.isPublished());
        assertEquals(true, vm.isVisibleIndividually());
        assertEquals(true, vm.isAllowedToOrder());
        assertEquals(false, vm.isFeatured());
        assertEquals(200L, vm.thumbnailMediaId());
        assertEquals("Samsung", vm.brand());
        assertEquals(List.of("Electronics"), vm.categories());
        assertEquals(List.of("Color: Black"), vm.attributes());
    }
}
