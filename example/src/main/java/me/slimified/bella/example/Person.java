package me.slimified.bella.example;

import me.slimified.bella.core.annotation.Persist;

@Persist("people")
public record Person(String name, int age) {
}
