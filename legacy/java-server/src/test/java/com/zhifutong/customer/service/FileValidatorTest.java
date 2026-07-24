package com.zhifutong.customer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zhifutong.customer.TestPropertiesFactory;
import com.zhifutong.customer.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class FileValidatorTest {
    @Test
    void validatesAllowedTypeAndSize() {
        FileValidator validator = new FileValidator(TestPropertiesFactory.create());
        MockMultipartFile file = new MockMultipartFile("file", "policy.md", "text/markdown", "hello".getBytes());
        assertEquals("md", validator.validate(file));
    }

    @Test
    void rejectsOversizedFile() {
        FileValidator validator = new FileValidator(TestPropertiesFactory.create());
        byte[] content = new byte[2 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "policy.md", "text/markdown", content);
        assertThrows(BusinessException.class, () -> validator.validate(file));
    }

    @Test
    void rejectsUnsupportedType() {
        FileValidator validator = new FileValidator(TestPropertiesFactory.create());
        MockMultipartFile file = new MockMultipartFile("file", "table.xlsx", "application/octet-stream", "x".getBytes());
        assertThrows(BusinessException.class, () -> validator.validate(file));
    }
}
