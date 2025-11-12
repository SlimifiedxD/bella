package me.slimified.bella.core.test;

import me.slimified.bella.core.repository.Repository;

import java.util.Optional;

public interface PersonRepository extends Repository<Person> {
    Optional<Person> findByName(String name);

    Optional<Person> findByAge(int age);
}
