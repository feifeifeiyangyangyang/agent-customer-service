package com.zhifutong.customer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.zhifutong.customer.mapper")
public class SmartCustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCustomerServiceApplication.class, args);
    }
}
