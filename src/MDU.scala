package MipsNPC

import chisel3._
import chisel3.util._

import Consts._
import Configure._
import IO._

class MDU extends Module with UnitOpConsts {
  val io = IO(new Bundle {
    val bypass = ValidIO(new BypassIO);
    val isu = Flipped(DecoupledIO(new ISU_MDU_IO));
    val wbu = DecoupledIO(new MDU_WBU_IO);
    val flush = Flipped(ValidIO(new FlushIO));
  });

  val datain = RegEnable(next=io.isu.bits, enable=io.isu.fire());
  val isu_valid = RegNext(next=io.isu.fire(), init=false.B);
  val in_stage_1 = isu_valid;
  val fu_op = datain.fu_op;
  val op1 = datain.op1;
  val op2 = datain.op2;
  val reg_dest_idx = datain.reg_dest_idx;

  val hi = RegInit(0.U(conf.xprlen.W));
  val lo = RegInit(0.U(conf.xprlen.W));

  val whi = WireInit(0.U(conf.xprlen.W));
  val wlo = WireInit(0.U(conf.xprlen.W));

  val mduop = Wire(new MDUOp());
  mduop := fu_op;

  io.isu.ready := !in_stage_1 || io.wbu.ready;

  io.bypass.valid := io.wbu.valid;
  io.bypass.bits.wen := io.wbu.bits.need_wb;
  io.bypass.bits.reg_dest_idx := io.wbu.bits.reg_dest_idx;
  io.bypass.bits.data := io.wbu.bits.data;

  io.wbu.bits.npc := datain.npc;
  io.wbu.bits.data := wlo;
  io.wbu.bits.need_wb := mduop.wb_reg === WB_RD;
  io.wbu.bits.reg_dest_idx := reg_dest_idx;
  io.wbu.valid := in_stage_1 && !io.flush.valid;

  when(io.isu.fire()) {
    log("[MDU] fu_op:%x, op1:%x, op2:%x\n", fu_op, op1, op2);
    log("[MDU] reg_dest_idx:%x\n", reg_dest_idx);
    log("[MDU] mf_reg:%x, fcn:%x\n", mduop.mf_reg, mduop.func);
    log("[MDU] sign:%x, wb_reg:%x\n", mduop.sign, mduop.wb_reg);
  }

  when(io.wbu.fire()) {
    log("[MDU] hi:%x, lo:%x\n", hi, lo);
    log("[MDU] need_wb:%x\n", io.wbu.bits.need_wb);
    log("[MDU] wb_data:%x\n", io.wbu.bits.data);
  }

  when(mduop.isMul()) {
    val result = Wire(UInt((2 * conf.xprlen).W));
    when(mduop.isSigned()) {
      result := (op1.asSInt * op2.asSInt).asUInt;
      } .otherwise {
        result := op1.asUInt * op2.asUInt;
      }

      whi := result(2 * conf.xprlen - 1, conf.xprlen);
      wlo := result(conf.xprlen - 1, 0);
      } .elsewhen(mduop.isDiv()) {
        when(mduop.isSigned()) {
          whi := (op1.asSInt % op2.asSInt).asUInt;
          wlo := (op1.asSInt / op2.asSInt).asUInt;
          } .otherwise {
            whi := op1.asUInt % op2.asUInt;
            wlo := op1.asUInt / op2.asUInt;
          }
          } .otherwise {
            when(mduop.mf_reg === MF_LO) {
              io.wbu.bits.data := lo;
              } .otherwise {
                io.wbu.bits.data := hi;
              }
          }

          when(io.isu.fire() && mduop.wb_reg === WB_HL) {
            hi := whi;
            lo := wlo;
          }

          when(io.isu.fire()) {
            log("[MDU] [CPC] >>>>>> %x <<<<<<\n", io.isu.bits.npc - 4.U);
          }
}
