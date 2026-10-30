app "hello"
    packages { pf: "https://github.com/roc-lang/basic-cli/releases/download/0.26.0/GddgvsGjiNXrHFW6au5i5dUDbTs8FinQdrgE2Gqmw53t.tar.zst" }
    imports [ pf.Stdout ]
    provides [ main ] to pf

main =
    Stdout.line "Hello, World!"
