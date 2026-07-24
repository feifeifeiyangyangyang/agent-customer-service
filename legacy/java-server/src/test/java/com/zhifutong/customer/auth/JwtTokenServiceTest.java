package com.zhifutong.customer.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhifutong.customer.TestPropertiesFactory;
import com.zhifutong.customer.domain.UserRole;
import com.zhifutong.customer.exception.BusinessException;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    @Test
    void createsAndVerifiesAccessToken() {
        JwtTokenService service = new JwtTokenService(TestPropertiesFactory.create(), new ObjectMapper());

        String token = service.createToken(7L, "user", "演示用户", UserRole.CUSTOMER);
        AuthenticatedUser user = service.verify(token);

        assertEquals(7L, user.userId());
        assertEquals("user", user.username());
        assertEquals(UserRole.CUSTOMER, user.role());
    }

    @Test
    void rejectsTamperedToken() {
        JwtTokenService service = new JwtTokenService(TestPropertiesFactory.create(), new ObjectMapper());

        String token = service.createToken(7L, "user", "演示用户", UserRole.CUSTOMER);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(BusinessException.class, () -> service.verify(tampered));
    }

    @Test
    void rejectsExpiredToken() {
        var properties = TestPropertiesFactory.create();
        properties.getAuth().setAccessTokenTtlMinutes(0);
        JwtTokenService service = new JwtTokenService(properties, new ObjectMapper());

        String token = service.createToken(7L, "user", "演示用户", UserRole.CUSTOMER);

        assertThrows(BusinessException.class, () -> service.verify(token));
    }
}
