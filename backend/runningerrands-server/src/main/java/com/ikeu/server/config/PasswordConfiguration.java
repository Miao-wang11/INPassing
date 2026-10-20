package com.ikeu.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置，提供 BCrypt 密码编码器 Bean。
 * @author ikeu
 * @since 2025/05/21
 */
@Configuration
public class PasswordConfiguration {

    /**
     * 创建 BCrypt 密码加密器 Bean。
     *
     * <p>BCrypt 是一种自适应哈希算法，内置 salt 且可通过 rounds 参数调节计算强度，
     * 广泛用于密码存储场景。该 Bean 供登录校验和密码修改时使用。
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
