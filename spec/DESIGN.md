# 环保时代 2：新纪元（EcoCraft 2: The New Era）设计规格与实施计划

> **项目定位**：工业时代 2：新纪元（[IndustrialCraft²: The New Era](https://github.com/spectrum527/IndustrialCraft2-The-New-Era)，本地 `rebuild/` v1.0.6）的独立开源附属模组（[EcoCraft2-The-New-Era](https://github.com/Alexme9125/EcoCraft2-The-New-Era)）。  
> **洁净室纪律**：全部代码洁净室独立编写（不抄录任何外部反编译源码或私有参考）；全部美术按 IC2NE 工业蓝灰风格 100% 原创重绘；任何第三方二进制与研究资料严格隔离在本仓库目录之外，不纳入 Git 仓库。

---

## 1. 工程基础与四平台同步架构

| 项目 | 约定 |
| --- | --- |
| 正式名称 | EcoCraft 2: The New Era（环保时代2：新纪元） |
| Mod ID / 资源命名空间 | `ecc2` |
| Java 包名 / Maven Group | `com.ecocraft2` |
| 开源许可 | 代码 `LGPL-3.0-only`；原创重绘游戏美术 `CC BY-SA 4.0` |
| 依赖宿主 | `ic2`（IC2NE 1.0.6+，**不修改 IC2NE 本体任何文件**） |
| 同步发行目标（四平台） | **NeoForge 1.21.1**（Java 21）· **NeoForge 26.1.2**（Java 25）· **NeoForge 26.2**（Java 25）· **Forge 1.19.2**（Java 17） |
| 联动模组 | **JEI** · **EMI** · **Jade** · **WTHIT** · **The One Probe (TOP)** · 通用标签（`c:` / `forge:`）与 `FoodProperties` 食物自动兼容 |

### 1.1 仓库目录布局

采用与 IC2NE 同构的**逻辑核心与平台适配分层**，最大化共享代码并把 1.19.2 / 1.21.1 / 26.1.2 / 26.2 差异收束在薄适配层：

```text
ecocraft2/
├── spec/                         # 洁净室行为规格与数值总表
├── assets-gen/                   # Python/Pillow 程序化像素美术与数据生成器（可复现重绘）
├── assets/ecc2/                  # 重绘产出（textures / lang / sounds / gui）
├── src/
│   ├── common/                   # 纯 Java 17 领域逻辑（无 MC/Loader 依赖，可全量 JUnit 单测）
│   │   ├── solar/                # 太阳能板状态机、量子发电机多包数学、分子重组仪进度机
│   │   ├── kinetic/              # 转子磨损曲线、风/水系数模型（耐用/超频/精密）
│   │   ├── giant/                # 3×3×3 多方块校验、9 路×3 份并行调度、8 下角接口权限位图
│   │   └── gas/                  # 水煤气机、气炉、燃气发电机、菌种培育、有机发酵状态机
│   ├── minecraft/shared/         # 跨 MC 版本共享逻辑（BlockEntity/Menu/Recipe/Mixin Hook 接口）
│   ├── neoforge/
│   │   ├── shared/               # NeoForge 1.21.1 & 26.x 共用注册、Mixin、CAP、联动
│   │   ├── pre-26.2/             # 1.21.1 / 26.1.2 屏幕绘制适配
│   │   ├── 1.21.1/               # NeoForge 21.1.265 适配层 + Mixin
│   │   ├── 26.x/                 # 26.1.2 & 26.2 共用适配层
│   │   ├── 26.1.2/               # NeoForge 26.1.2.128 目标
│   │   └── 26.2/                 # NeoForge 26.2.0.95 目标
│   ├── forge/1.19.2/             # 独立 ForgeGradle 1.19.2 (Forge 43.5.0 / Java 17) 构建工程
│   └── test/                     # JUnit 核心合约测试 + 游戏内回归测试
└── gradle/                       # NeoForge 主工程 + 可选联动依赖定义
```

> **构建策略说明**：NeoForge 三目标（1.21.1 / 26.1.2 / 26.2）使用 ModDevGradle 2.0.148 在同一主 Gradle 工程构建；Forge 1.19.2 因使用 ForgeGradle 6 + Java 17，作为独立子 Gradle 工程（`forge-1.19.2/`，复用 `src/common`、`src/minecraft/shared`、`assets/ecc2`）实现同仓一键构建，彻底避免插件冲突。编译期直接引用从本地 `rebuild/` 打出的 IC2NE 1.0.6 对应版本 JAR（不改 `rebuild/` 源码）。

---

## 2. 不修改 IC2NE 1.0.6 本体的 Mixin 无缝对接层

通过 6 组精准、低侵入的 Mixin + 公共桥接接口，让 EcoCraft 2 与现行 IC2NE 1.0.6 完全原生融合：

| # | 桥接目标（IC2NE 1.0.6） | 桥接接口 / 注入点 | 达成效果 |
| --- | --- | --- | --- |
| M1 | `CableBlock`（NeoForge `gameplay.CableBlock` / Forge `forge.content.CableBlock`） | `Ecc2PowerConnectable` 标记接口注入 `updateConnection` / `connects` | EcoCraft 2 所有发电/用电方块（含巨大机器 3×3×3 外表面）与 IC2 全部线缆视觉+物理无缝连线；BE 直接实现 `WorldPowerPort`、`EuBuffer`、`MachineAccessTarget` 并走 `WorldEnergy.connect/disconnect` |
| M2 | `WorkshopKinetic` | `Ecc2KineticSource` 接口注入 `WorkshopKinetic.convert` | `ic2:kinetic_generator` 原生抽取 6 台高级风/水动能机输出的 KU（IC2NE 本体已原生实现 5 种转子的经典耐久度、原版风/水机磨损与 Tooltip，EcC2 仅维护 6 台高级动能机的倍率磨损） |
| M3 | `CellFamily` + `TransportPlatform` + `WorkshopCells` | 流体单元扩展表注入 `admits`、`resultingPath`、`readCellFluid`、`withCellContents` | ① `ic2:empty_cell`（基本液体单元）接纳 `ecc2:culture_medium`，满 1000 mB 自动转为 `ecc2:basic_culture_medium_cell`；② `ic2:fluid_cell`（通用流体单元）满 1000 mB 水煤气/天然气/培养液自动转为对应命名单元（沼气直接复用 `ic2:biogas_cell`），倒空还原为空单元 |
| M4 | `WorkshopPlans` / `WorkshopRecipes` + `ProcessingRecipes` | 动态配方注入点（装罐机、压缩机、金属成型机挤出模式） | ① `ic2:canner`（装瓶模式）、`ic2:solid_canner`、`ic2:classic_canner` 原生支持 `3×甲烷冰 + 1×通用流体单元 → 1×天然气单元`；② `ic2:compressor` & `ic2:metal_former` 原生支持 ASP 铱锭（`ecc2:iridium_ingot`）与铀锭（复用 `ic2:uranium_ingot`）配方 |
| M5 | `PortableGear` + `ArmorEffects`（及 Forge `ForgeGear`） | `Ecc2ElectricEquipment` + `Ecc2ArmorSuitBridge` | ① 3 顶太阳能头盔可放入 IC2NE MFSU/MFE/CESU/BatBox 充电槽、站上充电座、被充电电池充电；② 高级太阳能头盔视同纳米头盔、混合/终极太阳能头盔视同量子头盔，完整参与 IC2NE 纳米/量子整套减伤判定 |
| M6 | `TopProbe` / 独立注册 | 注册 `ecc2` 的 TOP Provider + Jade Plugin + WTHIT Plugin + JEI Plugin + EMI Plugin | 探针精准显示 EU 储量/发电量、KU 动能/转子余寿、多气体储罐、巨大机器 9 路进度与下角端口权限 |

---

## 3. 玩法一：高级太阳能 2（ASP 4.3.0 洁净室重写）

### 3.1 机器清单与数值（全部独立方块 ID）

| ID | 中文名 | 英文名 | 白天 EU/t | 夜间/雨天 EU/t | 储能 (EU) | 输出 Tier（单包上限） | 特殊行为 |
| --- | --- | --- | ---: | ---: | ---: | --- | --- |
| `ecc2:advanced_solar_panel` | 高级太阳能发电机 | Advanced Solar Panel | 8 | 1 | 32,000 | Tier 1 (32) | 4 格充电槽（放宽允许给自家头盔充电）；晴/夜/雨三态图标；六面输出 |
| `ecc2:hybrid_solar_panel` | 混合太阳能发电机 | Hybrid Solar Panel | 64 | 8 | 100,000 | Tier 2 (128) | 同上 |
| `ecc2:ultimate_hybrid_solar_panel` | 终极混合太阳能发电机 | Ultimate Hybrid Solar Panel | 512 | 64 | 1,000,000 | Tier 3 (512) | 同上 |
| `ecc2:quantum_solar_panel` | 量子太阳能发电机 | Quantum Solar Panel | 4,096 | 2,048 | 10,000,000 | Tier 5 (8,192) | 昼夜比 2:1；Tier 5 输出与充电 |
| `ecc2:quantum_generator` | 量子发电机 | Quantum Generator | 可调（默认 512） | — | 无缓存 | 可调 Tier 1–5 + MAX (Tier 6 = 32,768) | **仅创造模式获取（无生存配方）**；多包输出 `N = ceil(P / P_tier)`；红石反相（有信号停机）；GUI ±1/10/100（Shift ±5/50/500）；修复非玩家放置激活与非法包校验 |
| `ecc2:molecular_transformer` | 分子重组仪 | Molecular Transformer | — | — | 按当前配方动态需求 | 受电无上限（Tier 14，不爆炸） | 1 输入 + 1 输出；数据包 JSON 配方（22 条内置默认）；默认开启重制 3D 等离子体核心动画；光照 12；修复上游全部能量/吞物/溢出缺陷 |

> **上游 12 项核心缺陷全部在洁净室实现中根治**：`injectEnergy` 精确收 `min(amount, wanted)` 并退回余量、完工清空剩余缓存、输出槽满时暂停结算不吞产物、`wait` 用 `int` 且上限封顶不循环闪烁、面板储能下限钳制 `>= 0`、头盔天气状态按玩家/堆叠隔离、终极头盔无序升级保留染色、`Enriched Sunnarium Alloy` 英文名修正、移除过时的双石台阶。

### 3.2 合成材料（13 项独立物品 ID + 复用 `ic2:uranium_ingot`）

`sunnarium`（阳光化合物）、`sunnarium_part`（小块阳光化合物）、`sunnarium_alloy`（阳光合金）、`irradiant_uranium`（光辉铀锭）、`enriched_sunnarium`（富集阳光化合物）、`enriched_sunnarium_alloy`（富集阳光合金）、`irradiant_glass_pane`（光辉玻璃板）、`iridium_iron_plate`（铱铁合金板）、`reinforced_iridium_iron_plate`（强化铱铁合金板）、`irradiant_reinforced_plate`（光辉铱铁合金板）、`iridium_ingot`（铱锭，Tag `c:ingots/iridium`）、`mt_core`（分子重组核心）、`quantum_core`（量子核心）；铀锭直接复用 IC2NE 本体 `ic2:uranium_ingot`（Tag `c:ingots/uranium`）。

### 3.3 三顶太阳能头盔

| ID | 中文名 | 昼/夜发电 | Tier | 容量 (EU) | 传输 (EU/t) | 护甲与套装联动 | 专属功能 |
| --- | --- | --- | ---: | ---: | ---: | --- | --- |
| `ecc2:advanced_solar_helmet` | 高级太阳能头盔 | 8 / 1 | 3 | 1,000,000 | 3,000 | 等效纳米头盔（参与纳米套减伤，800 EU/点） | 靴→腿→胸→副手→背包→自身充能；不可染色 |
| `ecc2:hybrid_solar_helmet` | 混合太阳能头盔 | 64 / 8 | 4 | 10,000,000 | 10,000 | 等效量子头盔（参与量子套减伤，2,000 EU/点） | 全套充能 + **完整量子头盔能力**（水下补氧、清毒/清凋零/清除辐射、自动食用背包罐头喂食）+ 皮革标准染色与升级保色 |
| `ecc2:ultimate_hybrid_solar_helmet` | 终极混合太阳能头盔 | 512 / 64 | 4 | 10,000,000 | 10,000 | 等效量子头盔（参与量子套减伤，2,000 EU/点） | 同混合太阳能头盔 + 更高发电速率；无序升级自混合头盔时保留颜色 |

### 3.4 分子重组仪内置 22 条数据包配方 & 机器配方 & 配置

- **22 条默认 MT 配方**：完整保留 ASP 4.3.0 数值（凋灵骷髅头→2.5亿EU→下界之星；铁锭→900万EU→铱矿；下界岩→7万EU→火药×2；沙→5万EU→沙砾；泥土→5万EU→黏土；木炭→6万EU→煤炭；萤石粉→100万EU→小块阳光化合物；萤石→900万EU→阳光化合物；黄/蓝/红羊毛→50万EU→萤石/青金石块/红石块；煤炭→900万EU→工业钻石；工业钻石→100万EU→钻石；以及青金石→蓝宝石、红石→红宝石、钛→铬粉/锭、下界石英→赛特斯石英、铜→镍、锡→银、银→金、金→铂，外模组条目通过 `c:`/`forge:` 条件标签自动启用或静默跳过）。
- **IC2 机器注入配方（6 条）**：压缩机 `铱矿→铱锭`、`铀矿/粉碎铀/净化铀/浓缩铀燃料→铀锭`；挤出机 `强化铱板→铱锭`。
- **配置项**：完整保留四档面板昼夜功率/储量/Tier、量子发电机默认产能/Tier、头盔是否仅充护甲（修复原版键名与语义反转）、`hardRecipes`（默认 `true`）、`easyASPRecipe`（默认 `false`）、各主配方禁用开关、客户端 `drawMolecularTransformerCore`（默认 `true`）。

---

## 4. 玩法二：高级风力 / 水力动能发生器（6 台机器 + 转子经典磨损）

### 4.1 IC2 转子经典耐久与磨损基准（公开 Wiki / MC 百科对齐）

通过 Mixin 给 IC2NE 5 种转子对齐经典耐久度（单位：运行秒数 = 每 20 tick 正常扣 1 点）：

| 转子物品 | 经典耐久（点 / 现实寿命） | 风力安全上限 (MCW) | 说明 |
| --- | ---: | ---: | --- |
| `ic2:wooden_rotor`（木转子） | **10,800**（3 小时） | 60 | 仅风力机可用 |
| `ic2:bronze_rotor`（青铜转子） | **86,400**（24 小时） | 75 | 风/水通用 |
| `ic2:iron_rotor`（铁转子） | **86,400**（24 小时） | 75 | 风/水通用 |
| `ic2:steel_rotor`（钢转子） | **172,800**（48 小时） | 90 | 风/水通用 |
| `ic2:carbon_rotor`（碳转子） | **604,800**（168 小时 / 7 天） | 110 | 风/水通用 |

- **磨损公式**：仅在转子有有效输出（KU > 0 且旋转）时累积磨损；每 20 tick 基准增量 `baseWear = (风力超上限 ? 4.0 : 1.0)`；实际增量 `wear = baseWear × 机器磨损倍率`。使用浮点小数累计器（存档保存）确保 `0.5×` 精确无舍入损失；耐久归零时转子破碎移除；物品 Tooltip 显示剩余耐久百分比与预计剩余小时数。

### 4.2 六台高级动能发生器参数与合成定位

风力沿用 IC2NE 净空与高度/风况模型（水平四向），水力沿用 IC2NE 轮盘浸水、净空与河/海/深海自然水体连通模型（六向），统一通过 `Ecc2KineticSource` 对接 `ic2:kinetic_generator`（4 KU = 1 EU），均带 3D 旋转转子渲染与升级后专属蓝灰/色标机身。

| 系列 | ID（风 / 水） | 动能系数 | 转子磨损倍率 | 科技阶段与合成配方思路 |
| --- | --- | ---: | ---: | --- |
| **耐用型** | `ecc2:durable_wind_kinetic_generator`<br>`ecc2:durable_water_kinetic_generator` | **80%**（-20%） | **50%**（-50%） | **中期（略高于普通）**：原版风/水动能发生机 + 高级合金 ×4 + 钢板 ×2 + 橡胶 ×2（强调抗疲劳减震轴承） |
| **超频型** | `ecc2:overclocked_wind_kinetic_generator`<br>`ecc2:overclocked_water_kinetic_generator` | **220%**（+120%） | **200%**（+100%） | **中期（略高于普通）**：原版风/水动能发生机 + 超频升级 ×2 + 电动马达 ×2 + 高级电路板 ×2 + 铜线圈 ×2 |
| **精密型** | `ecc2:precision_wind_kinetic_generator`<br>`ecc2:precision_water_kinetic_generator` | **240%**（+140%） | **50%**（-50%） | **后期（未到 UU 阶段）**：高级机械外壳 + 耐用型或超频型（或原机）+ 强化铱板 ×2 + 阳光合金 ×2 + 碳板 ×2 + 兰波顿水晶 ×1 + 高级电路板 ×1（可经分子重组仪造铱与阳光化合物，完全无需 UU 物质） |

---

## 5. 玩法三：巨大机器（3×3×3 多方块 + 可扩展框架）

### 5.1 扩展架构与首版 5 台巨型机器

核心抽象为 `GiantMachineType` 注册表 + `GiantRecipeAdapter` 策略接口（后续新增巨型感应炉、巨型离心机、巨型成型机等仅需注册一条定义与贴图即可零改动接入）：

| 元件方块 ID | 中文名 | 对应原机 | 单份默认基准（耗时 / EU/t） | 特殊行为 |
| --- | --- | --- | --- | --- |
| `ecc2:giant_macerator_unit` | 巨型打粉机元件 | `ic2:macerator` | 300t / 2 EU/t | 实时复用 IC2 打粉机配方（含数据包/Tag） |
| `ecc2:giant_compressor_unit` | 巨型压缩机元件 | `ic2:compressor` | 300t / 2 EU/t | 实时复用 IC2 压缩机配方（含 ASP 铱/铀锭） |
| `ecc2:giant_recycler_unit` | 巨型回收机元件 | `ic2:recycler` | 45t / 1 EU/t | 复用回收黑名单，**每份独立 1/8 概率产出废料** |
| `ecc2:giant_extractor_unit` | 巨型提取机元件 | `ic2:extractor` | 300t / 2 EU/t | 实时复用 IC2 提取机配方 |
| `ecc2:giant_ore_washer_unit` | 巨型洗矿机元件 | `ic2:ore_washing_plant` | 500t / 16 EU/t + 1000 mB 水/份 | 内置 **64,000 mB** 水储罐 + 灌水/退桶槽；多产物（纯净矿粉+小撮副产+石粉）智能合堆写入 3×3 输出格 |

### 5.2 巨构组件与多方块成型规则

- **巨构组件（`ecc2:megastructure_component`）定位中期**：基础/钢构框架 + 碳板 ×2 + 高级合金 ×2 + 电子电路板 ×2 + 防爆玻璃 ×2 + 机械外壳 ×1 → 产出 **2 个**（或 1 个，保证造满 27 块元件的成本在中期工厂可稳定量产且有工业成就感；具体数量见下表）。
- **元件合成**：`1× 对应原版机器 + 1× 巨构组件`（无序合成）→ `1× 对应巨大机器元件`。
- **3×3×3 成型**：任意 27 块同类型元件堆叠为实心 3×3×3 立方体即自动成型（内部指定底层中心为逻辑核心 `CORE`，正面中心外露块为 `FRONT` 朝向显示面板并同步运行发光动画，其余 25 块为角口/外壳代理块；右键任意一块均可打开同一 GUI；成型后 5 台巨型机器拥有各自独立配色的 3×3×3 工业外观；破坏任意一块立即安全解体并掉落全部内部物品与升级）。

### 5.3 9 路独立并行（每格 3 份）与能耗/升级模型

- **槽位结构**：
  - **3×3 输入格（9 格）** + **3×3 输出格（9 格）** + **1 格放电电池槽** + **4 格标准升级槽**（巨型洗矿机额外提供 1 格水容器输入槽 + 1 格空容器返还槽）。
- **9 路独立并行**：
  - 每个输入格维护独立的 `progress[0..8]` 与 `batchSize[0..8]`。
  - 启动该格时，计算可并发份数 `batch = min(3, availableInput / recipeInputCount, outputCapacityForBatch)`（若仅有 1 份或 2 份原料，按实际 `1` 或 `2` 份正常开工；满 3 份及以上按 `3` 份一批处理！）；叠加超频升级的 `operations` 还可进一步加速。
  - **总能耗**：本 tick 正在推进的 9 路活跃总份数之和 `M = sum(activeBatch[i])`，扣电 `M × upgradedEnergyPerTick`（满载无升级打粉机 = 27 × 2 = 54 EU/t；巨型洗矿机满载 = 27 × 16 = 432 EU/t）。
  - **基础电压等级**：**HV（Tier 3，512 EU/t）**，基础缓存 `max(32_000, 27 × singleReserve)`，兼容变压器升级（可达 Tier 4/5+）、超频升级、储电升级、物品/流体弹出与拉入升级、红石反相升级。

### 5.4 八个下角访问接口与全新科技感 GUI

- **接口几何**：3×3×3 底层（`y=0`）共有 4 个角块（前左 FL、前右 FR、后左 BL、后右 BR），每个角块朝外的 2 个水平面构成 **前左/前右、后左/后右、左前/左后、右前/右后 共 8 个下角访问接口**（外观上渲染专属金属端口框与输入蓝/输出橙/双通青/禁用灰指示灯）。
- **权限管理**：每个接口面独立配置 4 种模式：**双通（Full）/ 仅输入（Input）/ 仅输出（Output）/ 禁用（Blocked）**，物品与流体遵循同一接口权限与 IC2NE 槽位自动化规则（自动入料仅进合法输入格/电池格，自动出料仅取输出格/空容器格；同步兼容 IC2NE 管道抽吸嘴/注入嘴及同面升级避让）。
- **供电**：EU 线缆可接入 3×3×3 **任意外表面**。
- **全新大尺寸科技风 GUI（`256×236` 蓝灰钢板 + 青光监测屏）**：
  - 左侧：3×3 蓝色工业输入矩阵（9 格每格下方带 3 像素青色独立微进度条 + 份数角标 `×1/×2/×3`）；
  - 中部：科技感能源核心柱（EU 实时读数 & 当前活跃总份数 `M/27`）+ 巨型洗矿机专属 64,000 mB 水柱与容器槽 + 主流动箭头指示；
  - 右侧：3×3 橙框输出矩阵 + 4 格标准升级槽 + 电池槽；
  - 右外侧标签按钮：一键切换**「8 下角接口权限俯视矩阵」弹窗**（直观标注前/后/左/右 8 个下角端口，点击循环切换 禁用/仅入/仅出/双通，与 IC2NE 齿轮按钮语言风格统一）。

---

## 6. 玩法四、五、六：水煤气、天然气与沼气工业

### 6.1 流体与流体单元体系

| 流体 ID | 中文名 | 视觉特征与属性 | 通用单元（1000 mB） | 基本单元（1000 mB） | 标签 |
| --- | --- | --- | --- | --- | --- |
| `ecc2:water_gas` | 水煤气 | 淡青灰微光气体（密度 -400、上升扩散感） | `ecc2:water_gas_cell`（水煤气单元） | — | `c:water_gas` / `ecc2:gas_fuel` |
| `ecc2:natural_gas` | 天然气 | 冰蓝清澈气体（密度 -600） | `ecc2:natural_gas_cell`（天然气单元） | — | `c:natural_gas` / `ecc2:gas_fuel` |
| `ecc2:culture_medium` | 培养液 | 翠绿生物营养液（密度 1020） | `ecc2:culture_medium_cell`（培养液单元） | `ecc2:basic_culture_medium_cell`（基本培养液单元） | `c:culture_medium` |
| `ic2:biogas`（复用） | 沼气 | 复用 IC2NE 本体沼气 | `ic2:biogas_cell`（直接复用 IC2NE 沼气单元） | — | `c:biogas` / `ecc2:gas_fuel` |

> 所有流体均配齐流体方块与铁桶（除已存在的 `ic2:biogas_bucket`），在装罐机/储罐/管道中与 `ic2:fluid_cell`（及培养液对 `ic2:empty_cell`）100% 互通。

### 6.2 机器参数总表（全部支持 IC2NE 管道、六面权限 `MachineAccessTarget` 与 TOP/Jade）

| ID | 中文名 | 工艺与核心参数 | 供电 / 输出 | 储罐 & 槽位 | 科技阶段与合成 |
| --- | --- | --- | --- | --- | --- |
| `ecc2:water_gas_machine` | 水煤气机 | `100 mB 水 + 1 煤粉` → `1000 mB 水煤气`；单次 **1,000 EU / 200 tick** | 自带顶部光伏板白天 **2 EU/t**（可插最多 2 张 `ecc2:pv_power_upgrade` 光伏板功率升级，每张 +1.5 EU/t，最高达 **5 EU/t** 白昼自给自足），同时支持 **LV (Tier 1)** 电网供电；支持 4 格升级槽 | 水罐 8,000 mB + 水煤气罐 8,000 mB；煤粉输入槽 + 水/气通用单元灌装槽 | **前中期**：基础机械外壳 + 太阳能发电机 + 通用流体单元 ×2 + 基础电路板 ×2 + 铁板 ×3 |
| `ecc2:gas_furnace` | 气炉 | 烧炼任意熔炉配方，每件固定耗 **50 mB** 水煤气 / 天然气 / 沼气；基础 **100 tick/件** | 纯燃气驱动（无 EU 需求）；支持 4 格升级槽（超频/弹出/拉入，超频按 IC2 比例提速并增加对应单件耗气或维持总耗气——**按单件仍耗 50 mB、仅加速至更高 mB/t，不额外浪费燃料**） | 燃气罐 8,000 mB；原料/产物/单元卸气槽 | **前中期**：铁炉 + 基础机械外壳 + 通用流体单元 ×2 + 基础电路板 + 铁板 ×4 |
| `ecc2:gas_generator` | 燃气发电机 | **水煤气**：10,000 EU/1000mB @ **32 EU/t**<br>**天然气**：10,000 EU/1000mB @ **64 EU/t**<br>**沼气**：16,000 EU/1000mB @ **32 EU/t** | 输出 **MV (Tier 2, 128 EU/t)**，内部缓存 **16,000 EU**，六面输出 + 1 格充电槽 | 燃气罐 16,000 mB；单元卸气/退空槽 | **前中期**：基础机械外壳 + 火力发电机 + 通用流体单元 ×2 + 电动马达 + 高级/基础电路板 ×2 + 铜导线 ×2 |
| `ecc2:culture_incubator` | 菌种培育机 | `1 骨粉或腐肉 + 100 mB 水` → `100 mB 培养液`；单次 **200 EU / 100 tick**（2 EU/t） | 输入 **LV (Tier 1)**，支持 4 格升级槽 | 水罐 8,000 mB + 培养液罐 8,000 mB；骨粉/腐肉输入槽 + 单元灌装槽（同时接受通用流体单元与基本液体单元，满 1000 mB 灌出对应命名单元） | **前中期**：基础机械外壳 + 玻璃 ×3 + 通用流体单元 ×2 + 基础电路板 + 铜板 ×2 |
| `ecc2:organic_fermenter` | 有机发酵机 | `50 mB 培养液 + 任意含 FoodProperties（饥饿值 H > 0）物品` → `30 × H mB ic2:biogas`；单次 **200 EU / 100 tick**（2 EU/t）；带容器食物（蘑菇煲/蜂蜜瓶等）自动返还空碗/空瓶 | 输入 **LV (Tier 1)**，支持 4 格升级槽 | 培养液罐 8,000 mB + 沼气罐 16,000 mB；食物输入槽 + 空容器返还槽 + 单元灌装槽 | **前中期**：基础机械外壳 + 发酵机/铜板 ×2 + 通用流体单元 ×2 + 基础电路板 ×2 + 强化玻璃 ×2 |

### 6.3 天然气：甲烷冰矿簇世界生成与装罐

- **方块 ID**：`ecc2:methane_ice_cluster`（中文：**甲烷冰** / 矿簇实体块）
- **生成群系**：深海全变种（`deep_ocean`、`deep_lukewarm_ocean`、`deep_cold_ocean`、`deep_frozen_ocean`）与冻洋（`frozen_ocean`）的海底表层/浅层泥沙、沙砾与海底基岩上方沉积层，以 3–7 块晶簇形态分布（兼容 1.19.2 / 1.21.1 / 26.x 世界生成数据包 `BiomeModifier` + `PlacedFeature`）。
- **采集与物理属性**：需要**铁镐及以上**挖掘，直接掉落 `ecc2:methane_ice_cluster` 方块本身（不吃时运增产，避免与水煤气/沼气失衡）；水下散发微弱冰蓝光（光照等级 4）；半透明晶簇质感；离开水体或高温环境下稳定保存不自毁。
- **装罐工艺**：`3× 甲烷冰 + 1× 通用流体单元 (ic2:fluid_cell)` 放入 IC2NE 的任意装罐机（`ic2:canner` 装瓶模式 / `ic2:solid_canner` / `ic2:classic_canner`）→ `1× 天然气单元 (ecc2:natural_gas_cell, 1000 mB ecc2:natural_gas)`。

---

## 7. 美术重绘视觉规范（对齐 IC2NE 工业蓝灰风格）

基于对 IC2NE 实机纹理的采样（主钢灰 `#727d8b`、亮斜边 `#d6dee6`、暗槽底 `#39424f`、深蓝 LCD `#131e28` / `#0e323c`），全部资源由 `assets-gen/` 统一程序化重绘并输出标准 PNG + mcmeta：

- **高级太阳能 2**：彻底告别 1.12 旧脏噪点，统一 IC2NE 精致斜切金属框 + 高对比度光伏晶格（高级=深蓝晶格+冷银框；混合=深钴+淡金纹；终极混合=紫黑星晶+铱银边；量子=青白量子光脉+动态微闪；分子重组仪=全新高精 3D 机械支柱 + 青白旋转能量环与呼吸粒子核心）。
- **高级风/水动能机**：沿用 IC2NE 风/水机机械壳体语言，分别以**青铜绿防磨环（耐用）**、**橙红涡轮散热栅（超频）**、**钛白鎏金精密环（精密）**做清晰视觉分级。
- **巨大机器（3×3×3）**：5 台巨型机器在未成型元件与成型后 3×3×3 外壳/角口/正面中心动画显示面板上全面差异化（打粉机=工业钢+黄黑警示条与颚齿通风口；压缩机=深枪灰+钴蓝液压压梁；回收机=钢壳+翠绿回收色带与碎料百叶；提取机=暖钢+铜黄树脂管道与黄铜饰边；洗矿机=深海灰蓝+水青液压管与水尺条），底层 4 角 8 个接口带发光颜色边框（蓝=入、橙=出、青=双通、灰=禁）；全新 256×236 科技感 GUI。
- **燃气与生物能源线**：水煤气机顶部内嵌光伏阵+双视窗储气罐；气炉蓝焰玻璃炉膛；燃气发电机双涡轮+蓝绿表盘；甲烷冰晶莹冰蓝水合物晶簇；菌种培育机透明玻璃培菌柱；有机发酵机翠绿生物罐。

---

## 8. 分阶段实施与四平台验证计划

| 阶段 | 交付内容 | 验收标准 |
| --- | --- | --- |
| **Phase 1：工程骨架与纯逻辑核心** | 搭建多平台 Gradle 架构（NeoForge 1.21.1/26.1.2/26.2 + Forge 1.19.2），完成 `src/common` 全模块状态机与数学模型 | `common` Java 17 编译通过；JUnit 合约测试 100% 通过（覆盖太阳能昼夜雨、量子多包、MT 精准注入、转子磨损 50/100/200%、巨大机器 9×3 并行与 8 端口权限、水煤气/气炉/燃气机/菌种/有机发酵全公式） |
| **Phase 2：IC2NE 1.0.6 Mixin 桥接层** | 实现 M1–M6 六组 Mixin（线缆连线、动能接口与转子经典磨损、基本/通用流体单元扩展、装罐与压缩/挤出配方注入、电动护甲充电与套装联动） | NeoForge 1.21.1 与 Forge 1.19.2 加载真实 IC2NE 1.0.6 JAR 启动无 Mixin 报错，跨模组电/动/液/甲互通验证通过 |
| **Phase 3：全量重绘美术与数据生成管线** | 编写 `assets-gen/` 管线，产出全部方块/物品/流体/盔甲/GUI/模型/世界生成/配方/中英文语言文件 | 资源校验脚本 0 缺失引用、0 大写/非法路径、中英文本地化 100% 覆盖、像素风格与 IC2NE 一致 |
| **Phase 4：六大玩法完整实现（1.21.1 主验收）** | 完成 ASP 2、6 台高级风/水动能机、5 台 3×3×3 巨大机器（含科技风 GUI 与 8 端口权限）、水煤气/天然气/沼气全链与海底甲烷冰生成 | 1.21.1 编译打包通过，完整玩法单测 + 服务端/客户端集成验收通过 |
| **Phase 5：联动模组集成** | 实现 JEI、EMI、Jade、The One Probe 插件与外部通用 Tag 兼容 | 各联动编译与运行时按需加载正常（无对应模组时不崩溃） |
| **Phase 6：四平台同步打包与回归验证** | 完成 NeoForge 26.1.2、26.2 与 Forge 1.19.2 适配层、Mixin 对齐、四平台独立打包与跨平台验证 | 四个发行 JAR 全部成功构建并产出校验清单与交接报告 |

---

> 请审阅以上设计规格（特别是配方定位、能量数值与多方块/Mixin 方案）。如需微调任何细节请直接告诉我；确认无误即可批准开工！
