package com.zhifutong.customer.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FileNameUtilTest {
    @Test
    void rejectsUnsafeFileNames() {
        assertTrue(FileNameUtil.isSafeOriginalName("退货政策.md"));
        assertFalse(FileNameUtil.isSafeOriginalName("../secret.md"));
        assertFalse(FileNameUtil.isSafeOriginalName("dir/file.md"));
        assertEquals("md", FileNameUtil.extensionOf("退货政策.md"));
    }
}
