package com.coco.config;

import com.coco.core.CoQuartzScheduler;
import javax.sql.DataSource;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
public class QuartzAutoConfiguration {

    @Lazy
    @Bean(name = "quartzJdbcTemplate")
    JdbcTemplate jdbcTemplate(@Autowired DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Primary
    CoQuartzScheduler coQuartzScheduler(@Autowired Scheduler scheduler) {
        return new CoQuartzScheduler(scheduler);
    }
}
