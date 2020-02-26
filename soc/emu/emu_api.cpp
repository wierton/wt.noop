#include "emu_api.h"
#include "common.h"

#define GPRS(X) \
  X(0)  X(1)  X(2)  X(3)  X(4)  X(5)  X(6)  X(7)  \
  X(8)  X(9)  X(10) X(11) X(12) X(13) X(14) X(15) \
  X(16) X(17) X(18) X(19) X(20) X(21) X(22) X(23) \
  X(24) X(25) X(26) X(27) X(28) X(29) X(30) X(31)

void DiffTop::abort_prologue() {
  if (finished) return;
  finished = true;
  single_cycle();
}

void DiffTop::check_registers() {
#define check(cond, ...)  \
  if (!(cond)) {          \
    nemu_ptr->dump();     \
    eprintf(__VA_ARGS__); \
    abort_prologue();     \
    abort();              \
  }

  check(nemu_ptr->pc() == dut_ptr->io_commit_pc,
      "cycle %lu: pc: nemu:%08x <> pc:%08x\n", cycles,
      nemu_ptr->pc(), dut_ptr->io_commit_pc);
  check(nemu_ptr->get_instr() == dut_ptr->io_commit_instr,
      "cycle %lu: instr: nemu:%08x <> dut:%08x\n", cycles,
      nemu_ptr->get_instr(), dut_ptr->io_commit_instr);

#define GPR_TEST(i)                                     \
  check(nemu_ptr->gpr(i) == dut_ptr->io_commit_gpr_##i, \
      "cycle %lu: gpr[%d]: nemu:%08x <> dut:%08x\n",    \
      cycles, i, nemu_ptr->gpr(i),                      \
      dut_ptr->io_commit_gpr_##i);
  GPRS(GPR_TEST);
#undef GPR_TEST
}

uint32_t DiffTop::get_dut_gpr(uint32_t r) {
  switch (r) {
#define GET_GPR(i) \
  case i: return dut_ptr->io_commit_gpr_##i;
    GPRS(GET_GPR);
#undef GET_GPR
  }
  return 0;
}

device_t *DiffTop::find_device(const char *name) {
  for (device_t *head = get_device_list_head(); head;
       head = head->next) {
    if (strcmp(head->name, name) == 0) return head;
  }
  return nullptr;
}

// argv decay to the secondary pointer
DiffTop::DiffTop(int argc, const char *argv[])
    : cycles(0), finished(false) {
  /* `emu' must be created before srand */
  dut_ptr.reset(new emu);

  /* srand */
  seed = (unsigned)time(NULL) ^ (unsigned)getpid();
  srand(seed);
  srand48(seed);
  Verilated::randReset(seed);

  /* init nemu */
  nemu_ptr.reset(new NEMU_MIPS32(argc, argv));

  /* init ddr */
  device_t *ddr_dev = find_device("ddr");
  void *nemu_ddr_map = ddr_dev->map(0, ddr_size);
  memcpy(ddr, nemu_ddr_map, ddr_size);

  /* reset n cycles */
  for (int i = 0; i < 10; i ++) {
    dut_ptr->reset = 1;
    single_cycle();
    dut_ptr->reset = 0;
  }

  /* print seed */
  printf(ESC_BLUE "seed %u" ESC_RST "\n", seed);
}

void DiffTop::cycle_epilogue() {
  cycles++;
  silent_cycles++;

  if (silent_cycles >= 1000) {
    printf("cycle %lu: no commits in %ld cycles\n", cycles,
        silent_cycles);
    abort();
  }

  if (!dut_ptr->io_commit_valid) { return; }

  silent_cycles = 0;

  /* keep consistency when execute mfc0 */
  mips_instr_t instr = dut_ptr->io_commit_instr;
  if (instr.is_mfc0_count()) {
    uint32_t count0 = get_dut_gpr(instr.get_rt());
    nemu_ptr->set_c0_count(count0);
  }

  /* nemu executes one cycle */
  nemu_ptr->exec_one_instr();

  /* don't check eret and syscall instr */
  if (!instr.is_syscall() && !instr.is_eret())
    check_registers();
}

void DiffTop::single_cycle() {
  dut_ptr->clock = 0;
  dut_ptr->eval();

  dut_ptr->clock = 1;
  dut_ptr->eval();
}

int DiffTop::execute(uint64_t n) {
  while (!finished && n > 0) {
    single_cycle();
    cycle_epilogue();
    n--;
  }

  if (finished) return ret_code;
  return n == 0 ? -1 : 0;
}

void DiffTop::device_io(unsigned char valid, int addr,
    int data, char func, char wstrb, int *resp) {
  if (!valid) return;
  assert (func == MX_RD || func == MX_WR);

  /* ddr io */
  if (0 <= addr && addr < 0x08000000) {
    if (func == MX_RD) {
      // MX_RD
      memcpy(resp, &ddr[addr], 4);
    } else {
      // MX_WR
      for (int i = 0; i < 4; i++) {
        if (wstrb & (1 << i))
          ddr[addr + i] = (data >> (i * 8)) & 0xFF;
      }
    }
    return;
  }

  /* deal with dev_io */
  if (func == MX_RD) {
    *resp = paddr_peek(addr, 4);
    return;
  } else {
    if (addr == GPIO_TRAP) {
      finished = true;
      ret_code = data;
    }
  }
}
