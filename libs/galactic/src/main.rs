//! `galactic` binary — thin shell over `galactic::run`.

use std::process::exit;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    match galactic::run(&args) {
        Ok((out, code)) => {
            println!("{}", out);
            exit(code);
        }
        Err(msg) => {
            eprintln!("error: {}", msg);
            exit(2);
        }
    }
}
