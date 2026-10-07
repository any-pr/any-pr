//! Port of `convolution.py`: 2D convolution with zero padding, stride,
//! and optional kernel flip (strict convolution vs. cross-correlation).
//!
//! The Python original used numpy + PIL; this port is dependency-free,
//! so the demo operates on a synthetic in-memory image and renders the
//! edge map as ASCII instead of writing a PNG.

/// Convolve a single-channel image (H x W) with a kernel (Kh x Kw).
///
/// Returns `Err` rather than panicking when the arguments cannot produce
/// a result: an empty image or kernel, a stride of zero, ragged rows, or
/// a kernel larger than the padded image.
pub fn convolve2d(
    image: &[Vec<f64>],
    kernel: &[Vec<f64>],
    padding: usize,
    stride: usize,
    flip_kernel: bool,
) -> Result<Vec<Vec<f64>>, String> {
    if image.is_empty() || image[0].is_empty() {
        return Err("image must have at least one row and one column".into());
    }
    if kernel.is_empty() || kernel[0].is_empty() {
        return Err("kernel must have at least one row and one column".into());
    }
    if stride == 0 {
        return Err("stride must be at least 1".into());
    }
    let w = image[0].len();
    if image.iter().any(|row| row.len() != w) {
        return Err("image rows must all have the same width".into());
    }
    let kw0 = kernel[0].len();
    if kernel.iter().any(|row| row.len() != kw0) {
        return Err("kernel rows must all have the same width".into());
    }

    let kernel: Vec<Vec<f64>> = if flip_kernel {
        kernel
            .iter()
            .rev()
            .map(|row| row.iter().rev().copied().collect())
            .collect()
    } else {
        kernel.to_vec()
    };
    let (h, w) = (image.len(), image[0].len());
    let (kh, kw) = (kernel.len(), kernel[0].len());
    let (padded_h, padded_w) = (h + 2 * padding, w + 2 * padding);
    // Without this the subtraction below underflows, which is what made
    // an oversized kernel allocate an absurd output instead of failing.
    if kh > padded_h || kw > padded_w {
        return Err(format!(
            "kernel {}x{} does not fit in padded image {}x{}",
            kh, kw, padded_h, padded_w
        ));
    }

    // zero padding
    let mut padded = vec![vec![0.0; padded_w]; padded_h];
    for (i, row) in image.iter().enumerate() {
        padded[i + padding][padding..padding + w].copy_from_slice(row);
    }

    let out_h = (padded_h - kh) / stride + 1;
    let out_w = (padded_w - kw) / stride + 1;
    let mut out = vec![vec![0.0; out_w]; out_h];
    for (i, orow) in out.iter_mut().enumerate() {
        for (j, cell) in orow.iter_mut().enumerate() {
            let (r, c) = (i * stride, j * stride);
            let mut sum = 0.0;
            for (ki, krow) in kernel.iter().enumerate() {
                for (kj, &kv) in krow.iter().enumerate() {
                    sum += padded[r + ki][c + kj] * kv;
                }
            }
            *cell = sum;
        }
    }
    Ok(out)
}

/// Multi-channel variant: convolve each channel independently.
pub fn convolve2d_channels(
    image: &[Vec<Vec<f64>>],
    kernel: &[Vec<f64>],
    padding: usize,
    stride: usize,
    flip_kernel: bool,
) -> Result<Vec<Vec<Vec<f64>>>, String> {
    if image.is_empty() || image[0].is_empty() || image[0][0].is_empty() {
        return Err("image must have at least one row, column and channel".into());
    }
    let w = image[0].len();
    let c = image[0][0].len();
    // The plane built below indexes image[i][j][ch] for every i and j, so
    // a row that is narrower than the first, or a pixel with fewer
    // channels than the first, panics with an index out of bounds.
    if image.iter().any(|row| row.len() != w) {
        return Err("image rows must all have the same width".into());
    }
    if image.iter().any(|row| row.iter().any(|px| px.len() != c)) {
        return Err("image pixels must all have the same number of channels".into());
    }
    let (h, w, c) = (image.len(), w, c);
    (0..c)
        .map(|ch| {
            let plane: Vec<Vec<f64>> = (0..h)
                .map(|i| (0..w).map(|j| image[i][j][ch]).collect())
                .collect();
            convolve2d(&plane, kernel, padding, stride, flip_kernel)
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::{convolve2d, convolve2d_channels};

    fn kernel() -> Vec<Vec<f64>> {
        vec![vec![1.0]]
    }

    /// Rows and pixels of unequal length used to reach `image[i][j][ch]`
    /// with an index past the end, panicking instead of returning Err.
    fn ragged_inputs() -> Vec<(&'static str, Vec<Vec<Vec<f64>>>)> {
        vec![
            ("ragged channel count", vec![vec![vec![1.0, 2.0]], vec![vec![3.0]]]),
            ("ragged row width", vec![vec![vec![1.0]], vec![]]),
            ("one short pixel", vec![vec![vec![1.0, 2.0], vec![3.0]]]),
            ("short row deep in the image", vec![
                vec![vec![1.0, 2.0], vec![3.0, 4.0]],
                vec![vec![5.0, 6.0]],
            ]),
        ]
    }

    #[test]
    fn channels_accepts_a_well_formed_image() {
        let image = vec![
            vec![vec![1.0, 2.0], vec![3.0, 4.0]],
            vec![vec![5.0, 6.0], vec![7.0, 8.0]],
        ];
        let out = convolve2d_channels(&image, &kernel(), 0, 1, false).unwrap();
        assert_eq!(out.len(), 2);
    }

    #[test]
    fn channels_returns_err_for_ragged_images() {
        for (label, image) in ragged_inputs() {
            assert!(
                convolve2d_channels(&image, &kernel(), 0, 1, false).is_err(),
                "{} must be rejected, not panicked on",
                label
            );
        }
    }

    #[test]
    fn single_channel_returns_err_for_ragged_rows() {
        let image = vec![vec![1.0, 2.0], vec![3.0]];
        assert!(convolve2d(&image, &kernel(), 0, 1, false).is_err());
    }
}

fn main() {
    // Synthetic 8x8 grayscale frame: dark field, bright 4x4 square.
    let mut img = vec![vec![0.0f64; 8]; 8];
    for row in img.iter_mut().take(6).skip(2) {
        for cell in row.iter_mut().take(6).skip(2) {
            *cell = 255.0;
        }
    }

    // Laplacian edge-detection kernel (symmetric — flip is a no-op).
    let kernel = vec![
        vec![0.0, -1.0, 0.0],
        vec![-1.0, 4.0, -1.0],
        vec![0.0, -1.0, 0.0],
    ];
    let edges = convolve2d(&img, &kernel, 1, 1, true).expect("convolution failed");
    for row in &edges {
        println!(
            "{}",
            row.iter()
                .map(|&v| if v.abs() > 1.0 { '#' } else { ' ' })
                .collect::<String>()
        );
    }
    println!("convolution complete");
}
