package com.yas.rating;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
class RatingApplicationTest {

    @Test
    void contextLoads() {
        // It is suppose to be empty
    }

    @Test
    void mainMethodTest() {
        // Gọi trực tiếp hàm main với tham số trống để phủ dòng code khởi chạy
        assertDoesNotThrow(() -> RatingApplication.main(new String[]{}));
    }

}