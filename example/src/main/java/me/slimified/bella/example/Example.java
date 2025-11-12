package me.slimified.bella.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.slimified.bella.core.Bella;
import me.slimified.bella.core.repository.Repository;

public class Example {
    public static void main(String[] args) {
        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:example.db");
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        final HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        final Bella bella = Bella.builder().dataSource(dataSource).build();

        final Repository<Person> repo = bella.getRepository(Person.class);

        System.out.println(repo.insert(new Person("sigma", 69)));
    }
}
