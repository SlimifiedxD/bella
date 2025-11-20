package me.slimified.bella.example;

import me.slimified.bella.core.Bella;
import me.slimified.bella.core.repository.Repository;
import org.sqlite.SQLiteDataSource;

public class Example {
    public static void main(String[] args) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:mydb.db");
        final Bella bella = Bella.builder().dataSource(dataSource).build();

        final Repository<Person> repo = bella.getRepository(Person.class);

        System.out.println(repo.insert(new Person("sigma", 69)));
    }
}
