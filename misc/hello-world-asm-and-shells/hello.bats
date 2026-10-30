#!/usr/bin/env bats

@test "greeting" {
  result="$(echo Hello, World!)"
  [ "$result" = "Hello, World!" ]
}
