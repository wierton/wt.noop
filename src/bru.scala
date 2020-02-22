package njumips
package core

import chisel3._
import chisel3.util._
import njumips.consts._
import njumips.configs._


class BRU extends Module with UnitOpConstants {
  val io = IO(new Bundle {
    val isu = Flipped(DecoupledIO(new ISU_BRU_IO))
    val wbu = DecoupledIO(new EXU_WBU_IO)
    val brinfo = ValidIO(new BRINFO_IO)
    val bypass = ValidIO(new BypassIO)
    val flush = Flipped(ValidIO(new FlushIO))
    val bp_failed = Output(Bool())
  })

  val fu_in = RegEnable(next=io.isu.bits, io.isu.fire())
  val fu_valid = RegInit(N)

  io.isu.ready := io.wbu.ready || !fu_valid

  val I = (fu_in.pc + (fu_in.se_off << 2))(31, 0)
  val J = Cat(Seq(fu_in.pc(OP_MSB, OP_LSB + 2), fu_in.addr, 0.U(2.W)))
  val JR = fu_in.rs_data

  val br_info = Mux1H(Array(
    (fu_in.fu_op === BR_EQ) -> Cat(fu_in.rs_data === fu_in.rt_data, N, I),
    (fu_in.fu_op === BR_NE) -> Cat(fu_in.rs_data =/= fu_in.rt_data, N, I),
    (fu_in.fu_op === BR_LEZ) -> Cat(fu_in.rs_data.asSInt <= 0.S, N, I),
    (fu_in.fu_op === BR_GTZ) -> Cat(fu_in.rs_data.asSInt > 0.S, N, I),
    (fu_in.fu_op === BR_LTZ) -> Cat(fu_in.rs_data.asSInt < 0.S, N, I),
    (fu_in.fu_op === BR_J) -> Cat(true.B, N, J),
    (fu_in.fu_op === BR_JAL) -> Cat(true.B, Y, J),
    (fu_in.fu_op === BR_JR) -> Cat(true.B, N, JR),
    (fu_in.fu_op === BR_JALR) -> Cat(true.B, Y, JR)))

  io.bp_failed := fu_valid && br_info(33)

  /* bypass signals */
  io.bypass.valid := io.wbu.valid
  io.bypass.bits.wen := io.wbu.bits.wb.wen
  io.bypass.bits.rd_idx := io.wbu.bits.wb.rd_idx
  io.bypass.bits.data := io.wbu.bits.wb.data

  /* wbu signals */
  io.wbu.valid := fu_valid
  io.wbu.bits.wb.pc := fu_in.pc
  io.wbu.bits.wb.wen := br_info(32)
  io.wbu.bits.wb.data := fu_in.pc + 4.U
  io.wbu.bits.wb.rd_idx := Mux(fu_in.fu_op === BR_JAL, 31.U, fu_in.rd_idx)
  io.wbu.bits.ex := 0.U.asTypeOf(io.wbu.bits.ex)

  /* branch signals */
  io.brinfo.valid := fu_valid
  io.brinfo.bits.need_br := br_info(33)
  io.brinfo.bits.br_target := br_info(31, 0)

  when (io.flush.valid || (!io.isu.fire() && io.wbu.fire())) {
    fu_valid := N
  } .elsewhen(!io.flush.valid && io.isu.fire()) {
    fu_valid := Y
  }
}
