# 这是一个对该 256bit 工具的帮助列表
  对于util下的工具，使用方法如下:
```java
// 在启动时设置数据类型
McBigConfig.setNumberType(NumberType.INT256);

// 坐标运算
DynamicNumber x = DynamicNumber.of(30000000L);
DynamicNumber y = DynamicNumber.of(1000);
DynamicNumber z = x.add(y);  // 30001000

// 大数运算（远超 long 范围）
DynamicNumber big = DynamicNumber.of(BigInteger.valueOf(2).pow(200));
DynamicNumber result = big.multiply(DynamicNumber.of(3));

// 转换回普通类型
long coord = result.longValue();  // 若超出 long 范围会截断，使用 bigIntegerValue()
```

## 对于Int256 abs工具，使用方法如下:
```java
Int256 value = Int256.of(-1234567890123456789L);
Int256 absValue = value.abs(); // 1234567890123456789
```

## 对于BigMath RandomSource随机工具，使用方法如下:
```java
import me.noisefarlands.mcbig.Math.BigMath;
import me.noisefarlands.mcbig.util.DynamicNumber;
import net.minecraft.util.RandomSource;

RandomSource random = RandomSource.create();
DynamicNumber bound = DynamicNumber.of(1_000_000L);
DynamicNumber rand = BigMath.randomBigInt(random, bound);
System.out.println(rand); // 0 ~ 999999

// 超大范围随机
DynamicNumber bigBound = DynamicNumber.of(BigInteger.valueOf(2).pow(200));
DynamicNumber bigRand = BigMath.randomBigInt(random, bigBound);
System.out.println(bigRand);
```

## 对于BigMath工具，使用方法如下:
```java
import me.noisefarlands.mcbig.Math.BigMath;
import me.noisefarlands.mcbig.util.DynamicNumber;

DynamicNumber x = DynamicNumber.of(30000000L);
DynamicNumber y = DynamicNumber.of(64);
DynamicNumber dist = BigMath.length(x, y, DynamicNumber.ZERO);
System.out.println(dist); // ≈ 30000000.000068
```

## BigDecimal 256工具是优化的BigDecimal，他的使用方法:
package me.noisefarlands.mcbig.Math;
```java
public class Example {
    public static void main(String[] args) {
        // 整数运算
        Int256 a = Int256.of(1000000);
        Int256 b = Int256.of(2000000);
        Int256 sum = a.add(b);      // 3000000
        Int256 product = a.multiply(b); // 2e12

        // 十进制运算
        Decimal256 x = Decimal256.of(0.1);
        Decimal256 y = Decimal256.of(0.2);
        Decimal256 z = x.add(y);      // 0.3
        Decimal256 scaled = z.multiply(Decimal256.TEN); // 3.0

        // 转换
        long longVal = sum.longValue();
        double doubleVal = z.doubleValue();
    }
}
```