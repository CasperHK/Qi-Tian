# 🐒 齊天 (Qi-Tian): RISC-V AI Agile Chip

**齊天 (Qi-Tian)** 是一款基於 **Chisel HDL** 開發的高性能 RISC-V AI 處理器。取名自「齊天大聖」，象徵其具備 **「七十二變」的靈活配置能力** 與 **「大鬧天宮」的強大算力**。本專案透過敏捷硬體開發流程，將通用處理器核心與專屬張量加速器（NPU）完美融合。

---

## 🌌 核心亮點
- **靈魂核心**: 搭載 64-bit RISC-V 指令集，支援自定義指令擴展。
- **筋斗雲加速**: 內建專屬 **齊天卷積加速引擎 (Qi-Tian Engine)**，採用高效能脈動陣列架構。
- **定海神針 (Memory)**: 優化後的 Scratchpad Memory 與 Cache 系統，確保大模型運算不掉速。
- **Chisel 鑄造**: 全程使用 Chisel (Scala-based HDL) 編寫，支援參數化生成與快速迭代。

---

## 🏗 系統架構 (Architecture)

```text
      [ 齊天 SoC 頂層架構 ]
      
      +------------------------+      +--------------------------+

      |    RISC-V 控制核心      |      |   齊天 AI 加速矩陣 (PE)   |
      |   (Control Plane)      | <==> |    (Data Plane / NPU)    |
      |   [ RoCC Interface ]   |      |    [ Systolic Array ]    |
      +-----------+------------+      +------------+-------------+

                  |                                |
                  +---------------+----------------+

                                  |
                        [ TileLink 高速匯流排 ]
                                  |
                   +--------------+---------------+

                   |   內存控制器 (DDR/LPDDR)      |
                   +------------------------------+
```

## 🛠 快速上手 (Quick Start)
1. 環境配置
      確保您的系統已安裝 sbt, Verilator 以及 RISC-V Toolchain。
      ```bash
      # 複製齊天專案
      git clone https://github.com
      cd Qi-Tian
      ```

2. 生成 Verilog 硬件描述
      利用 Chisel 將高階代碼轉換為底層電路：
      ```bash
      sbt "runMain qitian.Generator"
      ```

3. 仿真測試 (Simulation)
      驗證「齊天」在神經網絡運算中的正確性：
      ```bash
      sbt "testOnly qitian.AITest"
      ```

## 📂 專案結構
* src/main/scala/core/: 處理器核心邏輯。
* src/main/scala/accel/: 齊天加速器 (NPU) 模組。
* src/test/: 基於 ScalaTest 的驗證腳本。
* firmware/: 跑在齊天晶片上的底層驅動與 AI 模型權重轉換工具。

## 🤝 參與貢獻
我們歡迎任何形式的 PR 或 Issue！
* 支援 BF16/INT8 混合精度。
* 增加 Transformer 注意力機制加速模組。
* 支援多核對稱處理 (SMP)。

---

「金睛火眼，洞察萬算。」
Built with Chisel & RISC-V Spirit.
