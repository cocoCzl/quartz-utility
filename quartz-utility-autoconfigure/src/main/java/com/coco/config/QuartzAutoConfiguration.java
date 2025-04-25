package com.coco.config;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EnableConfigurationProperties(QuartzJobStoreProperties.class)
public class QuartzAutoConfiguration {

    @Lazy
    @Bean(name = "quartzJdbcTemplate")
    JdbcTemplate jdbcTemplate(ApplicationContext applicationContext,
            QuartzJobStoreProperties jobStoreProperties) {
        // 从配置中获取名字，比如 "quartzDataSource"
        String dsBeanName = jobStoreProperties.getDataSource();
        if (!applicationContext.containsBean(dsBeanName)) {
            throw new IllegalStateException("DataSource bean named " + dsBeanName + "not found!");
        }
        DataSource quartzDataSource = applicationContext.getBean(dsBeanName, DataSource.class);
        return new JdbcTemplate(quartzDataSource);
    }
}
