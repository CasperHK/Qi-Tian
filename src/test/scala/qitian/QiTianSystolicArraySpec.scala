package qitian

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import qitian.accel.QiTianSystolicArray

// ============================================================
// QiTianSystolicArray 功能驗證測試
//
// 計算語義（Weight-Stationary）：
//   y(j) = Σᵢ W(i)(j) × x(i)    ← 這是 W 的第 j 個輸出（列）
//
// 測試流程（每個測試案例）：
//   1. load_weight 高電位一拍 → 載入權重矩陣 W
//   2. clear 高電位一拍       → 清除累加器
//   3. a_in 設值 + valid_in 高電位一拍 → 觸發計算
//   4. 等待 valid_out（約 dim 拍後） → 比對 y_out
// ============================================================
class QiTianSystolicArraySpec extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "QiTianSystolicArray"

  val dim = 4

  /** 載入 dim×dim 權重矩陣（拉高 load_weight 一拍）。 */
  def loadWeights(dut: QiTianSystolicArray, w: Seq[Seq[Int]]): Unit = {
    for (i <- 0 until dim; j <- 0 until dim)
      dut.io.weight_in(i)(j).poke(w(i)(j).S)
    dut.io.load_weight.poke(true.B)
    dut.clock.step()
    dut.io.load_weight.poke(false.B)
  }

  /** 清除所有累加器（拉高 clear 一拍）。 */
  def clearArray(dut: QiTianSystolicArray): Unit = {
    dut.io.clear.poke(true.B)
    dut.clock.step()
    dut.io.clear.poke(false.B)
  }

  /** 輸入激活向量並觸發計算（valid_in 高電位一拍）。 */
  def feedInput(dut: QiTianSystolicArray, x: Seq[Int]): Unit = {
    for (i <- 0 until dim)
      dut.io.a_in(i).poke(x(i).S)
    dut.io.valid_in.poke(true.B)
    dut.clock.step()
    dut.io.valid_in.poke(false.B)
  }

  /** 等待 valid_out 拉高後讀取 y_out（最多等待 2*dim 拍）。 */
  def waitAndRead(dut: QiTianSystolicArray): Seq[Int] = {
    var waited = 0
    while (!dut.io.valid_out.peek().litToBoolean && waited < 2 * dim) {
      dut.clock.step()
      waited += 1
    }
    (0 until dim).map(j => dut.io.y_out(j).peek().litValue.toInt)
  }

  /** 軟體計算參考值：y(j) = Σᵢ W(i)(j) × x(i)。 */
  def refMul(w: Seq[Seq[Int]], x: Seq[Int]): Seq[Int] =
    (0 until dim).map(j => (0 until dim).map(i => w(i)(j) * x(i)).sum)

  // ----------------------------------------------------------
  // 測試案例 1：單位矩陣 × 向量 → 結果等於向量本身
  // W = I₄，x = [1,2,3,4]，期望 y = [1,2,3,4]
  // ----------------------------------------------------------
  it should "return x unchanged when W is the identity matrix" in {
    test(new QiTianSystolicArray()) { dut =>
      val w = Seq(
        Seq(1, 0, 0, 0),
        Seq(0, 1, 0, 0),
        Seq(0, 0, 1, 0),
        Seq(0, 0, 0, 1)
      )
      val x        = Seq(1, 2, 3, 4)
      val expected = refMul(w, x)  // [1, 2, 3, 4]

      loadWeights(dut, w)
      clearArray(dut)
      feedInput(dut, x)
      val result = waitAndRead(dut)

      for (j <- 0 until dim)
        assert(result(j) == expected(j),
          s"y($j): got ${result(j)}, expected ${expected(j)}")
    }
  }

  // ----------------------------------------------------------
  // 測試案例 2：全 1 矩陣 × 全 1 向量 → 每個輸出均為 dim
  // W = 1₄ₓ₄，x = [1,1,1,1]，期望 y = [4,4,4,4]
  // ----------------------------------------------------------
  it should "accumulate all-ones correctly" in {
    test(new QiTianSystolicArray()) { dut =>
      val w        = Seq.fill(dim)(Seq.fill(dim)(1))
      val x        = Seq.fill(dim)(1)
      val expected = refMul(w, x)  // [4, 4, 4, 4]

      loadWeights(dut, w)
      clearArray(dut)
      feedInput(dut, x)
      val result = waitAndRead(dut)

      for (j <- 0 until dim)
        assert(result(j) == expected(j),
          s"y($j): got ${result(j)}, expected ${expected(j)}")
    }
  }

  // ----------------------------------------------------------
  // 測試案例 3：任意整數矩陣乘法（正數）
  // 驗證多元素乘積之累加正確性
  // ----------------------------------------------------------
  it should "compute a general matrix-vector product" in {
    test(new QiTianSystolicArray()) { dut =>
      val w = Seq(
        Seq(1, 2, 3, 4),
        Seq(5, 6, 7, 8),
        Seq(1, 0, 1, 0),
        Seq(0, 1, 0, 1)
      )
      val x        = Seq(2, 3, 4, 5)
      val expected = refMul(w, x)

      loadWeights(dut, w)
      clearArray(dut)
      feedInput(dut, x)
      val result = waitAndRead(dut)

      for (j <- 0 until dim)
        assert(result(j) == expected(j),
          s"y($j): got ${result(j)}, expected ${expected(j)}")
    }
  }

  // ----------------------------------------------------------
  // 測試案例 4：包含負數的 INT8 乘法
  // 驗證帶符號運算的正確性
  // ----------------------------------------------------------
  it should "handle negative weights and activations" in {
    test(new QiTianSystolicArray()) { dut =>
      val w = Seq(
        Seq(-1,  2, -3,  4),
        Seq( 5, -6,  7, -8),
        Seq(-1, -1,  1,  1),
        Seq( 1,  1, -1, -1)
      )
      val x        = Seq(-2, 3, -4, 5)
      val expected = refMul(w, x)

      loadWeights(dut, w)
      clearArray(dut)
      feedInput(dut, x)
      val result = waitAndRead(dut)

      for (j <- 0 until dim)
        assert(result(j) == expected(j),
          s"y($j): got ${result(j)}, expected ${expected(j)}")
    }
  }

  // ----------------------------------------------------------
  // 測試案例 5：clear 信號功能驗證
  // 兩次計算之間確認 clear 能正確重置累加器
  // ----------------------------------------------------------
  it should "reset accumulators correctly with clear" in {
    test(new QiTianSystolicArray()) { dut =>
      val w = Seq.fill(dim)(Seq.fill(dim)(1))
      val x = Seq.fill(dim)(1)

      // 第一次計算
      loadWeights(dut, w)
      clearArray(dut)
      feedInput(dut, x)
      waitAndRead(dut)

      // 清除後再計算一次，結果應與第一次相同
      clearArray(dut)
      feedInput(dut, x)
      val result   = waitAndRead(dut)
      val expected = refMul(w, x)  // [4, 4, 4, 4]

      for (j <- 0 until dim)
        assert(result(j) == expected(j),
          s"After clear: y($j): got ${result(j)}, expected ${expected(j)}")
    }
  }

  // ----------------------------------------------------------
  // 測試案例 6：valid_out 時序驗證
  // valid_out 應在 valid_in 後恰好 dim 拍拉高
  // ----------------------------------------------------------
  it should "assert valid_out exactly dim cycles after valid_in" in {
    test(new QiTianSystolicArray()) { dut =>
      val w = Seq.fill(dim)(Seq.fill(dim)(1))
      loadWeights(dut, w)
      clearArray(dut)

      // 拉高 valid_in 一拍
      dut.io.a_in.foreach(_.poke(1.S))
      dut.io.valid_in.poke(true.B)
      dut.clock.step()
      dut.io.valid_in.poke(false.B)

      // 前 dim-1 拍 valid_out 應為低
      for (_ <- 0 until dim - 1) {
        assert(!dut.io.valid_out.peek().litToBoolean,
          "valid_out should be low before dim cycles")
        dut.clock.step()
      }

      // 第 dim 拍（含前面的步進）valid_out 應拉高
      assert(dut.io.valid_out.peek().litToBoolean,
        s"valid_out should be high after $dim cycles")
    }
  }
}
