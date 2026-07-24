package com.zhifutong.customer.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhifutong.customer.domain.UserRole;
import com.zhifutong.customer.domain.UserStatus;
import com.zhifutong.customer.entity.UserAccount;
import com.zhifutong.customer.mapper.UserAccountMapper;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class DemoUserInitializer implements ApplicationRunner {
    private final UserAccountMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String customerUsername;
    private final String customerPassword;

    public DemoUserInitializer(UserAccountMapper userMapper, PasswordEncoder passwordEncoder,
                               @Value("${DEMO_ADMIN_USERNAME:admin}") String adminUsername,
                               @Value("${DEMO_ADMIN_PASSWORD:admin123}") String adminPassword,
                               @Value("${DEMO_CUSTOMER_USERNAME:user}") String customerUsername,
                               @Value("${DEMO_CUSTOMER_PASSWORD:123456}") String customerPassword) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.customerUsername = customerUsername;
        this.customerPassword = customerPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(customerUsername, customerPassword, "演示用户", UserRole.CUSTOMER);
        createIfMissing(adminUsername, adminPassword, "后台管理员", UserRole.ADMIN);
    }

    private void createIfMissing(String username, String password, String displayName, UserRole role) {
        if (userMapper.selectCount(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, username)) > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
    }
}
