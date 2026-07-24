package com.zhifutong.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhifutong.customer.entity.CustomerOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CustomerOrderMapper extends BaseMapper<CustomerOrder> {
}
