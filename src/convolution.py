import numpy as np
from PIL import Image
from numpy.lib.stride_tricks import sliding_window_view


def convolve2d_fast(image, kernel, padding=0, stride=1, flip_kernel=True):
    if flip_kernel:
        kernel = np.flip(kernel)
    img_pad = np.pad(image, padding, mode="constant")
    windows = sliding_window_view(img_pad, kernel.shape)  # 形状 (H_out, W_out, Kh, Kw)
    return np.einsum("ijkl,kl->ij", windows, kernel)  # 爱因斯坦求和


def convolve2d(
    image: np.ndarray,
    kernel: np.ndarray,
    padding: int = 0,
    stride: int = 1,
    flip_kernel: bool = True,
) -> np.ndarray:
    """
    2D卷积核心算法(支持单通道或多通道)

    参数:
        image: 输入图像 (H, W) 或 (H, W, C)
        kernel: 卷积核 (Kh, Kw)
        padding: 零填充大小
        stride: 步长
        flip_kernel: True=数学严格卷积(翻转核), False=互相关(深度学习常用)
    """
    # 处理多通道:逐通道应用卷积
    if image.ndim == 3:
        channels = image.shape[2]
        output_channels = []
        for c in range(channels):
            conv_ch = convolve2d(image[:, :, c], kernel, padding, stride, flip_kernel)
            output_channels.append(conv_ch)
        return np.stack(output_channels, axis=2)

    # --- 单通道处理 ---
    h, w = image.shape
    kh, kw = kernel.shape

    # 1. 核翻转(严格卷积定义必须)
    if flip_kernel:
        kernel = np.flip(kernel)  # 沿两个轴翻转

    # 2. 零填充
    img_pad = np.pad(image, pad_width=padding, mode="constant", constant_values=0)

    # 3. 计算输出尺寸
    out_h = (h + 2 * padding - kh) // stride + 1
    out_w = (w + 2 * padding - kw) // stride + 1
    output = np.zeros((out_h, out_w), dtype=np.float64)

    # 4. 滑动窗口计算(核心算法)
    for i in range(out_h):
        for j in range(out_w):
            # 提取当前感受野
            row_start = i * stride
            col_start = j * stride
            region = img_pad[row_start : row_start + kh, col_start : col_start + kw]
            # 点积求和
            output[i, j] = np.sum(region * kernel)

    return output


# ---------- 使用示例 ----------
if __name__ == "__main__":
    # 1. 加载图像(转灰度,便于观察边缘)
    img = Image.open("./input.png").convert("L")  # 灰度
    img_array = np.array(img, dtype=np.float64)

    # 2. 定义卷积核(拉普拉斯边缘检测,3x3)
    kernel = np.array([[0, -1, 0], [-1, 4, -1], [0, -1, 0]], dtype=np.float64)

    # 3. 执行卷积(padding=1 保证输入输出尺寸相同)
    # 数学严格卷积(翻转核,但对对称核拉普拉斯无影响)
    result = convolve2d_fast(img_array, kernel, padding=1, stride=1, flip_kernel=True)

    # 4. 结果归一化到 0-255 并转为 uint8
    result = np.clip(result, 0, 255).astype(np.uint8)

    # 5. 保存结果
    Image.fromarray(result).save("output_edge.png")
    print("卷积完成!")
