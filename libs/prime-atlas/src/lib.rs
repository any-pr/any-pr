//! Indexed prime lookup backed by reproducible, eight-digit ASCII records.
mod data;
const WIDTH: usize = 8;

/// Number of stored primes.
pub fn count() -> usize { data::PRIME_DIGITS.len() / WIDTH }
/// Read a prime by zero-based index, returning `None` outside the table.
pub fn prime(index: usize) -> Option<u32> {
    let start = index.checked_mul(WIDTH)?;
    let digits = data::PRIME_DIGITS.get(start..start.checked_add(WIDTH)?)?;
    Some(digits.iter().fold(0, |value, digit| value * 10 + u32::from(digit - b'0')))
}
/// Inclusive upper bound of the atlas.
pub fn max_prime() -> u32 { prime(count().saturating_sub(1)).unwrap_or(0) }
fn lower_bound(n: u32) -> usize {
    let (mut left, mut right) = (0, count());
    while left < right {
        let middle = left + (right - left) / 2;
        if prime(middle).unwrap() < n { left = middle + 1; } else { right = middle; }
    }
    left
}
/// Determine primality; numbers above `max_prime()` have unknown status.
pub fn is_prime(n: u32) -> Option<bool> {
    if n < 2 { return Some(false); }
    if n > max_prime() { return None; }
    Some(prime(lower_bound(n)) == Some(n))
}
/// Count primes at or below `n`, or return `None` beyond the atlas coverage.
pub fn prime_count(n: u32) -> Option<usize> {
    if n > max_prime() { return None; }
    let index = lower_bound(n);
    Some(index + usize::from(prime(index) == Some(n)))
}

#[cfg(test)]
mod tests {
    use super::*;
    fn trial(n: u32) -> bool { n >= 2 && (2..).take_while(|d| d * d <= n).all(|d| n % d != 0) }
    #[test]
    fn boundaries() {
        assert_eq!((0..5).map(prime).collect::<Vec<_>>(), [Some(2), Some(3), Some(5), Some(7), Some(11)]);
        assert_eq!((is_prime(0), is_prime(1), is_prime(2)), (Some(false), Some(false), Some(true)));
        assert_eq!((prime_count(0), prime_count(1), prime_count(2)), (Some(0), Some(0), Some(1)));
        assert_eq!(prime(count()), None);
        assert_eq!(prime(usize::MAX), None);
        assert_eq!(is_prime(max_prime()), Some(true));
        assert_eq!(prime_count(max_prime()), Some(count()));
        assert_eq!((is_prime(max_prime() + 1), prime_count(u32::MAX)), (None, None));
    }
    #[test]
    fn format_and_order() {
        assert_eq!(data::PRIME_DIGITS.len() % WIDTH, 0);
        assert!(data::PRIME_DIGITS.iter().all(u8::is_ascii_digit));
        let mut previous = 0;
        for i in 0..count() { let current = prime(i).unwrap(); assert!(current > previous); previous = current; }
    }
    #[test]
    fn independently_check_samples() {
        let mut expected_count = 0;
        for n in 0..=max_prime().min(1000) {
            let expected = trial(n); expected_count += usize::from(expected);
            assert_eq!(is_prime(n), Some(expected)); assert_eq!(prime_count(n), Some(expected_count));
        }
        let mut seed = 0x12345678_u64;
        for _ in 0..128 { seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1); assert!(trial(prime((seed % count() as u64) as usize).unwrap())); }
    }
}
