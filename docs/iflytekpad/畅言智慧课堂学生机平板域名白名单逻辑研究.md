# 畅言智慧课堂学生机平板域名白名单逻辑研究 仅供学习研究，严禁滥用

来源：`com.iflytek.mdmservice` 4.4.0.stu 逆向 + 华为 EMUI 8.0.0.131 ROM 反编译 + 在线接口 + BlueStack模拟器实测。
设备：华为 BZT-AL10（学生平板），BlueStack Android7 64bit模拟器。

| 项 | 值 |
|---|---|
| 白名单接口 | `POST http://api.changyan.com/mdm/site/getAppSiteV1` |
| 请求参数 | `schoolId`、`platformId`、**`devId`**、`timeStamp`、`sign` |
| 网关 | EPAS（Dubbo 泛化调用），`S-Auth-AppId: zhkt-mdm-service` |
| 规模 | **162** 条 = 137 IP/CIDR + 25 域名，分属 6 个应用 |
| 响应结构 | `{"dataList":[{"packageName":…, "siteUrl":[…]}, …], "md5":"…"}` |
| 浏览器白名单 | `/mdm/listSafeBrowserSites` → `http://111.15.175.11:3000/gx-lesson/#/` |

> **⚠ 两套独立机制**：*地址白名单*（能访问**哪些地址**）与 *应用白名单*（**哪些应用**能联网）。
> 本文第 1 节全部是**地址白名单**；`packageName` 是服务端的分组标签，
> EMUI 8 下发时被拍平成一份全设备列表，也就是EMUI 8平板能在任何地方访问以下所有地址（见 2.4 / 2.6）。

---

## 1. 白名单全量数据

| # | packageName | 合计 | IP/CIDR | 域名 |
|---|---|---:|---:|---:|
| 1 | `com.ets100.secondary` | 130 | 114 | 16 |
| 2 | `com.iflytek.classstudy` | 21 | 21 | 0 |
| 3 | `com.iflytek.elementstable` | 5 | 0 | 5 |
| 4 | `com.iflytek.psxzhjy` | 1 | 1 | 0 |
| 5 | `com.iflytek.student.hd` | 1 | 1 | 0 |
| 6 | `com.iflytek.yyt.phoneticstudy` | 4 | 0 | 4 |
| | **合计** | **162** | **137** | **25** |

### 1.1 `com.ets100.secondary`（E听说中学） — 130 条

**IP / CIDR**（114）

```text
103.143.19.181, 111.13.133.251, 111.13.133.252, 113.10.164.19, 113.10.164.21, 114.118.65.1/25,
114.118.65.75, 114.118.65.76, 114.118.65.82, 114.118.65.83, 114.118.65.84, 114.118.65.85,
114.118.65.86, 114.118.65.87, 114.118.65.88, 114.118.65.89, 114.118.65.90, 114.118.65.91,
114.118.65.92, 114.118.65.93, 114.118.65.94, 114.55.51.181, 120.92.140.195, 120.92.156.242,
120.92.156.243, 120.92.164.104, 120.92.164.242, 120.92.165.144, 120.92.165.146, 120.92.165.213,
120.92.165.60, 120.92.165.60/24, 120.92.165.79, 120.92.165.80, 120.92.216.52, 120.92.238.203,
120.92.238.208, 120.92.238.212, 120.92.238.218, 120.92.238.219, 120.92.238.22, 120.92.238.22/24,
120.92.238.23, 120.92.238.24, 120.92.238.29, 120.92.238.30, 124.243.239.140, 124.243.239.141,
124.243.239.165, 125.254.169.11, 125.254.169.12, 125.254.169.12/25, 125.254.169.35,
125.254.169.36, 125.254.169.37, 125.254.169.38, 125.254.169.39, 125.254.169.40, 125.254.169.41,
125.254.169.42, 125.254.169.43, 180.76.76.76, 220.248.230.134, 223.5.5.5, 39.156.149.91,
39.156.149.92, 42.62.116.18, 42.62.116.20, 42.62.116.20/25, 42.62.116.21, 42.62.116.22,
42.62.116.30, 42.62.116.34, 42.62.116.35, 42.62.116.36, 42.62.116.38, 42.62.116.42,
42.62.116.74, 42.62.116.75, 42.62.116.82, 42.62.116.83, 42.62.25.122, 42.62.25.123, 42.62.42.10,
42.62.42.11, 42.62.42.18, 42.62.42.19/25, 42.62.42.21, 42.62.42.22, 42.62.43.1/25, 42.62.43.18,
42.62.43.19, 42.62.43.34, 42.62.43.35, 43.230.89.114, 43.230.89.115, 43.255.231.116,
43.255.231.116/25, 43.255.231.117, 43.255.231.119, 58.67.161.131, 58.67.223.1/24, 58.67.223.131,
58.67.223.138, 58.67.223.139, 58.67.223.140, 58.67.223.141, 58.67.223.142, 59.107.24.11,
59.107.24.13, 59.107.24.3, 59.107.24.3/25, 59.107.24.5, 59.107.24.7
```

**域名**（16）— 华为机型上会被丢弃

```text
ei.oss-cn-hangzhou.aliyuncs.com, ets.eduaiplat.com, ets100.com,
ets60.oss-cn-hangzhou.aliyuncs.com, fei.oss-cn-hangzhou.aliyuncs.com, gcaptcha4.geetest.com,
geetest.com, hdns.openspeech.cn, hm.baidu.com, jkptclassroom.oss-cn-beijing.aliyuncs.com,
koukao.cn, ks-assistant.eduaiplat.com, mcs.volceapplog.com, oss-cn-beijing.aliyuncs.com,
static.geetest.com, subject.oss-cn-hangzhou.aliyuncs.com
```

### 1.2 `com.iflytek.classstudy`（课堂互动） — 21 条

**IP / CIDR**（21）

```text
10.1.0.200, 10.1.14.151, 10.1.20.80, 10.1.244.100, 10.1.96.28, 10.5.221.240, 10.5.34.158,
10.86.250.105, 113.10.164.0/27, 114.118.65.0/25, 120.92.238.0/24, 125.254.169.0/25,
172.117.0.37, 172.47.102.5, 42.62.116.16/29, 42.62.42.16/29, 42.62.42.8/29, 43.230.89.112/28,
43.255.231.64/27, 58.67.223.136/29, 59.107.24.0/28
```

### 1.3 `com.iflytek.elementstable` — 5 条

**域名**（5）

```text
bj.download.cycore.cn, jiumentongbu.com, jxwxxkj.com, xuexizhiwang.com, zhinengtongbu.com
```

### 1.4 `com.iflytek.psxzhjy` — 1 条

**IP / CIDR**（1）

```text
111.15.175.11
```

### 1.5 `com.iflytek.student.hd` — 1 条

**IP / CIDR**（1）

```text
119.6.237.1/24
```

### 1.6 `com.iflytek.yyt.phoneticstudy` — 4 条

**域名**（4）— 华为机型上会被丢弃

```text
jiumentongbu.com, jxwxxkj.com, xuexizhiwang.com, zhinengtongbu.com
```

### 1.7 域名 → 受影响服务

下列 **21 个去重域名**在华为机型上全部下发失败（原因见 2.3），但是IP里可能已经包含了对应服务，所以实测依旧可以访问（这也解释了为什么之前知乎，腾讯云在特定情况下也能访问，应该也是IP原因）：

- **阿里云 OSS** — `ei.oss-cn-hangzhou.aliyuncs.com`, `ets60.oss-cn-hangzhou.aliyuncs.com`, `fei.oss-cn-hangzhou.aliyuncs.com`, `jkptclassroom.oss-cn-beijing.aliyuncs.com`, `oss-cn-beijing.aliyuncs.com`, `subject.oss-cn-hangzhou.aliyuncs.com`
- **第三方同步资源** — `jiumentongbu.com`, `jxwxxkj.com`, `xuexizhiwang.com`, `zhinengtongbu.com`
- **极验验证码** — `gcaptcha4.geetest.com`, `geetest.com`, `static.geetest.com`
- **ETS100 / E听说主站** — `ets100.com`
- **ETS100 教育云** — `ets.eduaiplat.com`
- **口语考试** — `koukao.cn`
- **百度统计** — `hm.baidu.com`
- **考试助手** — `ks-assistant.eduaiplat.com`
- **讯飞语音 DNS** — `hdns.openspeech.cn`
- **讯飞语音日志** — `mcs.volceapplog.com`
- **阔知 cycore 下载** — `bj.download.cycore.cn`

---

## 2. 极简工作原理

### 2.1 获取

```text
mdmservice ──POST /mdm/site/getAppSiteV1──▶ EPAS 网关 ──▶ {packageName, siteUrl[]}
     ▲                                                 │
     └──── 签名同算法：listSafeBrowserSites / getLatestWOBSiteListV5 / getAppSiteV1 ◀──┘
```

**签名算法**（`com.android.launcher3.e.t` + `aa`，4.x/5.x 通用）：

```text
canonical = TreeMap 按 key 升序拼 "k=v&k=v…"，去掉末尾 &，整体 toLowerCase
salt      = rand(99999999) + rand(99999999)，右补 0 到 16 位
md5       = MD5hex(canonical + salt)                    // 32 位小写
sign      = 48 位：每 3 字符 = [ md5[2k], salt[k], md5[2k+1] ]   (k = 0..15)
```

**`devId` 是网关参数校验的一部分** —— 这是本次最大的坑：

| 请求 | 结果 |
|---|---|
| 仅 `schoolId` + `platformId` | `{"dataList":[], "md5":"ZpXM72EC…"}` ← 空 |
| 加上 `devId`（**任意值**，含空串） | 返回全量 162 条 |
| `getLatestWOBSiteListV5` **不带** `taskId` | HTTP 403 Dubbo `$invoke` 异常 |

> `devId` 的值不影响结果 —— 传 `BZT-AL10` / `BZI-W20` / 空串，返回完全一致。
> 名单是**全校/全平台级**的，不按机型区分。

### 2.2 下发（华为链路）

```text
MdmSdk.init(ctx) ── 按 Build.BRAND / Build.MODEL 选厂商实现
     │              非 HUAWEI/HONOR/Lenovo/… → throw SdkException("not supported")
     ▼
urlWhiteListWrite(List<String>)
     ▼
DeviceNetworkManager.addNetworkAccessWhitelist(admin, addrList)
     │
     ▼  system_server / hwServices
HwDevicePolicyManagerService.addNetworkAccessWhitelist(who, addrList, userHandle)
  ├─ enforceCallingOrSelfPermission("com.huawei.permission.sec.MDM_NETWORK_MANAGER")
  ├─ HwDevicePolicyManagerServiceUtil.isValidIPAddrs()
  │     └─ ★ Patterns.IP_ADDRESS —— 只认 IP，域名抛 IllegalArgumentException("addrlist invalid")
  ├─ isAddrOverLimit() —— MAX_ADDRS = 1000
  ├─ addListWithoutDuplicate()
  └─ saveSettingsLocked() → /data/system/users/<u>/device_policies.xml
       <network-access-whitelist>
         <network-access-whitelist-item>1.2.3.4</network-access-whitelist-item> …
       </network-access-whitelist>
     ▼  Binder transact CODE_SET_NETWORK_ACCESS_WHITELIST = 1106
HwNetworkManagementService.setNetworkAccessWhitelist(List<String>)
     ▼  socket → netd
net_filter ipwhitelist set <addr0>        ← 第一条用 set（覆盖）
net_filter ipwhitelist add <addr1> … <addrN>   ← 其余逐条 add
net_filter ipwhitelist clear              ← 清空
     ▼
iptables filter 表 ip_whitelist 链（-N 新建 / -P 默认策略）
   · 放行 UDP/TCP 53（DNS）
   · 放行名单内目标 IP
   · 其余 DROP
```

开机恢复：`systemReady()` → `initNetworkAccessWhitelist()` → `HwDeviceManager.getList(9)`
（`HwAdminCache.NETWORK_ACCESS_WHITELIST = 9`）。

### 2.3 厂商实现选择

`MdmSdk.init` 内是**硬编码的 Build.MODEL / Build.BRAND 查表**，直接决定用哪套 API：

| 机型 / 品牌 | 实现 | 支持域名？ |
|---|---|---|
| `BZI-W20` | `HarmonyMdm` | ✅ `addNetworkList(admin, true, isDomain, list)` |
| `AGS3-AL09HN` / `AGS3-W09HN` | `Ags3Mdm` | ✅ 同上 |
| `BZT-AL10` / `BZT-AL00` | `C510HMdm` → `C510Mdm` → `HwMdm` | ❌ 仅 IP |
| `MON-W19` / `KOB-W09` / `AGS2-W09HN` / `AGM3-W09HN` / `KRJ-AN00` | `HwMdm` / `Ags2Mdm` / … | ❌ 仅 IP |
| `Lenovo` 品牌 | `MiaMdm` / `Tab4Mdm` | — |
| 其他品牌 | **`throw SdkException`**，完全不工作 | — |

`HwMdm.urlWhiteListWrite` 原样透传，不做任何分流 —— **这是域名丢失的根源**。
`HarmonyMdm` / `Ags3Mdm` 才会用正则拆分：

```text
IP   ^((2(5[0-5]|[0-4]\d))|[0-1]?\d{1,2})(\.((2(5[0-5]|[0-4]\d))|[0-1]?\d{1,2})){3}(/\d{1,2})?$
域名  ^[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+$
     ↓ 不匹配任何一条 → 直接丢弃（无日志）
addNetworkList(admin, true, false, ipList)      ← IP 通道
addNetworkList(admin, true, true,  domainList)  ← 域名通道
```

### 2.4 两代华为 API 对比

| | 地址白名单 | 应用白名单 |
|---|---|---|
| 新 API | `addNetworkList(admin, isWhiteList, isDomain, list)` | `addNetworkAccessAppList(admin, appList, isWhiteList)` |
| 旧 API | `addNetworkAccessWhitelist(admin, addrList)` | `addNetworkAccessAppWhiteList(admin, appList)` |
| 管什么 | 能访问**哪些地址** | **哪些应用**能联网 |
| 参数含包名 | ❌ | ✅（就是包名） |
| 落盘 | `network-access-whitelist` | `network-access-app-whitelist` |
| EMUI 8 | ✅ 有 | ❌ **框架里根本不存在** |
| EMUI 10+ / HarmonyOS | ✅ | ✅ |

**EMUI 8 的 `boot-hwframework.dex` 里 `DeviceNetWhiteListManager` 全部方法**：

```text
addNetworkAccessWhitelist    getNetworkAccessWhitelist
addNetworkAccessBlackList    getNetworkAccessBlackList
removeNetworkAccessWhitelist removeNetworkAccessBlackList
```

→ 没有 `addNetworkAccessAppWhiteList`。而 mdmservice 仍会去调它（`AppWhiteListReceiver` +
`MdmSdk.setNetworkAccessAppWhiteList`），结果是 `NoSuchMethodError` → 被 `catch (Throwable)` 吞掉。

### 2.5 落盘与增量

```text
mdm_db (SQLite)
  WO_BLIST(_id, SITE_URL, SITE_TYPE, OPERATE_TYPE, SITE_NAME,
           IS_TEMP, CREATOR_TYPE, ENABLE)
     SITE_TYPE    white / black
     OPERATE_TYPE add / delete      ← 增量同步用
     CREATOR_TYPE 下发来源
     ENABLE       是否生效

ContentProvider  content://com.iflytek.mdmservice.provider.WoBListProvider/woblist
   vnd.android.cursor.dir/woblist   ·   vnd.android.cursor.item/woblist
```

**接收器（两套并存）**：

| 接收器 | Action | 权限 |
|---|---|---|
| `MdmWhiteUrlReceiver` | `ACTION_WHITE_ALL` / `_SINGLE` / `_SETONE` | `com.iflytek.mdm.permission.CALL_API` |
| `AppWhiteListReceiver` / `AppWhiteReceiver` | 应用白名单（日志串「拉取应用白名单」） | — |
| `MdmRefeshServiceData` | `ACTION_REFRESH_SERVICE_DATA` | 由 `com.iflytek.mdmstore` 广播触发 |

增量拉取走 `/mdm/site/getLatestWOBSiteListV5`（4.4.0 用 V5，非 V6），带 `taskId` 游标；
无变更时返回 `{}`。**全量列表则由 `getAppSiteV1` 提供。**

### 2.6 关键结论

| # | 结论 |
|---|---|
| 1 | **全链路无 DNS 解析**。服务端必须下发 IP 字面量，客户端不做域名→IP 转换 |
| 2 | **EMUI 8 是全设备级 IP 白名单**。`net_filter ipwhitelist` 无 UID 参数 → 不分应用、应用间无隔离 |
| 3 | **服务端 IP 与域名混发**（25 处域名条目 / 21 个去重域名），华为机型上域名被静默丢弃 |
| 4 | 域名只在 `HarmonyMdm`/`Ags3Mdm`（BZI-W20、AGS3-*）上能存活 |
| 5 | **不在 6 个分组里的 App 不会被禁止联网** —— EMUI 8 无应用维度，它和名单内 App 同样只能连那 137 条 IP |
| 6 | **跨应用隔离不存在**：A 应用能连 B 应用的白名单 IP（全设备共享一份列表） |
| 7 | 名单上限 `MAX_ADDRS = 1000`，超出会被拒；本次远未触及 |
| 8 | `MdmSdk.init` 只认硬编码厂商表，**非华为/联想等机型直接抛异常**，白名单完全不生效 |

---

## 3. 已知失效点速查

| 现象 | 根因 |
|---|---|
| 25 个域名对应的服务全部连不上 | `Patterns.IP_ADDRESS` 校验拒绝，`catch (Throwable)` 静默 |
| 非华为机型完全不下发 | `MdmSdk.init` 抛 `SdkException("… is not supported!")` |
| 应用白名单从未生效 | EMUI 8 无 `addNetworkAccessAppWhiteList` 方法 |
| `getAppSiteV1` 返回空 `dataList` | 请求缺 `devId` |
| `getLatestWOBSiteListV5` 返回 `{}` | 缺 `taskId` 或确无增量（非错误） |
| 重装 mdmservice 后仍 `MdmSdk is not initialized` | RePlugin 插件 `policy` 未加载（`provider loader: not found default plugin`） |

# 仅供学习研究，严禁滥用