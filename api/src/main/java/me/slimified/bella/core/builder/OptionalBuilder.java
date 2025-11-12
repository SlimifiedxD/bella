package me.slimified.bella.core.builder;

import me.slimified.bella.core.Bella;

public interface OptionalBuilder {
    OptionalBuilder upsert();

    Bella build();
}
