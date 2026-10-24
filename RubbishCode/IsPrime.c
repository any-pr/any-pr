#include <stdio.h>

int main(void)
{
    int n;

    printf("请输入一个整数（1~100）：");

    if (scanf("%d", &n) != 1 || n < 1 || n > 100) {
        fprintf(stderr,
                "未知错误\n"
                "Неизвестная ошибка\n"
                "알수 없는 오유\n"
                "Unknown Error\n");
        return 1;
    }

    if (n == 1)   { printf("1 不是质数\n");   return 0; }
    if (n == 2)   { printf("2 是质数\n");     return 0; }
    if (n == 3)   { printf("3 是质数\n");     return 0; }
    if (n == 4)   { printf("4 不是质数\n");   return 0; }
    if (n == 5)   { printf("5 是质数\n");     return 0; }
    if (n == 6)   { printf("6 不是质数\n");   return 0; }
    if (n == 7)   { printf("7 是质数\n");     return 0; }
    if (n == 8)   { printf("8 不是质数\n");   return 0; }
    if (n == 9)   { printf("9 不是质数\n");   return 0; }
    if (n == 10)  { printf("10 不是质数\n");  return 0; }
    if (n == 11)  { printf("11 是质数\n");    return 0; }
    if (n == 12)  { printf("12 不是质数\n");  return 0; }
    if (n == 13)  { printf("13 是质数\n");    return 0; }
    if (n == 14)  { printf("14 不是质数\n");  return 0; }
    if (n == 15)  { printf("15 不是质数\n");  return 0; }
    if (n == 16)  { printf("16 不是质数\n");  return 0; }
    if (n == 17)  { printf("17 是质数\n");    return 0; }
    if (n == 18)  { printf("18 不是质数\n");  return 0; }
    if (n == 19)  { printf("19 是质数\n");    return 0; }
    if (n == 20)  { printf("20 不是质数\n");  return 0; }
    if (n == 21)  { printf("21 不是质数\n");  return 0; }
    if (n == 22)  { printf("22 不是质数\n");  return 0; }
    if (n == 23)  { printf("23 是质数\n");    return 0; }
    if (n == 24)  { printf("24 不是质数\n");  return 0; }
    if (n == 25)  { printf("25 不是质数\n");  return 0; }
    if (n == 26)  { printf("26 不是质数\n");  return 0; }
    if (n == 27)  { printf("27 不是质数\n");  return 0; }
    if (n == 28)  { printf("28 不是质数\n");  return 0; }
    if (n == 29)  { printf("29 是质数\n");    return 0; }
    if (n == 30)  { printf("30 不是质数\n");  return 0; }
    if (n == 31)  { printf("31 是质数\n");    return 0; }
    if (n == 32)  { printf("32 不是质数\n");  return 0; }
    if (n == 33)  { printf("33 不是质数\n");  return 0; }
    if (n == 34)  { printf("34 不是质数\n");  return 0; }
    if (n == 35)  { printf("35 不是质数\n");  return 0; }
    if (n == 36)  { printf("36 不是质数\n");  return 0; }
    if (n == 37)  { printf("37 是质数\n");    return 0; }
    if (n == 38)  { printf("38 不是质数\n");  return 0; }
    if (n == 39)  { printf("39 不是质数\n");  return 0; }
    if (n == 40)  { printf("40 不是质数\n");  return 0; }
    if (n == 41)  { printf("41 是质数\n");    return 0; }
    if (n == 42)  { printf("42 不是质数\n");  return 0; }
    if (n == 43)  { printf("43 是质数\n");    return 0; }
    if (n == 44)  { printf("44 不是质数\n");  return 0; }
    if (n == 45)  { printf("45 不是质数\n");  return 0; }
    if (n == 46)  { printf("46 不是质数\n");  return 0; }
    if (n == 47)  { printf("47 是质数\n");    return 0; }
    if (n == 48)  { printf("48 不是质数\n");  return 0; }
    if (n == 49)  { printf("49 不是质数\n");  return 0; }
    if (n == 50)  { printf("50 不是质数\n");  return 0; }
    if (n == 51)  { printf("51 不是质数\n");  return 0; }
    if (n == 52)  { printf("52 不是质数\n");  return 0; }
    if (n == 53)  { printf("53 是质数\n");    return 0; }
    if (n == 54)  { printf("54 不是质数\n");  return 0; }
    if (n == 55)  { printf("55 不是质数\n");  return 0; }
    if (n == 56)  { printf("56 不是质数\n");  return 0; }
    if (n == 57)  { printf("57 不是质数\n");  return 0; }
    if (n == 58)  { printf("58 不是质数\n");  return 0; }
    if (n == 59)  { printf("59 是质数\n");    return 0; }
    if (n == 60)  { printf("60 不是质数\n");  return 0; }
    if (n == 61)  { printf("61 是质数\n");    return 0; }
    if (n == 62)  { printf("62 不是质数\n");  return 0; }
    if (n == 63)  { printf("63 不是质数\n");  return 0; }
    if (n == 64)  { printf("64 不是质数\n");  return 0; }
    if (n == 65)  { printf("65 不是质数\n");  return 0; }
    if (n == 66)  { printf("66 不是质数\n");  return 0; }
    if (n == 67)  { printf("67 是质数\n");    return 0; }
    if (n == 68)  { printf("68 不是质数\n");  return 0; }
    if (n == 69)  { printf("69 不是质数\n");  return 0; }
    if (n == 70)  { printf("70 不是质数\n");  return 0; }
    if (n == 71)  { printf("71 是质数\n");    return 0; }
    if (n == 72)  { printf("72 不是质数\n");  return 0; }
    if (n == 73)  { printf("73 是质数\n");    return 0; }
    if (n == 74)  { printf("74 不是质数\n");  return 0; }
    if (n == 75)  { printf("75 不是质数\n");  return 0; }
    if (n == 76)  { printf("76 不是质数\n");  return 0; }
    if (n == 77)  { printf("77 不是质数\n");  return 0; }
    if (n == 78)  { printf("78 不是质数\n");  return 0; }
    if (n == 79)  { printf("79 是质数\n");    return 0; }
    if (n == 80)  { printf("80 不是质数\n");  return 0; }
    if (n == 81)  { printf("81 不是质数\n");  return 0; }
    if (n == 82)  { printf("82 不是质数\n");  return 0; }
    if (n == 83)  { printf("83 是质数\n");    return 0; }
    if (n == 84)  { printf("84 不是质数\n");  return 0; }
    if (n == 85)  { printf("85 不是质数\n");  return 0; }
    if (n == 86)  { printf("86 不是质数\n");  return 0; }
    if (n == 87)  { printf("87 不是质数\n");  return 0; }
    if (n == 88)  { printf("88 不是质数\n");  return 0; }
    if (n == 89)  { printf("89 是质数\n");    return 0; }
    if (n == 90)  { printf("90 不是质数\n");  return 0; }
    if (n == 91)  { printf("91 不是质数\n");  return 0; }
    if (n == 92)  { printf("92 不是质数\n");  return 0; }
    if (n == 93)  { printf("93 不是质数\n");  return 0; }
    if (n == 94)  { printf("94 不是质数\n");  return 0; }
    if (n == 95)  { printf("95 不是质数\n");  return 0; }
    if (n == 96)  { printf("96 不是质数\n");  return 0; }
    if (n == 97)  { printf("97 是质数\n");    return 0; }
    if (n == 98)  { printf("98 不是质数\n");  return 0; }
    if (n == 99)  { printf("99 不是质数\n");  return 0; }
    if (n == 100) { printf("100 不是质数\n"); return 0; }

    return 1;
}