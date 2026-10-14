package com.collegeassist.backend.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/collegeassist}")
    private String mysqlUrl;

    @Value("${spring.datasource.username:root}")
    private String mysqlUsername;

    @Value("${spring.datasource.password:}")
    private String mysqlPassword;

    @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String mysqlDriverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        // Probe MySQL connection first
        if (canConnectToMysql()) {
            log.info("DataSourceConfig: Successfully connected to MySQL at {} (user: {})", mysqlUrl, mysqlUsername);
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(mysqlUrl);
            config.setUsername(mysqlUsername);
            config.setPassword(mysqlPassword);
            config.setDriverClassName(mysqlDriverClassName);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setPoolName("CollegeAssist-MySQL-Pool");
            return new HikariDataSource(config);
        }

        // Graceful fallback to embedded persistent H2 database so the server always boots and persists data
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        String h2Url = "jdbc:h2:file:./data/collegeassist;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE";
        log.warn("DataSourceConfig: MySQL is unavailable or credentials were not accepted. Falling back to persistent embedded database: {}", h2Url);

        HikariConfig h2Config = new HikariConfig();
        h2Config.setJdbcUrl(h2Url);
        h2Config.setUsername("sa");
        h2Config.setPassword("");
        h2Config.setDriverClassName("org.h2.Driver");
        h2Config.setMaximumPoolSize(10);
        h2Config.setMinimumIdle(2);
        h2Config.setPoolName("CollegeAssist-H2-Fallback-Pool");
        return new HikariDataSource(h2Config);
    }

    private boolean canConnectToMysql() {
        try {
            Class.forName(mysqlDriverClassName);
            // Append timeout to probe quickly without blocking startup
            String probeUrl = mysqlUrl;
            if (!probeUrl.contains("connectTimeout")) {
                probeUrl += (probeUrl.contains("?") ? "&" : "?") + "connectTimeout=2500&socketTimeout=2500";
            }
            try (Connection conn = DriverManager.getConnection(probeUrl, mysqlUsername, mysqlPassword)) {
                return conn.isValid(2);
            }
        } catch (Exception ex) {
            log.debug("DataSourceConfig: MySQL probe failed: {}", ex.getMessage());
            return false;
        }
    }
}
