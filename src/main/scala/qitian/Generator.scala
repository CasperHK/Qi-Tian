package qitian

import circt.stage.ChiselStage

/** Verilog 生成入口
  *
  * 執行方式：
  *   sbt "runMain qitian.Generator"
  *
  * 生成的 Verilog 會輸出至 generated/ 資料夾。
  */
object Generator extends App {
  ChiselStage.emitSystemVerilog(
    new accel.QiTianSystolicArray(),
    firtoolOpts = Array("--strip-debug-info", "--lowering-options=disallowLocalVariables")
  )
}
