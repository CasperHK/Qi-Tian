package qitian.accel

import chisel3._
import chisel3.util.ShiftRegister

// ============================================================
// 脈動處理單元 (Systolic Processing Element)
//
// 架構：Weight-Stationary（權重固定）
//
// 數據流：
//   - 權重 W[i][j] 載入後固定在本地寄存器中。
//   - 激活值 a_in（已在頂層完成時序偏移）進入 PE 並完成乘法。
//   - 部分和（psum）由上方 PE 傳入，加上本地乘積後向下傳遞。
//   - 最終結果由整列最底部的 PE 的 psum_out 輸出。
//
// MAC 公式（每次 valid_in 觸發）：
//   psum_out = psum_in + weight × a_in
// ============================================================
class SystolicPE extends Module {
  val io = IO(new Bundle {
    // ----- 權重載入 -----
    /** 高電位時從 weight_in 載入權重（單拍脈衝即可）。 */
    val load_weight = Input(Bool())
    /** 要載入的 INT8 權重值。 */
    val weight_in   = Input(SInt(8.W))

    // ----- 計算輸入 -----
    /** 激活值（由頂層完成 i 拍時序偏移後傳入）。 */
    val a_in        = Input(SInt(8.W))
    /** 來自上方 PE（或邊界零值）的部分和。 */
    val psum_in     = Input(SInt(32.W))
    /** 本 PE 的有效計算信號（由頂層完成 i 拍延遲後傳入）。 */
    val valid_in    = Input(Bool())
    /** 高電位時將部分和清零（異步廣播，優先級高於計算）。 */
    val clear       = Input(Bool())

    // ----- 計算輸出 -----
    /** 更新後的部分和，傳遞至下方 PE。 */
    val psum_out    = Output(SInt(32.W))
  })

  // 固定在本 PE 的權重寄存器
  val weight = RegInit(0.S(8.W))
  // 部分和累加寄存器
  val psum   = RegInit(0.S(32.W))

  // 權重載入：在 load_weight 高電位時更新
  when(io.load_weight) {
    weight := io.weight_in
  }

  // MAC 更新：clear 優先；否則在 valid_in 時執行乘累加
  when(io.clear) {
    psum := 0.S
  } .elsewhen(io.valid_in) {
    // 部分和 = 上方傳入的部分和 + 本地權重 × 激活值
    psum := io.psum_in + (io.a_in * weight)
  }

  io.psum_out := psum
}

// ============================================================
// 齊天脈動陣列 (QiTian Systolic Array)
//
// 規格：
//   - dim × dim（預設 4×4）個 SystolicPE 組成的二維陣列。
//   - 支援 INT8（SInt(8.W)）輸入、INT32（SInt(32.W)）累加。
//   - 採用 Weight-Stationary (WS) 架構。
//
// 計算語義：
//   y[j] = Σᵢ ( W[i][j] × x[i] )   (0 ≤ i, j < dim)
//   等同於對整列做內積，輸出即為 W^T × x 的結果。
//
// 時序說明：
//   ┌──────────────────────────────────────────────────────┐
//   │  時序偏移（Skewing）機制                              │
//   │  ─────────────────────────────────────────────────── │
//   │  頂層對 a_in(i) 及 valid_in 各做 i 拍延遲，使：      │
//   │    • 第 i 行 PE 在第 i 個時鐘週期才收到激活值與有效  │
//   │      信號；                                           │
//   │    • 部分和從第 0 行流向第 dim-1 行，每行延遲 1 拍；  │
//   │    • 第 dim-1 行的 psum_out 在 valid_in 拉高後        │
//   │      dim 拍即為最終結果 y。                           │
//   └──────────────────────────────────────────────────────┘
//
//   valid_out 在 valid_in 之後恰好延遲 dim 拍拉高，
//   此時 y_out 即為有效輸出。
//
// 使用流程：
//   1. 拉高 load_weight 一拍，同時驅動 weight_in → 載入 W。
//   2. 拉高 clear 一拍 → 清除所有 PE 的部分和。
//   3. 驅動 a_in，拉高 valid_in 一拍 → 啟動計算。
//   4. 等待 valid_out 拉高（即 dim 拍後）→ 讀取 y_out。
//
// @param dim       陣列邊長（預設 4）。
// @param dataWidth 輸入資料位寬（預設 8，對應 INT8）。
// ============================================================
class QiTianSystolicArray(val dim: Int = 4, val dataWidth: Int = 8) extends Module {
  /** 累加器位寬（固定 32-bit 以防溢位）。 */
  val accWidth = 32

  val io = IO(new Bundle {
    // ----- 權重載入接口 -----
    /** 拉高一拍以載入 weight_in 至所有 PE。 */
    val load_weight = Input(Bool())
    /** dim×dim 的 INT8 權重矩陣 W[row][col]。 */
    val weight_in   = Input(Vec(dim, Vec(dim, SInt(dataWidth.W))))

    // ----- 數據輸入接口 -----
    /** dim 維 INT8 輸入向量 x，x(i) 送至第 i 行 PE。 */
    val a_in        = Input(Vec(dim, SInt(dataWidth.W)))
    /** 輸入有效信號，高電位一拍觸發一次矩陣向量乘法。 */
    val valid_in    = Input(Bool())
    /** 拉高一拍以清除所有 PE 的累加器。 */
    val clear       = Input(Bool())

    // ----- 結果輸出接口 -----
    /** 輸出向量 y，y(j) = Σᵢ W[i][j] × x(i)。
      * 在 valid_out 拉高後有效。 */
    val y_out       = Output(Vec(dim, SInt(accWidth.W)))
    /** valid_in 延遲 dim 拍後拉高，表示 y_out 有效。 */
    val valid_out   = Output(Bool())
  })

  // ── 建立 dim×dim 個 PE ────────────────────────────────────
  val pes = Seq.tabulate(dim, dim) { (_, _) => Module(new SystolicPE) }

  // ── 時序偏移（Skewing）──────────────────────────────────────
  // a_in(i) 延遲 i 拍：確保第 i 行 PE 恰好在第 i 個時鐘週期
  // 收到激活值，與從上方流入的部分和完美對齊。
  // ShiftRegister(in, n) 建立 n 級暫存器鏈。
  // 寬度由 io.a_in(i) 推斷；en 預設 true.B（每拍均移位）。
  val aDelayed = VecInit(
    (0 until dim).map(i => ShiftRegister(io.a_in(i), i))
  )

  // valid_in 延遲 i 拍：第 i 行 PE 只在第 i 個週期執行 MAC。
  // 注意：此處使用兩參數版本，en 預設 true.B（每拍均移位）。
  val validRow = VecInit(
    (0 until dim).map(i => ShiftRegister(io.valid_in, i))
  )

  // ── 連接 PE 陣列 ──────────────────────────────────────────
  for (i <- 0 until dim) {
    for (j <- 0 until dim) {
      val pe = pes(i)(j)

      // 全域控制信號（廣播）
      pe.io.load_weight := io.load_weight
      pe.io.weight_in   := io.weight_in(i)(j)
      pe.io.clear       := io.clear

      // 激活值：同行 PE 共用相同延遲後的 a_in(i)
      pe.io.a_in := aDelayed(i)

      // 有效信號：同行 PE 共用相同偏移後的 valid
      pe.io.valid_in := validRow(i)

      // 部分和垂直流動：第 0 行邊界值為 0，其餘接上方 PE 輸出
      pe.io.psum_in := (if (i == 0) 0.S(accWidth.W) else pes(i - 1)(j).io.psum_out)
    }
  }

  // ── 結果輸出 ──────────────────────────────────────────────
  // 最後一行 PE 的部分和即為 y = W^T × x
  for (j <- 0 until dim) {
    io.y_out(j) := pes(dim - 1)(j).io.psum_out
  }

  // valid_out：valid_in 延遲 dim 拍後拉高，對應最後一行完成計算後的時序
  // 使用兩參數版本（en 預設 true.B）避免誤用 enable 覆載
  io.valid_out := ShiftRegister(io.valid_in, dim)
}
