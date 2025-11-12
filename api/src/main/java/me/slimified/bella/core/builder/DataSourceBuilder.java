package me.slimified.bella.core.builder;

import javax.sql.DataSource;

public interface DataSourceBuilder {
    OptionalBuilder dataSource(DataSource dataSource);
}
