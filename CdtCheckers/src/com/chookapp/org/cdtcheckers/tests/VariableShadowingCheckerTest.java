/*******************************************************************************
 * Copyright (c) 2010 Gil Barash
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Gil Barash  - Initial implementation
 *******************************************************************************/
package com.chookapp.org.cdtcheckers.tests;

import org.eclipse.cdt.codan.core.test.CheckerTestCase;

/**
 * Test for {@link#VariableShadowingChecker} class
 */
public class VariableShadowingCheckerTest extends CheckerTestCase {

	/*
	 * When running the com.chookapp.org.cdtcheckers.tests in "C" and not "C++" there is a problem with
	 * the scope searching... so I am forced to do all the com.chookapp.org.cdtcheckers.tests as CPP.
	 * I tested some "C" files in a real workspace and everything seems to
	 * be working fine...
	 */
	@Override
	public boolean isCpp() {
		return true;
	}

	// int a;
	// void foo(void) {
	//  int a;
	// }
	public void testGlobalVSFuncLoc() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(1, 3);
	}

	// int a;
	// class c {
	//  int a;
	// };
	public void testGlobalVSClass() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(1, 3);
	}

	// int a;
	// void foo(int a) {
	// }
	public void testGlobalVSFuncParam() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(1, 2);
	}

	// int a;
	// void foo(void) {
	//  for( int a = 1; a < 2; a++ ) {
	//  }
	// }
	public void testGlobalVSFor() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(1, 3, 1, 3); // for some reason, "a" is also found as a local variable in the scope of the function "foo"...
	}

	// class c {
	//  int a;
	//  void foo(void) {
	//   int a;
	//  }
	// };
	public void testClassVSFuncLoc() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(2, 4);
	}

	// class c {
	//  int a;
	//  void foo(int a) {
	//  }
	// };
	public void testClassVSFuncParam() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(2, 3);
	}

	// class c {
	//  int a;
	//  void foo(void) {
	//   for( int a = 1; a < 2; a++ ) {
	//   }
	//  }
	// };
	public void testClassVSFor() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(2, 4, 2, 4); // Same problem as in "testGlobalVSFor"
	}

	// class c {
	//  int a;
	//  void foo(void) {
	//  }
	// };
	// class c2 {
	//  void foo(void) {
	//   int a;
	//  }
	// };
	public void testClassVSFuncLocOK() {
		loadCodeAndRun(getAboveComment());
		checkNoErrors();
	}

	// void foo(void) {
	//  int a;
	//  for( int a = 1; a < 2; a++ ) {
	//  }
	// }
	public void testFuncLocVSFor() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(2, 3);
	}

	// void foo(int a) {
	//  for( int a = 1; a < 2; a++ ) {
	//  }
	// }
	public void testFuncParamVSFor() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(1, 2);
	}

	// void foo2(int a) {
	// }
	// void foo(void) {
	//  for( int a = 1; a < 2; a++ ) {
	//  }
	// }
	public void testFuncLocVSForOK() {
		loadCodeAndRun(getAboveComment());
		checkNoErrors();
	}

	// void foo(void) {
	//  for( int a = 1; a < 2; a++ ) {
	//  }
	//  for( int a = 1; a < 2; a++ ) {
	//  }
	// }
	public void test2ForOK() {
		loadCodeAndRun(getAboveComment());
		checkNoErrors();
	}

	// void foo(void) {
	//  for( int a = 1; a < 2; a++ ) {
	//   for( int a = 1; a < 2; a++ ) {
	//   }
	//  }
	// }
	public void testInnerFor() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(2, 3, 2, 3); // Same problem as in "testGlobalVSFor"
	}

	// int a;
	// class c {
	//  int a;
	//  void foo(void) {
	//   int a;
	//   for( int a = 1; a < 2; a++ ) {
	//   }
	//  }
	// };
	public void test5Hirarchies() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(1, 1, 1, 3, 3, 3, 5, 5, 5, 6, 6, 6);
	}

	// void foo(int a) {
	//  for( int b = 1; b < 2; b++ ) {
	//   int a;
	//  }
	// }
	public void testFuncParamVSlocal() {
		loadCodeAndRun(getAboveComment());
		checkErrorLines(1, 3, 1, 3); // Same problem as in "testGlobalVSFor"
	}
}