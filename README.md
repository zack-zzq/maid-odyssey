# Maid Odyssey（女仆奥德赛）

为 **GregTech Odyssey（GTO）整合包** 与 **车万女仆（Touhou Little Maid，TLM）** 提供兼容的 Minecraft 1.20.1 Forge 模组。

它给女仆增加了三个新的工作模式，让女仆替你打理格雷科技工厂里两件最烦人的杂活：

| 工作模式 | 做什么 |
| --- | --- |
| **清理消声仓** | 把附近消声仓（Muffler Hatch）里堆积的灰烬收走 |
| **维护维护仓** | 用女仆背包里的工具修复损坏的维护仓（Maintenance Hatch） |
| **工厂维护员** | 上面两件事一起做 |

只要任务受阻（背包满了、缺工具、格雷接口对不上），女仆会**在公屏聊天框里说明原因**，而不是默默站着发呆。

---

## 1. 安装

**前置：**

- Minecraft 1.20.1 + Forge 47.x
- Touhou Little Maid 1.4.0 或更高（1.20.1 Forge 版本）— **必需**
- GregTech Modern（GTO 整合包自带的 `gtceu`）— **可选但没有它这几个模式没意义**

把 `maid_odyssey-1.0.0+mc1.20.1.jar` 丢进 `mods/` 即可。服务端和客户端都要装。

> 本模组**不在编译期链接**格雷科技，全部通过反射调用。因此 GTO 更新自己的 GregTech-Modern 分支时，通常不需要重新编译本模组；万一某个方法签名真的改了，女仆会在聊天框里报出具体是哪个方法出了问题，而不是让游戏崩溃。

---

## 2. 快速上手

1. 造一个女仆，给她**背包**（女仆必须有背包才能装东西）。
2. 打开女仆界面，切换到工作模式列表，选择 **工厂维护员**（或单独的两个模式之一）。
3. **务必开启「家」模式**，并把家的位置设在工厂中间，工作范围调到能覆盖你的机器。
   - 没开家模式时，车万女仆本体的逻辑限定女仆**只在主人 8 格以内**工作，她会寸步不离地跟着你，起不到自动化的作用。
4. 往女仆背包里放好她需要的东西（见下文）。
5. 走开，让她干活。

---

## 3. 三个工作模式的详细说明

### 3.1 清理消声仓 `maid_odyssey:clean_muffler`

**背景：** GTO 魔改后的消声仓在多方块机器运行时会概率产出灰烬（`gtceu:tiny_ash_dust`）。灰烬堆满后消声仓会堵住，进而卡住整台多方块机器。

**女仆的行为循环：**

1. 每 6～12 秒，以「家」的位置为中心做一次螺旋扫描，水平半径取 `min(女仆工作范围, 配置里的 maxSearchRadius)`，垂直方向约 ±4 格。
2. 找到**里面有东西**的消声仓，并且女仆能走到它旁边的 3×3×2 范围内（因为机器本身是实心方块，站不上去）。
3. 走过去，站定后把消声仓物品栏里的东西**逐格搬进自己的背包**。
4. 搬完挥一下手，任务结束，接着找下一个。

**需要准备什么：** 背包里的空位。仅此而已。

**受阻时会在公屏说：**

> 背包已经满了，没办法再把这个消声仓里的灰烬取出来。

此时这个消声仓会被临时拉黑 `blockedRetryDelay` 刻（默认 600 刻 = 30 秒），女仆不会原地反复横跳。

**不想要灰烬？** 把配置里的 `muffler.ashHandling` 改成 `VOID`，女仆会直接把灰烬销毁，永远不会背包满。

**安全性：** 搬运使用「先模拟、再按能装下的数量提取」的两段式写法，中途装不下的部分会**原样塞回消声仓**，绝不会凭空吞物品。

---

### 3.2 维护维护仓 `maid_odyssey:maintenance_hatch`

**背景：** 维护仓会随机出现 6 种故障，每种故障需要一种特定工具去修，修好之前多方块机器不工作。故障用一个 6 位掩码表示，位为 0 表示坏了。

| 位 | 故障 | 需要的工具 |
| --- | --- | --- |
| 0 | 扳手故障 | 扳手 Wrench |
| 1 | 螺丝故障 | 螺丝刀 Screwdriver |
| 2 | 敲击故障 | 橡胶锤 Soft Mallet |
| 3 | 锤击故障 | 锤子 Hard Hammer |
| 4 | 线缆故障 | 剪线钳 Wire Cutter |
| 5 | 撬动故障 | 撬棍 Crowbar |

**女仆的行为循环：**

1. 同样的扫描逻辑，寻找**有故障且不是全自动维护仓**的维护仓（全自动维护仓 / 净化维护仓会被自动跳过）。
2. 走过去，逐条检查还没修好的故障。
3. 对每条故障：
   - 先看**主手**拿的是不是对的工具；不是的话，从背包里找一把出来，**换到主手**（原来主手的东西换进那个格子）。
   - 调用格雷的 `setMaintenanceFixed(位)` 修好这一条，
   - 按格雷自己的方式给工具**扣 1 点耐久**（电动工具走 `ToolHelper.damageItem`，会正确扣电而不是扣耐久），
   - 挥一次手。
4. 全部修完后，清掉「胶带」标记，并把磨损计时器 `timeActive` 归零——这是 GTOCore 在玩家手动修复时做的事，女仆保持一致。

**需要准备什么：** 把 6 种工具各放一把进女仆背包。GT 的电动工具（电动扳手等）同样识别。其它模组的工具只要打了 `forge:tools/wrenches` 之类的标签也能用。

**胶带兜底：** 如果缺工具，而背包里有格雷胶带（`gtceu:duct_tape`），女仆会消耗一卷胶带把**剩下所有故障**一次糊住，并打上胶带标记。不想要这个行为就把 `maintenance.useDuctTape` 关掉。

**受阻时会在公屏说：**

> 没有合适的工具来修这个维护仓，还缺少：扳手、剪线钳

**保护你的好工具：** `maintenance.toolDurabilityReserve` 可以设一个耐久下限，剩余耐久小于等于这个值的工具女仆不会去用，避免她把你的钨钢扳手用碎。

---

### 3.3 工厂维护员 `maid_odyssey:gt_housekeeping`

前两个模式的合体。一次扫描同时匹配两类目标，走到哪个就干哪个。适合让一个女仆守一整层工厂。

需要同时准备：工具 + 背包空位。

---

## 4. 聊天播报

这是本模组的一个核心诉求：**任务遇到阻碍时，详情要全体玩家可见**。

播报格式：

```
[女仆奥德赛] 灵梦 [128, 64, -302]: 没有合适的工具来修这个维护仓，还缺少：扳手、剪线钳
```

- 使用**可翻译文本**发送，所以每个玩家看到的是自己客户端语言的版本（内置中英文）。
- 同一个女仆 + 同一条消息 + 同一个坐标，在 `chatReportCooldown` 秒内只会播报一次（默认 60 秒），不会刷屏。
- 默认播报给全服所有玩家；把 `report.chatReportToEveryone` 设为 `false` 就只私聊女仆的主人。
- 默认**只播报问题**。想连成功也一起看，把 `report.chatReportSuccess` 打开，会多出「从消声仓中收走了 32 个灰烬。」这类消息。

会播报的情况：

| 消息 | 含义 |
| --- | --- |
| 背包已经满了…… | 消声仓还有灰烬但女仆装不下 |
| 没有合适的工具来修这个维护仓，还缺少：X、Y | 缺对应工具且没有胶带 |
| 无法与格雷科技通信：…… | 反射绑定失败，通常意味着 GTO 改了方法签名，请开 issue |
| 没有安装格雷科技…… | 装了本模组但没装 gtceu |

---

## 5. 配置文件

`config/maid_odyssey-common.toml`

```toml
[report]
  chatReportEnabled = true      # 聊天播报总开关
  chatReportToEveryone = true   # true=全服可见, false=只发给主人
  chatReportSuccess = false     # 是否也播报成功的工作
  chatReportCooldown = 60       # 同一条消息的最小间隔（秒）

[search]
  maxSearchRadius = 24          # 水平搜索半径上限（格），越小越省性能
  verticalSearchRange = 8       # 家点上下各搜多少格（消声仓在高炉顶上，需要留高度）
  workReach = 5                 # 站在离机器多远内可以干活（顶上的消声仓从地面够）
  blockedRetryDelay = 600       # 干不了的机器拉黑多少刻
  unreachableRetryDelay = 200   # 走不到的机器拉黑多少刻

[muffler]
  ashHandling = "COLLECT"       # COLLECT=收进背包, VOID=直接销毁

[maintenance]
  useDuctTape = true            # 缺工具时是否用胶带兜底
  resetMaintenanceTimer = true  # 修完后是否把磨损计时器归零
  toolDurabilityReserve = 0     # 剩余耐久 <= 该值的工具不使用
```

---

## 6. 已知边界

- **必须开家模式**，否则车万女仆只允许她在主人 8 格内工作（这是女仆本体的设计，不是本模组的限制）。
- 垂直搜索范围默认 ±8 格（可在配置里调 `search.verticalSearchRange`）。消声仓在电力高炉顶面、比控制器高 3 格，这个范围够用；如果多方块特别高，把女仆的家点设在中间层，或者把这个值调大。
- 女仆**不需要爬到消声仓旁边**。机器是实心的，顶上的消声仓周围通常也没有落脚点，所以她会站在多方块底下的地面上、在 `workReach`（默认 5 格）内直接清理。如果她走过去之后仍然够不着，会在公屏说「我走不到足够近的地方来操作这台机器。」
- 女仆不会自己把灰烬倒进箱子。想要全自动，给她装「隙间」类饰品或用支持 `MaidRequestItemEvent` 的存储扩展，本模组的取物逻辑走的是 TLM 的 `ItemsUtil`，能被这类扩展接管。
- 全自动维护仓（`auto_maintenance_hatch` / `cleaning_maintenance_hatch`）本来就不会坏，女仆会自动跳过。
- GTO 自带无人机维修与魔力维护基座。让女仆和它们同时看着同一台机器不会出错，只是会互相抢活。

---

## 7. 自己构建

```bash
# 需要 JDK 17（Gradle 会按 toolchain 自动准备）
./gradlew build
```

产物在 `build/libs/maid_odyssey-<version>.jar`。

`build.gradle` 里只声明了 Touhou Little Maid 一个 mod 依赖（走 Modrinth Maven）。格雷科技完全靠反射，见 `com.zackzzq.maidodyssey.gt.GtCompat`。

### 代码结构

```
com.zackzzq.maidodyssey
├─ MaidOdyssey            模组入口
├─ MaidOdysseyConfig      Forge 配置
├─ gt/
│  ├─ GtCompat            所有格雷科技反射调用集中在这里
│  ├─ GtJob               MUFFLER / MAINTENANCE
│  └─ MaintenanceProblem  6 种故障与工具的映射、标签兜底
├─ maid/
│  ├─ LittleMaidPlugin    @LittleMaidExtension，注册三个工作模式
│  ├─ task/               三个 IMaidTask 实现
│  ├─ behavior/           搜索行为 + 干活行为（Brain Behavior）
│  └─ work/               消声仓与维护仓的具体处理逻辑
└─ report/MaidReporter    带节流的公屏播报
```

## 许可

MIT
