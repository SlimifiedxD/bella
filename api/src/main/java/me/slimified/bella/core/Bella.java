package me.slimified.bella.core;

import me.slimified.bella.core.builder.BellaBuilder;
import me.slimified.bella.core.builder.DataSourceBuilder;
import me.slimified.bella.core.repository.Repository;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class Bella {
    private static final Method REPOSITORY_MANAGER_GET_REPO;

    static {
        try {
            final Class<?> generatedRepositoryManager = Class.forName("me.slimified.bella.generated.RepositoryManager");
            REPOSITORY_MANAGER_GET_REPO = generatedRepositoryManager.getMethod("getRepository", DataSource.class, Class.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
            throw new IllegalStateException("RepositoryManager does not exist!");
        }
    }

    private final DataSource dataSource;
    private boolean upsert;

    public static DataSourceBuilder builder() {
        return new BellaBuilder();
    }

    public Bella(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @SuppressWarnings("unchecked")
    public <T, R extends Repository<T>> R getRepository(Class<T> clazz) {
        try {
            return (R) REPOSITORY_MANAGER_GET_REPO.invoke(null, dataSource, clazz);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Something went horribly wrong.");
        }
    }

    public boolean isUpsert() {
        return upsert;
    }

    public void setUpsert(boolean upsert) {
        this.upsert = upsert;
    }
}
