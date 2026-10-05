//! Port of `convolution.py`: 2D convolution with zero padding, stride,
//! and optional kernel flip (strict convolution vs. cross-correlation).
//!
//! The Python original used numpy + PIL; this port is dependency-free,
//! so the demo operates on a synthetic in-memory image and renders the
//! edge map as ASCII instead of writing a PNG.

/// Convolve a single-channel image (H x W) with a kernel (Kh x Kw).
pub fn convolve2d(
    image: &[Vec<f64>],
    kernel: &[Vec<f64>],
    padding: usize,
    stride: usize,
    flip_kernel: bool,
) -> Vec<Vec<f64>> {
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

    // zero padding
    let mut padded = vec![vec![0.0; w + 2 * padding]; h + 2 * padding];
    for (i, row) in image.iter().enumerate() {
        padded[i + padding][padding..padding + w].copy_from_slice(row);
    }

    let out_h = (h + 2 * padding - kh) / stride + 1;
    let out_w = (w + 2 * padding - kw) / stride + 1;
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
    out
}

/// Multi-channel variant: convolve each channel independently.
pub fn convolve2d_channels(
    image: &[Vec<Vec<f64>>],
    kernel: &[Vec<f64>],
    padding: usize,
    stride: usize,
    flip_kernel: bool,
) -> Vec<Vec<Vec<f64>>> {
    let (h, w, c) = (image.len(), image[0].len(), image[0][0].len());
    (0..c)
        .map(|ch| {
            let plane: Vec<Vec<f64>> = (0..h)
                .map(|i| (0..w).map(|j| image[i][j][ch]).collect())
                .collect();
            convolve2d(&plane, kernel, padding, stride, flip_kernel)
        })
        .collect()
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
    let edges = convolve2d(&img, &kernel, 1, 1, true);
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
