//! Rust port of `build.sh`: drives the LLVM-based VastNova compiler
//! build by shelling out to g++ with flags from llvm-config.
//! Compile standalone with `rustc build.rs` (no Cargo required).

use std::process::{exit, Command};

fn main() {
    println!("Building VastNova...");

    let llvm = Command::new("llvm-config")
        .args(["--cxxflags", "--ldflags", "--libs", "core"])
        .output()
        .expect("failed to run llvm-config");
    if !llvm.status.success() {
        eprintln!("llvm-config failed");
        exit(1);
    }
    let flags = String::from_utf8_lossy(&llvm.stdout);
    let status = Command::new("g++")
        .args(["-std=c++17", "src/main.cpp", "src/CodeGen.cpp", "-I", "include"])
        .args(flags.split_whitespace())
        .args(["-fexceptions", "-o", "vastnova"])
        .status()
        .expect("failed to run g++");
    if !status.success() {
        exit(status.code().unwrap_or(1));
    }
    println!("Build successful!");
}
