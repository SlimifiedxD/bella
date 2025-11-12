package me.slimified.bella.core.test;

import me.slimified.bella.core.annotation.Persist;

@Persist("people")
public record Person(String name, int age) {
}
