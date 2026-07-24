package com.zhifutong.customer.vo;

import java.util.List;

public record PageResult<T>(
        long page,
        long size,
        long total,
        List<T> records
) {
}
