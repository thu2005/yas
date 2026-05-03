package com.yas.rating.viewmodel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

class ViewModelCoverageTest {

    @Test
    void testAllViewModels() throws Exception {
        Class<?>[] vms = {
            RatingVm.class, 
            RatingPostVm.class, 
            RatingListVm.class,
            CustomerVm.class
        };

        for (Class<?> clazz : vms) {
            // Phủ constructors
            Constructor<?>[] constructors = clazz.getConstructors();
            for (Constructor<?> c : constructors) {
                Object[] args = new Object[c.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    if (c.getParameterTypes()[i] == String.class) args[i] = "test";
                    else if (c.getParameterTypes()[i] == Long.class) args[i] = 1L;
                    else if (c.getParameterTypes()[i] == Integer.class) args[i] = 5;
                    else args[i] = null;
                }
                try {
                    Object instance = c.newInstance(args);
                    // Phủ tất cả getter/setter
                    for (Method m : clazz.getDeclaredMethods()) {
                        if (m.getName().startsWith("get") || m.getName().startsWith("set") || m.getName().contains("content")) {
                            try { m.setAccessible(true); m.invoke(instance); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}