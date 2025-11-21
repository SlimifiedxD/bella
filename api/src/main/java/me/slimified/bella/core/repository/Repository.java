package me.slimified.bella.core.repository;

import java.util.Collection;
import java.util.Optional;

public interface Repository<T> {
    long insert(T value);

    //void set(long id, T value);

    Optional<T> findById(long id);

    //Collection<T> findAll();

    //void deleteById(long id);

    //void deleteAll();
}
