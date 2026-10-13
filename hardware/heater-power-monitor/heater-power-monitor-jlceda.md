# 双通道加热器功率监测：嘉立创EDA 原理图自动绘制

这份文件在嘉立创EDA专业版的**当前原理图图页**里自动画出两路加热器功率监测电路：自动放置 22 个器件、连好 62 个引脚的网络。

- 每路电路由一片 INA226 测功率，再由一只 AO3400A MOSFET（Metal-Oxide-Semiconductor Field-Effect Transistor，金属氧化物半导体场效应晶体管）做低边开关。
- 主控 MCU（Microcontroller Unit，微控制器）暂不放置，它要接的信号都用网络端口引出。
- 画图通过 [easyeda-api skill](https://github.com/easyeda/easyeda-api-skill) 的桥接服务完成：本机的 Node.js 程序把脚本发给嘉立创EDA，在 EDA 里执行 API（Application Programming Interface，应用程序接口）调用。

## 1. 怎么运行

### 准备工作

1. 在嘉立创EDA专业版里打开或新建一个**空白原理图图页**，图纸尺寸建议用 A3。如果图页上已经有别的器件，脚本会直接停止，不会改动它们。
2. 启动桥接服务：`node <easyeda-api-skill 目录>/scripts/bridge-server.mjs`。如果已经在运行，跳过这一步。
3. 确认嘉立创EDA里的 run-api-gateway 扩展已经启用，并且连上了桥接服务。
4. 本机需要 Node.js 18 或更高版本。

### 方式 A：交给本地 Claude Code

在本文件所在目录启动 Claude Code（需要已安装 easyeda-api skill），然后告诉它：

> 按 heater-power-monitor-jlceda.md 第 1 节「方式 B」的步骤，在嘉立创EDA当前原理图图页里画出电路。遇到报错先按第 5 节处理，最后把 verify 的汇总结果告诉我。

### 方式 B：命令行

1. 在本文件所在目录打开终端，先把运行器提取出来（只需要做一次）：

   ```
   node -e "const fs=require('fs');const t=fs.readFileSync('heater-power-monitor-jlceda.md','utf8');fs.writeFileSync('run-eda.mjs',t.slice(t.indexOf('//RUNNER'+'-BEGIN'),t.indexOf('//RUNNER'+'-END')))"
   ```

   这条命令在 Windows 的 cmd、PowerShell 以及 macOS/Linux 终端里都能用。也可以手动把第 8 节的代码块另存为 `run-eda.mjs`。
2. 运行全部步骤：`node run-eda.mjs`。
3. 画完后在嘉立创EDA里按 Ctrl+S 保存。

运行器会读取本文件第 9 节的脚本，按下面的顺序一步步发给嘉立创EDA执行：

| 步骤 | 作用 |
|---|---|
| `check` | 确认当前文档是原理图图页、图页为空，并按立创商城编号（C 编号）查到全部 11 种器件 |
| `place:power`、`place:mcu`、`place:ch1`、`place:ch2` | 分组放置器件（会自动避开重叠）、设置位号、添加分组标题、放置主控接口端口 |
| `wire:power`、`wire:mcu`、`wire:ch1`、`wire:ch2` | 从每个引脚引出一小段导线并打上网络标签。朝下的 GND 引脚用接地符号，朝上的电源引脚用电源符号 |
| `verify` | 逐个核对 62 个引脚接到的网络，运行 DRC（Design Rule Check，设计规则检查），再把画布定位到电路上 |
| `clean` | **清空当前图页**上的全部器件、导线和文字，用于推倒重画。这一步不在默认流程里，需要单独执行 |

- **每一步都可以重复执行。** 已经放好的器件、已经连好线的引脚会自动跳过。
- **可以只跑部分步骤。** 单独跑一步：`node run-eda.mjs wire:ch1`；从某一步接着跑：`node run-eda.mjs wire:ch2 verify`。
- **为什么分成这么多步：** 桥接服务单次请求最长等 30 秒。某一步报超时不代表失败，EDA 里可能还在执行；等几秒后单独重跑这一步即可。
- **画法说明：** 所有连接都用"短导线 + 网络标签"表示，同名网络即相连，不画长导线。这样不依赖器件符号的具体尺寸，换成别的符号也能可靠连上。画好以后可以按自己的习惯手动整理。

## 2. 电路说明

两路电路完全相同，下图以通道 1 为例：

```
VHEAT ---+--- R1 0.1R ---+-------------------+------ J2.1 (heater 1 +)
         |               |                   |
      U1 IN+      U1 IN-, U1 VBUS          D1 K
                                             |
                                           D1 A
                                             |
                          J2.2 (heater 1 -) -+------ Q1 D
                                                     Q1 S --- GND
PWM1 --- R3 100R ---+--- Q1 G
                    |
                 R5 100k --- GND
```

- **低边开关，VBUS 接加热器上端。** 开关断开时，加热器上端仍接在电源上，VBUS 电压基本不变。所以 PWM（Pulse Width Modulation，脉冲宽度调制）加热时，INA226 功率寄存器读到的就是真实的平均功率；如果改成高边开关，读数会只有实际功率的"占空比"倍。
- **两片 INA226 共用 I²C（Inter-Integrated Circuit，集成电路总线）。** U1 的 A0 接 GND，地址为 0x40；U2 的 A0 接 +3V3，地址为 0x41。两片的 ALERT 引脚并在一起，由 R9 上拉。
- **R5、R6 是栅极下拉电阻。** 主控上电复位、引脚还没配置好时，它们保证加热器不会被误开启。
- **VHEAT 不要超过 20 V。** 限制来自 C1 的 25 V 耐压；AO3400A 可以到 30 V，INA226 可以到 36 V。

## 3. 器件清单（BOM，Bill of Materials）

| 位号 | 器件 | 立创商城编号 | 用途 |
|---|---|---|---|
| U1, U2 | INA226AIDGSR（VSSOP-10） | C49851 | 电流/电压/功率监测 |
| Q1, Q2 | AO3400A（SOT-23） | C20917 | 低边开关；30 V，3.3 V 栅压可以直接驱动 |
| R1, R2 | 100 mΩ ±1% 1206 | C25334 | 分流电阻 |
| R3, R4 | 100 Ω 0603 | C22775 | 栅极串联电阻 |
| R5, R6 | 100 kΩ 0603 | C25803 | 栅极下拉 |
| R7, R8 | 4.7 kΩ 0603 | C23162 | SDA/SCL 上拉；主控板上已有上拉时可以不贴 |
| R9 | 10 kΩ 0603 | C25804 | ALERT 上拉 |
| C1 | 10 µF 25 V 0805 | C15850 | VHEAT 储能 |
| C2 | 100 nF 50 V 0603 | C14663 | VHEAT 去耦 |
| C3, C4 | 100 nF 50 V 0603 | C14663 | U1、U2 电源去耦 |
| D1, D2 | 1N4148W（SOD-123） | C81598 | 加热器续流二极管 |
| J1 | XH2.54 2P | C158012 | VHEAT 输入 |
| J2, J3 | XH2.54 2P | C158012 | 加热器 1、加热器 2 |

## 4. 网络连接表

这张表和第 9 节脚本里的定义完全一致，可以用来人工核对。

| 网络 | 说明 | 连接的引脚 |
|---|---|---|
| `VHEAT` | 加热电源（外部输入） | J1.1，C1.1，C2.1，R1.1，U1.10（IN+），R2.1，U2.10（IN+） |
| `GND` | 地 | J1.2，C1.2，C2.2，端口 GND，U1.1（A1），U1.2（A0），U1.7（GND），C3.2，Q1.2（S），R5.2，U2.1（A1），U2.7（GND），C4.2，Q2.2（S），R6.2 |
| `+3V3` | 3.3 V，来自主控板 | R7.1，R8.1，R9.1，端口 +3V3，U1.6（VS），C3.1，U2.2（A0），U2.6（VS），C4.1 |
| `SDA` | I²C 数据 | R7.2，端口 SDA，U1.4（SDA），U2.4（SDA） |
| `SCL` | I²C 时钟 | R8.2，端口 SCL，U1.5（SCL），U2.5（SCL） |
| `ALERT` | INA226 告警（开漏，两片线与） | R9.2，端口 ALERT，U1.3（ALERT），U2.3（ALERT） |
| `PWM1` | 通道 1 加热控制，接主控 GPIO（General-Purpose Input/Output，通用输入输出口） | 端口 PWM1，R3.1 |
| `PWM2` | 通道 2 加热控制，接主控 GPIO | 端口 PWM2，R4.1 |
| `HT1_TOP` | 加热器 1 上端（分流电阻之后） | R1.2，U1.8（VBUS），U1.9（IN-），J2.1，D1.1（K） |
| `HT1_BOT` | 加热器 1 下端（MOSFET 漏极） | J2.2，D1.2（A），Q1.3（D） |
| `Q1_G` | Q1 栅极 | Q1.1（G），R3.2，R5.1 |
| `HT2_TOP` | 加热器 2 上端（分流电阻之后） | R2.2，U2.8（VBUS），U2.9（IN-），J3.1，D2.1（K） |
| `HT2_BOT` | 加热器 2 下端（MOSFET 漏极） | J3.2，D2.2（A），Q2.3（D） |
| `Q2_G` | Q2 栅极 | Q2.1（G），R4.2，R6.1 |

## 5. 检查与排错

画完后先看 `verify` 的汇总，应该是：引脚总数 62、网络正确 62、错误 0。

然后人工核对三处容易出错的地方：
- **D1、D2 的方向：** 阴极（符号上有横杠的一端）应接 HTn_TOP。
- **Q1、Q2 的引脚：** G、S、D 分别接 Qn_G、GND、HTn_BOT。
- **INA226 的地址脚：** U1 的 A0 接 GND，U2 的 A0 接 +3V3。

常见报错和处理方法：

| 现象 | 处理 |
|---|---|
| `check` 报「在系统库中没有找到 Cxxxx」 | 系统库里这个编号用不了。把第 9 节脚本 `PARTS` 里对应的编号换成同规格的其他编号；也可以手动放置该器件，把位号改成第 3 节表里的位号，再接着跑 `wire` 步骤 |
| `check` 提示「查到的器件名……请核对」 | 只是器件名和预期的关键字不一样。打开该器件确认一下规格即可 |
| 提示「引脚名……与预期不符」或「已按引脚名连接」 | 库里符号的引脚编号和数据手册不一致。按提示核对这个器件，必要时手动改连线 |
| 提示「网络标签……未显示」 | 导线本身已经带着网络名，电气连接有效。想让它显示出来，可以在嘉立创EDA里手动加网络标签 |
| 报超时（timed out） | 等几秒后重跑这一步 |
| 器件重叠或对布局不满意 | 手动拖动整理；或者先跑 `clean`，修改脚本 `GROUPS` 里的坐标后重画 |
| 想整张图重画 | 先 `node run-eda.mjs clean`，再 `node run-eda.mjs` |

## 6. PCB（Printed Circuit Board，印制电路板）布局要点

- **分流电阻用开尔文（Kelvin）四线连接：** INA226 的 IN+/IN− 从 R1、R2 焊盘内侧单独引出细线，不要和大电流走线共用。VBUS 单独走一根线到加热器插座 J2、J3 的 1 脚。
- **功率回路要短而宽：** 回路是 VHEAT → 分流电阻 → 加热器插座 → MOSFET → GND。INA226 的 GND 与 MOSFET 源极的地单点汇合。
- **去耦电容和栅极电阻靠近器件：** C3、C4 紧贴 INA226 的 VS 脚，栅极电阻 R3、R4 靠近 MOSFET。

## 7. 固件要点（主控侧）

| 项目 | 设置 |
|---|---|
| I²C 地址 | U1 = 0x40，U2 = 0x41 |
| 校准寄存器（0x05） | 2560。对应分流电阻 0.1 Ω、电流 LSB（Least Significant Bit，最低有效位）20 µA；功率 LSB 为 0.5 mW，电流量程 ±0.65 A |
| 配置寄存器（0x00） | 0x4527：16 次平均，分流和总线的转换时间都是 1.1 ms，连续测量 |
| 读功率 | 功率寄存器（0x03）的读数 × 0.5 mW，就是加热器的平均功率（含约 0.1–0.5% 的分流电阻和 MOSFET 损耗，需要的话可以在固件里扣除） |
| PWM 频率 | 20 kHz 以上，让每个读数覆盖足够多的 PWM 周期 |

## 8. 运行器源码（run-eda.mjs）

```js
//RUNNER-BEGIN
// 用法：node run-eda.mjs [步骤...]   不带参数 = 按顺序执行全部步骤
// 需要 Node.js 18+，并且 easyeda-api skill 的桥接服务已启动、嘉立创EDA已通过 run-api-gateway 扩展连上
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const MD = process.env.EDA_MD || join(dirname(fileURLToPath(import.meta.url)), 'heater-power-monitor-jlceda.md');
const BEGIN = '/*EDA-' + 'SCRIPT-BEGIN*/';
const END = '/*EDA-' + 'SCRIPT-END*/';
const ALL = ['check', 'place:power', 'place:mcu', 'place:ch1', 'place:ch2', 'wire:power', 'wire:mcu', 'wire:ch1', 'wire:ch2', 'verify'];

const md = readFileSync(MD, 'utf8');
const a = md.indexOf(BEGIN);
const b = md.indexOf(END);
if (a < 0 || b < 0) {
	console.error('在 ' + MD + ' 里找不到 EDA 脚本（可用环境变量 EDA_MD 指定 .md 路径）');
	process.exit(1);
}
const script = md.slice(a, b);
const steps = process.argv.length > 2 ? process.argv.slice(2) : ALL;

async function findBridge() {
	for (let p = 49620; p <= 49629; p++) {
		try {
			const r = await fetch('http://127.0.0.1:' + p + '/health', { signal: AbortSignal.timeout(800) });
			const j = await r.json();
			if (j.service === 'easyeda-bridge') return { port: p, health: j };
		}
		catch {}
	}
	return null;
}

const bridge = await findBridge();
if (!bridge) {
	console.error('没找到桥接服务（端口 49620-49629）。请先启动：node <easyeda-api-skill 目录>/scripts/bridge-server.mjs');
	process.exit(1);
}
if (!bridge.health.edaConnected) {
	console.error('桥接服务在端口 ' + bridge.port + '，但嘉立创EDA没有连上。请确认 run-api-gateway 扩展已安装并启用。');
	process.exit(1);
}
console.log('桥接端口 ' + bridge.port + '，EDA 窗口 ' + bridge.health.activeWindowId);

for (const step of steps) {
	console.log('\n=== ' + step + ' ===');
	const code = script.replace("'__STEP__'", JSON.stringify(step));
	let j;
	try {
		const r = await fetch('http://127.0.0.1:' + bridge.port + '/execute', {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify({ code }),
		});
		j = await r.json();
	}
	catch (e) {
		console.error('请求失败：' + e.message);
		process.exit(1);
	}
	if (!j.success) {
		console.error('执行出错：' + j.error);
		if (String(j.error).includes('timed out')) console.error('超时不代表失败，EDA 里可能还在执行。等几秒后单独重跑这一步即可：node run-eda.mjs ' + step);
		process.exit(1);
	}
	const res = j.result || {};
	for (const l of res.log || []) console.log('  ' + l);
	for (const w of res.warn || []) console.log('  [警告] ' + w);
	for (const e of res.error || []) console.log('  [错误] ' + e);
	if (res.summary) console.log('  汇总：' + JSON.stringify(res.summary));
	if (res.drc && res.drc.length) console.log('  DRC：' + JSON.stringify(res.drc).slice(0, 2000));
	if (res.ok === false) {
		console.error('\n步骤 ' + step + ' 有错误，已停止。处理后可以从这一步接着跑：node run-eda.mjs ' + steps.slice(steps.indexOf(step)).join(' '));
		process.exit(1);
	}
}
console.log('\n全部完成。记得在嘉立创EDA里按 Ctrl+S 保存。');
//RUNNER-END
```

## 9. 嘉立创EDA 脚本

运行器会自动提取这段代码，把第一行的步骤名替换后发送给嘉立创EDA执行，一般不需要手动修改。器件编号在 `PARTS` 里，布局坐标在 `GROUPS` 里，坐标单位是 0.01 英寸，Y 轴向上为正。

```js
/*EDA-SCRIPT-BEGIN*/
const STEP = String('__STEP__');
const out = { step: STEP, ok: true, log: [], warn: [], error: [] };
const log = (m) => { out.log.push(m); };
const warn = (m) => { out.warn.push(m); };
const fail = (m) => { out.ok = false; out.error.push(m); };

const CT = /** @type {any} */ (typeof ESCH_PrimitiveComponentType !== 'undefined'
	? ESCH_PrimitiveComponentType
	: { COMPONENT: 'part', DRAWING: 'sheet', NET_FLAG: 'netflag', NET_PORT: 'netport' });
const SCH_PAGE = typeof EDMT_EditorDocumentType !== 'undefined' ? EDMT_EditorDocumentType.SCHEMATIC_PAGE : 1;

const PARTS = {
	INA226: { c: 'C49851', key: 'INA226', desc: 'INA226AIDGSR 电流/功率监测 VSSOP-10' },
	AO3400A: { c: 'C20917', key: 'AO3400', desc: 'AO3400A N-MOS 30V SOT-23' },
	RSHUNT: { c: 'C25334', key: '1206W4F100LT5E', desc: '100mΩ ±1% 1206 分流电阻' },
	R100: { c: 'C22775', key: '0603WAF1000T5E', desc: '100Ω 0603' },
	R100K: { c: 'C25803', key: '0603WAF1003T5E', desc: '100kΩ 0603' },
	R4K7: { c: 'C23162', key: '0603WAF4701T5E', desc: '4.7kΩ 0603' },
	R10K: { c: 'C25804', key: '0603WAF1002T5E', desc: '10kΩ 0603' },
	C100N: { c: 'C14663', key: 'CC0603KRX7R9BB104', desc: '100nF 50V X7R 0603' },
	C10U: { c: 'C15850', key: 'CL21A106KAYNNNE', desc: '10µF 25V X5R 0805' },
	D4148: { c: 'C81598', key: '1N4148W', desc: '1N4148W SOD-123' },
	XH2P: { c: 'C158012', key: 'XH', desc: 'XH2.54 2P 直插座' },
};

const P2 = (n1, n2) => [{ num: '1', net: n1 }, { num: '2', net: n2 }];
const INA = (n) => [
	{ num: '1', names: ['A1'], net: 'GND' },
	{ num: '2', names: ['A0'], net: n === 1 ? 'GND' : '+3V3' },
	{ num: '3', names: ['ALERT'], net: 'ALERT' },
	{ num: '4', names: ['SDA'], net: 'SDA' },
	{ num: '5', names: ['SCL'], net: 'SCL' },
	{ num: '6', names: ['VS', 'VCC', 'VDD'], net: '+3V3' },
	{ num: '7', names: ['GND'], net: 'GND' },
	{ num: '8', names: ['VBUS'], net: 'HT' + n + '_TOP' },
	{ num: '9', names: ['IN-', 'VIN-'], net: 'HT' + n + '_TOP' },
	{ num: '10', names: ['IN+', 'VIN+'], net: 'VHEAT' },
];
const MOS = (n) => [
	{ num: '1', names: ['G', 'GATE'], net: 'Q' + n + '_G' },
	{ num: '2', names: ['S', 'SOURCE'], net: 'GND' },
	{ num: '3', names: ['D', 'DRAIN'], net: 'HT' + n + '_BOT' },
];
const DIODE = (n) => [
	{ num: '1', names: ['K', 'CATHODE'], net: 'HT' + n + '_TOP' },
	{ num: '2', names: ['A', 'ANODE'], net: 'HT' + n + '_BOT' },
];
const channel = (n) => ({
	key: 'ch' + n,
	origin: [0, n === 1 ? -260 : -640],
	title: '通道' + n + '：加热器' + n + ' 功率测量（U' + n + ' I2C 地址 0x4' + (n - 1) + '）',
	parts: [
		{ ref: 'R' + n, part: 'RSHUNT', at: [0, 0], pins: P2('VHEAT', 'HT' + n + '_TOP') },
		{ ref: 'U' + n, part: 'INA226', at: [300, 0], pins: INA(n) },
		{ ref: 'C' + (n + 2), part: 'C100N', at: [600, 0], pins: P2('+3V3', 'GND') },
		{ ref: 'J' + (n + 1), part: 'XH2P', at: [0, -200], pins: P2('HT' + n + '_TOP', 'HT' + n + '_BOT') },
		{ ref: 'D' + n, part: 'D4148', at: [200, -200], pins: DIODE(n) },
		{ ref: 'Q' + n, part: 'AO3400A', at: [400, -200], pins: MOS(n) },
		{ ref: 'R' + (n + 2), part: 'R100', at: [600, -200], pins: P2('PWM' + n, 'Q' + n + '_G') },
		{ ref: 'R' + (n + 4), part: 'R100K', at: [800, -200], pins: P2('Q' + n + '_G', 'GND') },
	],
});
const GROUPS = [
	{
		key: 'power',
		origin: [0, 0],
		title: '电源输入：VHEAT（≤20 V，受 C1 耐压限制）',
		parts: [
			{ ref: 'J1', part: 'XH2P', at: [0, 0], pins: P2('VHEAT', 'GND') },
			{ ref: 'C1', part: 'C10U', at: [200, 0], pins: P2('VHEAT', 'GND') },
			{ ref: 'C2', part: 'C100N', at: [350, 0], pins: P2('VHEAT', 'GND') },
		],
	},
	{
		key: 'mcu',
		origin: [650, 0],
		title: 'MCU 接口（主控待放置）：I2C 上拉 + 信号端口',
		parts: [
			{ ref: 'R7', part: 'R4K7', at: [0, 0], pins: P2('+3V3', 'SDA') },
			{ ref: 'R8', part: 'R4K7', at: [150, 0], pins: P2('+3V3', 'SCL') },
			{ ref: 'R9', part: 'R10K', at: [300, 0], pins: P2('+3V3', 'ALERT') },
		],
		portsAt: [500, 20],
		ports: [['IN', '+3V3'], ['IN', 'GND'], ['BI', 'SDA'], ['IN', 'SCL'], ['IN', 'PWM1'], ['IN', 'PWM2'], ['OUT', 'ALERT']],
	},
	channel(1),
	channel(2),
];
const EXPECTED_NETS = ['VHEAT', 'GND', '+3V3', 'SDA', 'SCL', 'ALERT', 'PWM1', 'PWM2', 'HT1_TOP', 'HT1_BOT', 'Q1_G', 'HT2_TOP', 'HT2_BOT', 'Q2_G'];

const g = (o, k) => {
	if (!o) return undefined;
	const f = o['getState_' + k];
	if (typeof f === 'function') return f.call(o);
	return o[k.charAt(0).toLowerCase() + k.slice(1)];
};
const safe = async (label, fn) => {
	try {
		return await fn();
	}
	catch (e) {
		warn(label + ' 异常：' + String((e && e.message) || e));
		return undefined;
	}
};
const near = (a, b) => Math.abs(Number(a) - Number(b)) < 0.01;
const keyOf = (x, y) => (Math.round(Number(x) * 100) / 100) + ',' + (Math.round(Number(y) * 100) / 100);
const norm = (v) => String(v == null ? '' : v).toUpperCase().replace(/[−–—]/g, '-').replace(/[^A-Z0-9+\-]/g, '');
const sgn = (v) => (v > 0.5 ? 1 : (v < -0.5 ? -1 : 0));
const hit = (a, b, m) => a.minX - m < b.maxX && b.minX < a.maxX + m && a.minY - m < b.maxY && b.minY < a.maxY + m;

const devCache = {};
async function getDevice(partKey) {
	if (devCache[partKey] !== undefined) return devCache[partKey];
	const p = PARTS[partKey];
	const list = await safe('查询 ' + p.c, () => eda.lib_Device.getByLcscIds([p.c]));
	const d = (list && list[0]) || null;
	if (!d) fail(p.c + '（' + p.desc + '）在系统库中没有找到');
	else if (!norm(d.name).includes(norm(p.key))) warn(p.c + ' 查到的器件名为「' + d.name + '」，预期包含「' + p.key + '」，请核对');
	devCache[partKey] = d;
	return d;
}

async function checkDoc() {
	const doc = await safe('读取当前文档', () => eda.dmt_SelectControl.getCurrentDocumentInfo());
	if (!doc || doc.documentType !== SCH_PAGE) {
		fail('当前激活的不是原理图图页。请先在嘉立创EDA里打开（或新建）一个空白原理图图页，再运行。');
		return false;
	}
	return true;
}

async function getAnchor() {
	const sheets = await safe('读取图框', () => eda.sch_PrimitiveComponent.getAllPrimitiveId(CT.DRAWING));
	if (sheets && sheets.length) {
		const bb = await safe('图框范围', () => eda.sch_Primitive.getPrimitivesBBox([sheets[0]]));
		if (bb) return [Math.round((bb.minX + 150) / 10) * 10, Math.round((bb.maxY - 150) / 10) * 10];
	}
	return [0, 0];
}

async function partsByRef() {
	const m = new Map();
	const all = (await safe('读取器件', () => eda.sch_PrimitiveComponent.getAll(CT.COMPONENT))) || [];
	for (const c of all) {
		const d = g(c, 'Designator');
		if (d) m.set(String(d), c);
	}
	return m;
}

async function wireEnds() {
	const m = new Map();
	const ws = (await safe('读取导线', () => eda.sch_PrimitiveWire.getAll())) || [];
	for (const w of ws) {
		const net = g(w, 'Net') || '';
		const line = g(w, 'Line') || [];
		const segs = Array.isArray(line[0]) ? line : [line];
		for (const s of segs) {
			for (let i = 0; i + 1 < s.length; i += 2) m.set(keyOf(s[i], s[i + 1]), net);
		}
	}
	return m;
}

async function pinsOf(id) {
	return (await safe('读取引脚', () => eda.sch_PrimitiveComponent.getAllPinsByPrimitiveId(id))) || [];
}

function pickPin(pins, s, ref, quiet) {
	const byNum = pins.find((p) => String(g(p, 'PinNumber')) === s.num);
	const names = (s.names || []).map(norm);
	const byName = names.length ? pins.find((p) => names.includes(norm(g(p, 'PinName')))) : undefined;
	if (byName && byNum && byName !== byNum) {
		if (!quiet) warn(ref + '：引脚名 ' + g(byName, 'PinName') + ' 在 ' + g(byName, 'PinNumber') + ' 号而不是 ' + s.num + ' 号，已按引脚名连接，请核对');
		return byName;
	}
	const pin = byNum || byName;
	if (!quiet && pin && names.length && !byName) {
		const nm = norm(g(pin, 'PinName'));
		if (nm && !/^\d+$/.test(nm)) warn(ref + '.' + s.num + ' 的引脚名是 ' + g(pin, 'PinName') + '，与预期 ' + s.names.join('/') + ' 不符，请核对');
	}
	return pin;
}

function outward(pin, pins, cx, cy) {
	const px = Number(g(pin, 'X'));
	const py = Number(g(pin, 'Y'));
	const rawR = Number(g(pin, 'Rotation'));
	const r = Number.isFinite(rawR) ? ((Math.round(rawR) % 360) + 360) % 360 : -1;
	let pcx = 0;
	let pcy = 0;
	for (const p of pins) {
		pcx += Number(g(p, 'X')) / pins.length;
		pcy += Number(g(p, 'Y')) / pins.length;
	}
	if (r === 0 || r === 180) return [sgn(px - cx) || sgn(px - pcx) || (r === 0 ? -1 : 1), 0];
	if (r === 90 || r === 270) return [0, sgn(py - cy) || sgn(py - pcy) || (r === 90 ? -1 : 1)];
	const vx = px - pcx;
	const vy = py - pcy;
	return Math.abs(vx) >= Math.abs(vy) ? [sgn(vx) || 1, 0] : [0, sgn(vy) || 1];
}

async function ensureText(content, x, y) {
	const ts = (await safe('读取文本', () => eda.sch_PrimitiveText.getAll())) || [];
	if (ts.some((t) => g(t, 'Content') === content)) return;
	const t = await safe('创建文本', () => eda.sch_PrimitiveText.create(x, y, content));
	if (!t) warn('标题文字未能创建：' + content);
}

async function placeGroup(G) {
	const anchor = await getAnchor();
	const refs = await partsByRef();
	const boxes = [];
	for (const c of refs.values()) {
		const bb = await safe('器件范围', () => eda.sch_Primitive.getPrimitivesBBox([g(c, 'PrimitiveId')]));
		if (bb) boxes.push(bb);
	}
	const ox = anchor[0] + G.origin[0];
	const oy = anchor[1] + G.origin[1];
	await ensureText(G.title, ox, oy + 100);
	for (const p of G.parts) {
		if (refs.has(p.ref)) {
			log(p.ref + ' 已存在，跳过');
			continue;
		}
		const dev = await getDevice(p.part);
		if (!dev) continue;
		let x = ox + p.at[0];
		const y = oy + p.at[1];
		const comp = await safe('放置 ' + p.ref, () => eda.sch_PrimitiveComponent.create(dev, x, y, undefined, 0, false, true, true));
		if (!comp) {
			fail(p.ref + ' 放置失败（' + PARTS[p.part].c + '）');
			continue;
		}
		const id = g(comp, 'PrimitiveId');
		const m = await safe('设置位号 ' + p.ref, () => eda.sch_PrimitiveComponent.modify(id, { designator: p.ref }));
		if (!m) fail(p.ref + ' 位号设置失败，请手动把刚放置的 ' + PARTS[p.part].c + ' 改成 ' + p.ref);
		let bb = await safe('器件范围', () => eda.sch_Primitive.getPrimitivesBBox([id]));
		let tries = 0;
		while (bb && boxes.some((b) => hit(b, bb, 40)) && tries < 40) {
			x += 50;
			await safe('移动 ' + p.ref, () => eda.sch_PrimitiveComponent.modify(id, { x }));
			bb = await safe('器件范围', () => eda.sch_Primitive.getPrimitivesBBox([id]));
			tries++;
		}
		if (bb) boxes.push(bb);
		log(p.ref + ' ← ' + PARTS[p.part].c + ' ' + dev.name + ' @(' + x + ', ' + y + ')');
	}
	if (G.ports) {
		const have = (await safe('读取网络端口', () => eda.sch_PrimitiveComponent.getAll(CT.NET_PORT))) || [];
		const haveNets = have.map((c) => g(c, 'Net'));
		let i = 0;
		for (const [dir, net] of G.ports) {
			const px = ox + G.portsAt[0];
			const py = oy + G.portsAt[1] - 40 * i;
			i++;
			if (haveNets.includes(net)) continue;
			const port = await safe('网络端口 ' + net, () => eda.sch_PrimitiveComponent.createNetPort(dir, net, px, py, 0, false));
			if (!port) fail('网络端口 ' + net + ' 创建失败');
			else log('网络端口 ' + net + '（' + dir + '）');
		}
	}
}

async function flagStub(kind, net, px, py, dx, dy) {
	const ex = px + dx * 20;
	const ey = py + dy * 20;
	let f = await safe('网络标识 ' + net, () => eda.sch_PrimitiveComponent.createNetFlag(kind, net, ex, ey, 0, false));
	if (!f) return false;
	let fid = g(f, 'PrimitiveId');
	let fp = await pinsOf(fid);
	if (!fp.length) {
		await safe('删除网络标识', () => eda.sch_PrimitiveComponent.delete([fid]));
		return false;
	}
	const fx = Number(g(fp[0], 'X'));
	const fy = Number(g(fp[0], 'Y'));
	if (!near(fx, ex) || !near(fy, ey)) {
		await safe('删除网络标识', () => eda.sch_PrimitiveComponent.delete([fid]));
		f = await safe('网络标识 ' + net, () => eda.sch_PrimitiveComponent.createNetFlag(kind, net, ex + (ex - fx), ey + (ey - fy), 0, false));
		if (!f) return false;
		fid = g(f, 'PrimitiveId');
		fp = await pinsOf(fid);
		if (!fp.length || !near(g(fp[0], 'X'), ex) || !near(g(fp[0], 'Y'), ey)) {
			await safe('删除网络标识', () => eda.sch_PrimitiveComponent.delete([fid]));
			return false;
		}
	}
	let w = await safe('导线 ' + net, () => eda.sch_PrimitiveWire.create([px, py, ex, ey], net));
	if (!w) w = await safe('导线 ' + net, () => eda.sch_PrimitiveWire.create([px, py, ex, ey]));
	if (!w) {
		await safe('删除网络标识', () => eda.sch_PrimitiveComponent.delete([fid]));
		return false;
	}
	return true;
}

async function connectPin(ref, s, pin, dx, dy) {
	const px = Number(g(pin, 'X'));
	const py = Number(g(pin, 'Y'));
	const tag = ref + '.' + s.num + '(' + g(pin, 'PinName') + ')';
	const net = s.net;
	const gnd = net === 'GND' && dy === -1;
	const pwr = (net === 'VHEAT' || net === '+3V3') && dy === 1;
	if (gnd || pwr) {
		if (await flagStub(gnd ? 'Ground' : 'Power', net, px, py, dx, dy)) {
			log(tag + ' → ' + net + '（网络标识）');
			return;
		}
		warn(tag + ' 网络标识没放成功，改用网络标签');
	}
	const ex = px + dx * 30;
	const ey = py + dy * 30;
	const w = await safe('导线 ' + tag, () => eda.sch_PrimitiveWire.create([px, py, ex, ey], net));
	if (!w) {
		fail(tag + ' → ' + net + ' 导线创建失败');
		return;
	}
	const lb = await safe('网络标签 ' + net, () => eda.sch_PrimitiveAttribute.createNetLabel(ex, ey, net));
	if (!lb) warn(tag + ' 的网络标签 ' + net + ' 未显示（导线本身已带网络名，电气连接不受影响）');
	log(tag + ' → ' + net);
}

async function wireGroup(G) {
	const refs = await partsByRef();
	const ends = await wireEnds();
	for (const p of G.parts) {
		const comp = refs.get(p.ref);
		if (!comp) {
			fail(p.ref + ' 不在图页上，请先运行 place:' + G.key);
			continue;
		}
		const id = g(comp, 'PrimitiveId');
		const pins = await pinsOf(id);
		if (!pins.length) {
			fail(p.ref + ' 读不到引脚');
			continue;
		}
		const bb = await safe('器件范围', () => eda.sch_Primitive.getPrimitivesBBox([id]));
		const cx = bb ? (bb.minX + bb.maxX) / 2 : Number(g(comp, 'X'));
		const cy = bb ? (bb.minY + bb.maxY) / 2 : Number(g(comp, 'Y'));
		for (const s of p.pins) {
			const pin = pickPin(pins, s, p.ref, false);
			if (!pin) {
				fail(p.ref + ' 找不到 ' + s.num + ' 号引脚');
				continue;
			}
			if (ends.has(keyOf(g(pin, 'X'), g(pin, 'Y')))) {
				log(p.ref + '.' + s.num + ' 已有连线，跳过');
				continue;
			}
			const [dx, dy] = outward(pin, pins, cx, cy);
			await connectPin(p.ref, s, pin, dx, dy);
		}
	}
}

async function verify() {
	const refs = await partsByRef();
	const ends = await wireEnds();
	let total = 0;
	let good = 0;
	let unsure = 0;
	let minX = Infinity;
	let minY = Infinity;
	let maxX = -Infinity;
	let maxY = -Infinity;
	for (const G of GROUPS) {
		for (const p of G.parts) {
			const comp = refs.get(p.ref);
			if (!comp) {
				fail('缺少器件 ' + p.ref);
				continue;
			}
			const pins = await pinsOf(g(comp, 'PrimitiveId'));
			for (const s of p.pins) {
				total++;
				const pin = pickPin(pins, s, p.ref, true);
				if (!pin) {
					fail(p.ref + '.' + s.num + ' 找不到引脚');
					continue;
				}
				const x = Number(g(pin, 'X'));
				const y = Number(g(pin, 'Y'));
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
				const k = keyOf(x, y);
				if (!ends.has(k)) {
					fail(p.ref + '.' + s.num + ' 未连线（应接 ' + s.net + '）');
					continue;
				}
				const n = ends.get(k);
				if (!n) {
					unsure++;
					warn(p.ref + '.' + s.num + ' 所连导线没有网络名（应为 ' + s.net + '，经网络标识连接时属正常），请在画布上确认');
				}
				else if (n !== s.net) fail(p.ref + '.' + s.num + ' 接到了 ' + n + '，应为 ' + s.net);
				else good++;
			}
		}
	}
	const names = await safe('读取网络名', () => eda.sch_Net.getAllNetsName());
	if (Array.isArray(names)) {
		const missing = EXPECTED_NETS.filter((n) => !names.includes(n));
		if (missing.length) warn('网络列表里没有：' + missing.join(', ') + '（若刚画完，可保存后再运行 verify）');
	}
	const drc = await safe('DRC', () => eda.sch_Drc.check(false, false, true));
	if (Array.isArray(drc)) {
		out.drc = drc.slice(0, 30);
		log('DRC 返回 ' + drc.length + ' 条');
	}
	out.summary = { 引脚总数: total, 网络正确: good, 待人工确认: unsure, 错误: out.error.length };
	if (minX < Infinity) await safe('定位画布', () => eda.sch_Document.navigateToRegion(minX - 200, maxX + 300, maxY + 250, minY - 150));
}

async function clean() {
	const ids = [];
	for (const t of [CT.COMPONENT, CT.NET_FLAG, CT.NET_PORT]) {
		const r = await safe('读取图元', () => eda.sch_PrimitiveComponent.getAllPrimitiveId(t));
		if (r) ids.push(...r);
	}
	if (ids.length) await safe('删除器件', () => eda.sch_PrimitiveComponent.delete(ids));
	const wids = (await safe('读取导线', () => eda.sch_PrimitiveWire.getAllPrimitiveId())) || [];
	if (wids.length) await safe('删除导线', () => eda.sch_PrimitiveWire.delete(wids));
	const tids = (await safe('读取文本', () => eda.sch_PrimitiveText.getAllPrimitiveId())) || [];
	if (tids.length) await safe('删除文本', () => eda.sch_PrimitiveText.delete(tids));
	log('已清空：器件/标识/端口 ' + ids.length + '，导线 ' + wids.length + '，文本 ' + tids.length);
}

async function check() {
	const refs = await partsByRef();
	const ours = new Set(GROUPS.flatMap((G) => G.parts.map((p) => p.ref)));
	const foreign = [...refs.keys()].filter((r) => !ours.has(r));
	if (foreign.length) fail('当前图页上已有其他器件：' + foreign.slice(0, 10).join(', ') + '。请换一个空白原理图图页。');
	if (refs.size && !foreign.length) log('图页上已有本脚本放置的 ' + refs.size + ' 个器件，后续步骤会跳过已完成部分');
	for (const k of Object.keys(PARTS)) {
		const d = await getDevice(k);
		if (d) log(PARTS[k].c + ' → ' + d.name);
	}
	const a = await getAnchor();
	log('布局起点：(' + a[0] + ', ' + a[1] + ')');
}

if (await checkDoc()) {
	const [verb, gk] = STEP.split(':');
	const G = GROUPS.find((x) => x.key === gk);
	if (verb === 'check') await check();
	else if (verb === 'place' && G) await placeGroup(G);
	else if (verb === 'wire' && G) await wireGroup(G);
	else if (verb === 'verify') await verify();
	else if (verb === 'clean') await clean();
	else fail('未知步骤：' + STEP + '。可用：check, place:power|mcu|ch1|ch2, wire:power|mcu|ch1|ch2, verify, clean');
}
return out;
/*EDA-SCRIPT-END*/
```
