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
        config.setJdbcUrl(Config.get("db.url"));
        config.setUsername(Config.get("db.user"));
        config.setPassword(Config.get("db.password"));
        config.setMaximumPoolSize(Config.getInt("db.pool.size"));

        dataSource = new HikariDataSource(config);
        ctx = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    public static void close() {
        dataSource.close();
    }

    private Database() {}
}
