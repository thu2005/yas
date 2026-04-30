package com.yas.search.constant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yas.search.constant.enums.SortType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class ConstantTest {

    @Test
    void testActionConstants() {
        assertEquals("u", Action.UPDATE);
        assertEquals("c", Action.CREATE);
        assertEquals("d", Action.DELETE);
        assertEquals("r", Action.READ);
    }

    @Test
    void testMessageCodeConstants() {
        assertEquals("PRODUCT_NOT_FOUND", MessageCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void testProductFieldConstants() {
        assertEquals("name", ProductField.NAME);
        assertEquals("brand", ProductField.BRAND);
        assertEquals("price", ProductField.PRICE);
        assertEquals("isPublished", ProductField.IS_PUBLISHED);
        assertEquals("categories", ProductField.CATEGORIES);
        assertEquals("attributes", ProductField.ATTRIBUTES);
        assertEquals("createdOn", ProductField.CREATE_ON);
    }

    @Test
    void testProductFieldConstructor_throwsUnsupportedOperationException() throws Exception {
        Constructor<ProductField> constructor = ProductField.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertEquals(UnsupportedOperationException.class, ex.getCause().getClass());
    }

    @Test
    void testSortTypeValues() {
        SortType[] values = SortType.values();
        assertEquals(3, values.length);
        assertEquals(SortType.DEFAULT, SortType.valueOf("DEFAULT"));
        assertEquals(SortType.PRICE_ASC, SortType.valueOf("PRICE_ASC"));
        assertEquals(SortType.PRICE_DESC, SortType.valueOf("PRICE_DESC"));
    }
}
