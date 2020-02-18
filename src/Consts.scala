package MipsNPC 

import chisel3._
import chisel3.util._

import Configure._

trait MemConsts {
  // MX
  val MX_SZ = 1
  val MX_X  = 0.U(MX_SZ.W)
  val MX_RD = 0.U(MX_SZ.W)
  val MX_WR = 1.U(MX_SZ.W)

  val MT_SZ = 2
  val MT_X  = 0.U(MT_SZ.W)
  val MT_B  = 1.U(MT_SZ.W)
  val MT_H  = 2.U(MT_SZ.W)
  val MT_W  = 3.U(MT_SZ.W)
}

trait InstrConsts {
  val REG_SZ    = 5;
  val OP_MSB    = 31;
  val OP_LSB    = 26;
  val OP_SZ     = (OP_MSB - OP_LSB + 1);
  val OP_LZ     = 32 - OP_SZ;
  val RS_MSB    = 25;
  val RS_LSB    = 21;
  val RS_SZ     = (RS_MSB - RS_LSB + 1);
  val RS_LZ     = 32 - RS_SZ;
  val RT_MSB    = 20;
  val RT_LSB    = 16;
  val RT_SZ     = (RT_MSB - RT_LSB + 1);
  val RT_LZ     = 32 - RT_SZ;
  val RD_MSB    = 15;
  val RD_LSB    = 11;
  val RD_SZ     = (RD_MSB - RD_LSB + 1);
  val RD_LZ     = 32 - RD_SZ;
  val IMM_MSB   = 15;
  val IMM_LSB   = 0;
  val IMM_SZ    = (IMM_MSB - IMM_LSB + 1);
  val IMM_LZ    = 32 - IMM_SZ;
  val ADDR_MSB  = 25;
  val ADDR_LSB  = 0;
  val ADDR_SZ   = (ADDR_MSB - ADDR_LSB + 1);
  val ADDR_LZ   = 32 - ADDR_SZ;
  val SHAMT_MSB = 10;
  val SHAMT_LSB = 6;
  val SHAMT_SZ  = (SHAMT_MSB - SHAMT_LSB + 1);
  val SHAMT_LZ  = 32 - SHAMT_SZ;
  val FUNC_MSB  = 5;
  val FUNC_LSB  = 0;
  val FUNC_SZ   = (FUNC_MSB - FUNC_LSB + 1);
  val FUNC_LZ   = 32 - FUNC_SZ;
}

trait InstrPattern {
  val LUI   = BitPat("b00111100000?????????????????????")
  val ADD   = BitPat("b000000???????????????00000100000")
  val ADDU  = BitPat("b000000???????????????00000100001")
  val SUB   = BitPat("b000000???????????????00000100010")
  val SUBU  = BitPat("b000000???????????????00000100011")
  val SLT   = BitPat("b000000???????????????00000101010")
  val SLTU  = BitPat("b000000???????????????00000101011")
  val AND   = BitPat("b000000???????????????00000100100")
  val OR    = BitPat("b000000???????????????00000100101")
  val XOR   = BitPat("b000000???????????????00000100110")
  val NOR   = BitPat("b000000???????????????00000100111")
  val SLTI  = BitPat("b001010??????????????????????????")
  val SLTIU = BitPat("b001011??????????????????????????")
  val SRA   = BitPat("b00000000000???????????????000011")
  val SRL   = BitPat("b00000000000???????????????000010")
  val SLL   = BitPat("b00000000000???????????????000000")
  val SRAV  = BitPat("b000000???????????????00000000111")
  val SRLV  = BitPat("b000000???????????????00000000110")
  val SLLV  = BitPat("b000000???????????????00000000100")

  val ADDI  = BitPat("b001000??????????????????????????")
  val ADDIU = BitPat("b001001??????????????????????????")
  val ANDI  = BitPat("b001100??????????????????????????")
  val ORI   = BitPat("b001101??????????????????????????")
  val XORI  = BitPat("b001110??????????????????????????")

  val BEQ   = BitPat("b000100??????????????????????????")
  val BNE   = BitPat("b000101??????????????????????????")
  val BLEZ  = BitPat("b000110?????00000????????????????")
  val BGTZ  = BitPat("b000111?????00000????????????????")
  val BLTZ  = BitPat("b000001?????00000????????????????")
  val J     = BitPat("b000010??????????????????????????")
  val JAL   = BitPat("b000011??????????????????????????")
  val JR    = BitPat("b000000?????000000000000000001000")
  val JALR  = BitPat("b000000???????????????00000001001")

  val LW    = BitPat("b100011??????????????????????????")
  val SW    = BitPat("b101011??????????????????????????")
  val LB    = BitPat("b100000??????????????????????????")
  val LBU   = BitPat("b100100??????????????????????????")
  val SB    = BitPat("b101000??????????????????????????")
  val LH    = BitPat("b100001??????????????????????????")
  val LHU   = BitPat("b100101??????????????????????????")
  val SH    = BitPat("b101001??????????????????????????")
  val LWL   = BitPat("b100010??????????????????????????")
  val LWR   = BitPat("b100110??????????????????????????")
  val SWL   = BitPat("b101010??????????????????????????")
  val SWR   = BitPat("b101110??????????????????????????")

  val MFHI  = BitPat("b0000000000000000?????00000010000")
  val MFLO  = BitPat("b0000000000000000?????00000010010")
  val MUL   = BitPat("b011100???????????????00000000010")
  val MULT  = BitPat("b000000??????????0000000000011000")
  val MULTU = BitPat("b000000??????????0000000000011001")
  val DIV   = BitPat("b000000??????????0000000000011010")
  val DIVU  = BitPat("b000000??????????0000000000011011")
  val MOVN  = BitPat("b000000???????????????00000001011")
  val MOVZ  = BitPat("b000000???????????????00000001010")
}

trait LSUConsts extends MemConsts {
  // LSU Operation Signal
  val LSU_E_SZ = 1
  val LSU_XE  = 0.U(LSU_E_SZ.W)
  val LSU_SE  = 0.U(LSU_E_SZ.W)
  val LSU_ZE  = 1.U(LSU_E_SZ.W)

  val LSU_B_SE = Cat(MT_B, LSU_SE)
  val LSU_B_ZE = Cat(MT_B, LSU_ZE)
  val LSU_H_SE = Cat(MT_H, LSU_SE)
  val LSU_H_ZE = Cat(MT_H, LSU_ZE)
  val LSU_W_SE = Cat(MT_W, LSU_SE)
  val LSU_W_ZE = Cat(MT_W, LSU_ZE)

  val LSU_L = 0.U(1.W)
  val LSU_R = 1.U(1.W)
}

trait MDUConsts {
  val MF_X  = 0.U(1.W)
  val MF_HI = 0.U(1.W)
  val MF_LO = 1.U(1.W)

  val F_MUL = 0.U(2.W)
  val F_DIV = 1.U(2.W)
  val F_MV  = 2.U(2.W)
  val F_X   = 3.U(2.W)

  val WB_RD = 0.U(1.W)
  val WB_HL = 1.U(1.W)
}

trait ISUConsts extends MDUConsts with LSUConsts
{
  val Y      = true.B
  val N      = false.B

  val FU_TYPE_SZ = 3
  val FU_OP_SZ = 5
  val FU_OP_X = 0.U(FU_OP_SZ.W)
  // Functional Unit Select Signal
  val FU_X   = 0.U(FU_TYPE_SZ.W)
  val FU_ALU = 1.U(FU_TYPE_SZ.W)
  val FU_BRU = 2.U(FU_TYPE_SZ.W)
  val FU_LSU = 3.U(FU_TYPE_SZ.W)
  val FU_MDU = 4.U(FU_TYPE_SZ.W)

  // RS Operand Select Signal
  val OP1_SEL_SZ = 2
  val OP1_X   = 0.U(OP1_SEL_SZ.W)
  val OP1_RS = 0.U(OP1_SEL_SZ.W); // Register Source #1
  val OP1_RT = 1.U(OP1_SEL_SZ.W)
  val OP1_IMU = 2.U(OP1_SEL_SZ.W); // immediate, U-type

  // RT Operand Select Signal
  val OP2_SEL_SZ = 3
  val OP2_X   = 0.U(OP2_SEL_SZ.W)
  val OP2_RS = 0.U(OP2_SEL_SZ.W); // Register Source #2
  val OP2_RT = 1.U(OP2_SEL_SZ.W); // Register Source #2
  val OP2_IMI = 2.U(OP2_SEL_SZ.W); // immediate, I-type
  val OP2_IMZ = 3.U(OP2_SEL_SZ.W); // zero-extended immediate, I-type
  val OP2_SA = 4.U(OP2_SEL_SZ.W); // shift amount

  // REG Dest Select Signal
  val DEST_SEL_SZ = 2
  val DEST_X = 0.U(2.W)
  val DEST_RD = 0.U(2.W)
  val DEST_RT = 1.U(2.W)
}


object ISUConstsImpl extends ISUConsts { }
import ISUConstsImpl._


// UInt definition cannot occur in Bundle subclass
trait UnitOpConsts extends ISUConsts with MemConsts {
  // Branch Operation Signal
  val BR_EQ   = 0.U(FU_OP_SZ.W);  // Branch on Equal
  val BR_NE   = 1.U(FU_OP_SZ.W);  // Branch on NotEqual
  val BR_LEZ  = 2.U(FU_OP_SZ.W);  // Branch on Greater/Equal
  val BR_GTZ  = 3.U(FU_OP_SZ.W);  // Branch on Greater/Equal Unsigned
  val BR_LTZ  = 4.U(FU_OP_SZ.W);  // Branch on Less Than
  val BR_J    = 6.U(FU_OP_SZ.W);  // Jump
  val BR_JAL  = 7.U(FU_OP_SZ.W);  // Jump Register
  val BR_JR   = 8.U(FU_OP_SZ.W);  // Jump Register
  val BR_JALR = 9.U(FU_OP_SZ.W);  // Jump Register

  // ALU Operation Signal
  val ALU_ADD = 0.U(FU_OP_SZ.W)
  val ALU_SUB = 1.U(FU_OP_SZ.W)
  val ALU_SLL = 2.U(FU_OP_SZ.W)
  val ALU_SRL = 3.U(FU_OP_SZ.W)
  val ALU_SRA = 4.U(FU_OP_SZ.W)
  val ALU_AND = 5.U(FU_OP_SZ.W)
  val ALU_OR  = 6.U(FU_OP_SZ.W)
  val ALU_XOR = 7.U(FU_OP_SZ.W)
  val ALU_NOR = 8.U(FU_OP_SZ.W)
  val ALU_SLT = 9.U(FU_OP_SZ.W)
  val ALU_SLTU = 10.U(FU_OP_SZ.W)
  val ALU_COPY1 = 11.U(FU_OP_SZ.W)

  //                 1    1      2     1
  val LSU_OP_X = Cat(Y, MX_X,  MT_X, LSU_XE)
  val LSU_LW   = Cat(Y, MX_RD, MT_W, LSU_SE)
  val LSU_LB   = Cat(Y, MX_RD, MT_B, LSU_SE)
  val LSU_LBU  = Cat(Y, MX_RD, MT_B, LSU_ZE)
  val LSU_LH   = Cat(Y, MX_RD, MT_H, LSU_SE)
  val LSU_LHU  = Cat(Y, MX_RD, MT_H, LSU_ZE)
  val LSU_SW   = Cat(Y, MX_WR, MT_W, LSU_SE)
  val LSU_SB   = Cat(Y, MX_WR, MT_B, LSU_SE)
  val LSU_SH   = Cat(Y, MX_WR, MT_H, LSU_SE)
  //                       1    1      2     1
  val LSU_LWL  = Cat(N, MX_RD, MT_W, LSU_L)
  val LSU_LWR  = Cat(N, MX_RD, MT_W, LSU_R)
  val LSU_SWL  = Cat(N, MX_WR, MT_W, LSU_L)
  val LSU_SWR  = Cat(N, MX_WR, MT_W, LSU_R)


  // MDU Operation Signal   1     2      1     1
  val MDU_MFHI  = Cat(MF_HI, F_MV,  Y, WB_RD)
  val MDU_MFLO  = Cat(MF_LO, F_MV,  Y, WB_RD)
  val MDU_MUL   = Cat(MF_X,  F_MUL, Y, WB_RD)
  val MDU_MULT  = Cat(MF_X,  F_MUL, Y, WB_HL)
  val MDU_MULTU = Cat(MF_X,  F_MUL, N, WB_HL)
  val MDU_DIV   = Cat(MF_X,  F_DIV, Y, WB_HL)
  val MDU_DIVU  = Cat(MF_X,  F_DIV, N, WB_HL)
}

object Consts extends InstrPattern
  with ISUConsts
  with InstrConsts
  with MemConsts
{
}
