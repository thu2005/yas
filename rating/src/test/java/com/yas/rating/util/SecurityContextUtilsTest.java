package com.yas.rating.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import java.lang.reflect.Constructor;

class SecurityContextUtilsTest {

    @Test
    void testConstructorAndStaticMethod() throws Exception {
        // 1. Phủ Constructor private
        Constructor<SecurityContextUtils> constructor = SecurityContextUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());

        // 2. Phủ hàm setUpSecurityContext
        SecurityContextUtils.setUpSecurityContext("ngoc-user");
        
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("ngoc-user", SecurityContextHolder.getContext().getAuthentication().getName());
        
        SecurityContextHolder.clearContext();
    }

    @Test
    void testConstants_Coverage() throws Exception {
        Constructor<com.yas.rating.utils.Constants> constructor = 
            com.yas.rating.utils.Constants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}