package com.chookapp.org.cdtcheckers.checkers;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "com.chookapp.org.cdtcheckers.checkers.messages"; //$NON-NLS-1$
	public static String VariableShadowingChecker_at_byte;
	public static String VariableShadowingChecker_at_line;
	public static String VariableShadowingChecker_shadowed_by;
	public static String VariableShadowingChecker_shadowing;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
