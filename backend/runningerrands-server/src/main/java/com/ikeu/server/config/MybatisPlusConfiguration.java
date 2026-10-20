package com.ikeu.server.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置，注册 MySQL 分页插件。
 * @author ikeu
 * @since 2025/05/21
 */
@Slf4j
@Configuration
public class MybatisPlusConfiguration {

    /**
     * 创建 MyBatis-Plus 拦截器，添加 MySQL 分页插件。
     *
     * <p>注册 {@link PaginationInnerInterceptor}，数据库类型为 MYSQL，
     * 使所有使用 MyBatis-Plus Page 对象的分页查询自动拦截并追加分页 SQL。
     *
     * @return 配置了分页插件的拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        log.info("开始创建MyBatisPlus拦截器对象...");
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
