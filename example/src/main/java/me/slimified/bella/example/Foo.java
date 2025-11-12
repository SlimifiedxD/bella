package me.slimified.bella.example;

import me.slimified.bella.core.annotation.Persist;

@Persist("foos")
public record Foo(int amount, float precision) {
}
