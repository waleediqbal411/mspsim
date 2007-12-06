/**
 * Copyright (c) 2007, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * This file is part of MSPSim.
 *
 * $Id: ELF.java,v 1.3 2007/10/21 21:17:34 nfi Exp $
 *
 * -----------------------------------------------------------------
 *
 * ELF
 *
 * Author  : Joakim Eriksson
 * Created : Sun Oct 21 22:00:00 2007
 * Updated : $Date: 2007/10/21 21:17:34 $
 *           $Revision: 1.3 $
 */

package se.sics.mspsim.util;
import se.sics.mspsim.core.*;

public class ELFDebug {

  private Stab[] stabs;

  public static final int N_FUN = 0x24;
  public static final int N_SLINE = 0x44;
  public static final int N_SO = 0x64; // filename and path

  public static final boolean DEBUG = false;

  ELFSection dbgStab;
  ELFSection dbgStabStr;

  public ELFDebug(ELF elf, ELFSection stab, ELFSection stabstr) {
    dbgStab = stab;
    dbgStabStr = stabstr;

    int len = dbgStab.size;
    int count = len / dbgStab.entSize;
    int addr = dbgStab.offset;

    if (DEBUG) System.out.println("Number of stabs:" + count);
    stabs = new Stab[count];
    for (int i = 0, n = count; i < n; i++) {
      elf.pos = addr;
      int nI = elf.readElf32();
      String stabData = elf.dbgStabStr.getName(nI);
      int type = elf.readElf8();
      int other = elf.readElf8();
      int desc = elf.readElf16();
      int value = elf.readElf32();
      stabs[i] = new Stab(stabData, type, other, desc, value);

      if (DEBUG) {
	System.out.println("Stab: " + Utils.hex8(type) +
			   " " + stabData + " o:" + other
			   + " d:" + desc + " v:" + value);
      }
      addr += dbgStab.entSize;
    }
  }

  /* Just pick up file + some other things */
  public DebugInfo getDebugInfo(int address) {
    String currentPath = null;
    String currentFile = null;
    String currentFunction = null;
    int lastAddress = 0;
    int currentLine = 0;
    int currentLineAdr = 0;
    for (int i = 0, n = stabs.length; i < n; i++) {
      Stab stab = stabs[i];
      switch(stab.type) {
      case N_SO:
	if (stab.value < address) {
	  if (stab.data != null && stab.data.endsWith("/")) {
	    currentPath = stab.data;
	    lastAddress = stab.value;
	    currentFunction = null;
	  } else {
	    currentFile = stab.data;
	    lastAddress = stab.value;
	    currentFunction = null;
	  }
	} else {
	  /* requires sorted order of all file entries in stab section */
	  System.out.println("FILE: Already passed address..." +
			     currentPath + " " +
			     currentFile + " " + currentFunction);
	  return null;
	}
	break;
      case N_SLINE:
	if (currentPath != null) { /* only files with path... */
	  if (currentLineAdr < address) {
	    currentLine = stab.desc;
	    currentLineAdr = lastAddress + stab.value;
	    if (currentLineAdr >= address) {
	      // Finished!!!
	      if (DEBUG) {
		System.out.println("File: " + currentPath + " " + currentFile);
		System.out.println("Function: " + currentFunction);
		System.out.println("Line No: " + currentLine);
	      }
	      return new DebugInfo(currentLine, currentPath, currentFile,
				   currentFunction);
	    }
	  }
	}
	break;
      case N_FUN:
	if (stab.value < address) {
	  currentFunction = stab.data;
	  lastAddress = stab.value;
	} else {
	  System.out.println("FUN: Already passed address...");
	  return null;
	}
	break;
      }
    }
    return null;
  }

  private class Stab {

    String data;
    int type;
    int other;
    int desc;
    int value;

    Stab(String data, int type, int other, int desc, int value) {
      this.data = data;
      this.type = type;
      this.other = other;
      this.desc = desc;
      this.value = value;
    }
  }

} // ELFDebug
