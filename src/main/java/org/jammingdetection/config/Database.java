package org.jammingdetection.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public class Database {

    private static final HikariDataSource dataSource;
    public static final DSLContext ctx;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/adsb_db");
        config.setUsername("adsb");
        config.setPassword("123456");
        config.setMaximumPoolSize(10);

        dataSource = new HikariDataSource(config);
        ctx = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public static void close() {
        dataSource.close();
    }

    private Database() {}
}
