package woop
package core

import chisel3._
import chisel3.util._
import woop.consts._
import woop.configs._
import woop.utils._
import woop.dumps._


class EXU extends Module {
  val io = IO(new Bundle {
    val fu_in = Flipped(DecoupledIO(new ISU_EXU_IO))
    val fu_out = DecoupledIO(new EXU_LSMDU_IO)

    val cp0 = new EXU_CP0_IO
    val cp0_rport = new CPR_RPORT
    val cp0_wport = ValidIO(new CPR_WPORT)
    val cp0_tlbr_port = new CP0_TLBR_PORT
    val cp0_tlbw_port = ValidIO(new CP0_TLBW_PORT)
    val cp0_tlbp_port = ValidIO(new CP0_TLBP_PORT)

    val daddr = new TLBTransaction
    val tlb_rport = new TLB_RPORT
    val tlb_wport = ValidIO(new TLB_WPORT)
    val tlb_pport = new TLB_PPORT

    val wb = Flipped(ValidIO(new WriteBackIO))
    val bp = ValidIO(new BypassIO)
    val can_log_now = Input(Bool())
  })

  val wait_wb = RegInit(N)
  val wait_id = RegInit(0.U(conf.INSTR_ID_SZ.W))
  val fu_in = RegEnable(next=io.fu_in.bits, enable=io.fu_in.fire())
  val fu_valid = RegInit(N)

  io.fu_in.ready := (io.fu_out.ready || !fu_valid) &&
    !(wait_wb || (io.cp0.valid && io.cp0.intr))

  val fu_type = fu_in.ops.fu_type
  val fu_op   = fu_in.ops.fu_op
  val op1     = fu_in.ops.op1
  val op2     = fu_in.ops.op2
  val op2_sa  = op2(REG_SZ - 1, 0).asUInt

  val alu_wdata = Mux1H(Array(
    (fu_op === ALU_ADD)  -> (op1 + op2).asUInt,
    (fu_op === ALU_SUB)  -> (op1 - op2).asUInt,
    (fu_op === ALU_SLL)  -> (op1 << op2_sa).asUInt,
    (fu_op === ALU_SRL)  -> (op1 >> op2_sa).asUInt,
    (fu_op === ALU_SRA)  -> (op1.asSInt >> op2_sa).asUInt,
    (fu_op === ALU_AND)  -> (op1 & op2).asUInt,
    (fu_op === ALU_OR)   -> (op1 | op2).asUInt,
    (fu_op === ALU_XOR)  -> (op1 ^ op2).asUInt,
    (fu_op === ALU_NOR)  -> ~(op1 | op2).asUInt,
    (fu_op === ALU_SLT)  -> (op1.asSInt < op2.asSInt).asUInt,
    (fu_op === ALU_SLTU) -> (op1 < op2).asUInt,
    (fu_op === ALU_LUI)  -> op2.asUInt,
    (fu_op === ALU_MOVN) -> op1.asUInt,
    (fu_op === ALU_MOVZ) -> op1.asUInt,
    (fu_op === ALU_ADD_OV) -> (op1 + op2).asUInt,
    (fu_op === ALU_SUB_OV) -> (op1 - op2).asUInt,
    (fu_op === ALU_CLZ)  -> CLZ_32(op1),
  ))
  val alu_ov = Mux1H(Array(
    (fu_op === ALU_ADD_OV)  -> ((!op1(31) && !op2(31) && alu_wdata(31)) || (op1(31) && op2(31) && !alu_wdata(31))),
    (fu_op === ALU_SUB_OV)  -> ((!op1(31) && op2(31) && alu_wdata(31)) || (op1(31) && !op2(31) && !alu_wdata(31)))))
  val alu_wen = MuxCase(!alu_ov, Array(
    (fu_op === ALU_MOVN) -> (op2 =/= 0.U),
    (fu_op === ALU_MOVZ) -> (op2 === 0.U)))
  val alu_ex = 0.U.asTypeOf(new CP0Exception)
  alu_ex.et := Mux(alu_ov, ET_Ov, ET_None)
  alu_ex.code := EC_Ov

  /* lsu addr translation */
  val lsu_op = io.fu_in.bits.ops.fu_op.asTypeOf(new LSUOp)
  val lsu_ex = io.daddr.resp.bits.ex
  io.daddr.req.valid := io.fu_in.valid &&
    io.fu_in.bits.ops.fu_type === FU_LSU
  io.daddr.req.bits.vaddr := io.fu_in.bits.ops.op1
  io.daddr.req.bits.func := lsu_op.func
  io.daddr.req.bits.len := lsu_op.len
  io.daddr.req.bits.is_aligned := lsu_op.align
  io.daddr.resp.ready := Y

  val cpr_addr = Cat(fu_in.wb.instr.rd_idx, fu_in.wb.instr.sel)

  /* mfc0 */
  io.cp0_rport.addr := cpr_addr
  val pru_wen = fu_type === FU_PRU && fu_op === PRU_MFC0
  val pru_wdata = io.cp0_rport.data

  /* mtc0 */
  io.cp0_wport.valid := io.fu_out.fire() &&
    fu_type === FU_PRU && fu_op === PRU_MTC0
  io.cp0_wport.bits.addr := cpr_addr
  io.cp0_wport.bits.data := op1

  /* pru */
  val pru_ex = WireInit(0.U.asTypeOf(new CP0Exception))
  pru_ex.et := MuxLookup(fu_op, ET_None, Array(
    PRU_SYSCALL -> ET_Sys,
    PRU_ERET    -> ET_Eret))
  pru_ex.code := Mux(fu_op === PRU_SYSCALL, EC_Sys, 0.U)

  /* cp0 */
  io.cp0.valid := io.fu_out.fire()
  io.cp0.wb := io.fu_out.bits.wb
  io.cp0.ex := MuxLookup(fu_type, fu_in.ex, Array(
    FU_ALU -> alu_ex, FU_LSU -> lsu_ex, FU_PRU -> pru_ex))
  when (io.cp0.valid && io.cp0.intr) { wait_wb := Y }
  when (io.wb.valid && io.wb.bits.id === wait_id) {
    wait_wb := N
  }

  /* fu_out */
  io.fu_out.valid := fu_valid
  io.fu_out.bits.wb := fu_in.wb
  val wb_info = MuxLookup(fu_type,
    Cat(fu_in.wb.v, fu_in.wb.wen, fu_in.wb.data), Array(
    FU_ALU -> Cat(Y, alu_wen, alu_wdata),
    FU_PRU -> Cat(pru_wen, pru_wen, pru_wdata)))
  io.fu_out.bits.wb.v := wb_info(33)
  io.fu_out.bits.wb.wen := wb_info(32)
  io.fu_out.bits.wb.data := wb_info(31, 0)
  io.fu_out.bits.ops := fu_in.ops
  io.fu_out.bits.ops.op1 := Mux(fu_in.ops.fu_type === FU_LSU, 
    io.daddr.resp.bits.paddr, fu_in.ops.op1)
  io.fu_out.bits.ops.fu_type := Mux(fu_in.ops.fu_type === FU_LSU
    && lsu_ex.et =/= ET_None, FU_PRU, FU_LSU)
  io.fu_out.bits.is_cached := Y

  /* write back */
  io.bp.valid := io.fu_out.valid
  io.bp.bits.v := io.fu_out.bits.wb.v
  io.bp.bits.rd_idx := io.fu_out.bits.wb.rd_idx
  io.bp.bits.wen := io.fu_out.bits.wb.wen
  io.bp.bits.data := io.fu_out.bits.wb.data

  /* tlbr */
  val tlbr_mask = ~io.cp0_tlbr_port.pagemask.mask.asTypeOf(UInt(32.W))
  io.tlb_rport.index := io.cp0_tlbr_port.index.index
  io.cp0_tlbw_port.valid := io.fu_out.fire() &&
    fu_type === FU_PRU && fu_op === PRU_TLBR
  io.cp0_tlbw_port.bits.pagemask._0 := 0.U
  io.cp0_tlbw_port.bits.pagemask._1 := 0.U
  io.cp0_tlbw_port.bits.pagemask.mask := tlbr_mask
  io.cp0_tlbw_port.bits.entry_hi.vpn := io.tlb_rport.entry.vpn & tlbr_mask
  io.cp0_tlbw_port.bits.entry_hi.asid := io.tlb_rport.entry.vpn & tlbr_mask
  io.cp0_tlbw_port.bits.entry_hi._0 := 0.U
  io.cp0_tlbw_port.bits.entry_lo0.v := io.tlb_rport.entry.p0.v
  io.cp0_tlbw_port.bits.entry_lo0.c := io.tlb_rport.entry.p0.c
  io.cp0_tlbw_port.bits.entry_lo0.d := io.tlb_rport.entry.p0.d
  io.cp0_tlbw_port.bits.entry_lo0.g := io.tlb_rport.entry.g
  io.cp0_tlbw_port.bits.entry_lo0.pfn := io.tlb_rport.entry.p0.pfn & tlbr_mask
  io.cp0_tlbw_port.bits.entry_lo0._0 := 0.U
  io.cp0_tlbw_port.bits.entry_lo0._1 := 0.U
  io.cp0_tlbw_port.bits.entry_lo1.v := io.tlb_rport.entry.p1.v
  io.cp0_tlbw_port.bits.entry_lo1.c := io.tlb_rport.entry.p1.c
  io.cp0_tlbw_port.bits.entry_lo1.d := io.tlb_rport.entry.p1.d
  io.cp0_tlbw_port.bits.entry_lo1.g := io.tlb_rport.entry.g
  io.cp0_tlbw_port.bits.entry_lo1.pfn := io.tlb_rport.entry.p1.pfn & tlbr_mask
  io.cp0_tlbw_port.bits.entry_lo1._0 := 0.U
  io.cp0_tlbw_port.bits.entry_lo1._1 := 0.U

  /* tlbw */
  val cpr_random = Reg(new CP0Random)
  val tlbw_mask = ~io.cp0_tlbr_port.pagemask.mask.asTypeOf(UInt(32.W))
  io.tlb_wport.valid := io.fu_out.fire() &&
    fu_type === FU_PRU && (fu_op === PRU_TLBWI ||
    fu_op === PRU_TLBWR)
  io.tlb_wport.bits.index := Mux(fu_op === PRU_TLBWI, io.cp0_tlbr_port.index.index, cpr_random.index)
  io.tlb_wport.bits.entry.pagemask := io.cp0_tlbr_port.pagemask.mask
  io.tlb_wport.bits.entry.vpn := io.cp0_tlbr_port.entry_hi.vpn & tlbw_mask
  io.tlb_wport.bits.entry.asid := io.cp0_tlbr_port.entry_hi.asid
  io.tlb_wport.bits.entry.g := io.cp0_tlbr_port.entry_lo0.g && io.cp0_tlbr_port.entry_lo1.g
  io.tlb_wport.bits.entry.p0.pfn := io.cp0_tlbr_port.entry_lo0.pfn & tlbw_mask
  io.tlb_wport.bits.entry.p0.c := io.cp0_tlbr_port.entry_lo0.c
  io.tlb_wport.bits.entry.p0.d := io.cp0_tlbr_port.entry_lo0.d
  io.tlb_wport.bits.entry.p0.v := io.cp0_tlbr_port.entry_lo0.v
  io.tlb_wport.bits.entry.p1.pfn := io.cp0_tlbr_port.entry_lo1.pfn & tlbw_mask
  io.tlb_wport.bits.entry.p1.c := io.cp0_tlbr_port.entry_lo1.c
  io.tlb_wport.bits.entry.p1.d := io.cp0_tlbr_port.entry_lo1.d
  io.tlb_wport.bits.entry.p1.v := io.cp0_tlbr_port.entry_lo1.v
  when (io.tlb_wport.valid) {
    cpr_random.index := cpr_random.index + 1.U
  }

  /* tlbp */
  io.tlb_pport.entry_hi := io.cp0_tlbr_port.entry_hi
  io.cp0_tlbp_port.valid := io.fu_out.fire() &&
    fu_type === FU_PRU && fu_op === PRU_TLBP
  io.cp0_tlbp_port.bits.index := io.tlb_pport.index

  /* pipeline logic */
  when (!io.fu_in.fire() && io.fu_out.fire()) {
    fu_valid := N
  } .elsewhen(io.fu_in.fire()) {
    fu_valid := Y
  }
}