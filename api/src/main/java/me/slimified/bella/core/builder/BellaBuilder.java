package me.slimified.bella.core.builder;

import me.slimified.bella.core.Bella;

import javax.sql.DataSource;

public class BellaBuilder implements DataSourceBuilder, OptionalBuilder {
    private Bella bella;

    @Override
    public BellaBuilder dataSource(DataSource dataSource) {
        bella = new Bella(dataSource);
        return this;
    }

    @Override
    public OptionalBuilder upsert() {
        bella.setUpsert(true);
        return this;
    }

    @Override
    public Bella build() {
        return bella;
    }
}
