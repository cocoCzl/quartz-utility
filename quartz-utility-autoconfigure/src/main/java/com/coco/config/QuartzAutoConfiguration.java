package com.coco.config;

import com.alibaba.druid.pool.DruidDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EnableConfigurationProperties(QuartzDbProperties.class)
public class QuartzAutoConfiguration {

    @Bean(name = "quartzDataSource")
    public DataSource quartzDataSource(QuartzDbProperties properties) {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl(properties.getUrl());
        dataSource.setUsername(properties.getUser());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setInitialSize(properties.getInitialSize());
        dataSource.setMinIdle(properties.getMinIdle());
        dataSource.setMaxActive(properties.getMaxActive());
        dataSource.setValidationQuery(properties.getValidationQuery());
        dataSource.setKeepAlive(true);
        dataSource.setTestOnBorrow(true);
        dataSource.setMinEvictableIdleTimeMillis(properties.getMinEvictableIdleTimeMillis());
        dataSource.setTimeBetweenEvictionRunsMillis(properties.getTimeBetweenEvictionRunsMillis());
        dataSource.setConnectionErrorRetryAttempts(properties.getConnectionErrorRetryAttempts());
        dataSource.setName("quartzDataSource");
        return dataSource;
    }

    @Lazy
    @Bean(name = "quartzJdbcTemplate")
    JdbcTemplate jdbcTemplate(@Autowired @Qualifier("quartzDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
