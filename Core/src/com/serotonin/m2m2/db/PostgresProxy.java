package com.serotonin.m2m2.db;

import java.sql.SQLException;

import org.postgresql.util.PSQLException;
import org.springframework.jdbc.BadSqlGrammarException;

import com.infiniteautomation.mango.spring.DatabaseProxyConfiguration;
import com.serotonin.db.spring.ExtendedJdbcTemplate;

public class PostgresProxy extends BasePooledProxy {
    public PostgresProxy(DatabaseProxyFactory factory, DatabaseProxyConfiguration configuration) {
        super(factory, configuration);
    }

    @Override
    public DatabaseType getType() {
        return DatabaseType.POSTGRES;
    }

    @Override
    protected String getDriverClassName() {
        return "org.postgresql.Driver";
    }

    @Override
    public boolean tableExists(ExtendedJdbcTemplate ejt, String tableName) {
        try {
            ejt.execute("SELECT COUNT(*) FROM " + tableName);
        }
        catch (BadSqlGrammarException e) {
            if (e.getCause() instanceof PSQLException) {
                SQLException se = (SQLException) e.getCause();
                // This state means a missing table.
                return !"42P01".equals(se.getSQLState());
            }
            throw e;
        }
        return true;
    }

    @Override
    public String getTableListQuery() {
        return "SELECT table_name FROM information_schema.tables "
                + "WHERE table_catalog=current_database() AND table_schema=current_schema()";
    }

    @Override
    public int batchInsertSize() {
        return 2000;
    }
}
